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

import fr.inria.ucn.R;

import android.preference.DialogPreference;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

/**
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class TimePreference extends DialogPreference {

    private TimePicker picker = null;
    
    private int h = 0;
    private int m = 0;

    /* We store the pref value internally as seconds since 00:00 */
    private int getValue() {
    	if (this.h==24)
        	return this.m*60; // 00:mm
    	return this.h*3600 + this.m*60; // hh:mm
    }
    private int getHour(int v) {
    	return (int)v/3600;
    }
    private int getMinute(int v) {
    	return (v-getHour(v)*3600)/60;
    }
    
    /**
     * 
     * @param c
     * @param attrs
     */
    public TimePreference(Context c, AttributeSet attrs) {
        super(c, attrs);
        setPositiveButtonText(c.getResources().getString(R.string.pref_hour_set));
        setNegativeButtonText(c.getResources().getString(R.string.pref_hour_cancel));
    }

    /*
     * (non-Javadoc)
     * @see android.preference.DialogPreference#onCreateDialogView()
     */
    @Override
    protected View onCreateDialogView() {
    	picker=new TimePicker(getContext());
    	return(picker);
    }

    /*
     * (non-Javadoc)
     * @see android.preference.DialogPreference#onBindDialogView(android.view.View)
     */
    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        picker.setCurrentHour(this.h);
        picker.setCurrentMinute(this.m);
    }

    /*
     * (non-Javadoc)
     * @see android.preference.DialogPreference#onDialogClosed(boolean)
     */
    @Override
    protected void onDialogClosed(boolean positiveResult) {
    	super.onDialogClosed(positiveResult);

    	if (positiveResult) {
            this.h = picker.getCurrentHour();
            this.m = picker.getCurrentMinute();
            setSummary(getSummary());
            if (callChangeListener(getValue())) {
                persistInt(getValue());
                notifyChanged();
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see android.preference.Preference#onGetDefaultValue(android.content.res.TypedArray, int)
     */
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
    	return a.getInteger(index,0);
    }

    /*
     * (non-Javadoc)
     * @see android.preference.Preference#onSetInitialValue(boolean, java.lang.Object)
     */
    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
    	if (restoreValue) {
    		int v = getPersistedInt(0);
    		this.h = getHour(v);
    		this.m = getMinute(v);
        } else {
            if (defaultValue != null) {
            	this.h = getHour((Integer)defaultValue);
            	this.m = getMinute((Integer)defaultValue);
            	persistInt(getValue()); // store the default value
            }
        }
        setSummary(getSummary());
    }
    
    /*
     * (non-Javadoc)
     * @see android.preference.Preference#getSummary()
     */
    @SuppressLint("DefaultLocale")
	@Override
    public CharSequence getSummary() {
        return String.format("%02d:%02d", this.h, this.m);
    }
}
