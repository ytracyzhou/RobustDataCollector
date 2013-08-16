package edu.umich.robustdatacollector.uploader;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

import com.enterprisedt.net.ftp.FTPException;
import com.enterprisedt.net.ftp.FileTransferClient;
import com.enterprisedt.net.ftp.WriteMode;

import edu.umich.robustdatacollector.Utilities;
import edu.umich.robustdatacollector.scheduler.SchedulerThread;
import edu.umich.robustdatacollector.userinput.InputTrace;

import android.os.Environment;
import android.util.Log;

public class UInpUploader {
    
    private static String TAG = "tracyzhou";
    private static final String uploadRoot = Environment.getExternalStorageDirectory() + "/UserInput";
    
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
    
    public static int uploadData(String deviceId, long uploadStartTime) {
        int ret = 0;
        File uploadRootFolder = new File(uploadRoot);
        if (uploadRootFolder.isDirectory()) {
            File [] folders = uploadRootFolder.listFiles();
            for (File folder: folders)
            {
                Calendar calendar = Calendar.getInstance();
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                if (hour >= 2 && hour <= 7) {
                    
                } else if (System.currentTimeMillis() - uploadStartTime > SchedulerThread.UPLOADING_UINP_DURATION * 1000) {
                    break;
                }
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
                    } catch (IOException e) {
                        Log.v(TAG, e.getMessage());
                        ret = -1;
                        break;
                    }
                    for (File file: list) {
                        String filename = file.getName();
                        String localCompletePath = file.getAbsolutePath();
                        if (filename.startsWith("oInputTrace")) {
                            if (!filename.endsWith(".zip")) {
                                Log.v(TAG, "compressing file: " + localCompletePath);
                                boolean compressSuccess = Utilities.zip(localCompletePath + ".zip", localCompletePath);
                                if (!compressSuccess) {
                                    ret = -1;
                                    break;
                                }
                                filename += ".zip";
                            }
                        } else if (filename.startsWith("oScreenShot")) {
                            if (filename.endsWith(".png")) {
                                String compressedFileName = InputTrace.compressScreenshot(localCompletePath);
                                if (compressedFileName == null) {
                                    ret = -1;
                                    break;
                                }
                                else if (compressedFileName == "NotDecoded") {
                                    file.delete();
                                    break;
                                }
                                filename = compressedFileName;
                            }
                        }
                        try {
                            String localFinalCompletePath = folder.getAbsolutePath() + "/" + filename;
                            Log.v(TAG, "uploading file: " + localFinalCompletePath);
                            client.changeDirectory(remoteFolderName);
                            long startTime = System.currentTimeMillis();
                            client.uploadFile(localFinalCompletePath, filename, WriteMode.OVERWRITE);
                            long endTime = System.currentTimeMillis();
                            long time = endTime - startTime;
                            Log.v(TAG, "fininshed uploading in " + time + " millis");
                            client.changeDirectory("/");
    
                            long size = getFileSize(localFinalCompletePath);
                            if (size > 0) {
                                Log.v(TAG, "uploaded file size: " + size);
                            }
                            file.delete();
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
                    list = folder.listFiles();
                    if (list == null || list.length == 0) {
                        folder.delete();
                    }
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
}
