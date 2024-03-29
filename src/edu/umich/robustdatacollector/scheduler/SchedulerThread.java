/**
 * @author Yuanyuan Zhou
 * @date Nov 18, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector.scheduler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import com.enterprisedt.net.ftp.FTPException;
import com.enterprisedt.net.ftp.FileTransferClient;
import com.enterprisedt.net.ftp.WriteMode;



//import edu.umich.robustdatacollector.TCPSettings;
import edu.umich.robustdatacollector.Utilities;
import edu.umich.robustdatacollector.imapcollector.IMAPCollector;
import edu.umich.robustdatacollector.passivemonitoring.PassiveMonitoringService;
import edu.umich.robustdatacollector.passivemonitoring.NoInterfaceNameException;
import edu.umich.robustdatacollector.uploader.AcpbUploader;
import edu.umich.robustdatacollector.uploader.IMAPUploader;
import edu.umich.robustdatacollector.uploader.LogUploader;
import edu.umich.robustdatacollector.uploader.PsmnUploader;
import edu.umich.robustdatacollector.uploader.UInpUploader;
import edu.umich.robustdatacollector.userinput.InputTrace;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SchedulerThread extends Thread {

    private String TAG = "tracyzhou";
    
    final private static String policyFileName = "policy.xml";
    final private static String mProcFileName = "mprocfile";
    
    final private static int INTERFACE_CHANGE_CHECK_INTERVAL = 10; // in seconds
    final private static int DATA_STORAGE_CHECK_INTERVAL = 60; // in seconds
    final private static int DATA_UPLOAD_CHECK_INTERVAL = 120; // in seconds
    final private static int SDCARD_CRITICAL_FOR_STOP_DATA_COLLECTION = 5;
    final private static int SDCARD_CRITICAL_FOR_UPLOADING = 15; //FENG_CHANGED, was 15
    public static String ACTIVE_PROBING_SERVER_NAME = "owl.eecs.umich.edu";
    public static int ACTIVE_PROBING_FRQ = 600;
    public static int ACTIVE_PROBING_AFTER_FRQ = 600;
    public static int ACTIVE_PROBING_LOW_BATTERY_LEVEL = 15;
    private static int UPLOADING_LOW_BATTERY_LEVEL = 10;
    private static int TWO_UPLOADS_MIN_INTERVAL = 3600; // in seconds FENG_CHANGED, was 7200
    private static int TWO_UPLOADS_MAX_INTERVAL = 86400; // in seconds
    public static int UPLOADING_IMAP_DURATION = 300;
    public static int UPLOADING_PSMN_DURATION = 300;
    public static int UPLOADING_UINP_DURATION = 300;
    public static int TCP_CONG_CTRL = 1;
    public static int TCP_ICW = 10;
    
    private ResourceLock resourceLock = null;
    private Context context = null;
    private ScreenReceiver screenReceiver = null;
    private BatteryReceiver batteryReceiver = null;
    private IMAPCollector imapCollector = null;
    private InputTrace inputTrace = null;
    
    public static int level = -1;
    public static int plugged = -1;
    private long sleepTimeTotal = 0;
    private String deviceId = null;
    
    TelephonyManager telephonyManager = null;
    InterfaceDetector interfaceDetector = null;
    
    public SchedulerThread(Context context) {
        this.context = context;
        imapCollector = new IMAPCollector(context);
        String sdcardPath = Environment.getExternalStorageDirectory().getPath();
        inputTrace = new InputTrace(context, sdcardPath + "/UserInput/", sdcardPath + "/" + mProcFileName);
        interfaceDetector = new InterfaceDetector(imapCollector, context);
        resourceLock = new ResourceLock(context);
        Utilities.clearUploadingFlag();
        Utilities.clearAcpbFlag();
        screenReceiver = new ScreenReceiver();
        batteryReceiver = new BatteryReceiver();
        IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(screenReceiver, screenFilter);
        context.registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        telephonyManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        deviceId = telephonyManager.getDeviceId();
    }
    
    public ResourceLock getResourceLock() {
        return resourceLock;
    }
    
    
    private void parsePolicyFile() {
        File policyFile = new File(Environment.getExternalStorageDirectory().getPath() + "/" + policyFileName);
        if (policyFile.exists()) {
            SAXReader reader = new SAXReader();
            try {
                Document  document = reader.read(policyFile);
                Element rootElm = document.getRootElement();
                
                Element activeprobingElm = rootElm.element("activeprobing");
                Element activeprobingSrvElm = activeprobingElm.element("servername");
                Element activeprobingBatteryElm = activeprobingElm.element("battery");
                Element activeprobingFrqElm = activeprobingElm.element("frequency");
                Element activeprobingAfterFrqElm = activeprobingElm.element("afterfrequency");
                
                Element uploadElm = rootElm.element("upload");
                Element uploadBatteryElm = uploadElm.element("battery");
                Element uploadMaxElm = uploadElm.element("max");
                Element uploadMinElm = uploadElm.element("min");
                Element uploadIMAPElm = uploadElm.element("imapduration");
                Element uploadPsmnElm = uploadElm.element("psmnduration");
                Element uploadUInpElm = uploadElm.element("uinpduration");
                //Element uploadServerElm = uploadElm.element("server");
                
                //Element tcpElm = rootElm.element("tcp");
                //Element ccElm = tcpElm.element("cc");
                //Element icwElm = tcpElm.element("icw");
                
                ACTIVE_PROBING_SERVER_NAME = activeprobingSrvElm.getText();
                ACTIVE_PROBING_LOW_BATTERY_LEVEL = Integer.valueOf(activeprobingBatteryElm.getText());
                ACTIVE_PROBING_FRQ = Integer.valueOf(activeprobingFrqElm.getText());
                ACTIVE_PROBING_AFTER_FRQ = Integer.valueOf(activeprobingAfterFrqElm.getText());
                
                UPLOADING_LOW_BATTERY_LEVEL = Integer.valueOf(uploadBatteryElm.getText());
                TWO_UPLOADS_MIN_INTERVAL = Integer.valueOf(uploadMinElm.getText());
                TWO_UPLOADS_MAX_INTERVAL = Integer.valueOf(uploadMaxElm.getText());
                UPLOADING_IMAP_DURATION = Integer.valueOf(uploadIMAPElm.getText());
                UPLOADING_PSMN_DURATION = Integer.valueOf(uploadPsmnElm.getText());
                UPLOADING_UINP_DURATION = Integer.valueOf(uploadUInpElm.getText());
                
                /*TCP_CONG_CTRL = Integer.valueOf(ccElm.getText());
                TCP_ICW = Integer.valueOf(icwElm.getText());
                TCPSettings.changeTCPSettings(TCPSettings.TCP_SETTINGS_CONG_CTRL, String.valueOf(TCP_CONG_CTRL));
                TCPSettings.changeTCPSettings(TCPSettings.TCP_SETTINGS_ICW, String.valueOf(TCP_ICW));
                String curICW = TCPSettings.currentTCPSettings(TCPSettings.TCP_SETTINGS_ICW);
                String curCC = TCPSettings.currentTCPSettings(TCPSettings.TCP_SETTINGS_CONG_CTRL);
                String curRMEM = TCPSettings.currentTCPSettings(TCPSettings.TCP_SETTINGS_RMEM);
                String curWMEM = TCPSettings.currentTCPSettings(TCPSettings.TCP_SETTINGS_WMEM);
                String curIProute = TCPSettings.currentTCPSettings(TCPSettings.TCP_SETTINGS_IPROUTE);
                if (curICW == null && curCC == null && curRMEM == null && curWMEM == null && curIProute == null)
                    return;
                try {
                    File logfile = new File(Environment.getExternalStorageDirectory().getPath() + "/curtcpsetting");
                    if (!logfile.exists())
                        logfile.createNewFile();
                    FileWriter fw = new FileWriter(Environment.getExternalStorageDirectory().getPath() + "/curtcpsetting", true);
                    PrintWriter bw = new PrintWriter(new BufferedWriter(fw));
                    String result = "[" + System.currentTimeMillis() + "]";
                    if (curICW != null) {
                        result += "[Current ICW]" + curICW;
                    }
                    if (curCC != null) {
                        result += "[Current CC]" + curCC;
                    }
                    if (curRMEM != null) {
                        result += "[Current RMEM]" + curRMEM;
                    }
                    if (curWMEM != null) {
                        result += "[Current WMEM]" + curWMEM;
                    }
                    if (curIProute != null) {
                        result += "[Current IProute]" + curIProute;
                    }
                    bw.println(result);
                    bw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }*/
            } catch (DocumentException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
    private void downloadConfig() {
        FileTransferClient client = new FileTransferClient();
        try {
            client.setRemoteHost(Utilities.FTPServerName);
            client.setUserName(Utilities.FTPUsername);
            client.setPassword(Utilities.FTPPassword);
            client.connect();
            client.downloadFile(Environment.getExternalStorageDirectory().getPath() + "/" + policyFileName, deviceId + "_" + policyFileName, WriteMode.OVERWRITE);
            client.downloadFile(Environment.getExternalStorageDirectory().getPath() + "/" + mProcFileName, mProcFileName, WriteMode.OVERWRITE);
            client.uploadFile(Environment.getExternalStorageDirectory().getPath() + "/curtcpsetting", "curtcpsetting-" +
                    System.currentTimeMillis() + "-" + deviceId, WriteMode.OVERWRITE);
        } catch (FTPException e) {
            e.printStackTrace();
            Log.v(TAG, e.getMessage());
        } catch(IOException e) {
            e.printStackTrace();
            Log.v(TAG, e.getMessage());
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
    
    private void upload() {
        if (interfaceDetector.isWifiOn() || interfaceDetector.is4GOn() || interfaceDetector.is3GOn()) {
            downloadConfig();
            parsePolicyFile();
        }
        boolean hasData1 = IMAPUploader.hasData();
        boolean hasData2 = PsmnUploader.hasData();
        boolean hasData3 = AcpbUploader.hasData();
        boolean hasData4 = UInpUploader.hasData();
        if (!hasData1 && !hasData2 && !hasData3 && !hasData4)
            return;
        
        boolean storageCritical = Utilities.isStorageCritical(SDCARD_CRITICAL_FOR_UPLOADING);
        long curTime = System.currentTimeMillis();
        boolean toUpload = false;
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (storageCritical || curTime - Utilities.getLastUploadTimestamp() > TWO_UPLOADS_MAX_INTERVAL * 1000) {
            if (interfaceDetector.isWifiOn() || interfaceDetector.is4GOn() || interfaceDetector.is3GOn()) {
                toUpload = true;
            }
        } else if (Utilities.isUserIdle()) {
            if (interfaceDetector.isWifiOn() || interfaceDetector.is4GOn()) {
                toUpload = true;
            }
        } else if (hour >= 2 && hour <= 7) {
            if (interfaceDetector.isWifiOn() || interfaceDetector.is4GOn() || interfaceDetector.is3GOn()) {
                toUpload = true;
            }
        }

        if (toUpload) {
            resourceLock.acquireCPU();
            imapCollector.stopCollectingIMAPData();
            Intent passiveMonitoringServiceIntent = new Intent(context, PassiveMonitoringService.class);
            context.stopService(passiveMonitoringServiceIntent);
            inputTrace.stopCapture();

            Utilities.setUploadingFlag();
            
            File [] imapList = IMAPUploader.getDataToUpload();
            try {
                imapCollector.startCollectingIMAPData(interfaceDetector.getCurrentInterfaceType());
            } catch (NoInterfaceNameException e) {
                e.printStackTrace();
            }
            
            File [] psmnList = PsmnUploader.getDataToUpload();
            context.startService(passiveMonitoringServiceIntent);
            
            if ((imapList != null && imapList.length != 0) || (psmnList != null && psmnList.length != 0)
                    || AcpbUploader.hasData() || UInpUploader.hasData()) {
                
                if (level <= UPLOADING_LOW_BATTERY_LEVEL && plugged <= 0) {
                    return;
                }
                Utilities.writeUploadLog("Start");
                long uploadStartTime = System.currentTimeMillis();
                int error1 = IMAPUploader.uploadData(deviceId, imapList, uploadStartTime);
                uploadStartTime = System.currentTimeMillis();
                int error2 = PsmnUploader.uploadData(deviceId, psmnList, uploadStartTime);
                int error3 = AcpbUploader.uploadData(deviceId);
                uploadStartTime = System.currentTimeMillis();
                int error4 = UInpUploader.uploadData(deviceId, uploadStartTime);
                Log.v(TAG, "error1: " + error1 + ", error2: " + error2 + ", error3: " + error3 + ", error4: " + error4);
                Utilities.writeUploadLog("End");
                Utilities.setLastUploadTimestamp(System.currentTimeMillis());
            }
            
            Utilities.clearUploadingFlag();
            LogUploader.upload(deviceId);
            if (!inputTrace.isRunning())
                inputTrace.doCapture(); 
            resourceLock.releaseCPU();
        }
    }
    
    private class ScreenReceiver extends BroadcastReceiver {
        
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                inputTrace.stopCapture();
                AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
                Intent activeProbingIntent = new Intent(context, ActiveProbingReceiver.class);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 100, activeProbingIntent, 0);
                Calendar calendar = Calendar.getInstance();
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                if (hour >= 2 && hour <= 7) {
                    alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + ACTIVE_PROBING_FRQ * 1000,
                            ACTIVE_PROBING_AFTER_FRQ * 1000, pendingIntent);
                } else {
                    alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + ACTIVE_PROBING_FRQ * 500,
                            ACTIVE_PROBING_AFTER_FRQ * 500, pendingIntent);
                }
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                boolean storageCritical = Utilities.isStorageCritical(SDCARD_CRITICAL_FOR_STOP_DATA_COLLECTION);
                AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
                Intent activeProbingIntent = new Intent(context, ActiveProbingReceiver.class);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 100, activeProbingIntent, 0);
                alarmManager.cancel(pendingIntent);
                if (!inputTrace.isRunning() && !storageCritical)
                    inputTrace.doCapture();
            }
        }
    }
    
    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int rawlevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (rawlevel >= 0 && scale > 0) {
                level = (rawlevel * 100) / scale;
            }
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        }
        
    }
    
    public void run() {
        try {
            while (true) {
                
                if (Utilities.readAcpbFlag() && Utilities.readUploadingFlag()) {
                    Utilities.clearAcpbFlag();
                    Utilities.clearUploadingFlag();
                }
                
                if (sleepTimeTotal % INTERFACE_CHANGE_CHECK_INTERVAL == 0) {
                    interfaceDetector.detect();
                }

                if (sleepTimeTotal % DATA_STORAGE_CHECK_INTERVAL == 0) {
                    boolean storageCritical = Utilities.isStorageCritical(SDCARD_CRITICAL_FOR_STOP_DATA_COLLECTION);
                    if (storageCritical) {
                        imapCollector.stopCollectingIMAPData();
                        Intent newDataCollectorService = new Intent(context, PassiveMonitoringService.class);
                        context.stopService(newDataCollectorService);
                        inputTrace.stopCapture();
                    } else {
                        if (imapCollector.isIMAPCollectorRunning() == false) {
                            try {
                                imapCollector.startCollectingIMAPData(interfaceDetector.getCurrentInterfaceType());
                            } catch (NoInterfaceNameException e) {
                                e.printStackTrace();
                            }
                        }
                        if (!inputTrace.isRunning())
                            inputTrace.doCapture();
                        Intent passiveMonitoringService = new Intent(context, PassiveMonitoringService.class);
                        context.startService(passiveMonitoringService);
                    }
                }
                Intent passiveMonitoringService = new Intent(context, PassiveMonitoringService.class);
                context.startService(passiveMonitoringService);
                
                if (sleepTimeTotal % DATA_UPLOAD_CHECK_INTERVAL == 0) {

                    if (!Utilities.readAcpbFlag()) {
                        long curTime = System.currentTimeMillis();
                        boolean shouldWait = false;

                        long lastUploadTimestamp = Utilities.getLastUploadTimestamp();
                        long diff = curTime - lastUploadTimestamp;
                        Log.v(TAG, "time elapsed: " + diff);
                        if (diff < TWO_UPLOADS_MIN_INTERVAL * 1000) {
                            shouldWait = true;
                        }
                        Log.v(TAG, "isUserIdle: " + Utilities.isUserIdle() + ", ShouldWait: " + shouldWait);
                        if (!shouldWait) {
                            upload();
                        }
                    }   
                }
            
                Thread.sleep(INTERFACE_CHANGE_CHECK_INTERVAL * 1000);
                sleepTimeTotal += INTERFACE_CHANGE_CHECK_INTERVAL;
                
            }
        }
        catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    
}
