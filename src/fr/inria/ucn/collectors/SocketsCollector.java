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

import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import fr.inria.ucn.Constants;
import fr.inria.ucn.Helpers;

import android.content.Context;
import android.util.Log;

/**
 * List of open sockets and apps.
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class SocketsCollector implements Collector {
	
	/**
	 * 
	 * @param c
	 * @param ts
	 */
	public void run(Context c, long ts) {
		try {			
			JSONObject data = new JSONObject();
			data.put("sockets", getSock(c));
			Helpers.sendResultObj(c,"sockets",ts,data);
		} catch (JSONException jex) {
			Log.w(Constants.LOGTAG, "failed to create json object",jex);
		}
	}

	private enum States {
		DUMMY,
	    TCP_ESTABLISHED, /* == 1*/
	    TCP_SYN_SENT,
	    TCP_SYN_RECV,
	    TCP_FIN_WAIT1,
	    TCP_FIN_WAIT2,
	    TCP_TIME_WAIT,
	    TCP_CLOSE,
	    TCP_CLOSE_WAIT,
	    TCP_LAST_ACK,
	    TCP_LISTEN,
	    TCP_CLOSING,    /* Now a valid state */
	    TCP_UNKNOWN,
	    TCP_MAX_STATES;  /* Leave at the end! */
	    
	    private static States[] values = null;
	    public static States fromInt(int v) {
	    	if (values == null)
	    		values = States.values();
	    	if (v >= values.length || v == 0)
	    		return States.TCP_UNKNOWN;
	    	return values[v];
	    }
	};
	
	private JSONObject parseAddr(String s)  throws JSONException {
		String[] addr = s.split(":");
		JSONObject o = new JSONObject();
		o.put("port", Integer.parseInt(addr[1],16));
		o.put("raw_ip", addr[0]);
		
		String ip = "";
		for (int j = addr[0].length(); j > (addr[0].length()>8 ? 24 : 0); j-=2) {
			String sub = addr[0].substring(j-2, j);
			int num = Integer.parseInt(sub, 16);
			ip += num+".";
		}
		if (ip.length()>0) {
			ip = ip.substring(0, ip.length()-1);
			o.put("ipv4", ip);
		}
		return o;
	}
	
	/* Read sockets info from proc file system. */
	private JSONObject getSock(Context c) throws JSONException {
		JSONObject jo = new JSONObject();
		for (String s : Arrays.asList("tcp","udp","tcp6")) {
			JSONArray a = new JSONArray();
			
			List<String> lines = Helpers.readProc("/proc/net/"+s);
			for (int i = 1; i < lines.size(); i += 1) {
				String[] vals = lines.get(i).split("\\s+");
				if (vals.length < 7)
					continue;
				
				JSONObject o = new JSONObject();
				o.put("idx", Integer.parseInt(vals[0].replace(':', ' ').trim()));
				
				o.put("local_addr", parseAddr(vals[1].trim()));
				o.put("remote_addr", parseAddr(vals[2].trim()));
				
				States st = States.fromInt(Integer.parseInt(vals[3].trim(),16));
				o.put("status_code", st.ordinal());
				o.put("status_text", st.name());

				String[] q = vals[4].trim().split(":");
				o.put("tx_queue", Integer.parseInt(q[0],16));
				o.put("rx_queue", Integer.parseInt(q[1],16));
				
				int uid = Integer.parseInt(vals[7].replace(':', ' ').trim());
				o.put("uid", uid);
				o.put("packages", Helpers.getPackagesForUid(c, uid));
				
				a.put(o);
			}
			jo.put(s,a);
		}
		Log.d(Constants.LOGTAG, jo.toString(4));
		return jo;
	}
}
