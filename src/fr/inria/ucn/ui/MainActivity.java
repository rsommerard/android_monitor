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

import java.io.BufferedInputStream;
import java.io.IOException;

import de.blinkt.openvpn.api.IOpenVPNAPIService;
import de.blinkt.openvpn.api.IOpenVPNStatusCallback;

import fr.inria.ucn.CollectorService;
import fr.inria.ucn.Constants;
import fr.inria.ucn.DataStore;
import fr.inria.ucn.DataUploader;
import fr.inria.ucn.Helpers;
import fr.inria.ucn.R;
import fr.inria.ucn.Scheduler;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.security.KeyChain;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * Simple activity to start/stop the collector and display status.
 * 
 * @author Anna-Kaisa Pietilainen <anna-kaisa.pietilainen@inria.fr>
 *
 */
public class MainActivity extends Activity implements Handler.Callback {

	private DataStore dstore = null;
	private boolean collectorOn = false;
	private TextView uptimeTv = null;
	private TextView uploadTv = null;
	private ToggleButton collectorToggle = null;
	private ToggleButton vpnToggle = null;
	private ResponseReceiver respRecv = null;
	
	// for handling OpenVPN
	private IOpenVPNAPIService mService = null;
    private ServiceConnection mConnection = null;
	private IOpenVPNStatusCallback mCallback = null;
	private Handler handler = null;
	
	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// layout
		setContentView(R.layout.activity_main);
		
		// few shortcuts
		this.uptimeTv = (TextView)findViewById(R.id.uptime_text);
		this.uploadTv = (TextView)findViewById(R.id.upload_text);
		this.collectorToggle = (ToggleButton)findViewById(R.id.toggleButton1);
		this.vpnToggle = (ToggleButton)findViewById(R.id.toggleButton2);

