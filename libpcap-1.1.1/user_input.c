#include "user_input.h"

//#define TEST_INPUT

#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <time.h>
#include <string.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/timeb.h>
#include <fcntl.h>
#include <linux/input.h>

//user input events and their string names
#define EVENT_SCREEN 	1
#define EVENT_VOLUP 	2
#define EVENT_VOLDOWN 3
#define EVENT_BALL		4
#define EVENT_POWER		5
#define EVENT_MENU		6
#define EVENT_HOME		7
#define EVENT_BACK		8
#define EVENT_SEARCH	9
#define EVENT_HORIZONTAL 10
#define EVENT_VERTICAL 11

#define ACTION_PRESS		1
#define ACTION_RELEASE 	2

#define DEVICE_NEXUSONE		1
#define DEVICE_CAPTIVATE 	2
#define DEVICE_INSPIRE 		3
#define DEVICE_ATRIX 			4

const char * event_str[] = {
	"",
	"screen",
	"key volup",
	"key voldown",
	"key ball",
	"key power",
	"key menu",
	"key home",
	"key back",
	"key search",
	"hor", //10
	"ver" //11
};

const char * action_str[] = {
	"",
	"press",
	"release",
	"touch" //3
};

#define N_EVENTS 128
struct input_event events[N_EVENTS];

fd_set rfds;

int myDevice;
extern FILE * ofsEvents;
extern double pcapTime;
extern double userTime;
extern int exitFlag;
extern int byteCounter;

void TerminateCapture();

#define FLUSH_INTERVAL 100 //count
#define RAPID_PUSH_INTERVAL 0.5	//in second
#define PUSH_COUNT_TO_COMPLAIN 3 //count
#define COMPLAIN_VERTICAL_RANGE 65 //in pixels
#define VIBRATE_DURATION 100 //in ms 

int flushCount = 0;
double lastReleaseTime = -100.0f;
double firstReleaseTime;
int rapidPushCount = 0;
int bValidVertPos = 1;

double GetTimestamp(struct timeval * pT) {
	return pT->tv_sec + pT->tv_usec / (double)1000000.0f;
}

///////////////////////////////////////////////
typedef struct {
	int arg1;
	int arg2;
	int arg3;
	int event;
	int action;
} KEY_ENTRY;

#define MAX_INPUT_FILE_ID 5
#define MAX_KEY_ENTRIES 10

KEY_ENTRY keyEntries[MAX_INPUT_FILE_ID+1][MAX_KEY_ENTRIES];
int nKeyEntries[MAX_INPUT_FILE_ID+1];

int inputEvents[MAX_INPUT_FILE_ID+1];
int maxEventPlusOne;
///////////////////////////////////////////////

void ReadDeviceKeyDB() {
	int device, input_file;
	KEY_ENTRY ke;	
	memset(nKeyEntries, 0, sizeof(nKeyEntries));
		
	FILE * ifs = fopen("/system/bin/key.db", "r");
	if (ifs == NULL) {
		printf("Cannot read file: key.db\n");
		exit(0);
	}
	
	while (1) {
		fscanf(ifs, "%d %d %d %d %d %d %d", &device, &input_file, &ke.arg1, &ke.arg2, &ke.arg3, &ke.event, &ke.action);
		
		if (device == -1) break;
		if (device != myDevice) continue;
			
		keyEntries[input_file][nKeyEntries[input_file]++] = ke;
	}
	fclose(ifs);
}

void GetMyDeviceType() {
	/*
	system("/system/bin/getprop > /sdcard/prop");
	FILE * ifs = fopen("/sdcard/prop", "r");
	if (ifs == NULL) {
		printf("Cannot determine device type\n");
		exit(0);
	}
	
	myDevice = -1;
	
	char buf[1024];
	while (!feof(ifs)) {
		if (fgets(buf, sizeof(buf), ifs) == NULL) break;
		if (strstr(buf, "[ro.product.device]") != NULL) {
			if (strstr(buf, "[passion]") != NULL) {
				myDevice = DEVICE_NEXUSONE;
				printf("Device type: Nexus One\n");
			} else if (strstr(buf, "[ace]") != NULL) {
				myDevice = DEVICE_INSPIRE;
				printf("Device type: HTC Inspire\n");
			} else if (strstr(buf, "[olympus]") != NULL) {
				myDevice = DEVICE_ATRIX;
				printf("Device type: Moto Atrix\n");
			} else if (strstr(buf, "[SGH-I897]") != NULL) {				
				myDevice = DEVICE_CAPTIVATE;
				printf("Device type: HTC Captivate\n");
			} else {
				printf("Unknown device type: %s\n", buf);
				exit(0);
			}
		}
	}	
	
	fclose(ifs);
	
	if (myDevice == -1) {
		printf("Unknown device type\n");
		exit(0);
	}		
	*/


	printf("Device type: Motorola Atirx\n");
	myDevice = DEVICE_ATRIX;

/*
	printf("Device type: HTC Captivate\n");
	myDevice = DEVICE_CAPTIVATE;
*/
}

