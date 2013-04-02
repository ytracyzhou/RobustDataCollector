package edu.umich.robustdatacollector.userinput;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.String;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

public class InputTrace {

	private static final String TAG = "USERINPUT_ALF";
	String outputFolder=Environment.getExternalStorageDirectory().getPath()+"CollectTrace/";
	String monitorProcsFile="mprocfile";
	String outputFileName="oInputTrace";
	String screenshotName="oScreenShot";
	String expFolder="oUserInput";
	String procName="user_input.s3";
	int killSignalNumber=7;
	int doScreenShotSignalNumber=10;
	int stopScreenShotSignalNumber=12;
	int screenShotFrequency=3;
	static double compressScale=0.25;
	boolean ContinueCapture=false;
	Process sh;
	Context ctx;
	DataOutputStream os;
	ArrayList<String> mprocs;
	screenshotDecision ssd;
	

	public InputTrace(Context ctx ,String outputfolder, String mprocsfilepath){
		Log.d(TAG, "User input Application is starting (Author: Alfred Chen <alfredrobustnet@gmail.com>)");
		this.ctx=ctx;		
		//only read the monitored process list when the phone launches
		
		
		outputFolder=outputfolder.trim();
		if (outputFolder.charAt(outputFolder.length()-1)=='/')
			outputFolder=outputFolder.substring(0, outputFolder.length()-1);
		
		expFolder="oUserInput";
		outputFileName="oInputTrace";
		screenshotName="oScreenShot";		
		monitorProcsFile=mprocsfilepath.trim();
		killSignalNumber=7;
	    doScreenShotSignalNumber=10;
		stopScreenShotSignalNumber=12;
		screenShotFrequency=3;
		ssd=null;

		Log.d(TAG, "[Configuration]  Output Folder Name:"+outputFolder+";"
                                  +"  SubFolder Name Prefix:"+expFolder+";"
				                  +"  User Input Trace File:"+outputFileName+";"
				                  +"  Screenshot Folder:"+screenshotName+";"
				                  +"  Target Application Configure File:"+monitorProcsFile+";"
				                  +"  Stop Capture Signal:"+killSignalNumber+";"
				                  +"  Start Screenshot Signal:"+doScreenShotSignalNumber+";"
				                  +"  Stop Screenshot Signal:"+stopScreenShotSignalNumber+";"
				                  +"  Screenshot Frequency:"+screenShotFrequency+";"
				                  );
	}
	public boolean isRunning(){
		boolean isRunning=false;
		try {
			Process sh = Runtime.getRuntime().exec("su");
			DataOutputStream os = new DataOutputStream(sh.getOutputStream());
			String Command;

			try {
				ArrayList<String> rows=getPlinesfromPS(procName);				
				for (int i=0;i<rows.size();i++){
			    	String[] cols = rows.get(i).split("\\s");
			    	for (int j=1;j<cols.length;j++)
			    		if (cols[j].length()!=0){ 
			    			isRunning=true;
			    			break;
			    		}
			    	if (isRunning)
			    		break;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}                    	

			Command="exit\n";
			os.writeBytes(Command);
	        os.flush();
	        os.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return isRunning;
		
	}
	
	public static String compressScreenshot(String ssnFileAbsolutePath) {
		//	ArrayList<String> tarNameList=new ArrayList<String>();
			Log.d(TAG, "compress the screenshots...");
	//		 File folder = new File(screenshotFolder);
	//		 File[] listOfFiles = folder.listFiles(); 
	//		 for (int i = 0; i < listOfFiles.length; i++) 
	//			 if (listOfFiles[i].isFile() && 
	//					 (listOfFiles[i].getName().endsWith("png")
	//					  || listOfFiles[i].getName().endsWith("PNG"))
	//				) 
	//				 tarNameList.add(listOfFiles[i].getName());
				 
	//		 for (int i=0;i<tarNameList.size();i++){		   
	//			 String tarName=tarNameList.get(i);
			String newTarName=null;
		
			 try {
				File tarFile=new File(ssnFileAbsolutePath.trim()); 
				if (!tarFile.exists()) return null;
				Bitmap oriimg = BitmapFactory.decodeFile(tarFile.getAbsolutePath());
				if (oriimg != null) {
					Bitmap tarimg=oriimg.createScaledBitmap(oriimg, (int)(oriimg.getWidth()*compressScale), (int)(oriimg.getHeight()*compressScale), false);
					newTarName=ssnFileAbsolutePath.substring(0, ssnFileAbsolutePath.length()-4);
				    tarimg.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(new File(newTarName)));
				    tarFile.delete();
				} else {
					tarFile.delete();
					newTarName = "NotDecoded";
				}
								
			} catch (IOException e) {
				e.printStackTrace();
			}
	//		 }
		   Log.d(TAG, "Done compression.");
	     return newTarName;
	}
	
	private void readMprocs(String filename){
		Log.d(TAG, "Read the target applications being monitored");
		try {
			File mpFile=new File(filename);
			if (!mpFile.exists())
				return;
			
			if (mprocs==null)
			    mprocs=new ArrayList<String>();
			else 
				mprocs.clear();
			BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(mpFile)));
			String line=null;
			while ((line=rdr.readLine())!=null)	{
				Log.d(TAG, "Read "+line);
				if (line.length()!=0)
				 mprocs.add(line.trim());
			}

		}catch (Exception e) {
			e.printStackTrace();
		}  
	}
	public void stopCapture(){	
		Log.d(TAG, "Stop user input capture");	
		if (ssd!=null){
		  ssd.stopMonitor();
		  ssd=null;
		}
		killProcess(procName,killSignalNumber);
	//	compressScreenshot();
	}
	public void doCapture(){
		Log.d(TAG, "Start user input capture");	

		if (isRunning()) return;
		
		readMprocs(monitorProcsFile);
		UserInputCollect uic=new UserInputCollect();
		uic.start();
		ssd=new screenshotDecision();
		ssd.start();
		
	}
	
	public void setKSN(int argknn){
		killSignalNumber=argknn;
	}
	public void setDoSSN(int argdossn){
		doScreenShotSignalNumber=argdossn;
	}
	public void setStopSSN(int argstopssn){
		stopScreenShotSignalNumber=argstopssn;
	}
	public void setSSNFreq(int argssnFreq){
		screenShotFrequency=argssnFreq;
	}
	
	private static String getCurrentTime(){
		Date dd=new Date();
		long mtime=dd.getTime();
		double ct0=mtime/1000.0;
		java.text.DecimalFormat df = new java.text.DecimalFormat("0.000");
		String ct=df.format(ct0);
		return ct;
	}
	
	
	class UserInputCollect extends Thread{
		public boolean torun=true;
    	public void run(){             
			try {
			//	String curTime=getCurrentTime();
				String absoluteOutputDir=outputFolder+"/"+expFolder+"-"+getCurrentTime();
				File filed = new File(absoluteOutputDir);
				if (!filed.exists()){
			//		Log.d(TAG, "KUN");
					filed.mkdirs();
				}
				
				
				String absoluteOutputFileName=absoluteOutputDir+"/"+outputFileName;
				String absoluteScreenShotName=absoluteOutputDir+"/"+screenshotName;
		//		Log.d(TAG, absoluteOutputFileName+":"+absoluteScreenShotName);
				
				File file = new File(absoluteOutputFileName);				
		
		//		if (file.exists()){			
		//			file.delete();
		//		}
				if (!file.exists()){	
					file.createNewFile();
				}
			
				sh = Runtime.getRuntime().exec("su");		    
				os = new DataOutputStream(sh.getOutputStream());
		//		String Command = "rm "+outputDir+"/*.png\n";
		//        os.writeBytes(Command);
		//        os.flush();
		        String Command = "./data/local/UserInput/user_input.s3.ind"
		        		+" "+absoluteOutputFileName
		        		+" "+absoluteScreenShotName
		        		+" "+killSignalNumber
		        		+" "+doScreenShotSignalNumber
		        		+" "+stopScreenShotSignalNumber
		        		+" "+screenShotFrequency
		        		+"\n";
		        os.writeBytes(Command); 
				sh.waitFor();
		        os.close();
			} catch (Exception e) {
				e.printStackTrace();
			}    		
    	}
    	
	};
	
	class screenshotDecision extends Thread{
        boolean torun;
		public screenshotDecision(){
			torun=true;
		}
		public void stopMonitor(){
			torun=false;
		}
    	public void run(){
    		boolean laststatus=false;
    		while (torun){
    			if (mprocs==null) continue;
	    		ActivityManager mActivityManager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
	    		List<ActivityManager.RunningAppProcessInfo> mActiveProcessprocess = mActivityManager.getRunningAppProcesses();
	
	            boolean doscreenshot=false;
	            for (int i=0;i<mprocs.size();i++){
	            	String targetProc=mprocs.get(i);
		            for (Iterator<RunningAppProcessInfo> iterator = mActiveProcessprocess.iterator(); iterator.hasNext();) {
		                    RunningAppProcessInfo runningAppProcessInfo = (RunningAppProcessInfo) iterator.next();
		                    int pImportance = runningAppProcessInfo.importance;
		                    
		                    if (!runningAppProcessInfo.processName.toString().contains(targetProc)) continue;
		                    if (pImportance==RunningAppProcessInfo.IMPORTANCE_FOREGROUND){
		                    	doscreenshot=true;
		                    	break;
		                    }	               
		            }
		            if (doscreenshot) break;
	            }
	            if (doscreenshot!=laststatus){
		            if (doscreenshot){
		        		Log.d(TAG, "Some target application is in foreground, start take screenshot");	
		            	killProcess(procName,doScreenShotSignalNumber);
		            }
		            else{
		        		Log.d(TAG, "no application is in foreground now, stop take screenshot");	
		            	killProcess(procName,stopScreenShotSignalNumber);       
		            }
		            laststatus=doscreenshot;
	            }
    		}
    		killProcess(procName,stopScreenShotSignalNumber);
 		
    	}
    	
	};
	
	protected void killProcess(String tar, int ksn){
		try {
			Process sh = Runtime.getRuntime().exec("su");
			DataOutputStream os = new DataOutputStream(sh.getOutputStream());
			String Command;

			try {
				ArrayList<String> rows=getPlinesfromPS(tar);
				for (int i=0;i<rows.size();i++){
			    	String[] cols = rows.get(i).split("\\s");
			    	for (int j=1;j<cols.length;j++)
			    		if (cols[j].length()!=0){ 
			      			Command="kill -s "+ksn+" "+ cols[j]+"\n";
			    			 os.writeBytes(Command);
			    		    break;
			    		}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}                    	

			Command="exit\n";
			os.writeBytes(Command);
	        os.flush();
	        os.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected static ArrayList<String> getPlinesfromPS(String processName){
		String resps=executePS();
		String[] lines = resps.split("\\n");
		ArrayList<String> reslines=new ArrayList<String>();
		for (int i=0;i<lines.length;i++){
			if (lines[i].contains(processName)){
				reslines.add(lines[i]);
			}
		}
		return reslines;
		
	}
	protected static String executePS() {
		String line = null;
		try {
		Process process = Runtime.getRuntime().exec("ps");
		InputStreamReader inputStream = new InputStreamReader(process.getInputStream());
		BufferedReader reader = new BufferedReader(inputStream);
			

			int read;
			char[] buffer = new char[4096];
			StringBuffer output = new StringBuffer();
			while ((read = reader.read(buffer)) > 0) {
				output.append(buffer, 0, read);
			}
			process.waitFor();

			line = output.toString();
			reader.close();
			inputStream.close();
			reader.close();			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return line;
	}
	protected static int getUidForPid(int pid) {
	    try {
	      BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/" + pid + "/status")));
	      for(String line = rdr.readLine(); line != null; line = rdr.readLine()) {
	        if(line.startsWith("Uid:")) {
	          String tokens[] = line.substring(4).split("[ \t]+"); 
	          String realUidToken = tokens[tokens[0].length() == 0 ? 1 : 0];
	          try {
	            return Integer.parseInt(realUidToken);
	          } catch(NumberFormatException e) {
	            return -1;
	          }
	        }
	      }
	    } catch(IOException e) {
	    	e.printStackTrace();
	    }
	    return -1;
	  }


}
