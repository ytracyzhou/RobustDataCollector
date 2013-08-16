/**
 * @author Yuanyuan Zhou
 * @date Nov 18, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector;

import edu.umich.robustdatacollector.scheduler.SchedulerThread;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.IBinder;

public class SchedulerService extends Service {
    
    private int notifyID = 1;
    NotificationManager notificationManager = null;
    SchedulerThread thread = null;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        if (notificationManager == null)
            notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int ret = super.onStartCommand(intent, flags, startId);
        if (thread == null || thread.isAlive() == false) {
            thread = new SchedulerThread(this);
            thread.start();
        } else {
            thread.getResourceLock().acquireCPU(60000);
        }
        
        createNotification();
        return ret;
    }
    
    @Override
    public void onDestroy() {
        notificationManager.cancel(notifyID);
        super.onDestroy();
    }

    public void createNotification() {
        Resources res = this.getResources();        
        Intent notificationIntent = new Intent(this, RobustDataCollector.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification = new Notification.Builder(this)
        .setContentTitle(res.getString(R.string.notif_title))
        .setContentText(res.getString(R.string.notif_text))
        .setSmallIcon(R.drawable.ic_launcher)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(contentIntent)
        .getNotification();

        notificationManager.notify(notifyID, notification);

    }
}
