#!/bin/sh
set -eu
APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "${JAVA_HOME:-}" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    JAVA_CMD=java
fi
exec "$JAVA_CMD" -Dorg.gradle.appname=gradlew -Dgradle.user.home="$APP_DIR/.gradle-user-home" -classpath "$APP_DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
