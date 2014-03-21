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
package fr.inria.ucn.ui;

import java.util.HashMap;
import java.util.Map;

import de.blinkt.openvpn.api.APIVpnProfile;
import de.blinkt.openvpn.api.IOpenVPNAPIService;

import fr.inria.ucn.Constants;
import fr.inria.ucn.DataUploader;
import fr.inria.ucn.Helpers;
import fr.inria.ucn.R;
import fr.inria.ucn.Scheduler;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;

/**
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {

	private Map<String,String> profiles = null;
	private IOpenVPNAPIService mService = null;
    private ServiceConnection mConnection = null;
	
	/*
	 * (non-Javadoc)
	 * @see android.preference.PreferenceFragment#onCreate(android.os.Bundle)
	 */
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // Listen for changes
        SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        // Set summaries
		Preference prefI = findPreference(Constants.PREF_INTERVAL);
		prefI.setSummary(prefs.getString(Constants.PREF_INTERVAL, 
				Long.toString(Scheduler.DEFAULT_IV)));
		Preference prefL = findPreference(Constants.PREF_LABEL);
		prefL.setSummary(prefs.getString(Constants.PREF_LABEL, 
				getResources().getString(R.string.pref_label_default)));
		Preference prefUp = findPreference(Constants.PREF_UPLOAD_URL);
		prefUp.setSummary(prefs.getString(Constants.PREF_UPLOAD_URL, 
				DataUploader.DEFAULT_URL));
		Preference prefWf = findPreference(Constants.PREF_WRITE_FILE);
		prefWf.setSummary(prefs.getString(Constants.PREF_WRITE_FILE, 
				DataUploader.DEFAULT_FILE));
		
		if (Helpers.isOpenVPNClientInstalled(getActivity())) {
			mConnection = new ServiceConnection() {
				/*
				 * (non-Javadoc)
				 * @see android.content.ServiceConnection#onServiceConnected(android.content.ComponentName, android.os.IBinder)
				 */
		        public void onServiceConnected(ComponentName className, IBinder service) {
		        	Log.d(Constants.LOGTAG, "openvpn api connected to " + className.getPackageName());
		            mService = IOpenVPNAPIService.Stub.asInterface(service);
					try {
						Intent p = mService.prepare(getActivity().getPackageName());
						if (p!=null) // need to request permission from the user
							getActivity().startActivityForResult(p,0);
						else // already has permission
							onActivityResult(0, Activity.RESULT_OK, null);
					} catch (RemoteException e) {
						Log.w(Constants.LOGTAG, "openvpn api prepare failed",e);
					}
		        }

		        /*
		         * (non-Javadoc)
		         * @see android.content.ServiceConnection#onServiceDisconnected(android.content.ComponentName)
		         */
		        public void onServiceDisconnected(ComponentName className) {
		        	Log.d(Constants.LOGTAG, "openvpn api disconnected from " + className.getPackageName());
		            mService = null;
		        }
		    };
		    
		    // connect
			getActivity().bindService(
					new Intent(IOpenVPNAPIService.class.getName()), 
					mConnection, 
					Context.BIND_AUTO_CREATE);			
		} else {
			// disable VPN settings
			findPreference(Constants.PREF_VPN).setEnabled(false);
		}
    }

	/*
	 * (non-Javadoc)
	 * @see android.preference.PreferenceFragment#onActivityResult(int, int, android.content.Intent)
	 */
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode==Activity.RESULT_OK) {			
			findPreference(Constants.PREF_VPN).setEnabled(true);

			ListPreference prefVpn = (ListPreference)findPreference(Constants.PREF_VPN_PROFILE);		

			this.profiles = new HashMap<String,String>();
			if (mService!=null) {
				try {
					for (APIVpnProfile p : mService.getProfiles()) {
						Log.d(Constants.LOGTAG, "OpenVPN profile "+p.mName + "/"+p.mUUID);
						this.profiles.put(p.mUUID,p.mName);
					}
				} catch (RemoteException e) {
					Log.w(Constants.LOGTAG, "failed to read OpenVPN profiles",e);
				}
			}
			
			// user readable name
			CharSequence[] ecs = profiles.values().toArray(new CharSequence[profiles.size()]);
			prefVpn.setEntries(ecs);

			// uuid
			CharSequence[] vcs = profiles.keySet().toArray(new CharSequence[profiles.size()]);
			prefVpn.setEntryValues(vcs);

	        SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
			String v = prefs.getString(Constants.PREF_VPN_PROFILE,
					getResources().getString(R.string.pref_vpn_profile_default));
			if (profiles.containsKey(v))
				prefVpn.setSummary(profiles.get(v));
			else
				prefVpn.setSummary(v);
		} else {
			// disable VPN settings
			findPreference(Constants.PREF_VPN).setEnabled(false);
		}
	}
	
	/* (non-Javadoc)
	 * @see android.preference.PreferenceFragment#onDestroy()
	 */
	@Override
	public void onDestroy() {
		mService = null;
		if (mConnection!=null)
			getActivity().unbindService(mConnection);
		mConnection = null;
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.SharedPreferences.OnSharedPreferenceChangeListener#onSharedPreferenceChanged(android.content.SharedPreferences, java.lang.String)
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		Preference pref = findPreference(key);
		if (key.equals(Constants.PREF_INTERVAL) ||
			key.equals(Constants.PREF_LABEL) ||
			key.equals(Constants.PREF_UPLOAD_URL) ||
			key.equals(Constants.PREF_WRITE_FILE)) {
			pref.setSummary(sharedPreferences.getString(key, ""));
			
		} else if (key.equals(Constants.PREF_VPN_PROFILE)) {
			String v = sharedPreferences.getString(key, 
					getResources().getString(R.string.pref_vpn_profile_default));
			if (profiles.containsKey(v))
				pref.setSummary(profiles.get(v));
			else
				pref.setSummary(v);
		}
	}
}
