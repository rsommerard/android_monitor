/*******************************************************************************
 * Copyright (C) 2014 MUSE team Inria Paris - Rocquencourt
 * 
 * This file is part of UCNDataCollector.
 * 
 * UCNDataCollector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * UCNDataCollector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero Public License for more details.
 * 
 * You should have received a copy of the GNU Affero Public License
 * along with UCNDataCollector.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package fr.inria.ucn;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Periodic data collection alarm setup / tear down + handler.
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class Scheduler extends BroadcastReceiver {
	
	/** Default scheduling interval: 3min */
	public static final int DEFAULT_IV = 3;

	/*
	 * (non-Javadoc)
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		if (intent.getAction().equals(Constants.ACTION_COLLECT_ALARM)) {		
			long tsend = Helpers.getNightEnd(context);
			if (tsend>0) {
				Log.d(Constants.LOGTAG,"enter night time, disable collector");
				Helpers.stopCollector(context, true);
				
				// schedule wakeup alarm for next morning
				AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
				Intent aintent = new Intent(Constants.ACTION_SCHEDULE_ALARM);
				PendingIntent pi = PendingIntent.getBroadcast(context, 0, aintent, PendingIntent.FLAG_ONE_SHOT);
				am.set(AlarmManager.RTC_WAKEUP, tsend, pi);
			} else {
				// run measurements
				Helpers.doSample(context, true);
			}
		} else if (intent.getAction().equals(Constants.ACTION_SCHEDULE_ALARM)) {
			// re-start the collector after night time (check user pref in case they changed it at nigth)
			if (prefs.getBoolean(Constants.PREF_HIDDEN_ENABLED, false)) {
				Log.d(Constants.LOGTAG,"collector schedule alarm went off, re-schedule periodic alarm");
				Helpers.startCollector(context, true);
			}
		} else if (intent.getAction().equals(Constants.ACTION_UPLOAD_ALARM)) {
			// scheduled upload
			Log.d(Constants.LOGTAG,"data upload alarm went off");
			Helpers.doUpload(context, true);
		}
	}
	
	/**
	 * Schedule alarms.
	 * @param c
	 */
	public static synchronized void setAlarms(Context c) {
		AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);

		// periodic collector alarm (configured exact interval)
		String iv = PreferenceManager.getDefaultSharedPreferences(c).getString(Constants.PREF_INTERVAL, Integer.toString(DEFAULT_IV));
		long interval = Integer.parseInt(iv) * 60 * 1000; // min -> s -> ms
		Intent intent = new Intent(Constants.ACTION_COLLECT_ALARM);
		PendingIntent pi = PendingIntent.getBroadcast(c, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		Log.d(Constants.LOGTAG,"next collection scheduled in " + (interval/1000) + " s");
		am.setRepeating(
				AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime()+interval,
		        interval, 
		        pi);
		
		// periodic upload alarm (roughly twice a day)
		Intent intent2 = new Intent(Constants.ACTION_UPLOAD_ALARM);
		PendingIntent pi2 = PendingIntent.getBroadcast(c, 0, intent2, PendingIntent.FLAG_CANCEL_CURRENT);
		Log.d(Constants.LOGTAG,"next upload scheduled in " + AlarmManager.INTERVAL_HALF_DAY/(1000*60*60) + " h");
		am.setInexactRepeating(
				AlarmManager.ELAPSED_REALTIME,
				SystemClock.elapsedRealtime()+AlarmManager.INTERVAL_HALF_DAY, 
				AlarmManager.INTERVAL_HALF_DAY, 
				pi2);
	}

	/**
	 * Cancel all alarms.
	 * @param c
	 */
	public static synchronized void cancelAllAlarms(Context c) {
		AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
		
		Intent intent = new Intent(Constants.ACTION_COLLECT_ALARM);
		PendingIntent pi = PendingIntent.getBroadcast(c, 0, intent, PendingIntent.FLAG_NO_CREATE);
		if (pi!=null) {
			am.cancel(pi);
		}
		Intent intent2 = new Intent(Constants.ACTION_UPLOAD_ALARM);
		PendingIntent pi2 = PendingIntent.getBroadcast(c, 0, intent2, PendingIntent.FLAG_NO_CREATE);
		if (pi2!=null) {
			am.cancel(pi2);
		}
		Intent intent3 = new Intent(Constants.ACTION_SCHEDULE_ALARM);
		PendingIntent pi3 = PendingIntent.getBroadcast(c, 0, intent3, PendingIntent.FLAG_NO_CREATE);
		if (pi3!=null) {
			am.cancel(pi3);
		}
		Log.d(Constants.LOGTAG,"alarms cancelled");
	}
	
	/**
	 * Cancel all alarms.
	 * @param c
	 */
	public static synchronized void cancelPeriodicAlarms(Context c) {
		AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
		
		Intent intent = new Intent(Constants.ACTION_COLLECT_ALARM);
		PendingIntent pi = PendingIntent.getBroadcast(c, 0, intent, PendingIntent.FLAG_NO_CREATE);
		if (pi!=null) {
			am.cancel(pi);
		}
		Intent intent2 = new Intent(Constants.ACTION_UPLOAD_ALARM);
		PendingIntent pi2 = PendingIntent.getBroadcast(c, 0, intent2, PendingIntent.FLAG_NO_CREATE);
		if (pi2!=null) {
			am.cancel(pi2);
		}
		
		// run one more upload few seconds later
		Intent aintent = new Intent(Constants.ACTION_UPLOAD_ALARM);
		PendingIntent pi3 = PendingIntent.getBroadcast(c, 0, aintent, PendingIntent.FLAG_ONE_SHOT);
		am.set(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+30000,pi3);
		
		Log.d(Constants.LOGTAG,"alarms cancelled");
	}
	
	/**
	 * Test if the prediodic alarm is scheduled.
	 * @param c
	 * @return
	 */
	public static synchronized boolean isScheduled(Context c) {
		Intent intent = new Intent(Constants.ACTION_COLLECT_ALARM);
		PendingIntent pi = PendingIntent.getBroadcast(c, 0, intent, PendingIntent.FLAG_NO_CREATE);
		return (pi!=null);
	}
	
}
