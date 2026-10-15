#!/usr/bin/env bash
# Check merged APK permissions. Never infer a flavor from a filename.
set -euo pipefail
[[ $# == 2 && ( "$2" == strict || "$2" == standard ) ]] || { echo 'Usage: verify-apk-permissions.sh APK strict|standard' >&2; exit 2; }
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
: "${sdk:?Set ANDROID_HOME or ANDROID_SDK_ROOT}"
permissions=$("$sdk/build-tools/36.0.0/aapt2" dump permissions "$1")
has_internet=false
if [[ "$permissions" == *"android.permission.INTERNET"* ]]; then has_internet=true; fi
if [[ "$2" == strict && "$has_internet" == true || "$2" == standard && "$has_internet" == false ]]; then
  echo "Rejected: $2 APK has INTERNET=$has_internet" >&2
  exit 1
fi
printf '%s APK permissions verified (INTERNET=%s)\n' "$2" "$has_internet"
