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

import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import fr.inria.ucn.Constants;
import fr.inria.ucn.Helpers;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.text.format.Time;
import android.util.Log;

/**
 * Collect data on system state: memory, screen on/off, battery status, cpu ?
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class SysStateCollector implements Collector {

	final static private String PROC_STAT_FILE = "/proc/stat";
	final static private String PROC_UPTIME_FILE = "/proc/uptime";
	final static private String PROC_LOADAVG_FILE = "/proc/loadavg";

	/**
	 * 
	 * @param c
	 * @param ts
	 * @param change
	 */
	@SuppressLint("NewApi")
	public void run(Context c, long ts, boolean change) {
		try {
			JSONObject data = new JSONObject();
			data.put("on_screen_state_change", change); // this collection run was triggered by screen state change
						
			data.put("hostname", Helpers.getSystemProperty("net.hostname","unknown hostname"));
			data.put("current_timezone", Time.getCurrentTimezone());
			
			// general memory state
			ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
			MemoryInfo mi = new MemoryInfo();
			am.getMemoryInfo(mi);
			
			JSONObject mem = new JSONObject();
			mem.put("available", mi.availMem);
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
				mem.put("total", mi.totalMem);
			}
			mem.put("is_low", mi.lowMemory);
			data.put("memory",mem);
			
			// screen state
			PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
			data.put("screen_on", pm.isScreenOn());

			// battery state
			IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
			Intent battery = c.registerReceiver(null, ifilter);
			int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
			int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
			float pct = (float) (100.0 * level) / scale;		
			int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
			boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
			                     status == BatteryManager.BATTERY_STATUS_FULL);
			int chargePlug = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
			boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
			boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;			
			
			JSONObject batt = new JSONObject();
			batt.put("level",level);
			batt.put("scale",scale);
			batt.put("pct",pct);
			batt.put("is_charging",isCharging);
			batt.put("usb_charge",usbCharge);
			batt.put("ac_charge",acCharge);
			data.put("battery", batt);

			// some proc stats
			data.put("cpu", getCpuStat());
			data.put("loadavg", getLoadStat());			
			data.put("uptime", getUptimeStat());			

			// audio state
			data.put("audio", getAudioState(c));			
			
			// done
			Helpers.sendResultObj(c,"system_state",ts,data);
			
		} catch (JSONException jex) {
			Log.w(Constants.LOGTAG, "failed to create json object",jex);
		}
	}	
	
	/* (non-Javadoc)
	 * @see fr.inria.ucn.collectors.Collector#run(android.content.Context)
	 */
	public void run(Context c,long ts) {
		run(c,ts,false);
	}
	
	private JSONObject getLoadStat() throws JSONException {
		List<String> s = Helpers.readProc(PROC_LOADAVG_FILE);
		if (s.size()<=0)
			return null;

		// 0.00 0.04 0.05 3/705 18577
		String[] tmp = s.get(0).split("[ ]+");
		JSONObject load = new JSONObject();
		load.put("1_min_average", Float.parseFloat(tmp[0]));
		load.put("5_min_average", Float.parseFloat(tmp[1]));
		load.put("15_min_average", Float.parseFloat(tmp[2]));
		load.put("active_tasks", Integer.parseInt(tmp[3].split("/")[0]));
		load.put("total_tasks", Integer.parseInt(tmp[3].split("/")[1]));
		return load;
	}

	private JSONObject getUptimeStat() throws JSONException {
		List<String> s = Helpers.readProc(PROC_UPTIME_FILE);
		if (s.size()<=0)
			return null;
		
		String[] tmp = s.get(0).split("[ ]+");
		
		JSONObject u = new JSONObject();
		u.put("uptime", Float.parseFloat(tmp[0]));
		u.put("idle_time", Float.parseFloat(tmp[1]));		
		return u;
	}

	private JSONObject getCpuStat() throws JSONException {
		List<String> sf1 = Helpers.readProc(PROC_STAT_FILE);
		// sleep 1s between two /proc/stat samples to count the instantaneous usage
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
		List<String> sf2 = Helpers.readProc(PROC_STAT_FILE);

		String[] stat1 = (sf1.size()>0 && sf1.get(0).startsWith("cpu ") ? sf1.get(0).split("[ ]+") : null);
		String[] stat2 = (sf2.size()>0 && sf2.get(0).startsWith("cpu ") ? sf2.get(0).split("[ ]+") : null);
		if (stat1 == null || stat2==null)
			return null; // unexpected format
		
		// user = user + nice
		long u1 = Long.parseLong(stat1[1]) + Long.parseLong(stat1[2]);
		// system = system + intr + soft_irq
		long s1 = Long.parseLong(stat1[3]) + Long.parseLong(stat1[6]) + Long.parseLong(stat1[7]);
		// total = user + system + idle + io_wait
		long t1 = u1 + s1 + Long.parseLong(stat1[4]) + Long.parseLong(stat1[5]);

		// user = user + nice
		long u2 = Long.parseLong(stat2[1]) + Long.parseLong(stat2[2]);
		// system = system + intr + soft_irq
		long s2 = Long.parseLong(stat2[3]) + Long.parseLong(stat2[6]) + Long.parseLong(stat2[7]);
		// total = user + system + idle + io_wait
		long t2 = u1 + s1 + Long.parseLong(stat2[4]) + Long.parseLong(stat2[5]);

		long dtotal = t2-t1;
		long duser = u2-u1;
		long dsys = s2-s1;

		if (t2>=t1) {
			JSONObject cpu = new JSONObject();
			cpu.put("user", (double)duser*100.0/dtotal);
			cpu.put("system", (double)dsys*100.0/dtotal);
			cpu.put("total", (double)(dsys+duser)*100.0/dtotal);
			return cpu;
		} else {
			return null;
		}
	}
	
	@SuppressWarnings("deprecation")
	private JSONObject getAudioState(Context c) throws JSONException {
		AudioManager am = (AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
		
		JSONObject data = new JSONObject();
		
		data.put("is_bluetooth_a2dp_on", am.isBluetoothA2dpOn());
		data.put("is_microphone_mute", am.isMicrophoneMute());
		data.put("is_music_active", am.isMusicActive());
		data.put("is_speaker_phone_on", am.isSpeakerphoneOn());
		data.put("is_wired_headset_on", am.isWiredHeadsetOn());
		
		switch (am.getMode()) {
		case AudioManager.MODE_IN_CALL:	
			data.put("mode", "in_call");
			break;
		case AudioManager.MODE_IN_COMMUNICATION:
			data.put("mode", "in_comm");
			break;
		case AudioManager.MODE_NORMAL:
			data.put("mode", "normal");
			break;
		case AudioManager.MODE_RINGTONE:
			data.put("mode", "ringtone");
			break;
		case AudioManager.MODE_INVALID:
		default:	
			data.put("mode", "invalid");
			break;
		}	

		switch (am.getRingerMode()) {
		case AudioManager.RINGER_MODE_VIBRATE:
			data.put("ringer_mode", "vibrate");
			break;
		case AudioManager.RINGER_MODE_SILENT:
			data.put("ringer_mode", "silent");
			break;
		case AudioManager.RINGER_MODE_NORMAL:
			data.put("ringer_mode", "normal");
			break;
		default:	
			data.put("ringer_mode", "invalid");
			break;
		}
		return data;
	}
}
