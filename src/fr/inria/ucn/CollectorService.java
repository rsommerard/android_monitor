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

import java.util.ArrayList;
import java.util.List;

import fr.inria.ucn.collectors.AppDataUsageCollector;
import fr.inria.ucn.collectors.Collector;
import fr.inria.ucn.collectors.DeviceInfoCollector;
import fr.inria.ucn.collectors.LlamaCollector;
import fr.inria.ucn.collectors.NetworkStateCollector;
import fr.inria.ucn.collectors.OpenVPNStatusCollector;
import fr.inria.ucn.collectors.RunningAppsCollector;
import fr.inria.ucn.collectors.SysStateCollector;
import fr.inria.ucn.listeners.MyPhoneStateListener;
import fr.inria.ucn.listeners.SystemStateListener;
import android.app.IntentService;
import android.content.Intent;
import android.database.SQLException;
import android.util.Log;

/**
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class CollectorService extends IntentService {

	private DataStore dstore = null;
	
	private List<Collector> oneshotCollectors = new ArrayList<Collector>();
	private List<Collector> periodicCollectors = new ArrayList<Collector>();
	private MyPhoneStateListener psl = null;
	
	public CollectorService() {
		super("UCNDataCollectorService");
	}
	
	/*
	 * (non-Javadoc)
	 * @see android.app.IntentService#onCreate()
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		dstore = new DataStore(getApplicationContext());
		try {
			dstore.open(false);
		} catch (SQLException ex) {
			Log.e(Constants.LOGTAG, "failed to open the datastore", ex);
		}
		
		// create instances of collectors		
		oneshotCollectors.add(new DeviceInfoCollector());

		periodicCollectors.add(new SysStateCollector());
		periodicCollectors.add(new NetworkStateCollector());		
		periodicCollectors.add(new RunningAppsCollector());
		periodicCollectors.add(new AppDataUsageCollector());
		periodicCollectors.add(new LlamaCollector());
		periodicCollectors.add(new OpenVPNStatusCollector());
		
		psl = new MyPhoneStateListener();
	}

	/*
	 * (non-Javadoc)
	 * @see android.app.IntentService#onDestroy()
	 */
	@Override
	public void onDestroy() {
		dstore.close();
		oneshotCollectors.clear();
		periodicCollectors.clear();
		
		// FIXME: this should really remain active on the bg.. 
		if (psl.isEnabled()) {
			psl.disable(this.getApplicationContext());
			psl = null;
		}
		super.onDestroy();
	}

	/* (non-Javadoc)
	 * @see android.app.IntentService#onHandleIntent(android.content.Intent)
	 */
	@Override
	protected void onHandleIntent(Intent intent) {		
		long nowts = System.currentTimeMillis();
		String nows = Long.toString(nowts);
		Log.d(Constants.LOGTAG, "service handle intent action="+intent.getAction());
		
		if (intent.getAction().equals(Constants.ACTION_SCHEDULE)) {
			boolean on = intent.getBooleanExtra(Constants.INTENT_EXTRA_SCHEDULER_START, false);			
			Log.d(Constants.LOGTAG, "set schedule " + (on ? "ON" : "OFF"));
				
			if (on) {
				// enable onBootReceiver
				Log.d(Constants.LOGTAG, "enableReceiver " + OnBootReceiver.class);
				Helpers.enableReceiver(this.getApplicationContext(), OnBootReceiver.class);

				// start background listeners
				Log.d(Constants.LOGTAG, "enableReceiver " + SystemStateListener.class);
				Helpers.enableReceiver(this.getApplicationContext(), SystemStateListener.class);

				// FIXME: this listener goes away if the service is killed?
				Log.d(Constants.LOGTAG, "enable " + psl.getClass().getSimpleName());
				psl.enable(this.getApplicationContext());

				// schedule periodic collection
				Log.d(Constants.LOGTAG,"set alarm");
				Scheduler.setAlarms(getApplicationContext());

				// run onetime collectors
				for (Collector c : oneshotCollectors) {
					Log.d(Constants.LOGTAG, "run " + c.getClass().getSimpleName());
					c.run(this.getApplicationContext(),nowts);
				}

			} else { // schedule OFF
				
				// set null uptime value in the dstore
				nows = Constants.NULL_UPTIME;

				// stop periodic collection
				Log.d(Constants.LOGTAG,"cancel alarm");
				Scheduler.cancelPeriodicAlarms(getApplicationContext());

				// stop background listeners
				Log.d(Constants.LOGTAG, "disableReceiver " + SystemStateListener.class);
				Helpers.disableReceiver(this.getApplicationContext(), SystemStateListener.class);

				Log.d(Constants.LOGTAG, "disable " + psl.getClass().getSimpleName());
				psl.disable(this.getApplicationContext());

				// disable onBootReceiver
				Log.d(Constants.LOGTAG, "disableReceiver " + OnBootReceiver.class);
				Helpers.disableReceiver(this.getApplicationContext(), OnBootReceiver.class);
			}

			// queue first/last collect action
			Intent sintent = new Intent(this.getApplicationContext(), CollectorService.class);
			sintent.setAction(Constants.ACTION_COLLECT);
			startService(sintent);

			if (!on) {
				// turned off, queue upload action
				Intent sintent2 = new Intent(this.getApplicationContext(), CollectorService.class);
				sintent2.setAction(Constants.ACTION_UPLOAD);
				startService(sintent2);
			}
			
		} else if (intent.getAction().equals(Constants.ACTION_COLLECT)) {
			for (Collector c : periodicCollectors) {
				Log.d(Constants.LOGTAG, "run " + c.getClass().getSimpleName());
				c.run(this.getApplicationContext(),nowts);
			}
			
		} else if (intent.getAction().equals(Constants.ACTION_DATA)) {
			String data = intent.getStringExtra(Constants.INTENT_EXTRA_DATA);
			dstore.addData(data);
			
		} else if (intent.getAction().equals(Constants.ACTION_UPLOAD)) {
			if (DataUploader.upload(getApplicationContext(), dstore)) {
				try {
					dstore.addKeyValue(Constants.STATUS_LAST_UPLOAD, nows);
				} catch (CollectorException ex) {
				}	
			} else {
				Log.w(Constants.LOGTAG, "data upload failed");
			}
			
		} else if (intent.getAction().equals(Constants.ACTION_RELEASE_WL)) {
			Helpers.releaseLock();
		}
		
		if (intent.getBooleanExtra(Constants.INTENT_EXTRA_RELEASE_WL, false) &&
				!intent.getAction().equals(Constants.ACTION_RELEASE_WL)) 
		{
			// wakelock release requested - pass the action through the message queue
			// so that the service has time to handle any other queued work before
			// going back to sleep
			Intent sintent = new Intent(this.getApplicationContext(), CollectorService.class);
			sintent.setAction(Constants.ACTION_RELEASE_WL);
			startService(sintent);
		}
	}
}
