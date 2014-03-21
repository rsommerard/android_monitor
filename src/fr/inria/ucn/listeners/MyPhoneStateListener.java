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
package fr.inria.ucn.listeners;

import org.json.JSONException;
import org.json.JSONObject;

import fr.inria.ucn.Constants;
import fr.inria.ucn.Helpers;
import android.content.Context;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

/**
 * Listen for changes in mobile cell and network connectivity.
 * @author apietila
 *
 */
public class MyPhoneStateListener extends PhoneStateListener {

	private Context c = null;
	
	/**
	 * 
	 * @param c
	 */
	public void enable(Context c) {
		TelephonyManager tm = (TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE);	
		if (tm.getPhoneType()!=TelephonyManager.PHONE_TYPE_NONE) {
			tm.listen(this, PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
			this.c = c;
		} // else no telephony
	}
	
	/**
	 * 
	 * @param c
	 */
	public void disable(Context c) {
		TelephonyManager tm = (TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE);	
		if (tm.getPhoneType()!=TelephonyManager.PHONE_TYPE_NONE) {
			tm.listen(this, PhoneStateListener.LISTEN_NONE);		
			this.c = null;
		} // else no telephony
	}

	/**
	 * 
	 * @return
	 */
	public boolean isEnabled() {
		return (this.c!=null);
	}

	/* (non-Javadoc)
	 * @see android.telephony.PhoneStateListener#onCellLocationChanged(android.telephony.CellLocation)
	 */
	@Override
	public void onCellLocationChanged(CellLocation location) {
		if (location != null) {
			try {
				JSONObject data = new JSONObject();
				if (location instanceof GsmCellLocation) {
					data.put("cid", ((GsmCellLocation)location).getCid());
					data.put("lac", ((GsmCellLocation)location).getLac());
					data.put("psc", ((GsmCellLocation)location).getPsc());
					Helpers.sendResultObj(this.c,"gsm_cell_location",System.currentTimeMillis(),data);					
				} else if (location instanceof CdmaCellLocation) {
					data.put("bs_id", ((CdmaCellLocation)location).getBaseStationId());
					data.put("bs_lat", ((CdmaCellLocation)location).getBaseStationLatitude());
					data.put("bs_lon", ((CdmaCellLocation)location).getBaseStationLongitude());
					data.put("net_id", ((CdmaCellLocation)location).getNetworkId());
					data.put("sys_id", ((CdmaCellLocation)location).getSystemId());
					Helpers.sendResultObj(this.c,"cdma_cell_location",System.currentTimeMillis(),data);					
				}
			} catch (JSONException jex) {
				Log.w(Constants.LOGTAG, "failed to create json object",jex);
			}
		}
	}

	/* (non-Javadoc)
	 * @see android.telephony.PhoneStateListener#onDataConnectionStateChanged(int, int)
	 */
	@Override
	public void onDataConnectionStateChanged(int state, int networkType) {
		try {
			JSONObject data = new JSONObject();
			data.put("state", state);
			data.put("network_type", networkType);
			data.put("network_type_str", Helpers.getTelephonyNetworkType(networkType));
			Helpers.sendResultObj(this.c,"data_conn_state",System.currentTimeMillis(),data);
		} catch (JSONException jex) {
			Log.w(Constants.LOGTAG, "failed to create json object",jex);
		}
	}
}