int OpenInputDevice(int id) {	
	char deviceName[128];
	sprintf(deviceName, "/dev/input/event%d", id);
	printf("Opening input device %s... ", deviceName);
	
	int fd = open(deviceName, O_RDWR);
	if (fd == -1) {
		printf("Error: cannot open input device %s\n");
		exit(0);
	}
	
	printf("fd = %d\n", fd);	
	return fd;
}

int CloseInputDevice(int id) {	
	if (inputEvents[id] != -1) close(inputEvents[id]);
}

void Vibrate() {
	FILE * ofs = fopen("/sys/class/timed_output/vibrator/enable", "wb");
	if (ofs != NULL) {
		fprintf(ofs, "%d", VIBRATE_DURATION);
		fclose(ofs);
	}
}

//return 0 to stop
static int HandleEvents() {
	int i, j, k, m;
	
	FD_ZERO(&rfds);	
	
	for (i=0; i<=MAX_INPUT_FILE_ID; i++) {
		if (inputEvents[i] > 0) {
			FD_SET(inputEvents[i], &rfds);
		}
	}
	
	if (exitFlag) return 0;	
	int retval = select(maxEventPlusOne, &rfds, NULL, NULL, NULL);				
	if (exitFlag) return 0;
	
	if (retval == 0) {
		return 1;
	} else if (retval == -1) {
		printf("Error: Select() fails\n");		
		exit(0);
	}
	
	for (i=0; i<=MAX_INPUT_FILE_ID; i++) {
		if (inputEvents[i] == -1) continue;
		if (!(FD_ISSET(inputEvents[i], &rfds))) continue;		
		int rd = read(inputEvents[i], events, sizeof(struct input_event) * N_EVENTS);
		int nEvents = rd / sizeof(struct input_event);		
		m = nKeyEntries[i];
		
		for (j=0; j<nEvents; j++) {
			int arg1 = events[j].type;
			int arg2 = events[j].code;
			int arg3 = events[j].value;
			double tm = GetTimestamp(&events[j].time);
			
			for (k=0; k<m; k++) {
				//a "-1" entry for arg3 means anything positive
				//a "-2" entry for arg3 means anything (positive or zero)
				if (keyEntries[i][k].arg1 == arg1 && keyEntries[i][k].arg2 == arg2 &&
						(keyEntries[i][k].arg3 == arg3 || (keyEntries[i][k].arg3 == -1 && arg3>0) || keyEntries[i][k].arg3 == -2)
				) {
					
					int e = keyEntries[i][k].event;
					
					#ifdef TEST_INPUT
						printf("%.6lf %s %s", 
							tm, 
							event_str[keyEntries[i][k].event], 
							action_str[keyEntries[i][k].action]
						);
						
						if (e == EVENT_HORIZONTAL || e == EVENT_VERTICAL) {
							printf(" %d\n", arg3);
						} else {
							printf("\n");
						}
					#endif
					
					fprintf(ofsEvents, "%.6lf %s %s", 
						tm, 
						event_str[keyEntries[i][k].event], 
						action_str[keyEntries[i][k].action]
					);
					
					if (e == EVENT_HORIZONTAL || e == EVENT_VERTICAL) {
						fprintf(ofsEvents, " %d\n", arg3);
					} else {
						fprintf(ofsEvents, "\n");
					}
					
					if (++flushCount % FLUSH_INTERVAL == 0) fflush(ofsEvents);
						
					//rapid push
					if (e == EVENT_VERTICAL) {
						if (arg3 >= COMPLAIN_VERTICAL_RANGE) bValidVertPos = 0;
					}
					
					if (e == EVENT_SCREEN && keyEntries[i][k].action == ACTION_RELEASE) {
						if (bValidVertPos) {
							if (tm - lastReleaseTime < RAPID_PUSH_INTERVAL) {
								if (++rapidPushCount >= PUSH_COUNT_TO_COMPLAIN) {
									
									#ifdef TEST_INPUT
										printf("%.6lf %.6lf complain\n", firstReleaseTime, tm);
									#endif
									fprintf(ofsEvents, "%.6lf %.6lf complain\n", firstReleaseTime, tm);
									
									Vibrate();									
									rapidPushCount = 0;								
									lastReleaseTime = -100.0f;
								} else {
									rapidPushCount++;
									lastReleaseTime = tm;
									if (rapidPushCount == 1) firstReleaseTime = tm;
								}
							} else {
								rapidPushCount = 1;
								lastReleaseTime = tm;
								firstReleaseTime = tm;
							}							
						} else {
							rapidPushCount = 0;
							lastReleaseTime = -100.0f;
						}
						bValidVertPos = 1;
					}
				}
			}
		}
	}
		
	return 1;
}

