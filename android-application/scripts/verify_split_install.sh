#!/usr/bin/env bash
set -euo pipefail

# Validates an Android App Bundle (.aab) by generating device-tailored split APKs,
# extracting connected device specifications via adb, and installing locally.
# Requires: bundletool.jar (or a `bundletool` on PATH), adb, and java in PATH.
#
# Device-dependent — needs a connected device or running emulator, so this is a
# local pre-release step, not (yet) wired into CI.

BUNDLETOOL_JAR="bundletool.jar"

if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <path-to-app-release.aab>"
    exit 1
fi

AAB_FILE="$1"

if [[ ! -f "$AAB_FILE" ]]; then
    echo "[-] Error: AAB file not found: $AAB_FILE"
    exit 1
fi

if ! command -v adb &>/dev/null; then
    echo "[-] Error: 'adb' utility not found in PATH."
    exit 1
fi

if ! command -v java &>/dev/null; then
    echo "[-] Error: 'java' runtime not found in PATH."
    exit 1
fi

if [[ ! -f "$BUNDLETOOL_JAR" ]]; then
    echo "[!] Warning: '$BUNDLETOOL_JAR' not found in current directory."
    echo "[*] Attempting to find bundletool globally or via environment variables..."
    if command -v bundletool &>/dev/null; then
        BUNDLETOOL_CMD="bundletool"
    else
        echo "[-] Error: Place bundletool.jar in this directory or install bundletool."
        exit 1
    fi
else
    BUNDLETOOL_CMD="java -jar $BUNDLETOOL_JAR"
fi

DEVICE_COUNT=$(adb devices | grep -w "device" | wc -l)
if [[ $DEVICE_COUNT -eq 0 ]]; then
    echo "[-] Error: No active Android devices connected via ADB."
    exit 1
fi

WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

SPEC_FILE="$WORK_DIR/device-spec.json"
APKS_FILE="$WORK_DIR/app-release.apks"

echo "[*] Step 1: Extracting target device specifications via adb..."
$BUNDLETOOL_CMD get-device-spec --output="$SPEC_FILE"

echo "[*] Step 2: Generating tailored split APKs from AAB..."
$BUNDLETOOL_CMD build-apks \
    --bundle="$AAB_FILE" \
    --output="$APKS_FILE" \
    --device-spec="$SPEC_FILE" \
    --mode=default

echo "[*] Step 3: Verifying estimated APK size and configuration split profile..."
$BUNDLETOOL_CMD get-size total --apks="$APKS_FILE"

echo "[*] Step 4: Installing generated split APKs onto connected device..."
$BUNDLETOOL_CMD install-apks --apks="$APKS_FILE"

echo "[+] Success: Split APKs generated, verified against device specs, and installed."
exit 0
