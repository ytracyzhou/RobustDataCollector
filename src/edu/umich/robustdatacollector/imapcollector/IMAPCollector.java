/**
 * @author Yuanyuan Zhou
 * @date Nov 18, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector.imapcollector;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import edu.umich.robustdatacollector.passivemonitoring.NoInterfaceNameException;
import edu.umich.robustdatacollector.scheduler.SchedulerThread;
import edu.umich.robustdatacollector.TCPSettings;

import android.content.Context;
import android.util.Log;

public class IMAPCollector {
	private final static String STOP_IMAP_FILE = "/data/tmp/dc_stop_flag";
	
	Process rootProcess = null;
	Context context = null;
	
	public IMAPCollector(Context context) {
		
		try {
			rootProcess = Runtime.getRuntime().exec("su");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		this.context = context;
	}
	
	public void startCollectingIMAPData(int currentInterfaceType) throws NoInterfaceNameException {
		File file = new File(STOP_IMAP_FILE);
        file.delete();
        
		String interfaceName = getInterfaceNameByType(currentInterfaceType);
		long id = Thread.currentThread().getId();
		if(interfaceName.equals(""))
		{
			throw new NoInterfaceNameException();
		}
		String cmd = "/data/local/imap-tcpdump -i " + interfaceName + " -C 1000 not src 141.212.110.239 and not dst 141.212.110.239 2>> /sdcard/imap.output";
		try {
			Log.v("tracyzhou", id + " Starting imap with cmd: " + cmd);
			
			DataOutputStream os = new DataOutputStream(rootProcess.getOutputStream());
    		os.writeBytes(cmd + "\n");
    		os.flush();
    		TCPSettings.changeTCPSettings(TCPSettings.TCP_SETTINGS_ICW, String.valueOf(SchedulerThread.TCP_ICW));
    		
		} catch (IOException e) {
			e.printStackTrace();
		}
	}



	public void stopCollectingIMAPData() {
		
		FileWriter fstream;
		try {
			fstream = new FileWriter(STOP_IMAP_FILE);
			BufferedWriter out = new BufferedWriter(fstream);
			out.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
        
        try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
        File file = new File(STOP_IMAP_FILE);
        file.delete();
	}
	
	public boolean isIMAPCollectorRunning() {
		// Executes the command.
		Process process;
		boolean found = false;
		
		try {
			process = Runtime.getRuntime().exec("ps");
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					process.getInputStream()));
			String line;
			
			while ((line = reader.readLine()) != null) {
				//System.out.println(line);
				if(line.indexOf("imap-tcpdump") != -1)
				{
					found = true;
					break;
				}
			}		
			reader.close();
			process.waitFor();
			
		} catch (IOException e1) {		
			e1.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return found;
	}
	
	public static String getInterfaceNameByType(int currentInterfaceType) {
		String interfaceName = "";
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
			    
			    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
			        InetAddress inetAddress = enumIpAddr.nextElement();
			        if(currentInterfaceType == 1)
			        {
				        if (intf.getName().equals("wlan0")) {
				            if(inetAddress.getHostAddress().toString().equals("0.0.0.0") == false)
				            {
				            	interfaceName = intf.getName();
				            	break;
				            }
				        }
			        }
			        
			        if(currentInterfaceType == 2)
			        {
				        if (intf.getName().equals("rmnet0")) {
				            if(inetAddress.getHostAddress().toString().equals("0.0.0.0") == false)
				            {
				            	interfaceName = intf.getName();
				            	break;
				            }
				        }
			        }
			        
			    }
			}
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return interfaceName;
	}
	
}
