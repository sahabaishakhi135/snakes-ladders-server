#!/bin/bash

export API_KEY=bb4b5b1c-b922-48e9-8220-3027a2ad0b6f
export SECRET_KEY=41abe20f-8ad9-4f1b-bedc-742556f0890c

export DIRECTOR_SERVICE=http://localhost:9091
export TURN_TIMEOUT=15
export PORT=8090
export NREPL_PORT=7575
export HOST_ID=1337
export ALLOW_CORS_SESSIONS=true
#export TABLES_JSON=${DIRECTOR_SERVICE}/tables
export TABLES_JSON=tables.json
export SITES_EDN=sites.edn
export HOST=127.0.0.1:8090
#export KAFKA_URL=127.0.0.1:9092

#lein repl
lein trampoline run
#java -Dlogback.configurationFile=./resources/logback.xml -jar target/snakes-and-ladders-*-standalone.jar
