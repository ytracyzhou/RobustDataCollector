package edu.umich.robustdatacollector;

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
	static int count = 0;
	
	public static final int TCP_SETTINGS_CONG_CTRL 	= 0;
	public static final int TCP_SETTINGS_ICW 		= 1;
	public static final int TCP_SETTINGS_RMEM 		= 2;
	public static final int TCP_SETTINGS_WMEM 		= 3;
	public static final int TCP_SETTINGS_TEST 		= 4;
	public static final int TCP_SETTINGS_IPROUTE 	= 5;
	public static final int TCP_SETTINGS_TCPPROBE 	= 6;
	
	public static final int TCP_CONG_CTRL_RENO 		= 0;
	public static final int TCP_CONG_CTRL_CUBIC		= 1;
	public static final int TCP_CONG_CTRL_BIC 		= 2;
	public static final int TCP_CONG_CTRL_WESTWOOD 	= 3;
	public static final int TCP_CONG_CTRL_HIGHSPEED	= 4;
	public static final int TCP_CONG_CTRL_HYBLA 	= 5;
	public static final int TCP_CONG_CTRL_HTCP 		= 6;
	public static final int TCP_CONG_CTRL_VEGAS 	= 7;
	public static final int TCP_CONG_CTRL_VENO 		= 8;
	public static final int TCP_CONG_CTRL_SCALABLE 	= 9;
	public static final int TCP_CONG_CTRL_LP 		= 10;
	public static final int TCP_CONG_CTRL_YEAH 		= 11;
	public static final int TCP_CONG_CTRL_ILLINOIS 	= 12;
	
	static final String[] TCP_CONG_CTRL_NAME = 
			{	"reno", "cubic", "bic",  "westwood", "highspeed", "hybla",
				"htcp", "vegas", "veno", "scalable", "lp", 	      "yeah", "illinois"};
	
	public static boolean changeTCPSettings(int settings, String value) {
		switch (settings) { 
		case TCP_SETTINGS_CONG_CTRL:
			return changeTCPCongCtrl(Integer.parseInt(value));
		case TCP_SETTINGS_ICW:
			return changeTCPICW(value);
		case TCP_SETTINGS_TCPPROBE:
			stopTCPProbe();
			return true;
		default:
			return true;
		}
	}
	
	public static String currentTCPSettings(int settings) {
		switch (settings) {
		case TCP_SETTINGS_CONG_CTRL:
			return currentTCPCongCtrl();
		case TCP_SETTINGS_ICW:
			return currentICW();
		case TCP_SETTINGS_RMEM:
			return currentTCPinProc("rmem");
		case TCP_SETTINGS_WMEM:
			return currentTCPinProc("wmem");
		case TCP_SETTINGS_TEST:
			return currentTCPTest();
		case TCP_SETTINGS_IPROUTE:
			return currentIPRoute();
		case TCP_SETTINGS_TCPPROBE:
			count++;
			return startTCPProbe("/sdcard/data"+count);
		default:
			return null;
		}
	}
	
	public static String startTCPProbe(String filename) {
		stopTCPProbe();
		String[] commands = {"insmod /data/local/tcp_probe.ko port=0 bufsize=10240 full=0", "cat /proc/net/tcpprobe >" + filename + " &",
				"pid=$!", "echo $pid"};
		long currTime = System.currentTimeMillis();
		String result = runSuCommand(commands);	
		Log.v(TAG, "tcpprobe: " + currTime);
		return result;
	}
	
	public static void stopTCPProbe() {
		String pid;
		while ((pid = findPID("cat")) != null) {
			String[] commands = {"kill -9 " + pid};
			runSuCommand(commands);
		}
		String[] rmmodCommand = {"rmmod tcp_probe"};
		runSuCommand(rmmodCommand);
	}
	
	private static String findPID(String prefix) {
		try {
			// Run ps to get the process list
			Process proc = Runtime.getRuntime().exec("ps");
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line;
			// Parse process list to find tcpdump entry
			while ((line = br.readLine()) != null) {
				// Split the line by white space
				if (line.contains(prefix)) {
					String pid[] = line.split("\\s+");
					if (pid[8].startsWith(prefix)) {
						// pid should be second string in line
						return pid[1];
					}
				}
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private static boolean changeTCPCongCtrl(int value) {
		String allVariant = currentTCPinProc("available_congestion_control").split("\n")[0];
		String[] command = {"echo \"" + allVariant + "\" > /proc/sys/net/ipv4/tcp_allowed_congestion_control"};
		runSuCommand(command);	
		String variant = TCP_CONG_CTRL_NAME[value];
		String[] commands = {"echo \"" + variant + "\" > /proc/sys/net/ipv4/tcp_congestion_control"};
		runSuCommand(commands);	
		String currentVariant = currentTCPCongCtrl().trim();
		if (currentVariant.equalsIgnoreCase(variant))
			return true;
		else
			return false;
	}
		
	private static boolean changeTCPICW(String value) {
		String[] commands = {"ip route show"};
		String[] result = runCommand(commands).split("\n");
		commands = null;
		
		for (int i = 0; i < result.length; i++) {
			if (result[i].startsWith("default")) {
				Log.v(TAG, result[i]);
				String[] prefix = result[i].split(" initcwnd");
				for (int j = 0; j < prefix.length; j++)
					Log.v(TAG, prefix[j]);
				String[] changeIPRoute = {"ip route change " + prefix[0] + " initcwnd " + value.trim()};
				runSuCommand(changeIPRoute);
				if (!currentICW().equalsIgnoreCase(value.trim()))
					return false;
				String[] wmemValue = currentTCPinProc("wmem").split("\\s+");
				String wmemNewValue = wmemValue[0] + "\t" 
						+ Integer.toString(Integer.parseInt(value.trim()) * 1500)
						+ "\t" + wmemValue[2];
				if (changeTCPinProc("wmem", wmemNewValue))
					return true;
				else
					return false;
			}
		}
		return false;
	}
	
	private static boolean changeTCPinProc(String settings, String value) {
		String[] commands = {"echo \"" + value + "\" > /proc/sys/net/ipv4/tcp_" + settings};
		runSuCommand(commands);	
		String currentValue = currentTCPinProc(settings).trim();
		if (currentValue.equalsIgnoreCase(value))
			return true;
		else
			return false;
	}
	
	private static String currentTCPCongCtrl() {
		String[] commands = {"cat /proc/sys/net/ipv4/tcp_congestion_control"};
		return runCommand(commands);
	}
	
	private static String currentTCPinProc(String settings) {
		String[] commands = {"cat /proc/sys/net/ipv4/tcp_" + settings};
		return runCommand(commands);
	}
	
	private static String currentICW() {
		String[] result = currentIPRoute().split("\n");
		for (int i = 0; i < result.length; i++) {
			if (result[i].startsWith("default")) {
				Log.v(TAG, result[i]);
				String[] prefix = result[i].split(" initcwnd");
				for (int j = 0; j < prefix.length; j++)
					Log.v(TAG, prefix[j]);
				if (prefix.length == 1) {
					return "10";
				} else {
					return prefix[1].trim();
				}
			}
		}
		return null;
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
				Log.v(TAG+" run", commands[i]);
				while ((line = br.readLine()) != null) {
					Log.v(TAG+" run", line);
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
