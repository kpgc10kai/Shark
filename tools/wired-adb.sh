#!/usr/bin/env bash
# Activate Shark's privileged backend over wired ADB.
#
# Requirements:
#   - USB debugging enabled and the PC authorized
#   - Shark installed (package com.shark.kty)
#   - the Shark app running in the foreground or background
#
# This starts the Shizuku-compatible ShellServer as the shell user. Shark
# detects it as the Shizuku backend and can run the root flow without
# Wireless debugging pairing.
set -euo pipefail

serial="${ANDROID_SERIAL:-}"
adb_args=()
if [ -n "$serial" ]; then
    adb_args=(-s "$serial")
fi

adb "${adb_args[@]}" wait-for-device
if ! adb "${adb_args[@]}" shell pm path com.shark.kty >/dev/null 2>&1; then
    echo "com.shark.kty is not installed" >&2
    exit 1
fi

apk=$(adb "${adb_args[@]}" shell pm path com.shark.kty | sed -n 's/^package://p' | tr -d '\r' | head -n 1)
if [ -z "$apk" ]; then
    echo "could not resolve the Shark APK path" >&2
    exit 1
fi

echo "starting ShellServer from $apk"
adb "${adb_args[@]}" shell \
    "CLASSPATH='$apk' setsid app_process /system/bin --nice-name=shark_shell com.shark.kty.shell.ShellServer </dev/null >/dev/null 2>&1 &"

sleep 1
if adb "${adb_args[@]}" shell pidof shark_shell >/dev/null 2>&1; then
    echo "ShellServer started; open Shark and it should show Shizuku authorized"
else
    echo "ShellServer did not start; check USB debugging authorization" >&2
    exit 1
fi
