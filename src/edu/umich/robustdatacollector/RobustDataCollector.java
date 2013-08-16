/**
 * @author Yuanyuan Zhou
 * @date Nov 18, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

import edu.umich.robustdatacollector.scheduler.SchedulerPeriodicChecker;

import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

class RefreshUIThread extends Thread {
    
    Activity activity = null;
    
    RefreshUIThread (Activity activity) {
        this.activity = activity;
    }
    
    double roundTwoDecimals(double d) {
        DecimalFormat twoDForm = new DecimalFormat("#.#");
        return Double.valueOf(twoDForm.format(d));
    }
    
    public void run() {
        final TextView lastUploadTimestampTextView = (TextView)activity.findViewById(R.id.lastUploadTimestampTextView);
        final TextView sdFreeSpaceTextView = (TextView)activity.findViewById(R.id.sdcardFreeTextView);
        final TextView isUploadingTextView = (TextView)activity.findViewById(R.id.isUploadingTextView);
        final TextView isAcpbTextView = (TextView)activity.findViewById(R.id.isAcpbTextView);
        while(true) {
            final long lastUploadTimestamp = Utilities.getLastUploadTimestamp();
            final double sdcardFreeSpace = roundTwoDecimals(Utilities.getStorageSpaceLeft() * 100);
            final boolean isUploading = Utilities.readUploadingFlag();
            final boolean isAcpb = Utilities.readAcpbFlag();

            RobustDataCollector.mHandler.post(new Runnable() {
                public void run() {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    lastUploadTimestampTextView.setText("Last Upload Timestamp: " + simpleDateFormat.format(lastUploadTimestamp));
                }
            });
            
            RobustDataCollector.mHandler.post(new Runnable() {
                public void run() {
                    sdFreeSpaceTextView.setText("SDCard Free Space in Percentage: " + String.valueOf(sdcardFreeSpace));
                }
            });
            
            RobustDataCollector.mHandler.post(new Runnable() {
                public void run() {
                    isUploadingTextView.setText("Is Uploading: " + String.valueOf(isUploading));
                }
            });
            
            RobustDataCollector.mHandler.post(new Runnable() {
                public void run() {
                    isAcpbTextView.setText("Is Active Probing: " + String.valueOf(isAcpb));
                }
            });

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}

public class RobustDataCollector extends Activity {

    static Handler mHandler = new Handler();
    public static final int REQUEST_CODE = 100000;
    public static final long INTERVAL = 30 * 60 * 1000;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_robust_data_collector);
        startService(new Intent(this, SchedulerService.class));
        enablePeriodicCheck(this);
        RefreshUIThread refreshUIThread = new RefreshUIThread(this);
        refreshUIThread.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.robust_data_collector, menu);
        return true;
    }

    public static void enablePeriodicCheck(Context context) {
        Intent intent = new Intent(context, SchedulerPeriodicChecker.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE);
        if (pendingIntent != null)
            Log.v("tracyzhou", "alarm already registered!");
        else {
            pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, 0);
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + INTERVAL, INTERVAL, pendingIntent);
        }
    }
}
