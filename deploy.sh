#!/bin/bash
# Usage: deploy.sh <ip:port>
# Example: deploy.sh 10.111.111.90:38541
export PATH=$HOME/android-sdk/platform-tools:$PATH
adb connect "$1" && sleep 2 && adb install -r /home/mew/repos/Saki.Android/app/build/outputs/apk/debug/app-debug.apk
