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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import fr.inria.ucn.Constants;
import fr.inria.ucn.Helpers;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

/**
 * Current network connectivity and configuration. Run once upon collection
 * start/stop and upon network state changes (from NetworkStateListener).
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class NetworkStateCollector extends BroadcastReceiver implements Collector {

	
	
	/**
	 * 
	 * @param c
	 * @param ts
	 * @param change
	 */
	@SuppressWarnings("deprecation")
	@SuppressLint({"DefaultLocale", "NewApi" })
	public void run(Context c, long ts, boolean change) {
		try {			
			// current active interface (wifi or mobile) and config
			ConnectivityManager cm = (ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);		
			TelephonyManager tm = (TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE);
			NetworkInfo ni = cm.getActiveNetworkInfo();

			JSONObject data = new JSONObject();
			data.put("on_network_state_change", change); // this collection run was triggered by network change
			data.put("is_connected", (ni != null && ni.isConnectedOrConnecting()));
			data.put("is_roaming", tm.isNetworkRoaming());
			
			// airplane mode ?
			if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.JELLY_BEAN) {
		        data.put("is_airplane_mode", 
		        		(Settings.System.getInt(c.getContentResolver(), 
		        				Settings.System.AIRPLANE_MODE_ON, 0) != 0));          
		    } else {
		        data.put("is_airplane_mode", 
		        		(Settings.Global.getInt(c.getContentResolver(), 
		        				Settings.Global.AIRPLANE_MODE_ON, 0) != 0));
		    }			
			
			if (ni != null ) {
				JSONObject active = new JSONObject();
				active.put("type",ni.getType());
				active.put("subtype", ni.getSubtype());
				active.put("type_name", ni.getTypeName());
				active.put("subtype_name", ni.getSubtypeName());
				active.put("state", ni.getState().toString());
				active.put("detailed_state", ni.getDetailedState().toString());
				active.put("is_wifi", (ni.getType() == ConnectivityManager.TYPE_WIFI));
				data.put("active_network",active);
				
				if (ni.getType() == ConnectivityManager.TYPE_WIFI) {					
					data.put("wifi_network",getWifi(c));
				}
			}
			
			// mobile network details
			data.put("mobile_network", getMobile(tm));
			
			// kernel network statistics
			data.put("netstat", getNetstat());
			
			// interfaces config
			Map<String, JSONObject> stats = networkStats();
			data.put("ifconfig", getIfconfig(stats));
			
			// double check interfaces
			data.put("ip_addr_show", getIpAddr(stats));
			
			// open connections
			data.put("sockets", getSock(c));
			
			Helpers.sendResultObj(c,"network_state",ts,data);
			
		} catch (JSONException jex) {
			Log.w(Constants.LOGTAG, "failed to create json object",jex);
		}
	}
	
	/* (non-Javadoc)
	 * @see fr.inria.ucn.collectors.Collector#run(android.content.Context)
	 */
	@Override
	public void run(Context c, long ts) {
		run(c,ts,false);
	}	
	
	/*
	 * (non-Javadoc)
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
	 */
	@SuppressLint("NewApi")
	@Override
	public void onReceive(Context context, Intent intent) {
		long ts = System.currentTimeMillis();
		if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
			context.unregisterReceiver(this);			
			try {
				JSONArray neighs = new JSONArray();
				WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);	
				for (ScanResult r : wm.getScanResults()) {
					JSONObject o = new JSONObject();
					o.put("frequency", r.frequency);
					o.put("level", r.level);
					if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
						o.put("timestamp", r.timestamp);
					}
					o.put("bssid", r.BSSID);
					o.put("ssid", r.SSID);
					neighs.put(o);
				}
				
				JSONObject wifi = new JSONObject();
				wifi.put("list", neighs);				
				Helpers.sendResultObj(context, "wifi_neigh", ts, wifi);	
				
			} catch (JSONException jex) {
				Log.w(Constants.LOGTAG, "failed to create json object",jex);				
			}
			// this will clean the CPU lock too if running on the bg
			Helpers.releaseWifiLock();
		}
	}
	
	/* Get mobile network config */
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	private JSONObject getMobile(TelephonyManager tm) throws JSONException {
		JSONObject mob = new JSONObject();
		
		mob.put("call_state", tm.getCallState());
		mob.put("data_activity", tm.getDataActivity());
		mob.put("network_type", tm.getNetworkType());
		mob.put("network_type_str", Helpers.getTelephonyNetworkType(tm.getNetworkType()));
		mob.put("phone_type", tm.getPhoneType());
		mob.put("phone_type_str", Helpers.getTelephonyPhoneType(tm.getPhoneType()));
		mob.put("sim_state", tm.getSimState());
		mob.put("network_country", tm.getNetworkCountryIso());
		mob.put("network_operator", tm.getNetworkOperator());
		mob.put("network_operator_name", tm.getNetworkOperatorName());
		
		// current cell location
		CellLocation cl = tm.getCellLocation();
		if (cl != null) {
			JSONObject loc = new JSONObject();
			if (cl instanceof GsmCellLocation) {
				JSONObject cell = new JSONObject();
				cell.put("cid", ((GsmCellLocation)cl).getCid());
				cell.put("lac", ((GsmCellLocation)cl).getLac());
				cell.put("psc", ((GsmCellLocation)cl).getPsc());
				loc.put("gsm", cell);
			} else if (cl instanceof CdmaCellLocation) {
				JSONObject cell = new JSONObject();
				cell.put("bs_id", ((CdmaCellLocation)cl).getBaseStationId());
				cell.put("bs_lat", ((CdmaCellLocation)cl).getBaseStationLatitude());
				cell.put("bs_lon", ((CdmaCellLocation)cl).getBaseStationLongitude());
				cell.put("net_id", ((CdmaCellLocation)cl).getNetworkId());
				cell.put("sys_id", ((CdmaCellLocation)cl).getSystemId());
				loc.put("cdma", cell);
			}
			mob.put("cell_location", loc);
		}
		
		// Cell neighbors
		List<NeighboringCellInfo> ncl = tm.getNeighboringCellInfo();
		if (ncl!=null) {
			JSONArray cells = new JSONArray();					
			for (NeighboringCellInfo nc : ncl) {
				JSONObject jnc = new JSONObject();
				jnc.put("cid", nc.getCid());
				jnc.put("lac", nc.getLac());
				jnc.put("network_type", nc.getNetworkType());
				jnc.put("network_type_str", Helpers.getTelephonyNetworkType(nc.getNetworkType()));
				jnc.put("psc", nc.getPsc());
				jnc.put("rssi", nc.getRssi());
				cells.put(jnc);
			}	
			mob.put("neigh_cells", cells);
		}
		
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
			// only works for API level >=17
			List<CellInfo> aci = (List<CellInfo>)tm.getAllCellInfo();
			if (aci!=null) {
				JSONArray cells = new JSONArray();	
				for(CellInfo ci : aci) {
					JSONObject jci = new JSONObject();
					if (ci instanceof CellInfoGsm) {
						CellInfoGsm cigsm = (CellInfoGsm)ci;
						jci.put("is_registered", cigsm.isRegistered());
						jci.put("type", "gsm");
						jci.put("cid", cigsm.getCellIdentity().getCid());
						jci.put("lac", cigsm.getCellIdentity().getLac());
						jci.put("mcc", cigsm.getCellIdentity().getMcc());
						jci.put("mnc", cigsm.getCellIdentity().getMnc());
						jci.put("psc", cigsm.getCellIdentity().getPsc());
						
						jci.put("asu_level", cigsm.getCellSignalStrength().getAsuLevel());
						jci.put("level", cigsm.getCellSignalStrength().getLevel());
						jci.put("dbm", cigsm.getCellSignalStrength().getDbm());
						
					} else if (ci instanceof CellInfoCdma) {
						CellInfoCdma cicdma = (CellInfoCdma)ci;
						jci.put("is_registered", cicdma.isRegistered());
						jci.put("type", "cdma");
						jci.put("bs_id", cicdma.getCellIdentity().getBasestationId());
						jci.put("bs_lat", cicdma.getCellIdentity().getLatitude());
						jci.put("bs_lon", cicdma.getCellIdentity().getLongitude());
						jci.put("net_id", cicdma.getCellIdentity().getNetworkId());
						jci.put("sys_id", cicdma.getCellIdentity().getSystemId());						
						
						jci.put("asu_level", cicdma.getCellSignalStrength().getAsuLevel());						
						jci.put("dbm", cicdma.getCellSignalStrength().getDbm());
						jci.put("level", cicdma.getCellSignalStrength().getLevel());
						jci.put("cdma_dbm", cicdma.getCellSignalStrength().getCdmaDbm());						
						jci.put("cdma_ecio", cicdma.getCellSignalStrength().getCdmaEcio());						
						jci.put("cdma_level", cicdma.getCellSignalStrength().getCdmaLevel());
						jci.put("evdo_dbm", cicdma.getCellSignalStrength().getEvdoDbm());
						jci.put("evdo_ecio", cicdma.getCellSignalStrength().getEvdoEcio());
						jci.put("evdo_level", cicdma.getCellSignalStrength().getEvdoLevel());
						jci.put("evdo_snr", cicdma.getCellSignalStrength().getEvdoSnr());
						
					} else if (ci instanceof CellInfoWcdma) {
						CellInfoWcdma ciwcdma = (CellInfoWcdma)ci;
						jci.put("is_registered", ciwcdma.isRegistered());
						jci.put("type", "wcdma");
						jci.put("cid", ciwcdma.getCellIdentity().getCid());
						jci.put("lac", ciwcdma.getCellIdentity().getLac());
						jci.put("mcc", ciwcdma.getCellIdentity().getMcc());
						jci.put("mnc", ciwcdma.getCellIdentity().getMnc());
						jci.put("psc", ciwcdma.getCellIdentity().getPsc());
						
						jci.put("asu_level", ciwcdma.getCellSignalStrength().getAsuLevel());						
						jci.put("dbm", ciwcdma.getCellSignalStrength().getDbm());
						jci.put("level", ciwcdma.getCellSignalStrength().getLevel());
						
					} else if (ci instanceof CellInfoLte) {
						CellInfoLte cilte = (CellInfoLte)ci;
						jci.put("is_registered", cilte.isRegistered());
						jci.put("type", "lte");
						jci.put("ci", cilte.getCellIdentity().getCi());
						jci.put("mcc", cilte.getCellIdentity().getMcc());
						jci.put("mnc", cilte.getCellIdentity().getMnc());
						jci.put("pci", cilte.getCellIdentity().getPci());
						jci.put("tac", cilte.getCellIdentity().getTac());
						
						jci.put("asu_level", cilte.getCellSignalStrength().getAsuLevel());						
						jci.put("dbm", cilte.getCellSignalStrength().getDbm());
						jci.put("level", cilte.getCellSignalStrength().getLevel());
						jci.put("timing_adv", cilte.getCellSignalStrength().getTimingAdvance());
						
					}
					cells.put(jci);
				}
				mob.put("all_cells", cells);
			}			
		}
		return mob;
	}

	/* Get wifi network config */
	private JSONObject getWifi(Context c) throws JSONException {
		WifiManager wm = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
		WifiInfo wi = wm.getConnectionInfo();

		// start a wifi AP scan
		Helpers.acquireWifiLock(c);
		IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		c.registerReceiver(this, filter);
		wm.startScan();
		
		JSONObject o = new JSONObject();
		o.put("link_speed",wi.getLinkSpeed());
		o.put("link_speed_units",WifiInfo.LINK_SPEED_UNITS);
		o.put("signal_level",WifiManager.calculateSignalLevel(wi.getRssi(), 100));
		o.put("rssi",wi.getRssi());
		o.put("bssid",wi.getBSSID());
		o.put("ssid",wi.getSSID().replaceAll("\"", ""));
		o.put("mac",wi.getMacAddress());
		
		int ip = wi.getIpAddress();
		String ipstr = String.format(Locale.US,
				   "%d.%d.%d.%d",
				   (ip & 0xff),
				   (ip >> 8 & 0xff),
				   (ip >> 16 & 0xff),
				   (ip >> 24 & 0xff));					
		o.put("ip",ipstr);
		
		return o;
	}
	
	private Map<String, JSONObject> networkStats() throws NumberFormatException, JSONException {
		Map<String, JSONObject> stats = new HashMap<String, JSONObject>();
		// Read interface statistics from /proc, add to the corresponding interface object below
		List<String> dev = Helpers.readProc("/proc/net/dev");
		for (String s: dev) {
			if (s.indexOf(':')>0) {
				// iface rxbytes rxpackets rxerrs rxdrop rxfifo rxframe rxcompressed rxmulticast txbytes txpackets txerrs txdrop txfifo txcolls txcarrier txcompressed
				String[] tmp = s.split("[ ]+");
				JSONObject o = new JSONObject();
				o.put("rx_bytes", Integer.parseInt(tmp[1]));
				o.put("rx_packets", Integer.parseInt(tmp[2]));
				o.put("rx_errors", Integer.parseInt(tmp[3]));
				o.put("rx_drop", Integer.parseInt(tmp[4]));
				o.put("tx_bytes", Integer.parseInt(tmp[9]));
				o.put("tx_packets", Integer.parseInt(tmp[10]));
				o.put("tx_errors", Integer.parseInt(tmp[11]));
				o.put("tx_drop", Integer.parseInt(tmp[12]));
				stats.put(tmp[0].replace(":", ""), o);
			}	
		}
		return stats;
	}
	
	private JSONArray getIpAddr(Map<String, JSONObject> stats) throws JSONException {
		JSONArray ifaces = new JSONArray();
		
		// make sure the stats is read
		networkStats();
		
		Process process = null;
		BufferedReader in = null;
		try {
		    process = Runtime.getRuntime().exec("ip addr show");
		    in = new BufferedReader(new InputStreamReader(process.getInputStream()));
			JSONObject iface = null;

			String line = null;
		    while ((line = in.readLine())!=null) {
		    	line = line.trim();
	    		String[] tmp = line.split(" ");
		    	if (line.contains("mtu")) {
		    		if (iface!=null)
		    			ifaces.put(iface);
		    		iface = new JSONObject();
		    		
		    		String name = tmp[1].replace(":", "");
		    		iface.put("name", name);
					iface.put("stats", stats.get(name));
					
					String[] flags = tmp[2].replaceAll("[<>]", "").split(",");
					JSONArray fo = new JSONArray();
					for (String f : flags)
						fo.put(f);
					iface.put("flags", fo);
					
					iface.put("mtu", Integer.parseInt(tmp[4]));
					iface.put("qdisc", tmp[6]);
					iface.put("state", tmp[8]);
					
		    	} else if (line.contains("ether")) {
					iface.put("mac", tmp[1]);
		    	} else if (line.startsWith("inet6")) {
		    		JSONObject ipv6 = new JSONObject();
					ipv6.put("ip", tmp[1].substring(0,tmp[1].indexOf('/')));
					ipv6.put("mask", tmp[1].substring(tmp[1].indexOf('/')+1));
					ipv6.put("scope", tmp[3]);
					iface.put("ipv6",ipv6);
		    	} else if (line.startsWith("inet")) {
		    		JSONObject ipv4 = new JSONObject();
					ipv4.put("ip", tmp[1].substring(0,tmp[1].indexOf('/')));
					ipv4.put("mask", tmp[1].substring(tmp[1].indexOf('/')+1));
					int i = 2;
					while (i<tmp.length-1) {
		    			ipv4.put(tmp[i], tmp[i+1]);
		    			i = i+2;
		    		}
					iface.put("ipv4",ipv4);
		    	}
		    }
		    
		    // last object
    		if (iface!=null)
    			ifaces.put(iface);
			
		} catch (IOException e) {
			Log.d(Constants.LOGTAG, "failed to execute \"ip addr show\"",e);				
		} finally {
			if (process!=null)
				process.destroy();
		}
		
		return ifaces;
	}
	
	/* Get low level network devices config and traffic stats */
	private JSONArray getIfconfig(Map<String, JSONObject> stats) throws JSONException {
		JSONArray ifaces = new JSONArray();
		
		// make sure the stats is read
		networkStats();
		
		Enumeration<NetworkInterface> en = null;
		try {
			en = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
			Log.d(Constants.LOGTAG, "failed to list interfaces", e);
		}
		
		if (en!=null) {
			while (en.hasMoreElements()) {
				NetworkInterface intf = en.nextElement();

				JSONObject iface = new JSONObject();
				iface.put("display_name", intf.getDisplayName());
				iface.put("name", intf.getName());
				iface.put("is_virtual", intf.isVirtual());
				iface.put("stats", stats.get(intf.getName()));

				try {						
					iface.put("mtu", intf.getMTU());
					iface.put("is_loopback", intf.isLoopback());
					iface.put("is_ptop", intf.isPointToPoint());
					iface.put("is_up", intf.isUp());
				} catch (SocketException e) {
					Log.d(Constants.LOGTAG, "failed to read interface data", e);
				}
				
				JSONArray ips = new JSONArray();
				List<InterfaceAddress> ilist = intf.getInterfaceAddresses();
				for (InterfaceAddress ia : ilist) {
					ips.put(ia.getAddress().getHostAddress());
				}
				iface.put("addresses", ips);						
				
				ifaces.put(iface);
			}
		} else {
			for (String name : stats.keySet()) {
				JSONObject iface = new JSONObject();
				iface.put("name", name);
				iface.put("stats", stats.get(name));		
				ifaces.put(iface);
			}
		}
		
		return ifaces;
	}

	/* Read network stats from proc file system. */
	private JSONObject getNetstat() throws JSONException {
		JSONObject jnetstat = new JSONObject();
		for (String s : Arrays.asList("netstat","snmp")) {
			List<String> lines = Helpers.readProc("/proc/net/"+s);
			for (int i = 0; i < lines.size(); i += 2) {
				String[] headers = lines.get(i).split("\\s+");
				String[] vals = lines.get(i+1).split("\\s+");
				JSONObject o = new JSONObject();
				for (int j = 1; j < headers.length; j++) {
					o.put(headers[j].toLowerCase(), Long.parseLong(vals[j]));
				}
				jnetstat.put(headers[0].toLowerCase(), o);
			}
		}
		return jnetstat;
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
