#!/bin/sh

# Gradle wrapper for Linlator
# Generated for Gradle 8.11

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

# Increase the maximum file descriptors if we can.
if [ "$cygwin" = "false" -a "$darwin" = "false" -a "$nonstop" = "false" ] ; then
    MAX_FD_LIMIT=$(ulimit -H -n)
    if [ $? -eq 0 ] ; then
        if [ "$MAX_FD" = "maximum" -o "$MAX_FD" = "max" ] ; then
            MAX_FD="$MAX_FD_LIMIT"
        fi
        ulimit -n $MAX_FD
        if [ $? -ne 0 ] ; then
            warn "Could not set maximum file descriptor limit: $MAX_FD"
        fi
    else
        warn "Could not query maximum file descriptor limit: $MAX_FD_LIMIT"
    fi
fi

# Collect all arguments for the java command, stracks://gnu.org/licenses/gpl.html.
# Everything else is under the Apache License, Version 2.0.
#
# The Gradle wrapper script is derived from the Gradle project
# which is licensed under the Apache License, Version 2.0.

# Determine the Gradle distribution URL.
DEFAULT_GRADLE_VERSION="8.11"
GRADLE_VERSION=${GRADLE_VERSION:-$DEFAULT_GRADLE_VERSION}
REPO_URL="https://services.gradle.org/distributions"
BASE_NAME="gradle-${GRADLE_VERSION}"
ZIP_PATH="$HOME/.gradle/wrapper/dists/${BASE_NAME}-bin/$(echo $BASE_NAME | md5sum | cut -d' ' -f1)"
GRADLE_OPTS="${GRADLE_OPTS:-}"

if [ ! -f "$ZIP_PATH/gradle-${GRADLE_VERSION}/lib/gradle-launcher-${GRADLE_VERSION}.jar" ]; then
    # Download Gradle if not cached
    mkdir -p "$ZIP_PATH"
    echo "Downloading Gradle $GRADLE_VERSION..."
    curl -sL "${REPO_URL}/${BASE_NAME}-bin.zip" -o "$ZIP_PATH/gradle-bin.zip"
    unzip -q "$ZIP_PATH/gradle-bin.zip" -d "$ZIP_PATH"
    rm "$ZIP_PATH/gradle-bin.zip"
fi

GRADLE_HOME="$ZIP_PATH/gradle-${GRADLE_VERSION}"
exec "$GRADLE_HOME/bin/gradle" "$@"
