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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import fr.inria.ucn.Constants;
import fr.inria.ucn.Helpers;
import fr.inria.ucn.R;
import fr.inria.ucn.Scheduler;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

/**
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
	
	private static DateFormat df = SimpleDateFormat.getDateTimeInstance();

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

        Preference pref;
        
        if (prefs.getBoolean(Constants.PREF_HIDDEN_FIRST, true)) {
        	// set the default upload country based on the current timezone
        	String locale = TimeZone.getDefault().getDisplayName();
			pref = findPreference(Constants.PREF_COUNTRY);
			
			SharedPreferences.Editor edit = prefs.edit();
    		if (locale.toLowerCase().contains("central european")) {
    			edit.putString(Constants.PREF_COUNTRY, "FR");
    		} else {
    			edit.putString(Constants.PREF_COUNTRY, "UK");
    		}
			edit.putBoolean(Constants.PREF_HIDDEN_FIRST, false);
    		edit.commit();
        }
        
        // Set summaries
		pref = findPreference(Constants.PREF_INTERVAL);
		pref.setSummary(prefs.getString(Constants.PREF_INTERVAL, 
				Long.toString(Scheduler.DEFAULT_IV)));
		
		String country = prefs.getString(Constants.PREF_COUNTRY, "");
		pref = findPreference(Constants.PREF_COUNTRY);
		pref.setSummary(country);		
		
		pref = findPreference(Constants.PREF_WEB);
		pref.setSummary(Constants.WEBSITE_URLS.get(country));		
		pref.getIntent().setData(Uri.parse(Constants.WEBSITE_URLS.get(country)));

		pref = findPreference(Constants.PREF_UPLOAD);
		pref.setSummary(df.format(prefs.getLong(Constants.PREF_HIDDEN_LASTUPLOAD, 0)));		
		
		// handle upload clicks from 'Advanced Settings'
		pref = findPreference("pref_upload");
		if (pref!=null) {
			pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					Helpers.doUpload(getActivity(), false);
					return true;
				}
			});
		}
    }
	
	/* (non-Javadoc)
	 * @see android.preference.PreferenceFragment#onDestroy()
	 */
	@Override
	public void onDestroy() {
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	/*
	 * (non-Javadoc)
	 * @see android.content.SharedPreferences.OnSharedPreferenceChangeListener#onSharedPreferenceChanged(android.content.SharedPreferences, java.lang.String)
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		Preference pref = findPreference(key);
		if (key.equals(Constants.PREF_INTERVAL)) {
			pref.setSummary(prefs.getString(key, ""));
			
		} else if (key.equals(Constants.PREF_COUNTRY)) {
			String country = prefs.getString(Constants.PREF_COUNTRY, "");
			pref.setSummary(country);
			
			pref = findPreference(Constants.PREF_WEB);
			pref.setSummary(Constants.WEBSITE_URLS.get(country));		
			pref.getIntent().setData(Uri.parse(Constants.WEBSITE_URLS.get(country)));			
		} else if (key.equals(Constants.PREF_HIDDEN_LASTUPLOAD)) {
			pref = findPreference(Constants.PREF_UPLOAD);
			pref.setSummary(df.format(prefs.getLong(Constants.PREF_HIDDEN_LASTUPLOAD, 0)));		
		}
	}
}
