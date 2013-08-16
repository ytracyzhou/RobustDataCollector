/**
 * @author Yuanyuan Zhou
 * @date Oct 30, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector.activeprobing;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Throughput {
    private double[] tps_result;
    public int size = 0;
    public long testStartTime = 0; //test start time, used to determine slow start period
    public long startTime = 0; //start time of this period to calculate throughput
    public final static long SAMPLE_PERIOD = 1000; 
    public final static long SLOW_START_PERIOD = 5000; //empirically set to 5 seconds
    private String filename = null;
    private String path = null;
    private String serverName = null;
    
    public Throughput(String filename, String path, String serverName) {
        tps_result = new double[]{};
        testStartTime = System.currentTimeMillis();
        this.filename = filename;
        this.path = path;
        this.serverName = serverName;
    }
    
    public void runTest(boolean isDown) {
        String type;
        if (isDown) {
            downlink();
            type = "DOWN";
        } else {
            uplink();
            type = "UP";
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
        Date now = new Date();
        String nowStr = dateFormat.format(now);
        AcpbUtilities.writeToSDCard(nowStr + "\n", filename, path);
        String result = "";
        for (int i = 0; i < tps_result.length; i++) {
            result += AcpbUtilities.roundDouble(tps_result[i]) + "#";
        }
        AcpbUtilities.writeToSDCard(type + "\t" + result + "\n",
                                    filename, path);
    }
    
    private void downlink() {
        Socket tcpSocket = null;
        DataInputStream dis = null;
        try {
            tcpSocket = new Socket();
            SocketAddress remoteAddr = new InetSocketAddress(serverName, Definition.PORT_DOWNLINK);
            tcpSocket.connect(remoteAddr, Definition.TCP_TIMEOUT_IN_MILLI);
            tcpSocket.setSoTimeout(Definition.TCP_TIMEOUT_IN_MILLI);
            tcpSocket.setTcpNoDelay(true);
            dis = new DataInputStream(tcpSocket.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        try {
            byte[] buffer = new byte[10000];
            int read_bytes = 0;
            do {
                read_bytes = dis.read(buffer, 0, buffer.length);
                updateSize(read_bytes);
            } while (read_bytes >= 0);
            dis.close();
            tcpSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }
    
    private void uplink() {
        Socket tcpSocket = null;
        DataInputStream dis = null;
        DataOutputStream dos = null;
        try {
            tcpSocket = new Socket();
            SocketAddress remoteAddr = new InetSocketAddress(serverName, Definition.PORT_UPLINK);
            tcpSocket.connect(remoteAddr, Definition.TCP_TIMEOUT_IN_MILLI);
            dis = new DataInputStream(tcpSocket.getInputStream());
            dos = new DataOutputStream(tcpSocket.getOutputStream());
            tcpSocket.setSoTimeout(Definition.TCP_TIMEOUT_IN_MILLI);
            tcpSocket.setTcpNoDelay(true);
        } catch (Exception e){
            e.printStackTrace();
            return;
        }

        long startTime = System.currentTimeMillis();
        long endTime = System.currentTimeMillis();

        //Test lasts 16 seconds - Junxian
        byte [] buffer = AcpbUtilities.genRandomString(Definition.THROUGHPUT_UP_SEGMENT_SIZE).getBytes();
        try {
            do {
                dos.write(buffer, 0, buffer.length);
                dos.flush();
                endTime = System.currentTimeMillis();
            } while ((endTime - startTime) < Definition.TP_DURATION_IN_MILLI);

            byte [] terminationBuffer = Definition.UPLINK_FINISH_MSG.getBytes();
            dos.write(terminationBuffer, 0, terminationBuffer.length);
            dos.flush();
            byte [] resultMsg = new byte[3000];
            int resultMsgLen = dis.read(resultMsg, 0, resultMsg.length);
            if (resultMsgLen > 0) {
                String resultMsgStr = new String(resultMsg).substring(0, resultMsgLen);
                String [] tps_result_str = resultMsgStr.split("#");
                for (int i = 0; i < tps_result_str.length; i++) {
                    double throughput = Double.valueOf(tps_result_str[i]);
                    tps_result = AcpbUtilities.pushResult(tps_result, throughput);
                }
            }
            
            dos.close();
            dis.close();
            tcpSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        
    }
    
    private void updateSize(int delta) {
        double gtime = System.currentTimeMillis() - testStartTime;
        if (gtime < SLOW_START_PERIOD) //ignore slow start
            return;
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
            size = 0;
        }
        size += delta;
        double time = System.currentTimeMillis() - startTime;
        if (time < SAMPLE_PERIOD) {
            return;
        } else {
            double throughput = AcpbUtilities.roundDouble((double)size * 8.0 / time); //time is in milli, so already kbps
            //System.out.println("_throughput: " + throughput + " kbps_Time(sec): " + (gtime / 1000.0));
            tps_result = AcpbUtilities.pushResult(tps_result, throughput);
            size = 0;
            startTime = System.currentTimeMillis();
        }   
    }
}