void * CaptureUserInput(void * arg) {
	int i;
	
	GetMyDeviceType();
	ReadDeviceKeyDB();
	
	printf("sizeof(struct input_event) = %d\n", sizeof(struct input_event));
	maxEventPlusOne = -1;
			
	for (i=0; i<=MAX_INPUT_FILE_ID; i++) {		
		if (nKeyEntries[i] == 0) {
			inputEvents[i] = -1;
		} else {
			inputEvents[i] = OpenInputDevice(i);
			if (inputEvents[i] > maxEventPlusOne) maxEventPlusOne = inputEvents[i];
		}
	}
	maxEventPlusOne++;	
	
	while (1) {
		if (!HandleEvents()) break;
	}

	printf("Thread CaptureUserInput() exit.\n");

	for (i=0; i<=MAX_INPUT_FILE_ID; i++) {
		CloseInputDevice(i);
	}
	
	return 0;
}

void MyAssert(int x) {
	if (!x) {
		printf("Assertion failure\n");
		exit(0);
	}
}

void * MonitorExit(void * arg) {
	
	int clk = 0;	
	
	#define QUEUE_CAPACITY 18
	#define CHECK_VOLUME_FREQUENCY 10
	
	int queue[QUEUE_CAPACITY];
	int qLen = 0;	
	int ptr = 0;
	int totBytes = 0;
	
	while (1) {
		usleep(1000000);
		
		FILE * ifs = fopen("/data/tmp/dc_stop_flag", "r");
		if (ifs != NULL) {
			exitFlag = 1;			
			fclose(ifs);
			remove("/data/tmp/dc_activity");
			usleep(200000);			
			TerminateCapture();
			//The program terminates here
		}
		
		if (++clk % CHECK_VOLUME_FREQUENCY == 0) {
			//every 10 sec, write the total send/recv bytes in the past 180 sec to the file
			FILE * ofs = fopen("/data/tmp/dc_activity", "w");
			
			//FILE * ofs = stdout;
			
			if (ofs == NULL) {
				printf("Unable to write to /data/tmp/dc_activity");
				exit(0);
			}
						
			if (qLen < QUEUE_CAPACITY) {
				queue[qLen++] = byteCounter;
				totBytes += byteCounter;
			}	else {
				totBytes -= queue[ptr];
				MyAssert(totBytes >= 0);
				queue[ptr++] = byteCounter;
				totBytes += byteCounter;
				if (ptr == QUEUE_CAPACITY) ptr = 0;
			}
			
			byteCounter = 0;
			MyAssert(qLen <= QUEUE_CAPACITY);
			
			if (qLen < QUEUE_CAPACITY) {
				fprintf(ofs, "-1\n");
			} else {
				fprintf(ofs, "%d\n", totBytes);
			}
			
			fclose(ofs);
		}
	}
	
	#undef QUEUE_CAPACITY
	#undef CHECK_VOLUME_FREQUENCY
}

/*
#Device type: nexus one=1, captivate=2, inspire=3, atrix=4
#Event Screen=1 volume up=2 volume down=3 ball=4 power=5 menu=6 home=7 back=8 search=9
#Action press=1 release=2
#Device	Input_File Arg1	Arg2 Arg3	Event Action
1	2	1	330	1	1 1
1	2	1	330	0	1 2
1	4	1	115	1	2 1
1	4	1	115	0	2 2
1	4	1	114	1	3 1
1	4	1	114	0	3 2
1	4	1	116	1	5 1
1	4	1	116	0	5 2
1	5	1	272	1	4 1
1	5	1	272	0	4 2
2	3	3	48	40	1 1
2	3	3	48	0	1 2
2	1	1	42	1	2 1
2	1	1	42	0	2 2
2	1	1	58	1	3 1
2	1	1	58	0	3 2
2	1	1	26	1	5 1
2	1	1	26	0	5 2
2	2	1	158	1	6 1
2	2	1	158	0	6 2
2	2	1	139	1	7 1
2	2	1	139	0	7 2
2	2	1	28	1	8 1
2	2	1	28	0	8 2
2	2	1	107	1	9 1
2	2	1	107	0	9 2
3	3	3	48 -1	1 1
3	3	3	48	0	1 2
3	4	1	115	1	2 1
3	4	1	115	0	2 2
3	4	1	114	1	3 1
3	4	1	114	0	3 2
3	4	1	116	1	5 1
3	4	1	116	0	5 2
4	3	3	48	1	1 1
4	3	3	48	0	1 2
4	1	1	115	1	2 1
4	1	1	115	0	2 2
4	1	1	114	1	3 1
4	1	1	114	0	3 2
4	0	1	107	1	5 1
4	0	1	107	0	5 2
-1 -1 -1 -1 -1 -1 -1
*/