		// set default preferences if not set yet
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		// check if this is the first time
		SharedPreferences prefs = Helpers.getUserSettings(this);
		if (prefs.getBoolean(Constants.PREF_HIDDEN_FIRST, true)) {
			SharedPreferences.Editor edit = prefs.edit();
			edit.putBoolean(Constants.PREF_HIDDEN_FIRST, false);
			// update defaults
			edit.putString(Constants.PREF_UPLOAD_URL, DataUploader.DEFAULT_URL);
			edit.putString(Constants.PREF_WRITE_FILE, DataUploader.DEFAULT_FILE);
			edit.commit();		
		}
	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {		
		// going hidden - release resources
		if (respRecv!=null) {
			unregisterReceiver(respRecv);
			respRecv = null;
		}
		if (dstore!=null) {
			dstore.close();
			dstore = null;
		}
		if (mService!=null) {
			try {
				if (mCallback!=null) {
					mService.unregisterStatusCallback(mCallback);
				}
			} catch (RemoteException e) {
			}
			mService = null;
			mCallback = null;
		}
		if (mConnection!=null)
			unbindService(mConnection);
		mConnection = null;
		super.onPause();
	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();
		
		this.dstore = new DataStore(getApplicationContext());
		this.dstore.open(true);
		
		// coming visible - check the UI state
		this.collectorOn = Scheduler.isCollectorScheduled(getApplicationContext());
		this.collectorToggle.setChecked(this.collectorOn);
		if (this.collectorOn) {
			String uptime = dstore.getKeyValue(Constants.STATUS_RUNNING_SINCE);		
			if (uptime!=null && !uptime.equals(Constants.NULL_UPTIME)) {
				// Uptime: HH:MM:SS
				long elapsed = (System.currentTimeMillis() - Long.parseLong(uptime)) / 1000;
				Log.d(Constants.LOGTAG, "uptime " + elapsed + "s " + DateUtils.formatElapsedTime(elapsed));
				uptimeTv.setText(DateUtils.formatElapsedTime(elapsed));
			} else { // Should not happen right ?
				Log.d(Constants.LOGTAG, "uptime not available ??");
				uptimeTv.setText(DateUtils.formatElapsedTime(0));
			}
		} // else not running

		String last = dstore.getKeyValue(Constants.STATUS_LAST_UPLOAD);
		if (last!=null) {
			// Last upload: xxx time ago
			uploadTv.setText(DateUtils.getRelativeTimeSpanString(Long.parseLong(last)));
		} // else never uploaded
		
		// register broadcast receiver for service status broadcasts		
		this.respRecv = new ResponseReceiver();
		registerReceiver(respRecv,new IntentFilter(Constants.ACTION_STATUS));
		
		// connect to the OpenVPN instance
		if (Helpers.isOpenVPNClientInstalled(getApplicationContext())) {
			this.handler = new Handler(this);
			this.mConnection = new ServiceConnection() {
		        public void onServiceConnected(ComponentName className, IBinder service) {
		        	Log.d(Constants.LOGTAG, "openvpn api connected to " + className.getPackageName());
		            mService = IOpenVPNAPIService.Stub.asInterface(service);
					try {
						Intent p = mService.prepare(getPackageName());
						if (p!=null) // need to request permission from the user
							startActivityForResult(p,0);
						else // already has permission
							onActivityResult(0, Activity.RESULT_OK, null);
					} catch (RemoteException e) {
						Log.w(Constants.LOGTAG, "openvpn api prepare failed",e);
					}
		        }

		        public void onServiceDisconnected(ComponentName className) {
		        	Log.d(Constants.LOGTAG, "openvpn api disconnected from " + className.getPackageName());
		            mService = null;
		        }
		    };
		    
		    // connect
			bindService(
					new Intent(IOpenVPNAPIService.class.getName()), 
					mConnection, 
					Context.BIND_AUTO_CREATE);		
		} else {
			Log.d(Constants.LOGTAG, "openvpn not found");
			onActivityResult(0, RESULT_CANCELED, null);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle presses on the action bar items
	    switch (item.getItemId()) {
	        case R.id.action_settings:
				Intent i = new Intent(this, SettingsActivity.class);
				startActivity(i);
	            return true;
	        case R.id.action_about:
	            startActivity(new Intent(this, AboutActivity.class));
	            return true;
	        case R.id.action_upload:
	            doUpload();
	            return true;
	        case R.id.action_sample:
	            doSample();
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
	 */
	@SuppressLint("NewApi")
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 0) {	// from onResume
			if (resultCode == RESULT_OK) {
				// can talk to OpenVPN service
				this.mCallback = new IOpenVPNStatusCallback.Stub() {
			        @Override
			        public void newStatus(String uuid, String state, String message, String level) throws RemoteException {
			            Message msg = Message.obtain(handler, 0, state + "|" + message);
			            msg.sendToTarget();
			        }
			    };
				try {
					mService.registerStatusCallback(mCallback);
				} catch (RemoteException e) {
					Log.w(Constants.LOGTAG, "openvpn api registerStatusCallback failed",e);
				}
			} else {
				mService = null;
				if (mConnection!=null)
					unbindService(mConnection);
				mConnection = null;					
			}
			
			// in any case, last step is to verify ca certs
			SharedPreferences prefs = Helpers.getUserSettings(this);
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH && 
					prefs.getString(Constants.PREF_UPLOAD_URL, DataUploader.DEFAULT_URL).contains("cmon.lip6.fr") &&
					!Helpers.isCaCertInstalled("cmon.lip6.fr"))  
			{					
				
				// prompt user to install the self-signed ca certificate - for https uploads
				Log.d(Constants.LOGTAG, "missing required ca certificate \"cmon.lip6.fr\""); 
				BufferedInputStream bis = null;
				try {
					bis = new BufferedInputStream(getAssets().open("cmon.lip6.fr.ca.pem"));
					byte[] keychain = new byte[bis.available()];
					bis.read(keychain);

					Intent installIntent = KeyChain.createInstallIntent();
					installIntent.putExtra(KeyChain.EXTRA_CERTIFICATE, keychain);
					installIntent.putExtra(KeyChain.EXTRA_NAME, "cmon.lip6.fr");
					startActivityForResult(installIntent, 1);		

				} catch (IOException e) {
					Log.e(Constants.LOGTAG, "failed to read certificate",e); 
				} finally {
					if (bis!=null)
						try {
							bis.close();
						} catch (IOException e) {
						}
				}
			}
		} // requestCode!=0 from cert installer
	}
	
	/**
	 * Data collector ON/OFF toggle handler.
	 * @param view
	 */
	public void handleDataToggle(View view) {
		this.collectorOn = ((ToggleButton)view).isChecked();
			
		// ask the service to schedule or cancel
		Intent intent = new Intent(getApplicationContext(), CollectorService.class);
		intent.setAction(Constants.ACTION_SCHEDULE);
		intent.putExtra(Constants.INTENT_EXTRA_SCHEDULER_START, this.collectorOn);
		
		// service will broadcast the new uptime value to the UI - refresh the ui then
		startService(intent);
	}

