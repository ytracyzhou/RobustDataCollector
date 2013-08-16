package edu.umich.robustdatacollector.scheduler;

import edu.umich.robustdatacollector.Utilities;
import edu.umich.robustdatacollector.activeprobing.NewTestThread;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

public class ActiveProbingReceiver extends BroadcastReceiver {

    private String ACTIVE_PROBING_SERVER_NAME = "owl.eecs.umich.edu";
    private static int SDCARD_CRITICAL_FOR_ACTIVE_PROBING = 10;
    
    @Override
    public void onReceive(Context context, Intent intent) {
        
        Log.v("tracyzhou", "in ActiveProbingReceiver!");
        if (!Utilities.readUploadingFlag()) {
            boolean storageCritical = Utilities.isStorageCritical(SDCARD_CRITICAL_FOR_ACTIVE_PROBING);
            if (!storageCritical) {
                if ((SchedulerThread.level >= SchedulerThread.ACTIVE_PROBING_LOW_BATTERY_LEVEL || SchedulerThread.plugged > 0) &&
                    Utilities.isUserIdle()) {
                    final ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    final TelephonyManager telephonyManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
                    NetworkInfo mobileInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                    if (mobileInfo.isConnected()) {
                        int networkType = telephonyManager.getNetworkType();
                        if (networkType == TelephonyManager.NETWORK_TYPE_LTE ||
                            networkType == TelephonyManager.NETWORK_TYPE_HSPA ||
                            networkType == TelephonyManager.NETWORK_TYPE_HSPAP ||
                            networkType == TelephonyManager.NETWORK_TYPE_HSUPA ||
                            networkType == TelephonyManager.NETWORK_TYPE_HSDPA ||
                            networkType == TelephonyManager.NETWORK_TYPE_UMTS) {
                            new NewTestThread(ACTIVE_PROBING_SERVER_NAME).start();
                        }
                    }
                }
            }
        }
    }
}


