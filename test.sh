#!/bin/sh
adb logcat -c
adb logcat -v time -s 'AndroidRuntime:*' 'GRKeyboard:*' | tee run.log


