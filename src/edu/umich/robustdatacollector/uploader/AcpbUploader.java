package edu.umich.robustdatacollector.uploader;

import java.io.File;
import java.io.IOException;

import android.os.Environment;
import android.util.Log;

import com.enterprisedt.net.ftp.FTPException;
import com.enterprisedt.net.ftp.FileTransferClient;
import com.enterprisedt.net.ftp.WriteMode;

import edu.umich.robustdatacollector.Utilities;

public class AcpbUploader {

	private static String uploadRoot = Environment.getExternalStorageDirectory() + "/ActiveProbing";
	private static String TAG = "tracyzhou";
	
	public static boolean hasData() {
		File uploadRootFolder = new File(uploadRoot);
		if (uploadRootFolder.isDirectory()) {
			String[] list = uploadRootFolder.list();
			if(list == null || list.length == 0)
				return false;
			else return true;
		}
		return false;
	}
	
	public static int uploadData(String deviceId) {
		int ret = 0;
		File uploadRootFolder = new File(uploadRoot);
		Log.v(TAG, "uploading active probing files");
		if (uploadRootFolder.isDirectory())
		{
			File [] files = uploadRootFolder.listFiles();
			FileTransferClient client = new FileTransferClient();
			try {
				client.setRemoteHost(Utilities.FTPServerName);
				client.setUserName(Utilities.FTPUsername);
				client.setPassword(Utilities.FTPPassword);
				client.connect();
				for (File file: files) {
					Log.v(TAG, "uploading " + file.getPath());
					String remotePath = file.getName() + "-" + deviceId;
					client.uploadFile(file.getPath(), remotePath, WriteMode.OVERWRITE);
					Log.v(TAG, "finished uploading " + file.getPath());
					file.delete();
				}
			} catch (FTPException e) {
				e.printStackTrace();
				if(e.getMessage().indexOf("exist") != 0)
	    		{
	    			Log.v(TAG, "file exists on FTP");
	    		} else {
					Log.v(TAG, e.getMessage());
					ret = -1;
				}
			} catch (IOException e) {
				e.printStackTrace();
				Log.v(TAG, e.getMessage());
				ret = -1;
			}
			finally
			{
				try {
					client.disconnect(true);
				} catch (IOException e) {
					Log.v(TAG, e.getMessage());				
					e.printStackTrace();
				} catch (FTPException e) {
					Log.v(TAG, e.getMessage());
					e.printStackTrace();
				}
			}
		}
		return ret;
	}
	
}
