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

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

/**
 * Periodic data collection alarm setup / tear down + handler.
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class Scheduler extends BroadcastReceiver {
	
	/** Default scheduling interval: 5min*/
	public static final int DEFAULT_IV = 5;

	/* default scheduling interval: 6h (in millis) */
	private static final long DEFAULT_UPLOAD_IV = 6 * 60 * 60 * 1000;

	/*
	 * (non-Javadoc)
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Constants.ACTION_COLLECT_ALARM)) {
			// check if night started
			SharedPreferences prefs = Helpers.getUserSettings(context);
			Calendar now = Calendar.getInstance();

			Calendar nightstart = Calendar.getInstance();
			nightstart.roll(Calendar.HOUR_OF_DAY, -1*nightstart.get(Calendar.HOUR_OF_DAY));
			nightstart.roll(Calendar.MINUTE, -1*nightstart.get(Calendar.MINUTE));
			nightstart.roll(Calendar.SECOND, -1*nightstart.get(Calendar.SECOND));
			nightstart.roll(Calendar.MILLISECOND, -1*nightstart.get(Calendar.MILLISECOND));
			nightstart.add(Calendar.SECOND, prefs.getInt(Constants.PREF_NIGHT_START, 23*3600));

			Calendar nightstop = Calendar.getInstance();
			nightstop.roll(Calendar.HOUR_OF_DAY, -1*nightstart.get(Calendar.HOUR_OF_DAY));
			nightstop.roll(Calendar.MINUTE, -1*nightstart.get(Calendar.MINUTE));
			nightstop.roll(Calendar.SECOND, -1*nightstart.get(Calendar.SECOND));
			nightstop.roll(Calendar.MILLISECOND, -1*nightstart.get(Calendar.MILLISECOND));
			nightstart.add(Calendar.SECOND, prefs.getInt(Constants.PREF_NIGHT_STOP, 6*3600));
			if (nightstop.before(nightstart))
				nightstop.add(Calendar.HOUR, 24);
			
			if (now.after(nightstart) && now.before(nightstop)) {
				// entering night
				Log.d(Constants.LOGTAG,"entered night time, disabling alarms");
				
				// get wake-lock to keep the CPU up
				Helpers.acquireLock(context);
				
				// start the service to do the actual work
				Intent sintent = new Intent(context, CollectorService.class);
				sintent.setAction(Constants.ACTION_SCHEDULE);
				sintent.putExtra(Constants.INTENT_EXTRA_SCHEDULER_START, false); // remove listeners etc
				sintent.putExtra(Constants.INTENT_EXTRA_RELEASE_WL, true); // request service to release the wl
				context.startService(sintent);
				
				// schedule wakeup alarm
				AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
				Intent aintent = new Intent(Constants.ACTION_SCHEDULE_ALARM);
				PendingIntent pi = PendingIntent.getBroadcast(context, 0, aintent, PendingIntent.FLAG_ONE_SHOT);

				Log.d(Constants.LOGTAG,"wakeup alarm in " + ((nightstop.getTimeInMillis()-now.getTimeInMillis())/1000) + " s");

				am.set(AlarmManager.RTC_WAKEUP,nightstop.getTimeInMillis(),pi);
				
			} else {
				// normal periodic wakeup
				
				// get wake-lock to keep the CPU up
				Helpers.acquireLock(context);
				
				// start the service to do the actual work
				Intent sintent = new Intent(context, CollectorService.class);
				sintent.setAction(Constants.ACTION_COLLECT);
				sintent.putExtra(Constants.INTENT_EXTRA_RELEASE_WL, true); // request service to release the wl
				context.startService(sintent);
			}
			
		} else if (intent.getAction().equals(Constants.ACTION_SCHEDULE_ALARM)) {
			// re-start the collector after night time
			Log.d(Constants.LOGTAG,"collector schedule alarm went off, re-schedule periodic alarm");

			// get wake-lock to keep the CPU up
			Helpers.acquireLock(context);
			
			// start the service to do the actual work
			Intent sintent = new Intent(context, CollectorService.class);
			sintent.setAction(Constants.ACTION_SCHEDULE);
			sintent.putExtra(Constants.INTENT_EXTRA_SCHEDULER_START, true);
			sintent.putExtra(Constants.INTENT_EXTRA_RELEASE_WL, true); // request service to release the wl
			context.startService(sintent);		
			
		} else if (intent.getAction().equals(Constants.ACTION_UPLOAD_ALARM)) {
			// scheduled upload
			Log.d(Constants.LOGTAG,"data upload alarm went off");

			// get wake-lock to keep the CPU up
			Helpers.acquireLock(context);
			
			// start the service to do the actual work
			Intent sintent = new Intent(context, CollectorService.class);
			sintent.setAction(Constants.ACTION_UPLOAD);
			sintent.putExtra(Constants.INTENT_EXTRA_RELEASE_WL, true); // request service to release the wl
			context.startService(sintent);						
		}
	}
	
	/**
	 * Schedule alarms.
	 * @param c
	 */
	public static synchronized void set(Context c) {
		AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);

		String iv = Helpers.getUserSettings(c).getString(Constants.PREF_INTERVAL, Integer.toString(DEFAULT_IV));
		long interval = Integer.parseInt(iv) * 60 * 1000; // min -> ms
		
		// periodic collection
		Intent intent = new Intent(Constants.ACTION_COLLECT_ALARM);
		PendingIntent pi = PendingIntent.getBroadcast(c, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		Log.d(Constants.LOGTAG,"next collection scheduled in " + (interval/1000) + " s");
		am.setRepeating(
				AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime()+interval,
		        interval, 
		        pi);
		
		// Check and enable automatic uploads based on user preference
		SharedPreferences prefs = Helpers.getUserSettings(c);
		if (prefs.getBoolean(Constants.PREF_UPLOAD, true)) {
			Intent intent2 = new Intent(Constants.ACTION_UPLOAD_ALARM);
			PendingIntent pi2 = PendingIntent.getBroadcast(c, 0, intent2, PendingIntent.FLAG_CANCEL_CURRENT);
			Log.d(Constants.LOGTAG,"next upload scheduled in " + DEFAULT_UPLOAD_IV/(1000*60*60) + " h");
			am.setInexactRepeating(
					AlarmManager.ELAPSED_REALTIME,    // TODO: don't wake up the device, should we ?
						SystemClock.elapsedRealtime(), 
						DEFAULT_UPLOAD_IV, 
						pi2);
		}
	}

	/**
	 * Cancel all alarms.
	 * @param c
	 */
	public static synchronized void cancel(Context c) {
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
	 * Check if the periodic collector alarm is scheduled.
	 * @param c
	 * @return
	 */
	public static synchronized boolean isCollectorScheduled(Context c) {
		Intent intent = new Intent(Constants.ACTION_COLLECT_ALARM);
		PendingIntent pi = PendingIntent.getBroadcast(c, 0, intent, PendingIntent.FLAG_NO_CREATE);
		return (pi!=null);
	}
}
