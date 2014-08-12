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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import fr.inria.ucn.Constants;
import fr.inria.ucn.Helpers;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

/**
 * Records the list of running apps, foreground app, ..
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class RunningAppsCollector implements Collector {
	
	/* (non-Javadoc)
	 * @see fr.inria.ucn.collectors.Collector#run()
	 */
	@SuppressLint("NewApi")
	@Override
	public void run(Context c, long ts) {
		try {
			ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
			PackageManager pm = c.getPackageManager();

			JSONObject data = new JSONObject();
						
			// running tasks
			JSONArray runTaskArray = new JSONArray();
			List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(50);
			boolean first = true;
			for (ActivityManager.RunningTaskInfo info : taskInfo) {
				JSONObject jinfo = new JSONObject();
				jinfo.put("task_id", info.id);
				jinfo.put("task_num_activities", info.numActivities);
				jinfo.put("task_num_running", info.numRunning);
				
				// is this the foreground process ?
				jinfo.put("task_foreground", first);		
				if (first) {
					first = false;
				}
				
				if (info.topActivity!=null) {
					jinfo.put("task_top_class_name", info.topActivity.getClassName());
					jinfo.put("task_top_package_name", info.topActivity.getPackageName());
					try {
						// map package name to app label
						CharSequence appLabel = 
								pm.getApplicationLabel(
										pm.getApplicationInfo(
													info.topActivity.getPackageName(), 
													PackageManager.GET_META_DATA));
						jinfo.put("task_app_label", appLabel.toString());
					} catch (NameNotFoundException e) {
					}
				}
				
				runTaskArray.put(jinfo);
			}
			data.put("runningTasks", runTaskArray);
			
			// processes
			JSONArray runProcArray = new JSONArray();
			for (ActivityManager.RunningAppProcessInfo pinfo : am.getRunningAppProcesses()) {
				JSONObject jinfo = new JSONObject();
				jinfo.put("proc_uid", pinfo.uid);
				jinfo.put("proc_name", pinfo.processName);
				jinfo.put("proc_packages", Helpers.getPackagesForUid(c,pinfo.uid));
				runProcArray.put(jinfo);
			}
			data.put("runningAppProcesses", runProcArray);
			
			// done
			Helpers.sendResultObj(c,"running_apps",ts,data);
			
		} catch (JSONException jex) {
			Log.w(Constants.LOGTAG, "failed to create json obj",jex);
		}
	}
}
