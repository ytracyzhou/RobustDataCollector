/**
 * @author Yuanyuan Zhou
 * @date Nov 18, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector.scheduler;

import android.content.Context;
import android.os.PowerManager;

public class ResourceLock {
	private final Context mContext;
	private PowerManager.WakeLock mWakeLock = null;
    
	public ResourceLock(Context context){
		mContext = context;
		// Create a wake lock
		PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tracyzhou");
		mWakeLock.setReferenceCounted(true);
	}

	public void acquireCPU(long timeout){
		mWakeLock.acquire(timeout);
	}
   
	public void acquireCPU(){
		mWakeLock.acquire();
	}
    
	public void releaseCPU(){
		try {
			if (mWakeLock.isHeld()) {
				mWakeLock.release();
			}
		}
		catch (Exception e) {
		}    	
	}

}
