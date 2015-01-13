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
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HttpsURLConnection;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public final class DataUploader {

	private static final String LF = "\r\n";
	private static final String TH = "--";
	private static final String BOUNDARY = "*****";

	// max number of items to upload in single batch
	private static final int UPLOAD_BATCH = 1000;
	
	/* Hide constructor, no instances needed. */
	private DataUploader() {};
	
	/**
	 * 
	 * @return
	 */
	public static boolean upload(Context c, DataStore ds) {
		// upload settings
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		boolean requireWifi = prefs.getBoolean(Constants.PREF_UPLOAD_WIFI, true);
		String country = prefs.getString(Constants.PREF_COUNTRY, null);
		
		if (country == null) {
			// no country ?
			Log.w(Constants.LOGTAG, "uploader: got null country, can't upload");
			return false;
		}
		
		ConnectivityManager cm = (ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = cm.getActiveNetworkInfo();
		if (networkInfo == null || !networkInfo.isConnected()) {
			// no network - do nothing
			Log.w(Constants.LOGTAG, "uploader: no network connection, can't upload");
			return false;
		} else if (requireWifi && networkInfo.getType() != ConnectivityManager.TYPE_WIFI) {
			// not a wifi network - do nothing
			Log.w(Constants.LOGTAG, "uploader: not a wifi network connection, can't upload");
			return false;
		}
		
		String uploadto = Constants.UPLOAD_URLS.get(country);
		URL url = null;
		try {
			url = new URL(uploadto);
			// TODO: remove once the uk server has valid SSL certificate
			if ("UK".equals(country)) {
				if (url.getProtocol().equals("https") && !Helpers.isCaCertInstalledHack(url.getHost())) {
					Log.w(Constants.LOGTAG, "uploader: missing required certificate, going to upload in cleartext!! " + url.getHost());
					url = new URL(uploadto.replace("https://", "http://"));						
				}
			}
		} catch (MalformedURLException e) {
			Log.w(Constants.LOGTAG, "uploader: invalid upload url "+uploadto+", can't upload",e);				
			return false;
		}
		
		Log.d(Constants.LOGTAG, "uploader: upload data to " + uploadto);
		
		// perform uploads in batch
		boolean res = false;
		int count = 0;
		while (true) {
			Map<Integer,String> data = ds.getData(UPLOAD_BATCH);
			List<Integer> uploaded = uploadBatch(data, url);

			if (uploaded == null) {
				res = false; // something went wrong, stop here
				break;				
			}
			
			// purge uploaded
			for (Integer id : uploaded) {
				Log.d(Constants.LOGTAG, "uploader: remove entry " + id);
				ds.removeData(id.intValue());
				count += 1;
			}
			
			if (uploaded.size()==0) {
				res = true; // done nothing more to upload
				break;
			} 
		}
		
		if (res) {
			SharedPreferences.Editor edit = prefs.edit();
			edit.putLong(Constants.PREF_HIDDEN_LASTUPLOAD, System.currentTimeMillis());
			edit.commit();
		}
		
		Log.d(Constants.LOGTAG,"uploader: uploaded " + count + " objects, result is " + (res ? "success" : "failure"));
		return res;
	}
	
	/*
	 * Process upload batch. Returns list of uploaded items (can be empty) or null in case of failure.
	 */
	private static List<Integer> uploadBatch(Map<Integer,String> data, URL url) {
		List<Integer> uploaded = new ArrayList<Integer>();
		if (data==null || data.size()==0) {
			return uploaded; // all done!
		}
		
		if (url==null) // should not happen
			return null;
		
		HttpURLConnection conn = null;
		DataOutputStream remoteout = null;
		BufferedReader remotein = null;
		try {			
			if (url!=null) {
				if (url.getProtocol().equals("https")) {
					conn = (HttpsURLConnection)url.openConnection();
				} else { 
					conn = (HttpURLConnection)url.openConnection();
				}
				conn.setDoInput(true);
				conn.setDoOutput(true);
				conn.setUseCaches(false);
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Connection", "Keep-Alive");
				conn.setRequestProperty("Content-Type", "multipart/form-data;boundary="+BOUNDARY);
				
				remoteout = new DataOutputStream(conn.getOutputStream());	
			}

			for (Entry<Integer,String> e : data.entrySet()) {				
				if (conn!=null) {
					remoteout.writeBytes(TH + BOUNDARY + LF);
					remoteout.writeBytes("Content-Disposition: form-data; name=\"json\";filename=\"" + e.getKey().toString() +"\"" + LF);
					remoteout.writeBytes("Content-Type: application/json" + LF);
					remoteout.writeBytes(LF);
					remoteout.writeBytes(e.getValue());
					remoteout.writeBytes(LF);
					remoteout.flush();					
				}

				uploaded.add(e.getKey());
			}
			Log.d(Constants.LOGTAG, "uploader: logged/uploaded " + uploaded.size() + " entries");
			
			if (conn!=null) {
				// end boundary
				remoteout.writeBytes(TH + BOUNDARY + TH + LF);
				remoteout.flush();					

				// check server response
				Log.d(Constants.LOGTAG,"uploader: server response " + conn.getResponseCode());
				if (conn.getResponseCode()!=200) {
					uploaded = null; // something went wrong, ignore this upload
				}
			}
			
		} catch (FileNotFoundException e) {
			Log.w(Constants.LOGTAG, "datauploader failed", e);
			uploaded = null; // something went wrong, ignore this upload
		} catch (MalformedURLException e) {
			Log.w(Constants.LOGTAG, "datauploader failed", e);
			uploaded = null; // something went wrong, ignore this upload
		} catch (IOException e) {
			Log.w(Constants.LOGTAG, "datauploader failed", e);
			uploaded = null; // something went wrong, ignore this upload
		} finally {
			if (remoteout!=null) {
				try {
					remoteout.flush();
					remoteout.close();
				} catch (IOException e) {
				}
				remoteout = null;				
			}
			
			if (remotein != null) {
				try {
					remotein.close();
					remotein = null;
				} catch (IOException e) {
				}
			}
			
			if (conn!=null) {
				conn.disconnect();
				conn = null;
			}
		}
		
		return uploaded;
	}
}
