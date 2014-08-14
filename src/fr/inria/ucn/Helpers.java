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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Log;

/**
 * Misc helper methods.
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 */
public final class Helpers {
	
	/* Hide the constructor, this class only has static methods */
	private Helpers() {};
	
	/* CPU wakelock for keeping the service alive on the background */
	private static PowerManager.WakeLock lock = null;
	
	/* Wifi wakelock for keeping the wifi device alive on the background */
	private static WifiManager.WifiLock wifilock = null;
	
	/**
	 * Acquire the wakelock to keep the CPU running.
	 * @param c
	 */
	public static synchronized void acquireLock(Context c) {
		PowerManager mgr = (PowerManager)c.getSystemService(Context.POWER_SERVICE);
		if (lock==null) {
			lock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,Constants.CPU_WAKE_LOCK);		
			lock.setReferenceCounted(true);
		}
		Log.d(Constants.LOGTAG, "acquire lock");
		lock.acquire();
	}
	
	/**
	 * Release the wakelock and let CPU sleep.
	 */
	public static synchronized void releaseLock() {
		Log.d(Constants.LOGTAG, "release lock");
		if (lock!=null)
			lock.release();
	}

	/**
	 * Acquire the wifi wakelock.
	 * @param c
	 */
	public static synchronized void acquireWifiLock(Context c) {
		WifiManager mgr = (WifiManager)c.getSystemService(Context.WIFI_SERVICE);
		if (wifilock==null) {
			wifilock = mgr.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY, "ucn");
			wifilock.setReferenceCounted(false);
		}
		Log.d(Constants.LOGTAG, "acquire wifilock");
		wifilock.acquire();
		acquireLock(c);
	}
	
	/**
	 * Release the wifi wakelock.
	 */
	public static synchronized void releaseWifiLock() {
		Log.d(Constants.LOGTAG, "release wifilock");
		if (wifilock!=null)
			wifilock.release();
		releaseLock();
	}
	
    /** Retrieve default preferences object. */
    public static SharedPreferences getUserSettings(Context c) {
    	return c.getSharedPreferences("fr.inria.ucn_preferences", Context.MODE_PRIVATE);
    }
    
    /**
     * Retrieves a system property
     * @param key the property key
     * @param def the value to be returned if the key could not be resolved
     */
    public static String getSystemProperty(String key, String def) {
        try {
            Method getString = Build.class.getDeclaredMethod("getString", String.class);
            getString.setAccessible(true);
            return getString.invoke(null, key).toString();
        } catch (Exception ex) {
            return def;
        }
    }
    
	/**
	 * Enable broadcast received component.
	 * @param c
	 * @param component
	 */
	public static void enableReceiver(Context c, Class component) {
		ComponentName receiver = new ComponentName(c, component);
		PackageManager pm = c.getPackageManager();
		pm.setComponentEnabledSetting(
				receiver,
		        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
		        PackageManager.DONT_KILL_APP);		
	}

	/**
	 * Disable broadcast received component.
	 * @param c
	 * @param component
	 */
	public static void disableReceiver(Context c, Class component) {		
		ComponentName receiver = new ComponentName(c, component);
		PackageManager pm = c.getPackageManager();
		pm.setComponentEnabledSetting(
				receiver,
		        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
		        PackageManager.DONT_KILL_APP);		
	}

	/**
	 * Collectors and Listeners should use this method to send the results to the service.
	 * @param c
	 * @param cid data collection id (maps to mongodb collection used to store the data)
	 * @param ts  periodic collection timestamp or event time if triggered by timestamp
	 * @param data 
	 */
	public static void sendResultObj(Context c, String cid, long ts, JSONObject data) {
	    try {
	    	
			// wrap the collected data object to a common object format
			JSONObject res = new JSONObject();
	
			// data collection in the backend db
			res.put("collection", cid);
			
			// store unique user id to each result object
			res.put("uid", getDeviceUuid(c));

			// some labels to help filtering test devices away from data
			res.put("hostname", getSystemProperty("net.hostname", 
					c.getResources().getString(R.string.pref_label_default)));
			res.put("userlabel", getUserSettings(c).getString(Constants.PREF_LABEL, 
					c.getResources().getString(R.string.pref_label_default)));
			
			// this app version to identify data format changes
			try {
				PackageManager manager = c.getPackageManager();
				PackageInfo info = manager.getPackageInfo(c.getPackageName(), 0);
				res.put("app_version_name", info.versionName);
				res.put("app_version_code", info.versionCode);
			} catch (NameNotFoundException e) {
			}
			
			// event (alarm or listener) timestamp
			res.put("ts_event", ts);

			// current time in UTC and local time
			res.put("ts_utc", System.currentTimeMillis());
			
			Time today = new Time(Time.getCurrentTimezone());
			today.setToNow();
			res.put("ts_local", today.toMillis(false));
			res.put("tz", Time.getCurrentTimezone());
			
			res.put("data", data);
			
			// ask the service to handle the data
			Intent intent = new Intent(c, CollectorService.class);
			intent.setAction(Constants.ACTION_DATA);
			intent.putExtra(Constants.INTENT_EXTRA_DATA, res.toString());
			c.startService(intent);
	    
	    	Log.d(Constants.LOGTAG, res.toString(4));
	    	
	    } catch (JSONException ex) {
			Log.w(Constants.LOGTAG, "failed to create json obj",ex);
	    }
	}

	/**
	 * 
	 * @param type
	 * @return
	 */
	public static String getTelephonyPhoneType(int type) {
	    switch (type) {
	    case TelephonyManager.PHONE_TYPE_CDMA:
	      return "CDMA";
	    case TelephonyManager.PHONE_TYPE_GSM:
	      return "GSM";
	    case TelephonyManager.PHONE_TYPE_NONE:
	      return "None";
	    default:
	      return "Unknown["+type+"]";
	    }
	}
	
	/**
	 * 
	 * @param type
	 * @return
	 */
	public static String getTelephonyNetworkType(int type) {
		switch (type) {
			case TelephonyManager.NETWORK_TYPE_1xRTT:
				return "1xRTT";
			case TelephonyManager.NETWORK_TYPE_CDMA:
				return "CDMA";
			case TelephonyManager.NETWORK_TYPE_EDGE:
				return "EDGE";
			case TelephonyManager.NETWORK_TYPE_EVDO_0:
				return "EVDO_0";
			case TelephonyManager.NETWORK_TYPE_EVDO_A:
				return "EVDO_A";
			case TelephonyManager.NETWORK_TYPE_GPRS:
				return "GPRS";
			case TelephonyManager.NETWORK_TYPE_HSDPA:
				return "HSDPA";
			case TelephonyManager.NETWORK_TYPE_HSPA:
				return "HSPA";
			case TelephonyManager.NETWORK_TYPE_HSUPA:
				return "HSUPA";
			case TelephonyManager.NETWORK_TYPE_IDEN:
				return "IDEN";
			case TelephonyManager.NETWORK_TYPE_UMTS:
				return "UMTS";
			case TelephonyManager.NETWORK_TYPE_EVDO_B:
				return "EVDO_B";
			case TelephonyManager.NETWORK_TYPE_LTE:
				return "LTE";
			case TelephonyManager.NETWORK_TYPE_EHRPD:
				return "EHRPD";
			case TelephonyManager.NETWORK_TYPE_HSPAP:
				return "HSPAP";
			default: 
				return "UNKNOWN [" + type + "]";
		}
	}	
	
	// Source : http://stackoverflow.com/questions/2785485/is-there-a-unique-android-device-id
	private volatile static UUID uuid = null;
    private static final String ID_PREFS_FILE = "ucn_device_id.xml";
    private static final String ID_PREFS_DEVICE_ID = "ucn_device_id";
    private static final String BAD_UUID = "9774d56d682e549c";
	private static UUID getUuid(Context c) {
		final SharedPreferences prefs = c.getSharedPreferences(ID_PREFS_FILE, Context.MODE_PRIVATE);
		final String id = prefs.getString(ID_PREFS_DEVICE_ID, null);
		final UUID uuid;
		if (id != null) {
			uuid = UUID.fromString(id);
		} else {
			final String androidId = Secure.getString(c.getContentResolver(), Secure.ANDROID_ID);
			try {
				if (!BAD_UUID.equals(androidId)) {
					uuid = UUID.nameUUIDFromBytes(androidId.getBytes("utf8"));
				} else {
					final String deviceId = ((TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
					if (deviceId != null)
						uuid = UUID.nameUUIDFromBytes(deviceId.getBytes("utf8"));
					else
						uuid = UUID.randomUUID();
				}
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
			prefs.edit().putString(ID_PREFS_DEVICE_ID, uuid.toString()).commit();
		}
		return uuid;
	}

	/**
	 * Get a unique id for this device.
	 * @param c
	 * @return
	 */
	public static UUID getDeviceUuid(Context c) {
		if (uuid==null) {
			synchronized (Helpers.class) {
				if (uuid==null)
					uuid = getUuid(c);				
			}
		}
		return uuid;
	}
	
	/**
	 * 
	 * @param c
	 * @param uid
	 * @return
	 * @throws JSONException
	 */
	public static JSONArray getPackagesForUid(Context c, int uid) throws JSONException {
		PackageManager pm = c.getPackageManager();
		JSONArray res = new JSONArray();
		String[] pkgs = pm.getPackagesForUid(uid);
		if (pkgs!=null) {
			for (int i = 0; i < pkgs.length; i++) {
				try {
					CharSequence appLabel = 
							pm.getApplicationLabel(
									pm.getApplicationInfo(
											pkgs[i], 
											PackageManager.GET_META_DATA));
					JSONObject pkg = new JSONObject();
					pkg.put("package", pkgs[i]);
					pkg.put("app_label",appLabel.toString());
					res.put(pkg);
				} catch (NameNotFoundException e) {
				} catch (Exception e) {
				}
			}
		}
		return res;
	}
	
	/**
	 * Check for the OpenVPN for Android app:
	 * https://play.google.com/store/apps/details?id=de.blinkt.openvpn
	 * @param c
	 * @return
	 */
	public static boolean isOpenVPNClientInstalled(Context c) {
		boolean res = false;
		PackageManager pm = c.getPackageManager();
		for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
			if (ai.packageName.contains("de.blinkt.openvpn")) {
				res = true;
				break;
			}
		}
		return res;
	}
	
	/**
	 * Check for the Llama app.
	 * @param c
	 * @return
	 */
	public static boolean isLlamaInstalled(Context c) {
		boolean res = false;
		PackageManager pm = c.getPackageManager();
		for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
			if (ai.packageName.contains("com.kebab.Llama")) {
				res = true;
				break;
			}
		}
		return res;		
	}
	
	/**
	 * 
	 * @return
	 */
    public static boolean isCaCertInstalled(String match) {
    	boolean res = false;
		try {
			KeyStore ks = KeyStore.getInstance("AndroidCAStore");
			ks.load(null, null);
			Enumeration<String> aliases = ks.aliases();
			while (aliases.hasMoreElements()) {
			    String alias = aliases.nextElement();
			    X509Certificate cert = (X509Certificate)ks.getCertificate(alias);
			    //Log.d(Constants.LOGTAG, "keystore: " + alias + "/" + cert.getIssuerDN().getName());
			    if (cert.getIssuerDN().getName().contains(match)) {
			    	res = true;
			    	break;
			    }
			}		
		} catch (KeyStoreException e) {
			Log.w(Constants.LOGTAG, "failed to check certificates", e);
		} catch (NoSuchAlgorithmException e) {
		} catch (CertificateException e) {
		} catch (IOException e) {
		}
		return res;
	}
    
    /**
     * Read a given file and return a list of lines.
     * @param file
     * @return
     */
    public static List<String> readProc(String file) {
		List<String> lines = new ArrayList<String>();
		BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file), 500);
            String line;
			while ((line = in.readLine()) != null) {
				lines.add(line.trim());
			}
        } catch (FileNotFoundException e) {
        	Log.w(Constants.LOGTAG, "could not find " + file, e);
        } catch (IOException e) {
        	Log.w(Constants.LOGTAG, "could not read " + file, e);
        }
        if (in!=null)
			try {
				in.close();
			} catch (IOException e) {
			}
        return lines;
	}
}
