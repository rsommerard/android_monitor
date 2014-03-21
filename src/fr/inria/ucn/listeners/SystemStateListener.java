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
package fr.inria.ucn.listeners;

import java.util.Random;

import fr.inria.ucn.CollectorService;
import fr.inria.ucn.Constants;
import fr.inria.ucn.Helpers;
//import fr.inria.ucn.CollectorService;
//import fr.inria.ucn.Constants;
//import fr.inria.ucn.Helpers;
import fr.inria.ucn.collectors.NetworkStateCollector;
import fr.inria.ucn.collectors.SysStateCollector;
//import fr.inria.ucn.collectors.SysStateCollector;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

/**
 * Listen for changes in network state.
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class SystemStateListener extends BroadcastReceiver {

	/*
	 * (non-Javadoc)
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		long ts = System.currentTimeMillis();
		if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
			// collect current network state as connectivity has changed
			new NetworkStateCollector().run(context, ts, true);
		
		} else if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
			// log sys state with changed flag to report screen on
			new SysStateCollector().run(context, ts, true);
			
			// screen just turned on, collect a full sample after some random back-off: 2+[0,2) seconds
			Helpers.acquireLock(context);
			try {
				Thread.sleep(2000+new Random(System.currentTimeMillis()).nextInt(2000));
			} catch (InterruptedException e) {
			}
			Intent sintent = new Intent(context, CollectorService.class);
			sintent.setAction(Constants.ACTION_COLLECT);
			sintent.putExtra(Constants.INTENT_EXTRA_RELEASE_WL, true); // request service to release the wl
			context.startService(sintent);
		}
	}
}
