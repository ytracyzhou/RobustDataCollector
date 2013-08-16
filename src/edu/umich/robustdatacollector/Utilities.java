/**
 * @author Yuanyuan Zhou
 * @date Nov 18, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.os.Environment;
import android.os.StatFs;

public class Utilities {
	
	private final static int IDLE_THRESHOLD_BY_BYTES = 100000;	//100KB (in recent 180s)
	private static long lastUploadTimestamp = -1;
    final public static String FTPServerName = "141.212.110.143";
    final public static String FTPUsername = "tracyzhou";
    final public static String FTPPassword = "robustnet";
	
	public static boolean lastUploadTimestampExists() {
		File file = new File(Environment.getExternalStorageDirectory().getPath() + "/lastUploadTimestamp");
		return file.exists();
	}
	
	public static void setLastUploadTimestamp(long timestamp) {
		try {
			lastUploadTimestamp = timestamp;
			FileWriter fstream = new FileWriter(Environment.getExternalStorageDirectory().getPath() + "/lastUploadTimestamp");
			BufferedWriter out = new BufferedWriter(fstream);
			out.write(timestamp + "\n");
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static long getLastUploadTimestamp() {
		long ret = 0;
		if(lastUploadTimestamp != -1) {
			return lastUploadTimestamp;
		}
		File file = new File(Environment.getExternalStorageDirectory().getPath() + "/lastUploadTimestamp");
		if (!file.exists()) {
			lastUploadTimestamp = -1;
			return lastUploadTimestamp;
		}
		
		try {
			FileInputStream fstream = new FileInputStream(Environment.getExternalStorageDirectory().getPath() + "/lastUploadTimestamp");
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			while ((strLine = br.readLine()) != null) {
				ret = Long.valueOf(strLine);
				System.out.println(strLine);
				break;
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}
	
	public static boolean readAcpbFlag() {
		boolean isActiveProbing = false;
		File file = new File(Environment.getExternalStorageDirectory().getPath() + "/isActiveProbing");
		if (file.exists())
			isActiveProbing = true;
		return isActiveProbing;
	}
	
	public static void setAcpbFlag() {
		File file = new File(Environment.getExternalStorageDirectory().getPath() + "/isActiveProbing");
		try {
			file.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void clearAcpbFlag() {
		File file = new File(Environment.getExternalStorageDirectory().getPath() + "/isActiveProbing");
		if (file.exists())
			file.delete();
	}
	
	public static boolean readUploadingFlag() {
		boolean isUploading = false;
		File file = new File(Environment.getExternalStorageDirectory().getPath() + "/isUploading");
		if (file.exists())
			isUploading = true;
		return isUploading;
	}
	
	public static void setUploadingFlag() {
		File file = new File(Environment.getExternalStorageDirectory().getPath() + "/isUploading");
		try {
			file.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
	    }
	}
	
	public static void clearUploadingFlag() {
		File file = new File(Environment.getExternalStorageDirectory().getPath() + "/isUploading");
		if (file.exists())
			file.delete();
	}
	
	public static boolean isUserIdle() {
		boolean isIdle = false;
		try {
			FileInputStream fstream = new FileInputStream("/data/tmp/dc_activity");
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			if ((strLine = br.readLine()) != null) {
				int ret = Integer.valueOf(strLine);
				System.out.println(strLine);
				if(ret == -1 || ret < IDLE_THRESHOLD_BY_BYTES)
				{
					isIdle = true;
				}
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return isIdle;
	}
	
	public static double getStorageSpaceLeft() {
		StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
		double freeSpace = (double)stat.getAvailableBlocks() / (double)stat.getBlockCount();
		return freeSpace;
	}
	
	public static boolean isStorageCritical(int limit) {
		StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
		double freeSpace = (double)stat.getAvailableBlocks() / (double)stat.getBlockCount();
		System.out.println(freeSpace);
		if(freeSpace * 100 < limit)
			return true;
		return false;
	}
	
	public static boolean zip(String zipFilePath, String localFilePath)
	{
		byte[] buf = new byte[512];
		File localFile = new File(localFilePath);
		String localFilename = localFile.getName();
		
		try {
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFilePath));
			FileInputStream in = new FileInputStream(localFilePath);
			out.putNextEntry(new ZipEntry(localFilename));
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			out.closeEntry();
			in.close();
			out.close();
			localFile.delete();
			return true;
		} catch (IOException e) {
			File zipFile = new File(zipFilePath);
			if (zipFile.exists())
				zipFile.delete();
			return false;
		}
	}
	
	public static void writeUploadLog(String startOrEnd) {
        File logfile = new File(Environment.getExternalStorageDirectory().getPath() + "/uploadlog");
        if (!logfile.exists()) {
            try {
                logfile.createNewFile();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try {
            FileWriter fw = new FileWriter(Environment.getExternalStorageDirectory().getPath() + "/uploadlog", true);
            PrintWriter bw = new PrintWriter(new BufferedWriter(fw));
            bw.println(startOrEnd + "\t" + System.currentTimeMillis());
            bw.flush();
            bw.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

	}
}
