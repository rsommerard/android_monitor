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

import android.content.Context;
import android.os.Environment;
import android.util.Log;

/**
 * Record stored Llama app areas (user tagged environments either based on available WiFi
 * networks or mobile cells. The areas are usually stored to /sdcard/Llama/Llama_Areas.txt
 * (after user manually exports the data).
 * 
 * TODO: can we somehow trigger data export from Llama ? 
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class LlamaCollector implements Collector {
	
	/* (non-Javadoc)
	 * @see fr.inria.ucn.collectors.Collector#run(android.content.Context, long)
	 */
	@Override
	public void run(Context c, long ts) {
		try {
			String state = Environment.getExternalStorageState();
			File llamaAreas = new File(Environment.getExternalStorageDirectory() + "/Llama/Llama_Areas.txt");
			
			if (Environment.MEDIA_MOUNTED.equals(state) && llamaAreas.exists()) {
				JSONObject data = new JSONObject();
				data.put("provider","Llama");
				data.put("source_file",llamaAreas.toString());			

				BufferedReader in = null;
				try {
					in = new BufferedReader(new FileReader(llamaAreas), 1024);
					String line;
					JSONObject a = new JSONObject();
					while ((line = in.readLine()) != null) {						
						String[] tmp = line.trim().split("\\|");
						JSONArray wifi = new JSONArray();
						JSONArray cell = new JSONArray();
						for (int i = 1; i < tmp.length; i++) {
							String[] tmp2 = tmp[i].split("\\:");
							if (tmp2[0].trim().equalsIgnoreCase("W")) {
								wifi.put(tmp2[1].trim());
							} else {
								// FIXME: figure out what these values are ?!?
								JSONObject cid = new JSONObject();
								cid.put("1", Integer.parseInt(tmp2[0].trim()));
								cid.put("2", Integer.parseInt(tmp2[1].trim()));
								cid.put("3", Integer.parseInt(tmp2[2].trim()));
								cell.put(cid);
							}
						}
						JSONObject o = new JSONObject();
						o.put("wifi_networks", wifi); // wifi networks associated to this area
						o.put("cells", cell);         // cell ids associated to this area
						a.put(tmp[0].trim(),o);
					}
					data.put("locations", a);
					
					// done
					Helpers.sendResultObj(c,"user_location",ts,data);
					
				} catch (FileNotFoundException e) {
					Log.w(Constants.LOGTAG, "Llama exports not available", e);
				} catch (IOException e) {
					Log.w(Constants.LOGTAG, "Llama exports not available", e);
				}
				
				if (in!=null)
					try {
						in.close();
					} catch (IOException e) {
					}
			} else {
				Log.w(Constants.LOGTAG, "Llama exports not available, file="+llamaAreas.toString()+", exists=" + llamaAreas.exists());
			}
			
		} catch (JSONException jex) {
			Log.w(Constants.LOGTAG, "failed to create json object",jex);
		}
	}
}
