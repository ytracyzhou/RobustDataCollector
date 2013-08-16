/**
 * @author Yuanyuan Zhou
 * @date Oct 30, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector.activeprobing;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RTT {
    
    private String filename = null;
    private String path = null;
    private double[] rtts;
    private String serverName = null;
    
    public RTT(String filename, String path, String serverName) {
        rtts = new double[]{};
        this.filename = filename;
        this.path = path;
        this.serverName = serverName;
    }
    
    public void runTest() {
        for(int i = 1; i <= 16 ; i++){
            rtts = AcpbUtilities.pushResult(rtts, unitTest(serverName));
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
        Date now = new Date();
        String nowStr = dateFormat.format(now);
        AcpbUtilities.writeToSDCard(nowStr + "\n", filename, path);
        String result = "";
        for (int i = 0; i < rtts.length; i++) {
            result += AcpbUtilities.roundDouble(rtts[i]) + "#";
        }
        AcpbUtilities.writeToSDCard("RTT\t" + result + "\n", filename, path);
        
    }
    /**
     * 
     * @param host
     * @return rtt of TCP Handshake in milliseconds
     */
    public static long unitTest(String host){
        long rtt = 0;

        long start, end;
        Socket tcpSocket = new Socket();

        try {
            tcpSocket.setSoTimeout(Definition.TCP_TIMEOUT_IN_MILLI);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        SocketAddress remoteAddr = new InetSocketAddress(host, Definition.PORT_UPLINK);

        start = System.currentTimeMillis();
        try {
            tcpSocket.connect(remoteAddr, Definition.TCP_TIMEOUT_IN_MILLI);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //test connect time
        end = System.currentTimeMillis();

        try {
            tcpSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        rtt = end - start;
        return rtt;
    }
}
