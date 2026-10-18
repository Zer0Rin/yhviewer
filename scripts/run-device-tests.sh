#!/usr/bin/env bash
# Dedicated CI emulator only: never operate on a phone or an ambiguous device list.
set -euo pipefail
if [[ ${GITHUB_ACTIONS:-} != true || ${CI:-} != true ]]; then
  echo 'Device test runner is restricted to GitHub Actions disposable emulators.' >&2
  exit 2
fi
yh_test_devices=()
while IFS= read -r yh_test_device; do
  yh_test_devices+=("$yh_test_device")
done < <(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')
if [[ ${#yh_test_devices[@]} != 1 || ${yh_test_devices[0]:-} != emulator-* ]]; then
  echo 'Device tests require exactly one dedicated emulator and no phone.' >&2
  exit 2
fi
yh_test_serial=${yh_test_devices[0]}
# Compile before waking the screen, so a long cold build cannot relock the test window.
./gradlew :app:assembleStrictDebug :app:assembleStandardDebug :app:assembleStrictDebugAndroidTest :app:assembleStandardDebugAndroidTest --no-configuration-cache --stacktrace
mkdir -p app/build/outputs/ci-device-diagnostics
yh_test_result=0
for yh_test_flavor in Strict Standard; do
  adb -s "$yh_test_serial" shell settings put system screen_off_timeout 2147483647
  adb -s "$yh_test_serial" shell svc power stayon true
  adb -s "$yh_test_serial" shell input keyevent 224
  adb -s "$yh_test_serial" shell locksettings set-disabled true
  adb -s "$yh_test_serial" shell wm dismiss-keyguard
  if ./gradlew ":app:connected${yh_test_flavor}DebugAndroidTest" -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.example.yhviewer.ExternalFixturePack --no-configuration-cache --stacktrace; then
    continue
  fi
  yh_test_result=1
  # Preserve actual display/focus evidence; never turn a failed suite into a green build.
  adb -s "$yh_test_serial" exec-out screencap -p > "app/build/outputs/ci-device-diagnostics/${yh_test_flavor}.png" || true
  adb -s "$yh_test_serial" shell dumpsys window > "app/build/outputs/ci-device-diagnostics/${yh_test_flavor}-window.txt" || true
  adb -s "$yh_test_serial" shell dumpsys power > "app/build/outputs/ci-device-diagnostics/${yh_test_flavor}-power.txt" || true
done
exit "$yh_test_result"
