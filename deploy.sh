#!/bin/bash

if [ "$#" -ne 1 ]; then
    echo "Target server is missing"
    exit
fi

if [ $1 == "tg" ]; then
    jar=$(ls target/*standalone.jar | sed 's/target\///g')
    echo "Sharing the current build on Telegram"
    exec ~/bin/Telegram/Telegram -sendpath $PWD/target/$jar && \
    exit
fi

echo "Building uberjar"
lein uberjar

jar=$(ls target/*standalone.jar | sed 's/target\///g')
if [ -z $jar ]; then
    echo "Build failed. Aborting..."
    exit
fi

echo "Deploying $jar"

#if [ $1 == "stg" ]; then
#    scp target/$jar root@stg.peka:~/turup/
#    ssh root@stg.peka "cd ~/turup && ln -sf $jar ludo.jar && systemctl --user restart ludo"
#fi

if [ $1 == "stg" ]; then
    scp target/$jar root@stg2.josh:~/josh/
    ssh root@stg2.josh "cd ~/josh && ln -sf $jar snakesladders.jar && systemctl restart snakesladders && bash ../mogambot Deployed\ $jar"
fi

if [ $1 == "aio-prod" ]; then
    HOST=$AIO_snakesladders
    scp -i ~/.ssh/PAX-AIO.pem target/$jar ubuntu@$HOST:~/
    ssh -i ~/.ssh/PAX-AIO.pem ubuntu@$HOST "ln -sf $jar snakesladders.jar && sudo systemctl --user restart snakesladders && bash mogambot Deployed\ $jar"
fi

if [ $1 == "aio2-prod" ]; then
    aws s3 cp target/$jar s3://vume-deployment-builds --acl public-read
fi
