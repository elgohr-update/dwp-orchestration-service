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

sh -c "java -Dhttp.proxyHost='${PROXY_HOST}' -Dhttp.proxyPort='3128' -Dhttp.nonProxyHosts='${NON_PROXY_HOSTS}' -Dhttps.proxyHost='${PROXY_HOST}' -Dhttps.proxyPort='3128' -jar ./${APP_NAME}.jar \"$@\"" --
