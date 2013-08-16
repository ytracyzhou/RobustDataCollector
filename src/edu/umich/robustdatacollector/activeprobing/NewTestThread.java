/**
 * @author Yuanyuan Zhou
 * @date Oct 30, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector.activeprobing;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.os.Environment;
import edu.umich.robustdatacollector.Utilities;

public class NewTestThread extends Thread {
    
    private String serverName = null;
    
    public NewTestThread(String serverName) {
        this.serverName = serverName;
    }
    
    @Override
    public void run() {
        Utilities.setAcpbFlag();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
        Date now = new Date();
        String filename = "acpb-" + dateFormat.format(now);
        String path = Environment.getExternalStorageDirectory() + "/ActiveProbing";
        RTT rtt = new RTT(filename, path, serverName);
        rtt.runTest();
        Throughput tps_down = new Throughput(filename, path, serverName);
        tps_down.runTest(true);
        Throughput tps_up = new Throughput(filename, path, serverName);
        tps_up.runTest(false);
        Utilities.clearAcpbFlag();
    }
}
