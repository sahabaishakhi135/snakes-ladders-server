#!/bin/bash

#URL=https://2978-223-226-55-177.ngrok.io
URL=http://localhost:8090

ROOM_TYPE=4 # 4 player snakes and ladders


curl -X POST -H "Content-Type: application/json" \
     -d "{\"tableType\": \"${ROOM_TYPE}\"}" \
    ${URL}/admin/games

