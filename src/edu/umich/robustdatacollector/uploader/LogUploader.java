package edu.umich.robustdatacollector.uploader;

import java.io.File;
import java.io.IOException;

import android.os.Environment;

import com.enterprisedt.net.ftp.FTPException;
import com.enterprisedt.net.ftp.FileTransferClient;
import com.enterprisedt.net.ftp.WriteMode;

import edu.umich.robustdatacollector.Utilities;

public class LogUploader {
	public static int upload(String deviceId) {
		int ret = 0;
		File file = new File(Environment.getExternalStorageDirectory() + "/uploadlog");
		if (!file.exists())
			return ret;
		FileTransferClient client = new FileTransferClient();
		try {
			client.setRemoteHost(Utilities.FTPServerName);
			client.setUserName(Utilities.FTPUsername);
			client.setPassword(Utilities.FTPPassword);
			client.connect();
			client.uploadFile(file.getAbsolutePath(), file.getName() + "-" +
			System.currentTimeMillis() + "-" + deviceId, WriteMode.OVERWRITE);
			file.delete();
		} catch (FTPException e) {
			e.printStackTrace();
			ret = -1; 
		} catch (IOException e) {
			e.printStackTrace();
			ret = -1;
		}
		finally	{
			try {
				client.disconnect(true);
			} catch (IOException e) {				
				e.printStackTrace();
			} catch (FTPException e) {
				e.printStackTrace();
			}
		}
		return ret;
	}
}
