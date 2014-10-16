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

import java.util.TimeZone;

import fr.inria.ucn.Constants;
import fr.inria.ucn.R;
import fr.inria.ucn.Scheduler;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
	
	/*
	 * (non-Javadoc)
	 * @see android.preference.PreferenceFragment#onCreate(android.os.Bundle)
	 */
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Resources res = getResources();
        
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
        
        // Listen for changes
        SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        Preference pref;
        
        if (prefs.getBoolean(Constants.PREF_HIDDEN_FIRST, true)) {
        	// set the default upload country based on the current timezone
        	String locale = TimeZone.getDefault().getDisplayName();
    		Log.d(Constants.LOGTAG, "locale="+locale);
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
		
		String hpage = (country.equals("FR") ? Constants.HOMEPAGE_URL_FR : Constants.HOMEPAGE_URL_UK);
		pref = findPreference(Constants.PREF_WEB);
		pref.setSummary(hpage);		
		pref.getIntent().setData(Uri.parse(hpage));
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
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		Preference pref = findPreference(key);
		if (key.equals(Constants.PREF_INTERVAL)) {
			pref.setSummary(sharedPreferences.getString(key, ""));
		}
	}
}
