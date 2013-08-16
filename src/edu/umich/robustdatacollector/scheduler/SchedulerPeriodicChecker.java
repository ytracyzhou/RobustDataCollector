/**
 * @author Yuanyuan Zhou
 * @date Nov 18, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector.scheduler;

import edu.umich.robustdatacollector.SchedulerService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SchedulerPeriodicChecker extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v("tracyzhou", "receive broadcast in scheduler periodic checker!");
        Intent schedulerService = new Intent(context, SchedulerService.class);
        context.startService(schedulerService);
    }
}
