/**
 * @author Yuanyuan Zhou
 * @date Nov 18, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector.scheduler;

import java.util.Date;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import edu.umich.robustdatacollector.Utilities;
import edu.umich.robustdatacollector.imapcollector.IMAPCollector;
import edu.umich.robustdatacollector.passivemonitoring.NoInterfaceNameException;

public class InterfaceDetector {
	
	private static String TAG = "tracyzhou";
	
	private static int lastInterfaceType = -1; // 0 for nothing, 1 for wifi, 2 for 3G, 3 for 4g, 4 for LTE
	private IMAPCollector imapCollector = null;
	private TelephonyManager telephonyManager = null;
	private ConnectivityManager connectivityManager = null;
	private WifiManager wifiManager = null;
	
	public InterfaceDetector(IMAPCollector imapCollector, Context context)
	{
		this.imapCollector = imapCollector;
		if (telephonyManager == null)
			telephonyManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
		if (connectivityManager == null)
			connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (wifiManager == null)
			wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
	}

	public void detect() {
		int currentInterfaceType = getCurrentInterfaceType();
		Log.v(TAG, "current interface type: " + currentInterfaceType);
		Log.v(TAG, "last interface type: " + String.valueOf(lastInterfaceType));
		if (lastInterfaceType == -1)
		{
			// initialize the lastInterfaceType, used for interface switching (stopping and starting data collector)
			lastInterfaceType = currentInterfaceType;
		}
		
		if (currentInterfaceType == lastInterfaceType) 
		{
			boolean isIMAPRunning = imapCollector.isIMAPCollectorRunning();
			boolean isUploading = Utilities.readUploadingFlag();
			Log.v(TAG, "now: " + new Date().toString() + " isUploading: " + isUploading + ", currentInterfaceType: " + currentInterfaceType + ", imap running: " + isIMAPRunning);
			if(isUploading == false && currentInterfaceType != 0 && isIMAPRunning == false)
			{
				try {
					imapCollector.startCollectingIMAPData(currentInterfaceType);
					Thread.sleep(2000);
				} catch (NoInterfaceNameException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			else
			{
				// do nothing
			}
		}
		
		else if(currentInterfaceType == 0)	//currentInterfaceType != lastInterfaceType
		{
			// no network interface now, stop collecting data
			imapCollector.stopCollectingIMAPData();
		}
		else
		{
			// an interface switch happended, adjust the imap data collector accordingly
			imapCollector.stopCollectingIMAPData();
			try {
				imapCollector.startCollectingIMAPData(currentInterfaceType);
			} catch (NoInterfaceNameException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		lastInterfaceType = currentInterfaceType;
	}
	
	public int getCurrentInterfaceType()
	{
		int type = 0;
		if (isWifiOn())
		{
			type = 1;
		}
		else if (is2GOn() || is3GOn() || is4GOn())
		{
			type = 2;
		}
		else
		{
			type = 0;
		}
		return type;
	}

	public boolean isWifiOn() {

		NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (wifiInfo.isConnected())
			return true;
		return false;
	}
	
	public boolean is4GOn() {
		NetworkInfo mobileInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		if (mobileInfo.isConnected()) {
			return telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_LTE || 
				telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_HSPA ||
				telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_HSPAP ||
				telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_HSUPA ||
				telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_HSDPA;
		}
		return false;
	}
	
	public boolean is3GOn() {
		NetworkInfo mobileInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		if (mobileInfo.isConnected()) {
			return telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_UMTS;
		}
		return false;
	}
	
	public boolean is2GOn() {
		NetworkInfo mobileInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		if (mobileInfo.isConnected()) {
			return telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_EDGE ||
					telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_GPRS;
		}
		return false;
	}
}
