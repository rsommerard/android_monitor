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

import fr.inria.ucn.Constants;
import fr.inria.ucn.Helpers;
import fr.inria.ucn.R;
import fr.inria.ucn.Scheduler;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.CompoundButton;
import android.widget.Switch;

/**
 * Collector preferences.
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class SettingsActivity extends Activity {
	
	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
                
        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }
	
	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.actionbar_toggle, menu);	    	  
	    
	    // On-Off toggle handler
	    Switch s = (Switch)menu.findItem(R.id.onoffswitch).getActionView().findViewById(R.id.switchForActionBar);
    	s.setChecked(Scheduler.isScheduled(getApplicationContext()));
	    
	    s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				// store the setting so that the on-boot receiver can restore the correct state
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
				SharedPreferences.Editor edit = prefs.edit();
				edit.putBoolean(Constants.PREF_HIDDEN_ENABLED, isChecked);
				edit.commit();
				
				if (isChecked) {
					Log.d(Constants.LOGTAG, "enable data collector");
					Helpers.startCollector(getApplicationContext(), false);
				} else {
					Log.d(Constants.LOGTAG, "disable data collector");
					Helpers.stopCollector(getApplicationContext(), false);
				}
			}
		});
	    
	    return super.onCreateOptionsMenu(menu);
	}
}
