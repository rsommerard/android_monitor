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
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
public class NetworkStateCollector extends Collector {

	/**
	 * 
	 * @param c
	 * @param ts
	 * @param change
	 */
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	public void run(Context c, long ts, boolean change) {
		try {
			JSONObject data = new JSONObject();
			
			// current active interface (wifi or mobile) and config
			ConnectivityManager cm = (ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);		
			TelephonyManager tm = (TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE);		

			NetworkInfo ni = cm.getActiveNetworkInfo();
			data.put("onchange", change); // this collection run was triggered by network change
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
			
			// may or may not be available
			CellLocation cl = tm.getCellLocation();
			if (cl != null) {
				JSONObject cell = new JSONObject();
				if (cl instanceof GsmCellLocation) {
					cell.put("cid", ((GsmCellLocation)cl).getCid());
					cell.put("lac", ((GsmCellLocation)cl).getLac());
					cell.put("psc", ((GsmCellLocation)cl).getPsc());
					mob.put("gsm_cell_location", cell);
				} else if (cl instanceof CdmaCellLocation) {
					cell.put("bs_id", ((CdmaCellLocation)cl).getBaseStationId());
					cell.put("bs_lat", ((CdmaCellLocation)cl).getBaseStationLatitude());
					cell.put("bs_lon", ((CdmaCellLocation)cl).getBaseStationLongitude());
					cell.put("net_id", ((CdmaCellLocation)cl).getNetworkId());
					cell.put("sys_id", ((CdmaCellLocation)cl).getSystemId());
					mob.put("cdma_cell_location", cell);
				}
			}
			
			// the old way
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
			data.put("mobile_network", mob);
			
			if (ni != null ) {
				// details of the current active network
				JSONObject active = new JSONObject();
				int netType = ni.getType();
				active.put("type", netType);
				active.put("subtype", ni.getSubtype());
				active.put("type_name", ni.getTypeName());
				active.put("subtype_name", ni.getSubtypeName());
				active.put("state", ni.getState().toString());
				active.put("detailed_state", ni.getDetailedState().toString());
				active.put("is_wifi", (netType == ConnectivityManager.TYPE_WIFI));
				data.put("active_network",active);
				
				if (netType == ConnectivityManager.TYPE_WIFI) {					
					// get details on active wifi network
					WifiManager wm = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
					WifiInfo wi = wm.getConnectionInfo();
					
					JSONObject jni = new JSONObject();
					jni.put("link_speed",wi.getLinkSpeed());
					jni.put("link_speed_units",WifiInfo.LINK_SPEED_UNITS);
					jni.put("rssi",wi.getRssi());
					jni.put("bssid",wi.getBSSID());
					jni.put("ssid",wi.getSSID().replaceAll("\"", ""));
					jni.put("mac",wi.getMacAddress());
					int ip = wi.getIpAddress();
					String ipstr = String.format(Locale.US,
							   "%d.%d.%d.%d",
							   (ip & 0xff),
							   (ip >> 8 & 0xff),
							   (ip >> 16 & 0xff),
							   (ip >> 24 & 0xff));					
					jni.put("ip",ipstr);
					
					// get nearby APs ?
					
					data.put("wifi_network",jni);
				}
			}
			
			// Read interface statistics from /proc, add to the corresponding interface object below
			List<String> dev = Helpers.readProc("/proc/net/dev");
			Map<String, JSONObject> stats = new HashMap<String, JSONObject>();
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
			
			Enumeration<NetworkInterface> en = null;
			try {
				en = NetworkInterface.getNetworkInterfaces();
			} catch (SocketException e) {
				Log.d(Constants.LOGTAG, "failed to list interfaces", e);
			}
			
			if (en!=null) {
				JSONArray ifaces = new JSONArray();
				while (en.hasMoreElements()) {
					try {
						NetworkInterface intf = en.nextElement();

						JSONObject iface = new JSONObject();
						iface.put("mtu", intf.getMTU());
						iface.put("display_name", intf.getDisplayName());
						iface.put("name", intf.getName());
						iface.put("is_loopback", intf.isLoopback());
						iface.put("is_ptop", intf.isPointToPoint());
						iface.put("is_up", intf.isUp());
						iface.put("is_virtual", intf.isVirtual());
						iface.put("stats", stats.get(intf.getName()));
						
						JSONArray ips = new JSONArray();
						List<InterfaceAddress> ilist = intf.getInterfaceAddresses();
						for (InterfaceAddress ia : ilist) {
							ips.put(ia.getAddress().getHostAddress());
						}
						iface.put("addresses", ips);
						
					} catch (SocketException e) {
						Log.d(Constants.LOGTAG, "failed to read interface", e);
					}
				}
				data.put("net_interfaces", ifaces);
			} else {
				Log.d(Constants.LOGTAG, "getNetworkInterfaces returns null");				
			}

			// TODO: does this always work in fact?
			// lets try from the cmd line
			Process process = null;
			BufferedReader in = null;
			try {
			    process = Runtime.getRuntime().exec("ip addr show");
			    in = new BufferedReader(new InputStreamReader(process.getInputStream()));
			    String line = null;
				JSONArray ifaces = new JSONArray();
				JSONObject iface = null;
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
				data.put("ip_addr_show", ifaces);
				
			} catch (IOException e) {
				Log.d(Constants.LOGTAG, "failed to execute \"ip addr show\"",e);				
			} finally {
				if (process!=null)
					process.destroy();
			}
			
			// done
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
}
