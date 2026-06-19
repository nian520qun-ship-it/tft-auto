#!/bin/sh
# Gradle wrapper script
# 下载 gradle wrapper jar 后即可使用

# Determine the project base directory
APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")" && pwd)

# Use JAVA_HOME if set, otherwise try PATH
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Check Java version
if ! command -v "$JAVACMD" >/dev/null 2>&1 ; then
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found."
    echo "Please set JAVA_HOME to point to a JDK 11+ installation."
    exit 1
fi

# Gradle wrapper jar
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Download wrapper jar if not exists
if [ ! -f "$CLASSPATH" ]; then
    echo "Downloading Gradle wrapper..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    if command -v curl >/dev/null 2>&1; then
        curl -sL "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar" -o "$CLASSPATH"
    elif command -v wget >/dev/null 2>&1; then
        wget -q "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar" -O "$CLASSPATH"
    else
        echo "Please download gradle-wrapper.jar manually or use Android Studio"
        exit 1
    fi
fi

exec "$JAVACMD" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
