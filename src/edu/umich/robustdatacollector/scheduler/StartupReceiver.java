/**
 * @author Yuanyuan Zhou
 * @date Nov 18, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector.scheduler;

import edu.umich.robustdatacollector.RobustDataCollector;
import edu.umich.robustdatacollector.SchedulerService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StartupReceiver extends BroadcastReceiver {

	private static String TAG = "tracyzhou";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.v(TAG, "receive broadcast in startup receiver!");
		RobustDataCollector.enablePeriodicCheck(context);
		Intent schedulerService = new Intent(context, SchedulerService.class);
		context.startService(schedulerService);
	}

}
