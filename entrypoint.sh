#!/bin/sh

APP_NAME=orchestration-service
APP_HOME=/opt/${APP_NAME}
USER=os_user

cd ${APP_HOME}

keytool -genkey \
        -keystore ${APP_HOME}/orchestration-service.keystore \
        -keyalg RSA -keysize 4096 -validity 3650 -alias orchestration-service \
        -dname "cn=orchestration-service, ou=orchestration-service, o=orchestration-service, c=orchestration-service" \
        -storepass changeit -keypass changeit

chown -R ${USER}.${USER} ${APP_HOME}/orchestration-service.keystore

sh -c "java -jar ./${APP_NAME}.jar \"$@\"" --
