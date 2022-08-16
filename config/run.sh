#!/bin/bash
java -DMYSQL_USER=<user> \
     -DMYSQL_PASS=<pass> \
     -DALLOW_CORS_SESSIONS=true \
     -DTEMPLATES_ROOT=public \
     -jar pekaplay-0.5.2-SNAPSHOT-standalone.jar \
     2>pekaplay.`date +"%Y%m%d"`.log
