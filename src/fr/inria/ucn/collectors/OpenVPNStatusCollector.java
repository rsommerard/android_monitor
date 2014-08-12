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
package fr.inria.ucn.collectors;

import org.json.JSONException;
import org.json.JSONObject;

import de.blinkt.openvpn.api.IOpenVPNAPIService;
import de.blinkt.openvpn.api.IOpenVPNStatusCallback;
import fr.inria.ucn.Constants;
import fr.inria.ucn.Helpers;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * Get current status from the OpenVPN service if available.
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class OpenVPNStatusCollector implements Collector {

	// for handling OpenVPN
	private IOpenVPNAPIService api = null;
	private IOpenVPNStatusCallback cb = null;
	private boolean done = false;
	private static Object lock = new Object();

	/* (non-Javadoc)
	 * @see fr.inria.ucn.collectors.Collector#run(android.content.Context, long)
	 */
	@Override
	public void run(final Context c, final long ts) {		
		if (Helpers.isOpenVPNClientInstalled(c)) {
			done = false;
			
			// connect to the OpenVPN instance
			ServiceConnection conn = new ServiceConnection() {
		    	
		    	public void onServiceConnected(ComponentName className, IBinder service) {
		    		api = IOpenVPNAPIService.Stub.asInterface(service);
		    		try {
		    			Intent p = api.prepare(c.getPackageName());
		    			if (p!=null) {
		    				Log.w(Constants.LOGTAG, "openvpn permission not granted");
		    				return;
		    			}

		    			// can talk to OpenVPN service
		    			cb = new IOpenVPNStatusCallback.Stub() {
		    				@Override
		    				public void newStatus(String uuid, String state, String message, String level) throws RemoteException {
		    					try {
		    						JSONObject data = new JSONObject();
		    						data.put("provider","OpenVPN");
		    						data.put("uuid",uuid);
		    						data.put("state",state);
		    						data.put("message",message);
		    						if (message.startsWith("SUCCESS")) {
		    							String[] tmp = message.split(",");
		    							JSONObject tunnel = new JSONObject();
		    							tunnel.put("local_ip", tmp[1]);
		    							tunnel.put("remote_ip", tmp[2]);
		    							data.put("tunnel", tunnel);
		    						}
		    						data.put("level",level);
		    						Helpers.sendResultObj(c,"vpn_status",ts,data);
		    						synchronized (lock) {
		    							done = true;
		    						}
		    					} catch (JSONException e) {
		    					}
		    				}
		    			};
		    			api.registerStatusCallback(cb);
		    			
		    		} catch (RemoteException e) {
		    			Log.w(Constants.LOGTAG, "openvpn api error",e);
		    		}
		    	}

		        public void onServiceDisconnected(ComponentName className) {
		        }
		    };
		    
		    // connect
			c.bindService(
					new Intent(IOpenVPNAPIService.class.getName()), 
					conn, 
					Context.BIND_AUTO_CREATE);		
			
			// busy wait for the service - quite ugly
			int waits = 0;
			while (waits < 10) {
				synchronized (lock) {
					if (done)
						break;
				}
				
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
				}
				
				waits += 1;
			}
			
			try {
				if (api!=null && cb != null)
					api.unregisterStatusCallback(cb);
				if (conn!=null)
					c.unbindService(conn);
			} catch (RemoteException e) {
			}
		}
	}

}
