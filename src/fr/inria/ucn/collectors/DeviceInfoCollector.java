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

import fr.inria.ucn.Constants;
import fr.inria.ucn.Helpers;
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;

/**
 * Collect static info about the device. Basically only needs to be called once
 * per device.
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class DeviceInfoCollector implements Collector {

	/* (non-Javadoc)
	 * @see fr.inria.ucn.collectors.Collector#run(android.content.Context)
	 */
	@Override
	public void run(Context c, long ts) {
		try {
			JSONObject data = new JSONObject();
			
			// android dev info + build
			JSONObject build = new JSONObject();
			build.put("brand", android.os.Build.BRAND);
			build.put("board", android.os.Build.BOARD);
			build.put("cpu_api", android.os.Build.CPU_ABI);
			build.put("device", android.os.Build.DEVICE);
			build.put("display", android.os.Build.DISPLAY);
			build.put("manufacturer", android.os.Build.MANUFACTURER);
			build.put("model", android.os.Build.MODEL);
			build.put("product", android.os.Build.PRODUCT);
			build.put("version_sdk", android.os.Build.VERSION.SDK_INT);
			build.put("version_release", android.os.Build.VERSION.RELEASE);
			data.put("build", build);

			// display
			JSONObject display = new JSONObject();
			DisplayMetrics metrics = c.getResources().getDisplayMetrics();
			display.put("dpi", metrics.densityDpi);
			display.put("height", metrics.heightPixels);
			display.put("width", metrics.widthPixels);
			display.put("density", metrics.density);
			data.put("display",display);

			// done
			Helpers.sendResultObj(c,"device_info",ts,data);
			
		} catch (JSONException jex) {
			Log.w(Constants.LOGTAG, "failed to create json object",jex);
		}

	}

}
