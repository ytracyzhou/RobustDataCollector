package edu.umich.robustdatacollector.scheduler;

/*
 * Author: Yihua Guo
 * Date: 2013.03.29
 */

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import android.util.Log;

public class TCPSettings {
	static final String TAG = "TCP Settings";
	
	static final int TCP_SETTINGS_CONG_CTRL = 0;
	static final int TCP_SETTINGS_ICW = 1;
	static final int TCP_SETTINGS_TEST = 4;
	
	static final int TCP_CONG_CTRL_RENO 		= 0;
	static final int TCP_CONG_CTRL_CUBIC 		= 1;
	static final int TCP_CONG_CTRL_BIC 			= 2;
	static final int TCP_CONG_CTRL_WESTWOOD 	= 3;
	static final int TCP_CONG_CTRL_HIGHSPEED 	= 4;
	static final int TCP_CONG_CTRL_HYBLA 		= 5;
	static final int TCP_CONG_CTRL_HTCP 		= 6;
	static final int TCP_CONG_CTRL_VEGAS 		= 7;
	static final int TCP_CONG_CTRL_VENO 		= 8;
	static final int TCP_CONG_CTRL_SCALABLE 	= 9;
	static final int TCP_CONG_CTRL_LP 			= 10;
	static final int TCP_CONG_CTRL_YEAH 		= 11;
	static final int TCP_CONG_CTRL_ILLINOIS 	= 12;
	
	static final String[] TCP_CONG_CTRL_NAME = 
			{	"reno", "cubic", "bic",  "westwood", "highspeed", "hybla",
				"htcp", "vegas", "veno", "scalable", "lp", 	      "yeah", "illinois"};
	
	public static boolean changeTCPSettings(int settings, String value) {
		switch (settings) {
		case TCP_SETTINGS_CONG_CTRL:
			return changeTCPCongCtrl(Integer.parseInt(value));
		default:
			return true;
		}
	}
	
	public static String currentTCPSettings(int settings) {
		switch (settings) {
		case TCP_SETTINGS_CONG_CTRL:
			return currentTCPCongCtrl();
		case TCP_SETTINGS_ICW:
			return currentIPRoute();
		case TCP_SETTINGS_TEST:
			return currentTCPTest();
		default:
			return null;
		}
	}
	
	private static boolean changeTCPCongCtrl(int value) {
		String variant = TCP_CONG_CTRL_NAME[value];
		String[] commands = {"echo \"" + variant + "\" > /proc/sys/net/ipv4/tcp_congestion_control"};
		runSuCommand(commands);	
		String currentVariant = currentTCPCongCtrl().trim();
		if (currentVariant.equalsIgnoreCase(variant))
			return true;
		else
			return false;
	}
	
	private static String currentTCPCongCtrl() {
		String[] commands = {"cat /proc/sys/net/ipv4/tcp_congestion_control"};
		return runCommand(commands);
	}
	
	private static String currentIPRoute() {
		String[] commands = {"ip route show"};
		return runCommand(commands);
	}
	
	private static String currentTCPTest() {
		String[] commands = {"echo $PATH", "ls"};
		return runSuCommand(commands);
	}
	
	public static String runCommand(String[] commands) {
		try {
			Process rootProc = null;
			String line, string = "";
			for (int i = 0; i < commands.length; i++) {
				rootProc = Runtime.getRuntime().exec(commands[i]);
				BufferedReader br = new BufferedReader(new InputStreamReader(rootProc.getInputStream()));
				Log.v(TAG, commands[i]);
				while ((line = br.readLine()) != null) {
					Log.v(TAG, line);
					string += (line + '\n');
				}
				br.close();
			}
			return string;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static String runSuCommand(String[] commands) {
		try {
			Process rootProc = Runtime.getRuntime().exec("su");
			DataOutputStream os = new DataOutputStream(rootProc.getOutputStream());
			for (int i = 0; i < commands.length; i++)
				os.writeBytes(commands[i] + "\n");
            os.writeBytes("exit\n");
			os.flush();
            BufferedReader br = new BufferedReader(new InputStreamReader(rootProc.getInputStream()));
            String line, string = "";
            while ((line = br.readLine()) != null) {
				Log.v(TAG, line);
				string += (line + '\n');
			}
            Log.v(TAG, "Su Commands End.");
            return string;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
