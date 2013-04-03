package edu.umich.robustdatacollector.uploader;

import java.io.File;
import java.io.IOException;

import com.enterprisedt.net.ftp.FTPException;
import com.enterprisedt.net.ftp.FileTransferClient;
import com.enterprisedt.net.ftp.WriteMode;

import edu.umich.robustdatacollector.Utilities;

import android.os.Environment;
import android.util.Log;

public class IMAPUploader {
	
	private static String TAG = "tracyzhou";
	private static final String uploadRoot = Environment.getExternalStorageDirectory() + "/IMAP";
	
	public static boolean hasData() {
		File uploadRootFolder = new File(uploadRoot);
		if (uploadRootFolder.isDirectory()) {
			String [] list = uploadRootFolder.list();
			if(list == null || list.length == 0)
				return false;
			else return true;
		}
		return false;
	}
	
	public static File [] getDataToUpload() {
		File uploadRootFolder = new File(uploadRoot);
		if (uploadRootFolder.isDirectory()) {
			File [] list = uploadRootFolder.listFiles();
			return list;
		}
		return null;
	}
	
	public static int uploadData(String deviceId) {
		int ret = 0;
		File uploadRootFolder = new File(uploadRoot);
		if (uploadRootFolder.isDirectory()) {
			File [] folders = uploadRootFolder.listFiles();
			for (File folder: folders)
			{
				if(folder.isDirectory()) {
				    File [] list = folder.listFiles();
				    if(list == null || list.length == 0) {
				    	folder.delete();
				    	continue;
				    }
				    String remoteFolderName = folder.getName() + "-" + deviceId;
				    FileTransferClient client = new FileTransferClient();
				    
					try {
						client.setRemoteHost(Utilities.FTPServerName);
						client.setUserName(Utilities.FTPUsername);
						client.setPassword(Utilities.FTPPassword);
						client.connect();
						try {
							Log.v(TAG, "creating directory: " + remoteFolderName);
							client.createDirectory(remoteFolderName);
						} catch (FTPException e) {
							if (e.getMessage().indexOf("exist") != 0) {
								Log.v(TAG, "directory " + remoteFolderName + " exists on FTP");
				    		} else {
								Log.v(TAG, e.getMessage());
								ret = -1;
								break;
							}
						}
						for (File file: list) {
							String filename = file.getName();
							String localCompletePath = file.getAbsolutePath();
							Log.v(TAG, "uploading file: " + localCompletePath);
							client.changeDirectory(remoteFolderName);
							long startTime = System.currentTimeMillis();
							client.uploadFile(localCompletePath, filename, WriteMode.OVERWRITE);
							long endTime = System.currentTimeMillis();
							long time = endTime - startTime;
							Log.v(TAG, "fininshed uploading in " + time + " millis");
							client.changeDirectory("/");

							// record throughput
							long size = getFileSize(localCompletePath);
							if (size > 0) {
								Log.v(TAG, "uploaded file size: " + size);
							}

				    		file.delete();
						}
						if (ret == -1)
							break;
						list = folder.listFiles();
					    if (list == null || list.length == 0) {
					    	folder.delete();
					    }
					} catch (FTPException e) {
						// TODO Auto-generated catch block
						if(e.getMessage().indexOf("exist") != 0)
			    		{
			    			Log.v(TAG, "file exists on FTP");
			    			Log.v(TAG, e.getMessage());
			    		} else {
							Log.v(TAG, e.getMessage());
							ret = -1;
							break;
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						Log.v(TAG, "IO exception when uploading");
						ret = -1;
						break;
					}

				}
			}
		}
		return ret;
	}
	
	public static int uploadData(String deviceId, File [] folders) {
		int ret = 0;
		for (File folder: folders)
		{
			if(folder.isDirectory()) {
			    File [] list = folder.listFiles();
			    if(list == null || list.length == 0) {
			    	folder.delete();
			    	continue;
			    }
			    String remoteFolderName = folder.getName() + "-" + deviceId;
			    FileTransferClient client = new FileTransferClient();
				    
				try {
					client.setRemoteHost(Utilities.FTPServerName);
					client.setUserName(Utilities.FTPUsername);
					client.setPassword(Utilities.FTPPassword);
					client.connect();
					try {
						Log.v(TAG, "creating directory: " + remoteFolderName);
						client.createDirectory(remoteFolderName);
					} catch (FTPException e) {
						if (e.getMessage().indexOf("exist") != 0) {
							Log.v(TAG, "directory " + remoteFolderName + " exists on FTP");
			    		} else {
							Log.v(TAG, e.getMessage());
							ret = -1;
							break;
						}
					}
					for (File file: list) {
						String filename = file.getName();
						String localCompletePath = file.getAbsolutePath();
						Log.v(TAG, "uploading file: " + localCompletePath);
						client.changeDirectory(remoteFolderName);
						long startTime = System.currentTimeMillis();
						client.uploadFile(localCompletePath, filename, WriteMode.OVERWRITE);
						long endTime = System.currentTimeMillis();
						long time = endTime - startTime;
						Log.v(TAG, "fininshed uploading in " + time + " millis");
						client.changeDirectory("/");

						long size = getFileSize(localCompletePath);
						if (size > 0) {
							Log.v(TAG, "uploaded file size: " + size);
						}
			    		file.delete();
					}
					if (ret == -1)
						break;
					list = folder.listFiles();
				    if (list == null || list.length == 0) {
				    	folder.delete();
				    }
				} catch (FTPException e) {
					// TODO Auto-generated catch block
					if(e.getMessage().indexOf("exist") != 0)
		    		{
		    			Log.v(TAG, "file exists on FTP");
		    			Log.v(TAG, e.getMessage());
		    		} else {
						Log.v(TAG, e.getMessage());
						ret = -1;
						break;
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					Log.v(TAG, "IO exception when uploading");
					ret = -1;
					break;
				}
			}
		}
		return ret;
	}
	
	private static long getFileSize(String filepath) {
		File file = new File(filepath);
		long bytes = file.length();
		return bytes;
	}
	
	/*private static double lastThroughput = 0;
	private static double lastUploadBytes = 0;
	
	public static boolean hasData() {
		File directorypath = Environment.getExternalStorageDirectory();
		String[] list = directorypath.list();
	    for (int i = 0; i < list.length; i++)
	    {
	    	String filename = list[i];
	    	if(filename.startsWith("imap"))
	    	{
	    		String imapDIRStr = Environment.getExternalStorageDirectory() + "/" + filename;
	    		File imapDIR = new File(imapDIRStr);
	    		if(imapDIR.isDirectory())
	    		{
	    			return true;
	    		}
	    	}
	    }
		
		return false;
	}
	
	public static int uploadData(String deviceId) {
		int result = 0;
		File directoryPath = Environment.getExternalStorageDirectory();
		File [] folders = directoryPath.listFiles();
		for (File folder: folders)
		{
			String foldername = folder.getName();
			if(foldername.startsWith("imap"))
			{
				if(folder.isDirectory())
				{
	    			int ret = uploadDIRAndFiles(folder, deviceId);
	    			if(ret == 0)
	    			{
	    				break;
	    			}
	    			else if(ret == 1)
	    			{
	    				continue;
	    			}
	    			else
	    			{
	    				result = -1;
	    				break;
	    			}
				}
			}
		}
		return result;
	}

	private static int uploadDIRAndFiles(File imapDIRPath, String deviceId) {
	    File [] list = imapDIRPath.listFiles();
	    if(list == null || list.length == 0)
	    {		    
	    	imapDIRPath.delete();
	    	return 1;
	    }
	    String imapDirNameNewStr = imapDIRPath.getName() + "-" + deviceId;
	    int ret = uploadDIR(imapDirNameNewStr);
	    if (ret == -1)
	    	return -1;
	    
	    for (File file: list)
	    {
	    	String filename = file.getName();
	    	if(filename.equals("appname") || filename.equals("processed_events") || filename.equals("time"))
	    	{
	    		ret = uploadFile(imapDIRPath.toString(), imapDirNameNewStr, filename, false);
	    		if (ret == -1)
	    			return -1;
	    		else
	    			file.delete();
	    	}
	    	else
	    	{
	    		ret = uploadFile(imapDIRPath.toString(), imapDirNameNewStr, filename, true);
	    		if (ret == -1)
	    			return -1;
	    		else
	    			file.delete();
	    	}
	    }
	    
	    list = imapDIRPath.listFiles();
	    if(list == null || list.length == 0)
	    	imapDIRPath.delete();
	    
	    return 0;
	}
	
	private static int uploadDIR(String DIRName) {
		Log.v("tracyzhou", "uploading dir: " + DIRName);
		int ret = 0;
		FileTransferClient client = new FileTransferClient ();
		try {
			client.setRemoteHost(Utilities.FTPServerName);
			client.setUserName(Utilities.FTPUsername);
			client.setPassword(Utilities.FTPPassword);
			client.connect();
			client.createDirectory(DIRName);
			
		} catch (IOException e) {
			e.printStackTrace();
			Log.v("tracyzhou", "an IOException just caught in uploadDIR()");
			ret = -1;
		} catch (FTPException e) {
			e.printStackTrace();
			if(e.getMessage().indexOf("already") != -1)
			{
				Log.v("tracyzhou", "Directory already exists");				
			}
			else
			{
				ret = -1;
			}
		}
		finally
		{
			try {
				client.disconnect(true);
			} catch (IOException e) {
				Log.v("tracyzhou", "an IOException just caught in uploadDIR() because of disconnect()");
				e.printStackTrace();
			}  catch (FTPException e) {
				Log.v("tracyzhou", e.getMessage());
				e.printStackTrace();
			}
		}
		return ret;
	}
	
	private static int uploadFile(String localDIRPath, String remoteDIRName, String filename, boolean recordThroughput) {
		int result = 0;
		FileTransferClient client = new FileTransferClient();
	    
		try
		{
			String localCompletePath = localDIRPath + "/" + filename;
			Log.v("tracyzhou", "uploading file: " + localCompletePath);
			
			// Connect to an FTP server on port 21.
			client.setRemoteHost(Utilities.FTPServerName);
			client.setUserName(Utilities.FTPUsername);
			client.setPassword(Utilities.FTPPassword);
			client.connect();
			
			client.changeDirectory(remoteDIRName.toString());
			long startTime = System.currentTimeMillis();
			client.uploadFile(localCompletePath, filename, WriteMode.OVERWRITE);
			long endTime = System.currentTimeMillis();
			long time = endTime - startTime;
			Log.v("tracyzhou", "fininshed uploading");
			
			if(recordThroughput == true)
			{
				long size = getFileSize(localDIRPath.toString(), filename);	
				double throughput = (double)(size * 8)/ (double)time;	// kbps
				Log.v("tracyzhou", "file size: " + size);
				if(size < 1024*100)	//10KB
				{
					throughput = 100;
				}
	    		lastThroughput = throughput;
	    		System.out.println("thoughput: " + lastThroughput);
	    		lastUploadBytes = (double)size/(double)1048576;	//convert to MB
			}
		}
		catch(FTPException e)
		{
			e.printStackTrace();
			if(e.getMessage().equals("file exists"))
    		{
    			Log.v("tracyzhou", "file exists on FTP");
    		}
			else
			{
				Log.v("tracyzhou", e.getMessage());
				result = -1;
			}
		}
		catch(IOException e)
		{
			Log.v("tracyzhou", "an IOException just caught in uploadFile()");
			e.printStackTrace();
			result = -1;
		}
		finally {
			try {
				client.disconnect(true);
			} catch (IllegalStateException e) {
				e.printStackTrace();
				Log.v("tracyzhou", "disconnect Exception: " + e.getMessage());
			} catch (IOException e) {
				//e.printStackTrace();
				Log.v("tracyzhou", "an IOException just caught in uploadFile() because of disconnect");
			} catch (FTPException e) {
				//e.printStackTrace();
				Log.v("tracyzhou", "disconnect Exception: " + e.getMessage());
			}
		}
		return result;
	}
	
	// return kbps
	public static double getLastThroughput() {
		return lastThroughput ;
	}
	
	// return MB
	public static double getLastUploadBytes() {
		return lastUploadBytes ;
	}
	
	private static long getFileSize(String DIR, String filename) {
		File file = new File(DIR + "/" + filename);
		long bytes = file.length();
		return bytes;
	}*/
}