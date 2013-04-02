/**
 * @author Yuanyuan Zhou
 * @date Oct 30, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector.activeprobing;

public class Definition {
	//public static final String SERVER_NAME = "falcon.eecs.umich.edu";
	public static final int PORT_DOWNLINK = 49995;
	public static final int PORT_UPLINK = 49996;
	public static final int TCP_TIMEOUT_IN_MILLI = 10000; // 5 seconds for timeout
	public static final int TP_DURATION_IN_MILLI = 16000; // 16 seconds for throughput tests
	public static final int THROUGHPUT_UP_SEGMENT_SIZE = 1300;
	public static final String UPLINK_FINISH_MSG = "*";
}