	/**
	 * Start/stop VPN tunnel.
	 * @param view
	 */
	public void handleVpnToggle(View view) {
		if (Helpers.isOpenVPNClientInstalled(getApplicationContext())) {
			if (mService!=null) {
				if (((ToggleButton)view).isChecked()) {
					String profile = PreferenceManager.getDefaultSharedPreferences(this).getString(Constants.PREF_VPN_PROFILE, null);
					if (profile != null) {
						try {
							mService.startProfile(profile);
						} catch (RemoteException e) {
							Log.w(Constants.LOGTAG, "failed to connect OpenVPN profile " + profile,e);
						}
						/*
						final String EXTRA_NAME = "de.blinkt.openvpn.shortcutProfileName";
						Intent vpnIntent = new Intent(Intent.ACTION_MAIN);
						vpnIntent.setClassName("de.blinkt.openvpn", "de.blinkt.openvpn.LaunchVPN");
						vpnIntent.putExtra(EXTRA_NAME,profile);
						startActivity(vpnIntent);
						*/
					} else {
						Toast.makeText(getApplicationContext(), "No OpenVPN profile set (see Menu -> Settings)", Toast.LENGTH_LONG).show();
					}
				} else {
					try {
						mService.disconnect();
					} catch (RemoteException e) {
						Log.w(Constants.LOGTAG, "failed to disconnect OpenVPN",e);
					}
				}
			} else { // should not happen
				Log.w(Constants.LOGTAG, "OpenVPN service connection not available");
			}
		} else {
			showNoVpnAlertDialog();
		}
	}

	/* Simple alert to direct user to the OpenVPN for Android market page */
	private void showNoVpnAlertDialog() {
		// show an alert to remind user to install openvpn
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.dialog_openvpn_install_msg);
		builder.setPositiveButton(R.string.dialog_openvpn_install_yes, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// User clicked OK button
				Intent intent = new Intent(Intent.ACTION_VIEW); 
				intent.setData(Uri.parse("market://details?id=de.blinkt.openvpn")); 
				startActivity(intent);			
			}
		});
		builder.setNegativeButton(R.string.dialog_openvpn_install_no, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// User cancelled the dialog
				// TODO: remember in preferences in order to not to annoy ?
			}
		});
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	/* Request upload from the service. */
	private void doUpload() {
		// ask the service to do an upload
		Intent intent = new Intent(getApplicationContext(), CollectorService.class);
		intent.setAction(Constants.ACTION_UPLOAD);
		
		// service will broadcast the new upload time value to the UI - refresh the ui then
		startService(intent);
	}

	/* Request a measurement round from the service. */
	private void doSample() {
		// ask the service to do an upload
		Intent intent = new Intent(getApplicationContext(), CollectorService.class);
		intent.setAction(Constants.ACTION_COLLECT);
		
		// service will broadcast the new upload time value to the UI - refresh the ui then
		startService(intent);
	}

	
	/* Broadcast receiver for receiving status updates from the IntentService. */
	private class ResponseReceiver extends BroadcastReceiver {
	    /*
	     * (non-Javadoc)
	     * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
	     */
	    public void onReceive(Context context, Intent intent) {
			String key = intent.getStringExtra(Constants.INTENT_EXTRA_STATUS_KEY);
			String value = intent.getStringExtra(Constants.INTENT_EXTRA_STATUS_VALUE);
			Log.d(Constants.LOGTAG, "server bcast " + key);
			
			if (key!=null && key.equals(Constants.STATUS_LAST_UPLOAD)) {
				// service has uploaded data
				uploadTv.setText(DateUtils.getRelativeTimeSpanString(Long.parseLong(value)));
				Toast.makeText(getApplicationContext(), "Data upload ready!", Toast.LENGTH_LONG).show();
				
			} else if (key!=null && key.equals(Constants.STATUS_LAST_UPLOAD_FAILED)) {
				// upload failure
				Toast.makeText(getApplicationContext(), "Data upload failed! Try again later.", Toast.LENGTH_LONG).show();
				
			} else if (key!=null && key.equals(Constants.STATUS_RUNNING_SINCE)) {
				// collector state change
				if (Scheduler.isCollectorScheduled(getApplicationContext())) {
					uptimeTv.setText(getResources().getText(R.string.stopped));
				} else {
					long elapsed = (System.currentTimeMillis() - Long.parseLong(value)) / 1000;
					Log.d(Constants.LOGTAG, "uptime " + elapsed + "s " + DateUtils.formatElapsedTime(elapsed));
					uptimeTv.setText(DateUtils.formatElapsedTime(elapsed));
				}
			} else {
				Log.w(Constants.LOGTAG, "invalid or missing status data key: " + key);
			}
	    }
	}

	/*
	 * (non-Javadoc)
	 * @see android.os.Handler.Callback#handleMessage(android.os.Message)
	 */
	@Override
	public boolean handleMessage(Message msg) {
		String s = (String) msg.obj;
		Log.d(Constants.LOGTAG, "message from openvpn: " + s);
		vpnToggle.setChecked(s.contains("CONNECTED"));
		return true;
	};
}
