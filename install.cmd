mount -o rw,remount -t yaffs2 /dev/block/mtdblock1 /system
cat  /sdcard/imap-tcpdump  > /system/bin/imap-tcpdump
chmod 777 /system/bin/imap-tcpdump
mount -o ro,remount -t yaffs2 /dev/block/mtdblock1 /system
cat /sdcard/imap-tcpdump > /data/local/imap-tcpdump
chmod 777 /data/local/imap-tcpdump
mkdir /data/local/UserInput
cat /sdcard/user_input.s3.ind > /data/local/UserInput/user_input.s3.ind
chmod 777 /data/local/UserInput/user_input.s3.ind
mkdir /data/tmp