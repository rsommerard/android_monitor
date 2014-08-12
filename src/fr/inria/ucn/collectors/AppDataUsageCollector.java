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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import fr.inria.ucn.Constants;
import fr.inria.ucn.Helpers;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.net.TrafficStats;
import android.util.Log;

/**
 * Bytes send/recv per app.
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class AppDataUsageCollector implements Collector {

	private static final String PROC_UID_STAT = "/proc/uid_stat";

	/* (non-Javadoc)
	 * @see fr.inria.ucn.collectors.Collector#run(android.content.Context)
	 */
	@Override
	public void run(Context c, long ts) {	
		try {
			// data used per app
			JSONArray parray = new JSONArray();
			
			File f = new File(PROC_UID_STAT);
			if (f.exists() && f.isDirectory() && f.canRead()) {
				for (String dir: f.list()) {
					parray.put(getProcInfo(c, Integer.parseInt(dir)));
				}
			} else {
				ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
				for (ActivityManager.RunningAppProcessInfo pinfo : am.getRunningAppProcesses()) {
					parray.put(getProcInfo(c, pinfo.uid));					
				}
			}
			
			// done
			Helpers.sendResultObj(c,"app_data_usage", ts, new JSONObject().put("process_list", parray));
			
		} catch (JSONException jex) {
			Log.w(Constants.LOGTAG, "failed to create json object",jex);
		}		
	}
	
	/* Build process info object. */
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	private JSONObject getProcInfo(Context c, int uid) throws JSONException {
		// uid + related package list
		JSONObject pinfo = new JSONObject();
		pinfo.put("uid", uid);
		pinfo.put("packages", Helpers.getPackagesForUid(c,uid));
		
		// simple TCP stats
		JSONObject tcp = new JSONObject();
		tcp.put("send", getSysLongValue(PROC_UID_STAT + "/" +uid+ "/tcp_snd"));
		tcp.put("recv", getSysLongValue(PROC_UID_STAT + "/" +uid+ "/tcp_rcv"));
		pinfo.put("proc_uid_stat_tcp",tcp);
		
		// complete traffic stats (may not be available)
		JSONObject tstat = new JSONObject();
		tstat.put("uid_rx_bytes", TrafficStats.getUidRxBytes(uid));
		tstat.put("uid_tx_bytes", TrafficStats.getUidTxBytes(uid));
		
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB_MR1) {
			tstat.put("uid_rx_pkts", TrafficStats.getUidRxPackets(uid));
			tstat.put("uid_tx_pkts", TrafficStats.getUidTxPackets(uid));
			tstat.put("uid_tcp_rx_pkts", TrafficStats.getUidTcpRxSegments(uid));
			tstat.put("uid_tcp_tx_pkts", TrafficStats.getUidTcpTxSegments(uid));
			tstat.put("uid_udp_rx_pkts", TrafficStats.getUidUdpRxPackets(uid));
			tstat.put("uid_udp_tx_pkts", TrafficStats.getUidUdpTxPackets(uid));
			tstat.put("uid_udp_rx_bytes", TrafficStats.getUidUdpRxBytes(uid));
			tstat.put("uid_udp_tx_bytes", TrafficStats.getUidUdpTxBytes(uid));
			tstat.put("uid_tcp_rx_bytes", TrafficStats.getUidTcpRxBytes(uid));
			tstat.put("uid_tcp_tx_bytes", TrafficStats.getUidTcpTxBytes(uid));
		}
		
		pinfo.put("android_traffic_stats", tstat);					
		return pinfo;
	}
	
	/* Read a long value from a proc file. */
	private long getSysLongValue(String name) {
		long res = -1;
		File f = new File(name);
		try {
			BufferedReader br = new BufferedReader(new FileReader(f));
			String value = br.readLine().trim();
			br.close();
			if (value!=null && value.length()>0) {
				res = Long.parseLong(value);
			}
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		} catch (NumberFormatException e) {			
		}
		return res;
	}
}
