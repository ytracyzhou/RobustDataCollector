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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

//import edu.umich.robustdatacollector.TCPSettings;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

public class PassiveMonitoringService extends Service {

    private static String TAG = "tracyzhou";
    
    private OutputStream mActiveProcessOutputFile;
    private BufferedWriter mActiveProcessTracewriter;
    private OutputStream mDeviceInfoOutputFile;
    private BufferedWriter mDeviceInfoTracewriter;
    private OutputStream mNetworkDetailsOutputFile;
    private BufferedWriter mNetworkDetailsTracewriter;
    private OutputStream mCpuAndMemOutputFile; 
    private BufferedWriter mCpuAndMemTracewriter;

    private Timer checkActiveProcessesTimer = new Timer();
    private Timer checkCpuAndMemTimer = new Timer();
    //private boolean isFirstBearerChange = true;
    private int mPrevNetworkType;
    private WifiManager mWifiManager = null;
    private TelephonyManager mTelephonyManager = null;
    private ConnectivityManager mConnectivityManager = null;
    private LocationManager mLocationManager = null;
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
            startActiveProcessesTrace();
            startBatteryLevelMonitor();
            startCpuAndMemMonitor();
            startLocationMonitor();
            startScreenStatusMonitor();        
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
        stopActiveProcessesTrace();
        stopBatteryLevelMonitor();
        stopCpuAndMemMonitor();
        stopLocationMonitor();
        stopScreenStatusMonitor();
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
        mActiveProcessOutputFile = new FileOutputStream(mTraceDatapath + "active_process");
        mActiveProcessTracewriter = new BufferedWriter(new OutputStreamWriter(mActiveProcessOutputFile));
        mDeviceInfoOutputFile = new FileOutputStream(mTraceDatapath + "device_info");
        mDeviceInfoTracewriter = new BufferedWriter(new OutputStreamWriter(mDeviceInfoOutputFile));
        mCpuAndMemOutputFile = new FileOutputStream(mTraceDatapath + "cpu_mem_states");
        mCpuAndMemTracewriter = new BufferedWriter(new OutputStreamWriter(mCpuAndMemOutputFile));
        mNetworkDetailsOutputFile = new FileOutputStream(mTraceDatapath + "network_details");
        mNetworkDetailsTracewriter = new BufferedWriter(new OutputStreamWriter(mNetworkDetailsOutputFile));
        //TCPSettings.startTCPProbe(mTraceDatapath + "probedata" + System.currentTimeMillis());
    }
    
    private void closeTraceFile() throws IOException {
        if (mActiveProcessTracewriter != null) {
            mActiveProcessTracewriter.close();
            mActiveProcessOutputFile.flush();
            mActiveProcessOutputFile.close();
        }
        if (mDeviceInfoTracewriter != null) {
            mDeviceInfoTracewriter.close();
            mDeviceInfoOutputFile.flush();
            mDeviceInfoOutputFile.close();
        }
        if (mNetworkDetailsTracewriter != null) {
            mNetworkDetailsTracewriter.close();
            mNetworkDetailsOutputFile.flush();
            mNetworkDetailsOutputFile.close();
        }

        if (mCpuAndMemTracewriter != null){
            mCpuAndMemTracewriter.close();
            mCpuAndMemOutputFile.flush();
            mCpuAndMemOutputFile.close();
        }
        //TCPSettings.stopTCPProbe();
    }
    
    private void writeTraceLineToTraceFile(BufferedWriter outputfilewriter, String content,
            boolean timestamp) {
        try {
            final String eol = System.getProperty("line.separator");
            if (timestamp) {
                outputfilewriter.write("" + System.currentTimeMillis() + "\t" + content + eol);
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
    
    private void captureDeviceInfo() {
        writeTraceLineToTraceFile(mDeviceInfoTracewriter, getApplicationContext().getPackageName(), false);
        writeTraceLineToTraceFile(mDeviceInfoTracewriter, Build.MODEL, false);
        writeTraceLineToTraceFile(mDeviceInfoTracewriter, Build.VERSION.RELEASE, false);
        
        String softwareVersion = "unknown";
        try {
            PackageManager manager = this.getPackageManager();
            PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
            softwareVersion = info.versionName;
        } catch (Exception e) {
            Log.e(TAG, "exception in getSoftwareVersion", e);
        }
        writeTraceLineToTraceFile(mDeviceInfoTracewriter, softwareVersion, false);
        
        String lineNum = mTelephonyManager.getLine1Number();
        if (lineNum == null) {
            lineNum = "unknown";
        }
        writeTraceLineToTraceFile(mDeviceInfoTracewriter, lineNum, false);
    }
    
    private LocationListener mLocationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            if (location.getProvider().equals(LocationManager.GPS_PROVIDER)){
                writeTraceLineToTraceFile(mDeviceInfoTracewriter, "LOCATION CHANGED\t" + location.getProvider() + "\t" + location.getAccuracy()
                        + "\t[" + location.getLatitude() + "," + location.getLongitude() + "]", true);
            } else if (location.getAccuracy() <= 200){
                writeTraceLineToTraceFile(mDeviceInfoTracewriter, "LOCATION CHANGED\t" + location.getProvider() + "\t" + location.getAccuracy()
                        + "\t[" + location.getLatitude() + "," + location.getLongitude() + "]", true);
            }
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            writeTraceLineToTraceFile(mDeviceInfoTracewriter, "STATUS CHANGED\t" + provider + "\t" + status, true);
        }

        public void onProviderEnabled(String provider) {
            writeTraceLineToTraceFile(mDeviceInfoTracewriter, "ENABLED\t" + provider, true);
        }

        public void onProviderDisabled(String provider) {
            writeTraceLineToTraceFile(mDeviceInfoTracewriter, "DISABLED\t" + provider, true);
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
            int batteryStatus = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean batteryPresent = batteryIntent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
            int batteryLevel = 0;
            
            if (rawlevel >= 0 && scale > 0) {
                batteryLevel = (rawlevel * 100) / scale;
            }
            int powerSource = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            writeTraceLineToTraceFile(mDeviceInfoTracewriter, "BATTERY UPDATES\t" + batteryLevel + "\t" + batteryTemp + "\t" + powerSource + "\t" +
                                    batteryHealth + "\t" + batteryVoltage + "\t" + batteryStatus + "\t" + batteryPresent, true);
        }
    };
    
    private void startActiveProcessesTrace() {
        checkActiveProcessesTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                List<RunningAppProcessInfo> activeProcesses = mActivityManager.getRunningAppProcesses();
                String processResult = "";
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
                    processResult += pProcessName + "\t" + pImportance + "\t" + pImportanceReasonCode +
                                "\t" + importanceReasonPid + "\t" + pid + "\t" + uid + "\t" + utime + "\t" + stime +
                                "\t" + TrafficStats.getUidRxBytes(uid) +
                                "\t" + TrafficStats.getUidRxPackets(uid) + 
                                "\t" + TrafficStats.getUidTxBytes(uid) +
                                "\t" + TrafficStats.getUidTxPackets(uid) +
                                "\t" + TrafficStats.getUidTcpRxBytes(uid) +
                                "\t" + TrafficStats.getUidTcpRxSegments(uid) + 
                                "\t" + TrafficStats.getUidTcpTxBytes(uid) +
                                "\t" + TrafficStats.getUidTcpTxSegments(uid) +
                                "\t" + TrafficStats.getUidUdpRxBytes(uid) +
                                "\t" + TrafficStats.getUidUdpRxPackets(uid) + 
                                "\t" + TrafficStats.getUidUdpTxBytes(uid) +
                                "\t" + TrafficStats.getUidUdpTxPackets(uid) + ";";
                }
                if (processResult != "")
                    writeTraceLineToTraceFile(mActiveProcessTracewriter, processResult, true);
            }
        }, 0, 10000);
    }
    
    private void stopActiveProcessesTrace() {
        checkActiveProcessesTimer.cancel();
    }
    
    private BroadcastReceiver mScreenTraceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                writeTraceLineToTraceFile(mDeviceInfoTracewriter, "SCREEN OFF", true);
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                writeTraceLineToTraceFile(mDeviceInfoTracewriter, "SCREEN OFF", true);
            }
        }
    };
    
    private void startScreenStatusMonitor() {
        IntentFilter screenIntentFilter = new IntentFilter();
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mScreenTraceReceiver, screenIntentFilter);
    }

    private void stopScreenStatusMonitor() {
        if (mScreenTraceReceiver != null) {
            unregisterReceiver(mScreenTraceReceiver);
        }
    }
    
    private void startBatteryLevelMonitor() {
        registerReceiver(mBatteryLevelReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }
    
    private void stopBatteryLevelMonitor() {
        if (mBatteryLevelReceiver != null) {
            unregisterReceiver(mBatteryLevelReceiver);
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
                
                String memresult = "";
                
                try {
                    BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"));
                    for (int i = 0; i < 4; i++) {
                        String str = br.readLine();
                        String [] splittedWords = str.split(" ");
                        memresult += splittedWords[splittedWords.length - 2];
                        if (i < 3)
                            memresult += " ";               
                    }
                    br.close();
                } catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                
                writeTraceLineToTraceFile(mCpuAndMemTracewriter, usr + "\t" + sys
                        + "\t" + total + "\t" + cpufreq1 + "\t" + cpufreq2 + "\t" + memresult, true);
            }
        }, 0, 1000);
    }
    
    private void stopCpuAndMemMonitor() {
        checkCpuAndMemTimer.cancel();
    }
    
    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCellLocationChanged(CellLocation location) {
            super.onCellLocationChanged(location);
            GsmCellLocation gsmlocation = (GsmCellLocation)mTelephonyManager.getCellLocation();
            int cellid = gsmlocation.getCid();
            int lacid = gsmlocation.getLac();
            int pscid = gsmlocation.getPsc();
            writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "CELL LOCATION CHANGED\t" + cellid + "\t" + lacid + "\t" + pscid, true);
        }
        
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            if (state == TelephonyManager.CALL_STATE_RINGING) {
                writeTraceLineToTraceFile(mDeviceInfoTracewriter, "CALL STATE CHANGED\t" + state + "\t" + incomingNumber, true);
            } else {
                writeTraceLineToTraceFile(mDeviceInfoTracewriter, "CALL STATE CHANGED\t" + String.valueOf(state), true);
            }
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
                    radioSignalStrength = lteSignalStrength + "\t" + lteRsrp + "\t" + lteRsrq + "\t" + lteRssnr + "\t" + lteCqi;
                }
            }
            writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "Signal Strength Changed\t" + mTelephonyManager.getNetworkType() + "\t" + radioSignalStrength, true);
        }
        
        @Override
        public void onDataConnectionStateChanged(int state, int networkType){
            if (state == TelephonyManager.DATA_CONNECTED)
                writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "DATA CONNECTED\t" + networkType, true);
            else if (state == TelephonyManager.DATA_DISCONNECTED)
                writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "DATA DISCONNECTED\t" + networkType, true);
            else if (state == TelephonyManager.DATA_SUSPENDED)
                writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "DATA SUSPENDED\t" + networkType, true);
            NetworkInfo activeNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo != null && state == TelephonyManager.DATA_CONNECTED 
                    && getDeviceNetworkType(activeNetworkInfo) != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                int currentNetworkType = getDeviceNetworkType(activeNetworkInfo);
                if (mPrevNetworkType != currentNetworkType){
                    writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "NETWORK TYPE CHANGED\t" + currentNetworkType, true);
                    mPrevNetworkType = currentNetworkType;
                }
            }
        }
    };
    
    private BroadcastReceiver mWifiTraceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
                    writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "WIFI DISCONNECTED", true);

                } else if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED) {
                    writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "WIFI OFF", true);
                }
            }
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {

                final NetworkInfo info = (NetworkInfo) intent
                        .getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                final NetworkInfo.State state = info.getState();

                switch (state) {
                case CONNECTING:
                    writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "WIFI CONNECTING", true);
                    break;
                case CONNECTED:
                    int wifiNetworkId = mWifiManager.getConnectionInfo().getNetworkId();
                    int wifiRssi = mWifiManager.getConnectionInfo().getRssi();
                    String wifiNetworkBSSID = mWifiManager.getConnectionInfo().getBSSID();
                    String wifiNetworkSSID = mWifiManager.getConnectionInfo().getSSID();
                    int wifiLinkSpeed = mWifiManager.getConnectionInfo().getLinkSpeed();
                    int wifiIpAddress = mWifiManager.getConnectionInfo().getIpAddress();
                    String wifiMacAddress = mWifiManager.getConnectionInfo().getMacAddress();
                    writeTraceLineToTraceFile(mNetworkDetailsTracewriter,
                                    "WIFI CONNECTED" + "\t" + wifiNetworkId + "\t" + wifiRssi + "\t"
                                    + wifiNetworkBSSID + "\t" + wifiNetworkSSID + "\t"
                                    + wifiLinkSpeed + "\t" + wifiIpAddress + "\t" + wifiMacAddress, true);              
                    break;
                case DISCONNECTING:
                    writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "WIFI DISCONNECTING", true);             
                    break;
                case DISCONNECTED:
                    writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "WIFI DISCONNECTED", true);
                    break;
                case SUSPENDED:
                    writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "WIFI SUSPENDED", true);
                    break;
                case UNKNOWN:
                    writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "WIFI UNKNOWN", true);
                    break;
                }
            }
        }
    };
    
    private void startNetworkDetailsMonitor() {
        registerReceiver(mBearerChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        NetworkInfo activeNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
        if (activeNetworkInfo != null) {
            if (activeNetworkInfo.isConnected()) {
                if (getDeviceNetworkType(activeNetworkInfo) != TelephonyManager.NETWORK_TYPE_UNKNOWN){
                    int currentNetworkType = getDeviceNetworkType(activeNetworkInfo);
                    if (mPrevNetworkType != currentNetworkType){
                        writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "NETWORK TYPE CHANGED\t" + currentNetworkType, true);
                        mPrevNetworkType = currentNetworkType;
                    }
                }
                if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    int wifiNetworkId = mWifiManager.getConnectionInfo().getNetworkId();
                    int wifiRssi = mWifiManager.getConnectionInfo().getRssi();
                    String wifiNetworkBSSID = mWifiManager.getConnectionInfo().getBSSID();
                    String wifiNetworkSSID = mWifiManager.getConnectionInfo().getSSID();
                    int wifiLinkSpeed = mWifiManager.getConnectionInfo().getLinkSpeed();
                    int wifiIpAddress = mWifiManager.getConnectionInfo().getIpAddress();
                    String wifiMacAddress = mWifiManager.getConnectionInfo().getMacAddress();
                    writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "WIFI CONNECTED" + "\t"
                                    + wifiNetworkId + "\t" + wifiRssi + "\t" + wifiNetworkBSSID + "\t" + wifiNetworkSSID + "\t"
                                    + wifiLinkSpeed + "\t" + wifiIpAddress + "\t" + wifiMacAddress, true);   
                }
            }
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
        if (mBearerChangeReceiver != null) {
            unregisterReceiver(mBearerChangeReceiver);
        }
        if (mWifiTraceReceiver != null) {
            unregisterReceiver(mWifiTraceReceiver);
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
                NetworkInfo activeNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
                if (activeNetworkInfo != null && !noConnectivity 
                        && getDeviceNetworkType(activeNetworkInfo) != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                    int currentNetworkType = getDeviceNetworkType(activeNetworkInfo);
                    if (mPrevNetworkType != currentNetworkType){
                        writeTraceLineToTraceFile(mNetworkDetailsTracewriter, "NETWORK TYPE CHANGED\t" + currentNetworkType, true);
                        mPrevNetworkType = currentNetworkType;
                    }
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
}