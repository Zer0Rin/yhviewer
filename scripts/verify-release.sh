#!/usr/bin/env bash
# Verify the signed final artifact, rather than the source Manifest.
set -euo pipefail
if [[ $# != 3 ]]; then
  echo 'Usage: verify-release.sh APK vVERSION APPLICATION_ID' >&2
  exit 2
fi
apk=$1
tag=$2
application_id=$3
[[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Expected a vMAJOR.MINOR.PATCH tag' >&2; exit 1; }
[[ -f "$apk" ]] || { echo 'APK missing' >&2; exit 1; }
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
: "${sdk:?Set ANDROID_HOME or ANDROID_SDK_ROOT}"
tools="$sdk/build-tools/36.0.0"
"$tools/apksigner" verify --verbose --print-certs "$apk"
badging=$("$tools/aapt2" dump badging "$apk")
package_line=$(printf '%s\n' "$badging" | sed -n '/^package: /p')
[[ "$package_line" == *"name='$application_id'"* ]] || { echo 'APK applicationId mismatch' >&2; exit 1; }
[[ "$package_line" == *"versionName='${tag#v}'"* ]] || { echo 'APK versionName does not match tag' >&2; exit 1; }
permissions=$("$tools/aapt2" dump permissions "$apk")
printf '%s\n' "$package_line" "$permissions"
if [[ "$permissions" == *"android.permission.INTERNET"* ]]; then
  echo 'Release rejected: final APK declares INTERNET' >&2
  exit 1
fi
printf 'Release artifact verified: %s\n' "$apk"
