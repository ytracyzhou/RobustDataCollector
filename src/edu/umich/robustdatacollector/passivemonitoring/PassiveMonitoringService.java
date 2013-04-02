/**
 * @author Yuanyuan Zhou
 * @date Nov 27, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector.passivemonitoring;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

public class PassiveMonitoringService extends Service {

	private static String TAG = "tracyzhou";
	
	private OutputStream mAudioOutputFile; 
	private BufferedWriter mAudioTracewriter;
	private OutputStream mWifiTraceOutputFile;
	private BufferedWriter mWifiTracewriter;
	private OutputStream mBatteryTraceOutputFile;
	private BufferedWriter mBatteryTracewriter;
	private OutputStream mBluetoohTraceOutputFile;
	private BufferedWriter mBluetoothTracewriter;
	private OutputStream mScreenOutputFile;
	private BufferedWriter mScreenTracewriter;
	private OutputStream mScreenRotationOutputFile;
	private BufferedWriter mScreenRotationTracewriter;
	private OutputStream mActiveProcessOutputFile;
	private BufferedWriter mActiveProcessTracewriter;
	private OutputStream mDeviceInfoOutputFile;
	private BufferedWriter mDeviceInfoWriter;
	private OutputStream mNetworkDetailsOutputFile;
	private BufferedWriter mNetworkDetailsTracewriter;
	private OutputStream mLocationOutputFile; 
	private BufferedWriter mLocationTracewriter;
	private OutputStream mCpuAndMemOutputFile; 
	private BufferedWriter mCpuAndMemTracewriter;
	private OutputStream mCallStatesOutputFile; 
	private BufferedWriter mCallStatesTracewriter;
	private OutputStream mCellOutputFile;
	private BufferedWriter mCellTracewriter;
	
	private Timer checkActiveProcessesTimer = new Timer();
	private Timer checkScreenStatusTimer = new Timer();
	private Timer checkAudioStatusTimer = new Timer();
	private Timer checkCpuAndMemTimer = new Timer();
	private float mScreencurBrightness = 0;
	private float mPrevScreencurBrightness = 1;
	private int mScreenTimeout = 0;
	private int mPrevScreenTimeout = 0;
	private boolean isFirstBearerChange = true;
	private int mPrevNetworkType;
	private Boolean mMusicActive = false;
	private Boolean mPrevMusicActive = true;
	private WifiManager mWifiManager = null;
	private TelephonyManager mTelephonyManager = null;
	private ConnectivityManager mConnectivityManager = null;
	private LocationManager mLocationManager = null;
	private AudioManager mAudioManager = null;
	private ActivityManager mActivityManager = null;
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public void onCreate() {
		super.onCreate();
		try {
			initTraceFile();
			if (mTelephonyManager == null) {
				mTelephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
			}
			if (mAudioManager == null) {
				mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
			}
			if (mActivityManager == null) {
				mActivityManager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
			}
			if (mLocationManager == null) {
				mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
			}
			if (mConnectivityManager == null) {
				mConnectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
			}
			if (mWifiManager == null) {
				mWifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
			}
			captureDeviceInfo();
			startAudioMonitor();
			startActiveProcessesTrace();
			startBatteryLevelMonitor();
			startBluetoothMonitor();
			startCpuAndMemMonitor();
			startLocationMonitor();
			startScreenStatusMonitor();
			startScreenRotationMonitor();			
			startNetworkDetailsMonitor();
		} catch (FileNotFoundException e) {
			Log.e(TAG, "exception in initTraceFile: Failed to start Data Collector Trace", e);
		}
		
	}
	
	public int onStartCommand(Intent intent, int flags, int startId) {
		int ret = super.onStartCommand(intent, flags, startId);
    	return ret;
    }
	
	public void onDestroy() {
		stopAudioMonitor();
		stopActiveProcessesTrace();
		stopBatteryLevelMonitor();
		stopBluetoothMonitor();
		stopCpuAndMemMonitor();
		stopLocationMonitor();
		stopScreenStatusMonitor();
		stopScreenRotationMonitor();
		stopNetworkDetailsMonitor();
		try {
			closeTraceFile();
		} catch (IOException e) {
			Log.e(TAG, "exception in closeTraceFile", e);
		}
    }
	
	private void initTraceFile() throws FileNotFoundException {

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
		Date now = new Date();
		String foldername = "psmn-" + dateFormat.format(now);
		final String mTraceDatapath = Environment.getExternalStorageDirectory() + "/PassiveMonitoring/" + foldername + "/";
		File folder = new File(mTraceDatapath);
		if (!folder.exists())
			folder.mkdirs();
		mAudioOutputFile = new FileOutputStream(mTraceDatapath + "audio_events");
		mAudioTracewriter = new BufferedWriter(new OutputStreamWriter(mAudioOutputFile));
		mActiveProcessOutputFile = new FileOutputStream(mTraceDatapath + "active_process");
		mActiveProcessTracewriter = new BufferedWriter(new OutputStreamWriter(mActiveProcessOutputFile));
		mBatteryTraceOutputFile = new FileOutputStream(mTraceDatapath + "battery_events");
		mBatteryTracewriter = new BufferedWriter(new OutputStreamWriter(mBatteryTraceOutputFile));
		mBluetoohTraceOutputFile = new FileOutputStream(mTraceDatapath + "bluetooth_events");
		mBluetoothTracewriter = new BufferedWriter(new OutputStreamWriter(mBluetoohTraceOutputFile));
		mDeviceInfoOutputFile = new FileOutputStream(mTraceDatapath + "device_info");
		mDeviceInfoWriter = new BufferedWriter(new OutputStreamWriter(mDeviceInfoOutputFile));
		mScreenOutputFile = new FileOutputStream(mTraceDatapath + "screen_events");
		mScreenTracewriter = new BufferedWriter(new OutputStreamWriter(mScreenOutputFile));
		mScreenRotationOutputFile = new FileOutputStream(mTraceDatapath + "screen_rotations");
		mScreenRotationTracewriter = new BufferedWriter(new OutputStreamWriter(mScreenRotationOutputFile));
		mLocationOutputFile = new FileOutputStream(mTraceDatapath + "location_events");
		mLocationTracewriter = new BufferedWriter(new OutputStreamWriter(mLocationOutputFile));
		mCpuAndMemOutputFile = new FileOutputStream(mTraceDatapath + "cpu_mem_states");
		mCpuAndMemTracewriter = new BufferedWriter(new OutputStreamWriter(mCpuAndMemOutputFile));
		mWifiTraceOutputFile = new FileOutputStream(mTraceDatapath + "wifi_events");
		mWifiTracewriter = new BufferedWriter(new OutputStreamWriter(mWifiTraceOutputFile));
		mNetworkDetailsOutputFile = new FileOutputStream(mTraceDatapath + "network_details");
		mNetworkDetailsTracewriter = new BufferedWriter(new OutputStreamWriter(mNetworkDetailsOutputFile));
		mCallStatesOutputFile = new FileOutputStream(mTraceDatapath + "call_states");
		mCallStatesTracewriter = new BufferedWriter(new OutputStreamWriter(mCallStatesOutputFile));
		mCellOutputFile = new FileOutputStream(mTraceDatapath + "cell_events");
		mCellTracewriter = new BufferedWriter(new OutputStreamWriter(mCellOutputFile));
	}
	
	private void closeTraceFile() throws IOException {
		if (mAudioTracewriter != null){
			mAudioTracewriter.close();
			mAudioOutputFile.flush();
			mAudioOutputFile.close();
		}
		if (mBluetoothTracewriter != null) {
			mBluetoothTracewriter.close();
			mBluetoohTraceOutputFile.flush();
			mBluetoohTraceOutputFile.close();
		}
		if (mWifiTracewriter != null) {
			mWifiTracewriter.close();
			mWifiTraceOutputFile.flush();
			mWifiTraceOutputFile.close();
		}
		if (mBatteryTracewriter != null) {
			mBatteryTracewriter.close();
			mBatteryTraceOutputFile.flush();
			mBatteryTraceOutputFile.close();
		}
		if (mScreenTracewriter != null) {
			mScreenTracewriter.close();
			mScreenOutputFile.flush();
			mScreenOutputFile.close();
		}
		if (mScreenRotationTracewriter != null) {
			mScreenRotationTracewriter.close();
			mScreenRotationOutputFile.flush();
			mScreenRotationOutputFile.close();
		}
		if (mActiveProcessTracewriter != null) {
			mActiveProcessTracewriter.close();
			mActiveProcessOutputFile.flush();
			mActiveProcessOutputFile.close();
		}
		if (mDeviceInfoWriter != null) {
			mDeviceInfoWriter.close();
			mDeviceInfoOutputFile.flush();
			mDeviceInfoOutputFile.close();
		}
		if (mNetworkDetailsTracewriter != null) {
			mNetworkDetailsTracewriter.close();
			mNetworkDetailsOutputFile.flush();
			mNetworkDetailsOutputFile.close();
		}
		if (mLocationTracewriter != null){
			mLocationTracewriter.close();
			mLocationOutputFile.flush();
			mLocationOutputFile.close();
		}

		if (mCpuAndMemTracewriter != null){
			mCpuAndMemTracewriter.close();
			mCpuAndMemOutputFile.flush();
			mCpuAndMemOutputFile.close();
		}
		if (mCallStatesTracewriter != null){
			mCallStatesTracewriter.close();
			mCallStatesOutputFile.flush();
			mCallStatesOutputFile.close();
		}
		if (mCellTracewriter != null){
			mCellTracewriter.close();
			mCellOutputFile.flush();
			mCellOutputFile.close();
		}
	}
	
	private void writeTraceLineToTraceFile(BufferedWriter outputfilewriter, String content,
			boolean timestamp) {
		try {
			final String eol = System.getProperty("line.separator");
			if (timestamp) {
				outputfilewriter.write("[" + System.currentTimeMillis() + "] " + content + eol);
				outputfilewriter.flush();
			} else {
				outputfilewriter.write(content + eol);
				outputfilewriter.flush();
			}
		} catch (IOException e) {
			
		}
	}
	
	private final String getSpecifiedFieldValues(Class<?> mClass, Object mInstance, String fieldName) {

		String fieldValue = "";

		if (mClass == null || mInstance == null || fieldName == null)
			return fieldValue;

		try {
			final Field field = mClass.getDeclaredField(fieldName);

			if (field != null) {
				field.setAccessible(true);
				fieldValue = field.get(mInstance).toString();
			}

		} catch (NoSuchFieldException exp) {
			fieldValue = "";
			Log.e(TAG, "Exception in getSpecifiedFieldValues NoSuchFieldException" + exp);
		} catch (IllegalAccessException ile) {
			fieldValue = "";
			Log.e(TAG, "Exception in getSpecifiedFieldValues IllegalAccessException" + ile);
		}

		return fieldValue;
	}
	
	private String getLocalIpAddress() throws SocketException {
		for (final Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
				.hasMoreElements();) {
			final NetworkInterface intf = en.nextElement();
			for (final Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
					.hasMoreElements();) {
				final InetAddress inetAddress = enumIpAddr.nextElement();
				if (!inetAddress.isLoopbackAddress()) {
					return inetAddress.getHostAddress();
				}
			}
		}
		return null;
	}
	
	private void captureDeviceInfo() {
		writeTraceLineToTraceFile(mDeviceInfoWriter, getApplicationContext().getPackageName(), false);
		writeTraceLineToTraceFile(mDeviceInfoWriter, Build.MODEL, false);
		writeTraceLineToTraceFile(mDeviceInfoWriter, Build.VERSION.RELEASE, false);
		
		String softwareVersion = "unknown";
		try {
			PackageManager manager = this.getPackageManager();
			PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
			softwareVersion = info.versionName;
		} catch (Exception e) {
			Log.e(TAG, "exception in getSoftwareVersion", e);
		}
		
		writeTraceLineToTraceFile(mDeviceInfoWriter, softwareVersion, false);
		
		String ipAddress;
		try {
			ipAddress = getLocalIpAddress();
			if (ipAddress != null) {
				writeTraceLineToTraceFile(mDeviceInfoWriter, ipAddress, false);
			}
		} catch (SocketException e) {
			Log.e(TAG, "exception in getLocalIpAddress", e);
		}
	}
	
	private LocationListener mLocationListener = new LocationListener() {
		public void onLocationChanged(Location location) {
			if (location.getProvider().equals(LocationManager.GPS_PROVIDER)){
				writeTraceLineToTraceFile(mLocationTracewriter,
						location.getProvider() + " " + location.getAccuracy()
						+ " [" + location.getLatitude() + ", " + location.getLongitude() + "]", true);
			} else if (location.getAccuracy() <= 200){
				writeTraceLineToTraceFile(mLocationTracewriter,
						location.getProvider() + " " + location.getAccuracy()
						+ " [" + location.getLatitude() + ", " + location.getLongitude() + "]", true);
			}
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
			writeTraceLineToTraceFile(mLocationTracewriter, provider + " STATUS CHANGED " + status, true);
		}

		public void onProviderEnabled(String provider) {
			writeTraceLineToTraceFile(mLocationTracewriter, provider + " ENABLED", true);
		}

		public void onProviderDisabled(String provider) {
			writeTraceLineToTraceFile(mLocationTracewriter, provider + " DISABLED", true);
		}
	};
	
	private BroadcastReceiver mScreenTraceReceiver = new BroadcastReceiver() {
		Boolean screenOn = false;
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			if (action.equals(Intent.ACTION_SCREEN_OFF)) {
				screenOn = false;
			} else if (action.equals(Intent.ACTION_SCREEN_ON)) {
				screenOn = true;
			}
			getScreenBrigthnessTimeout();
			if (screenOn) {
				writeTraceLineToTraceFile(mScreenTracewriter, "ON" + " " +
										mScreenTimeout + " " + mScreencurBrightness, true);
				mPrevScreencurBrightness = mScreencurBrightness;
				mPrevScreenTimeout = mScreenTimeout;
			} else {
				writeTraceLineToTraceFile(mScreenTracewriter, "OFF", true);
				mPrevScreencurBrightness = mScreencurBrightness;
				mPrevScreenTimeout = mScreenTimeout;
			}
		}
	};
	
	private BroadcastReceiver mBluetoothTraceReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {

				switch (BluetoothAdapter.getDefaultAdapter().getState()) {
				case BluetoothAdapter.STATE_ON:
					writeTraceLineToTraceFile(mBluetoothTracewriter, "DISCONNECTED", true);
					break;

				case BluetoothAdapter.STATE_OFF:
					writeTraceLineToTraceFile(mBluetoothTracewriter, "OFF", true);
					break;
				}
			}
			if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
					|| BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)
					|| BluetoothDevice.ACTION_FOUND.equals(action)) {

				final BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					writeTraceLineToTraceFile(mBluetoothTracewriter, "DISCONNECTED", true);
				} else if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
					writeTraceLineToTraceFile(mBluetoothTracewriter, "CONNECTED", true);
				}
			}
		}
	};
	
	private BroadcastReceiver mBatteryLevelReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
	    	Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
	    	int rawlevel = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
	    	int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
	    	int batteryTemp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
	    	int batteryHealth = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
	    	int batteryVoltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
	    	String batteryTech = batteryIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
	    	int batteryStatus = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
	    	boolean batteryPresent = batteryIntent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
	    	int batteryLevel = 0;
	    	
	    	if (rawlevel >= 0 && scale > 0) {
	    		batteryLevel = (rawlevel * 100) / scale;
	    	}
	    	int powerSource = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
			writeTraceLineToTraceFile(mBatteryTracewriter, batteryLevel + " " +
									batteryTemp + " " + powerSource + " " + batteryHealth + " " +
									batteryVoltage + " " + batteryTech + " " + batteryStatus + " " +
									batteryPresent, true);
		}
	};
	
	private BroadcastReceiver mScreenRotationReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
				final Configuration newConfig = getResources().getConfiguration();
				if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
					writeTraceLineToTraceFile(mScreenRotationTracewriter, "LANDSCAPE", true);
				} else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
					writeTraceLineToTraceFile(mScreenRotationTracewriter, "PORTRAIT", true);
				}
			}
		}
	};
	
	private void startAudioMonitor() {
		checkAudioStatusTimer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				mMusicActive = mAudioManager.isMusicActive();
				if (mMusicActive != mPrevMusicActive) {
					writeTraceLineToTraceFile(mAudioTracewriter, String.valueOf(mMusicActive), true);
					mPrevMusicActive = mMusicActive;
				}
			}
		}, 0, 1000);
	}
	
	private void stopAudioMonitor() {
		checkAudioStatusTimer.cancel();
	}
	
	private void startActiveProcessesTrace() {
		checkActiveProcessesTimer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				List<RunningAppProcessInfo> activeProcesses = mActivityManager.getRunningAppProcesses();
				for (Iterator<RunningAppProcessInfo> iter = activeProcesses.iterator(); iter.hasNext();) {
					RunningAppProcessInfo runningAppProcessInfo = (RunningAppProcessInfo) iter.next();
					String pProcessName = runningAppProcessInfo.processName;
					int pImportance = runningAppProcessInfo.importance;
					int pImportanceReasonCode = runningAppProcessInfo.importanceReasonCode;
					int importanceReasonPid = runningAppProcessInfo.importanceReasonPid;
					int pid = runningAppProcessInfo.pid;
					int uid = runningAppProcessInfo.uid;
					String utime = "";
					String stime = "";
					try {
						BufferedReader br = new BufferedReader(new FileReader("/proc/" + pid + "/stat"));
						String str = br.readLine();
						br.close();
						String [] result = str.split(" ");
						utime = result[13];
						stime = result[14];
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}	
					writeTraceLineToTraceFile(mActiveProcessTracewriter, pProcessName +
											" " + pImportance +
											" " + pImportanceReasonCode +
											" " + importanceReasonPid +
											" " + pid + " " + uid +
											" " + utime + " " + stime +
											" " + TrafficStats.getUidRxBytes(uid) +
											" " + TrafficStats.getUidRxPackets(uid) + 
											" " + TrafficStats.getUidTxBytes(uid) +
											" " + TrafficStats.getUidTxPackets(uid) +
											" " + TrafficStats.getUidTcpRxBytes(uid) +
											" " + TrafficStats.getUidTcpRxSegments(uid) + 
											" " + TrafficStats.getUidTcpTxBytes(uid) +
											" " + TrafficStats.getUidTcpTxSegments(uid) +
											" " + TrafficStats.getUidUdpRxBytes(uid) +
											" " + TrafficStats.getUidUdpRxPackets(uid) + 
											" " + TrafficStats.getUidUdpTxBytes(uid) +
											" " + TrafficStats.getUidUdpTxPackets(uid), true);
				}
			}
		}, 0, 5000);
	}
	
	private void stopActiveProcessesTrace() {
		checkActiveProcessesTimer.cancel();
	}
	
	private void startBluetoothMonitor() {
		switch (BluetoothAdapter.getDefaultAdapter().getState()) {
		case BluetoothAdapter.STATE_ON:
			if (BluetoothAdapter.getDefaultAdapter().getBondedDevices().isEmpty()) {
				writeTraceLineToTraceFile(mBluetoothTracewriter, "DISCONNECTED", true);
			} else {
				writeTraceLineToTraceFile(mBluetoothTracewriter, "CONNECTED", true);
			}
			break;

		case BluetoothAdapter.STATE_OFF:
			writeTraceLineToTraceFile(mBluetoothTracewriter, "OFF", true);
			break;
		}

		IntentFilter bluetoothIntentFilter = new IntentFilter();
		bluetoothIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		bluetoothIntentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		bluetoothIntentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		bluetoothIntentFilter.addAction(BluetoothDevice.ACTION_FOUND);
		registerReceiver(mBluetoothTraceReceiver, bluetoothIntentFilter);
	}

	private void stopBluetoothMonitor() {
		try {
			if (mBluetoothTraceReceiver != null) {
				unregisterReceiver(mBluetoothTraceReceiver);
			}
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "IllegalArgumentException at unregister mBluetoothTraceReceiver");
		}
	}
	
	private void startScreenStatusMonitor() {
		IntentFilter screenIntentFilter = new IntentFilter();
		screenIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
		screenIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
		checkScreenStatusTimer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				getScreenBrigthnessTimeout();
				if ((mScreencurBrightness != mPrevScreencurBrightness)
						|| (mScreenTimeout != mPrevScreenTimeout)) {
					writeTraceLineToTraceFile(mScreenTracewriter, "ON" + " "
							+ mScreenTimeout + " " + mScreencurBrightness, true);
					mPrevScreencurBrightness = mScreencurBrightness;
					mPrevScreenTimeout = mScreenTimeout;
				}
			}
		}, 0, 1000);
		registerReceiver(mScreenTraceReceiver, screenIntentFilter);
	}

	private void stopScreenStatusMonitor() {
		try {
			if (mScreenTraceReceiver != null) {
				unregisterReceiver(mScreenTraceReceiver);
			}
			checkScreenStatusTimer.cancel();
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "IllegalArgumentException at unregister mScreenTraceReceiver");
		}
	}

	private void getScreenBrigthnessTimeout() {
		try {
			mScreencurBrightness = Settings.System.getInt(getContentResolver(),
					Settings.System.SCREEN_BRIGHTNESS);
			if (mScreencurBrightness >= 255)
				mScreencurBrightness = 240;
			// Brightness Min value 15 and Max 255
			mScreencurBrightness = Math.round((mScreencurBrightness / 240) * 100);
			mScreenTimeout = Settings.System.getInt(getContentResolver(),
					Settings.System.SCREEN_OFF_TIMEOUT);
			mScreenTimeout = mScreenTimeout / 1000; // In Seconds
		} catch (SettingNotFoundException e) {
			Log.e(TAG, "exception in getScreenBrigthnessTimeout", e);
		}
	}
	
	private void startScreenRotationMonitor() {
		registerReceiver(mScreenRotationReceiver, new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));
	}

	private void stopScreenRotationMonitor() {
		try {
			if (mScreenRotationReceiver != null) {
				unregisterReceiver(mScreenRotationReceiver);
			}
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "IllegalArgumentException at unregister mScreenRotationReceiver");
		}
	}
	
	private void startBatteryLevelMonitor() {
		registerReceiver(mBatteryLevelReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
	}
	
	private void stopBatteryLevelMonitor() {
		try {
			if (mBatteryLevelReceiver != null) {
				unregisterReceiver(mBatteryLevelReceiver);
			}
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "IllegalArgumentException at unregister mBatteryLevelReceiver");
		}
	}
	
	private void startLocationMonitor() {
		mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 0, mLocationListener);
		mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 0, mLocationListener);
	}

	private void stopLocationMonitor() {
		mLocationManager.removeUpdates(mLocationListener);
	}
	
	private void startCpuAndMemMonitor() {
		checkCpuAndMemTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				getCPUInfo();
				getMemInfo();
			}
		}, 0, 1000);
	}
	
	private void stopCpuAndMemMonitor() {
		checkCpuAndMemTimer.cancel();
	}
	
	private void getCPUInfo() {
		double cpufreq1 = -1;
		double cpufreq2 = -1;
		long usr = -1;
		long sys = -1;
		long total = -1;
		
		try {
			BufferedReader br1 = new BufferedReader(new FileReader("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"));
			cpufreq1 = Long.parseLong(br1.readLine()) / 1000.0;
			br1.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			BufferedReader br2 = new BufferedReader(new FileReader("/sys/devices/system/cpu/cpu1/cpufreq/scaling_cur_freq"));
			cpufreq2 = Long.parseLong(br2.readLine()) / 1000.0;
			br2.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			BufferedReader br = new BufferedReader(new FileReader("/proc/stat"));
			String cpuinfoResult = br.readLine();
			String [] timeStr = cpuinfoResult.split(" ");
			long [] times = new long[7];
			for (int i = 0; i < 7; i++) {
				times[i] = Long.parseLong(timeStr[i + 2]);
			}
			br.close();
			usr = times[0] + times[1];
			sys = times[2] + times[5] + times[6];
			total = usr + sys + times[3] + times[4];
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		writeTraceLineToTraceFile(mCpuAndMemTracewriter, usr + " " + sys
				+ " " + total + " " + cpufreq1 + " " + cpufreq2, true);
	}
	
	private void getMemInfo() {
		try {
			BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"));
			for (int i = 0; i < 4; i++) {
				String str = br.readLine();
				writeTraceLineToTraceFile(mCpuAndMemTracewriter, str, true);
			}
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
		@Override
		public void onCellLocationChanged(CellLocation location) {
			super.onCellLocationChanged(location);
			GsmCellLocation gsmlocation = (GsmCellLocation)mTelephonyManager.getCellLocation();
			int cellid = gsmlocation.getCid();
			int lacid = gsmlocation.getLac();
			int pscid = gsmlocation.getPsc();
			writeTraceLineToTraceFile(mCellTracewriter, cellid + " " + lacid + " " + pscid, true);
		}
		
		@Override
		public void onSignalStrengthsChanged(SignalStrength signalStrength) {
			super.onSignalStrengthsChanged(signalStrength);
			String radioSignalStrength = String.valueOf(0);
			if (signalStrength.isGsm() || mTelephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_LTE) {
				int lteSignalStrength = 0;
				int lteRsrp = 0;
				int lteRsrq = 0;
				int lteRssnr = 0;
				int lteCqi = 0;
				if (mTelephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_LTE) {
					try {
						lteSignalStrength = Integer.parseInt(getSpecifiedFieldValues(SignalStrength.class, signalStrength, "mLteSignalStrength"));
					} catch (NumberFormatException nmb) {
						Log.e(TAG, "mLteSignalStrength not found in LTE Signal Strength");
					}
					try {
						lteRsrp = Integer.parseInt(getSpecifiedFieldValues(SignalStrength.class, signalStrength, "mLteRsrp"));
					} catch (NumberFormatException nmb) {
						Log.e(TAG, "mLteRsrp not found in LTE Signal Strength");
					}
					try {
						lteRsrq = Integer.parseInt(getSpecifiedFieldValues(SignalStrength.class, signalStrength, "mLteRsrq"));
						} catch (NumberFormatException nmb) {
						Log.e(TAG, "mLteRsrq not found in LTE Signal Strength");
					}
					try {
						lteRssnr = Integer.parseInt(getSpecifiedFieldValues(SignalStrength.class, signalStrength, "mLteRssnr"));
					} catch (NumberFormatException nmb) {
						Log.e(TAG, "mLteRssnr not found in LTE Signal Strength");
					}
					try {
						lteCqi = Integer.parseInt(getSpecifiedFieldValues(SignalStrength.class, signalStrength, "mLteCqi"));
					} catch (NumberFormatException nmb) {
						Log.e(TAG, "mLteCqi not found in LTE Signal Strength");
					}
				}

				if ((lteSignalStrength == 0 && lteRsrp == 0 && lteRsrq == 0 && lteCqi == 0)
						|| (lteSignalStrength == -1 && lteRsrp == -1 && lteRsrq == -1 && lteCqi == -1)) {
					final int gsmSignalStrength = signalStrength.getGsmSignalStrength();
					if (signalStrength.isGsm() && gsmSignalStrength != 99) {
						radioSignalStrength = String.valueOf(-113 + (gsmSignalStrength * 2));
					}
				} else {
					radioSignalStrength = lteSignalStrength + " " + lteRsrp + " " + lteRsrq	+ " " + lteRssnr + " " + lteCqi;
				}
			}
			writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "[SignalStrengthChanged]" + " " + mTelephonyManager.getNetworkType() + " " + radioSignalStrength, true);
		}
		
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			super.onCallStateChanged(state, incomingNumber);
			if (state == TelephonyManager.CALL_STATE_RINGING) {
				writeTraceLineToTraceFile(mCallStatesTracewriter, state + " " + incomingNumber, true);
			} else {
				writeTraceLineToTraceFile(mCallStatesTracewriter, String.valueOf(state), true);
			}
		}
		
		@Override
		public void onDataConnectionStateChanged(int state, int networkType){
			if (state == TelephonyManager.DATA_CONNECTED)
				writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "[DataConnectionStateChanged] DATA_CONNECTED " + networkType, true);
			else if (state == TelephonyManager.DATA_DISCONNECTED)
				writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "[DataConnectionStateChanged] DATA_DISCONNECTED " + networkType, true);
			else if (state == TelephonyManager.DATA_SUSPENDED)
				writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "[DataConnectionStateChanged] DATA_SUSPENDED" + networkType, true);
			NetworkInfo activeNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
			boolean isNetworkConnected = (state == TelephonyManager.DATA_CONNECTED);
			if (activeNetworkInfo != null && isNetworkConnected 
					&& getDeviceNetworkType(activeNetworkInfo) != TelephonyManager.NETWORK_TYPE_UNKNOWN){
				int currentNetworkType = getDeviceNetworkType(activeNetworkInfo);
				if (mPrevNetworkType != currentNetworkType){
					writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "[DataConnectionStateChanged] " + currentNetworkType, true);
					mPrevNetworkType = currentNetworkType;
				}
			}

		}
		
	};
	
	private int getDeviceNetworkType(NetworkInfo currentNetworkType) {
		// Check if the current network is WiFi *//
		if (currentNetworkType.getType() == 1) {
			return -1;
		}
		return mTelephonyManager.getNetworkType();
	}
	
	private Boolean getifCurrentBearerWifi() {
		int type = 0;
		if (mConnectivityManager == null)
			return false;
		if (mConnectivityManager.getActiveNetworkInfo() != null) {
			type = mConnectivityManager.getActiveNetworkInfo().getType();
		}
		if (type == ConnectivityManager.TYPE_MOBILE) {
			return false;
		} else {
			return true;
		}
	}
	
	private BroadcastReceiver mWifiTraceReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
				if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
					writeTraceLineToTraceFile(mWifiTracewriter,	"DISCONNECTED", true);

				} else if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED) {
					writeTraceLineToTraceFile(mWifiTracewriter, "OFF", true);
				}
			}
			if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {

				final NetworkInfo info = (NetworkInfo) intent
						.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
				final NetworkInfo.State state = info.getState();

				switch (state) {

				case CONNECTING:
					writeTraceLineToTraceFile(mWifiTracewriter, "CONNECTING", true);
					break;
				case CONNECTED:
					recordConnectedWifiDetails();					
					break;
				case DISCONNECTING:
					writeTraceLineToTraceFile(mWifiTracewriter,	"DISCONNECTING", true);				
					break;
				case DISCONNECTED:
					writeTraceLineToTraceFile(mWifiTracewriter,	"DISCONNECTED", true);
					break;
				case SUSPENDED:
					writeTraceLineToTraceFile(mWifiTracewriter, "SUSPENDED", true);
					break;
				case UNKNOWN:
					writeTraceLineToTraceFile(mWifiTracewriter, "UNKNOWN", true);
					break;
				}
			}
		}
	};
	
	private void recordConnectedWifiDetails() {
		int wifiNetworkId = mWifiManager.getConnectionInfo().getNetworkId();
		int wifiRssi = mWifiManager.getConnectionInfo().getRssi();
		String wifiNetworkBSSID = mWifiManager.getConnectionInfo().getBSSID();
		String wifiNetworkSSID = mWifiManager.getConnectionInfo().getSSID();
		int wifiLinkSpeed = mWifiManager.getConnectionInfo().getLinkSpeed();
		int wifiIpAddress = mWifiManager.getConnectionInfo().getIpAddress();
		String wifiMacAddress = mWifiManager.getConnectionInfo().getMacAddress();
		writeTraceLineToTraceFile(mWifiTracewriter,
						"CONNECTED" + " " + wifiNetworkId + " " + wifiRssi + " "
						+ wifiNetworkBSSID + " " + wifiNetworkSSID + " "
						+ wifiLinkSpeed + " " + wifiIpAddress + " " + wifiMacAddress, true);
	}
	
	private void startNetworkDetailsMonitor() {
		IntentFilter connectivityIntentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(mBearerChangeReceiver, connectivityIntentFilter);
		NetworkInfo activeNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
		boolean isNetworkConnected = false;
		if (activeNetworkInfo != null)
			isNetworkConnected = activeNetworkInfo.isConnected();
		if (activeNetworkInfo != null && isNetworkConnected 
				&& getDeviceNetworkType(activeNetworkInfo) != TelephonyManager.NETWORK_TYPE_UNKNOWN){
			int currentNetworkType = getDeviceNetworkType(activeNetworkInfo);
			if (mPrevNetworkType != currentNetworkType){
				writeTraceLineToTraceFile(mNetworkDetailsTracewriter, Integer.toString(currentNetworkType), true);
				mPrevNetworkType = currentNetworkType;
			}
			if (isFirstBearerChange) {
				writeTraceLineToTraceFile(mNetworkDetailsTracewriter, Integer.toString(currentNetworkType), true);
				isFirstBearerChange = false;
			}
		}
		if (getifCurrentBearerWifi() && isNetworkConnected){
			recordConnectedWifiDetails();
		}
		
		IntentFilter wifiIntentFilter;
		wifiIntentFilter = new IntentFilter();
		wifiIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		wifiIntentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		wifiIntentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		wifiIntentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
		wifiIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		registerReceiver(mWifiTraceReceiver, wifiIntentFilter);
		
		mTelephonyManager.listen(mPhoneStateListener,
				PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
				PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
	}

	private void stopNetworkDetailsMonitor() {
		try {
			if (mBearerChangeReceiver != null) {
				unregisterReceiver(mBearerChangeReceiver);
			}
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "IllegalArgumentException at unregister mBearerChangeReceiver");
		}
		
		try {
			if (mWifiTraceReceiver != null) {
				unregisterReceiver(mWifiTraceReceiver);
			}
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "IllegalArgumentException at unregister mAROWifiTraceReceiver");
		}
		
		if (mPhoneStateListener != null) {
			mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
		}
	}
	
	private BroadcastReceiver mBearerChangeReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			String action = intent.getAction();
			if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {

				boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY , false);
				boolean isNetworkConnected = !noConnectivity;
				NetworkInfo activeNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
				if (!isFirstBearerChange) {
					if (activeNetworkInfo != null && isNetworkConnected 
							&& getDeviceNetworkType(activeNetworkInfo) != TelephonyManager.NETWORK_TYPE_UNKNOWN){

						int currentNetworkType = getDeviceNetworkType(activeNetworkInfo);

						if (mPrevNetworkType != currentNetworkType){
							writeTraceLineToTraceFile(mNetworkDetailsTracewriter, Integer.toString(currentNetworkType), true);
							mPrevNetworkType = currentNetworkType;
						}

						if (isFirstBearerChange) {
							writeTraceLineToTraceFile(mNetworkDetailsTracewriter, Integer.toString(currentNetworkType), true);
							isFirstBearerChange = false;
						}
					}
				}
			}
		}
	};
}