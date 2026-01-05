#!/bin/bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME=/Users/nitishbhardwaj/Library/Android/sdk
export PATH=$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH

cd /Users/nitishbhardwaj/Desktop/Weelo/android_kotlin

# Download gradle wrapper if not exists
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    mkdir -p gradle/wrapper
    curl -L https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar
fi

# Make gradlew executable
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug --stacktrace
