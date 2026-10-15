#!/usr/bin/env python3
"""Install and cold-start an R8 APK on a dedicated test device (never uninstalls)."""
import os
from pathlib import Path
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = "io.github.zer0rin.yhviewer"
COMPONENT = PACKAGE + "/com.example.yhviewer.MainActivity"
sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
adb = [str(Path(sdk) / "platform-tools/adb") if sdk else "adb"]
if os.environ.get("ANDROID_SERIAL"):
    adb += ["-s", os.environ["ANDROID_SERIAL"]]


def run(*args):
    return subprocess.check_output(adb + list(args), text=True, timeout=60).strip()


def main():
    if len(sys.argv) != 2:
        raise ValueError("Usage: smoke-release.py APK (dedicated test device only)")
    print(run("install", "-r", sys.argv[1]), flush=True)
    run("shell", "am", "force-stop", PACKAGE)
    print(run("shell", "am", "start", "-n", COMPONENT), flush=True)
    time.sleep(5)
    pid = run("shell", "pidof", PACKAGE)
    if not pid:
        raise RuntimeError("Release process did not survive startup")
    remote = "/sdcard/yhviewer-release-startup.xml"
    try:
        rendered = False
        for _ in range(5):
            try:
                run("shell", "uiautomator", "dump", remote)
                root = ET.fromstring(run("exec-out", "cat", remote))
                app_nodes = [n for n in root.iter("node") if n.get("package") == PACKAGE]
                rendered = any("书架" in n.get("text", "") for n in app_nodes)
                if rendered:
                    break
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ET.ParseError):
                pass  # Accessibility can be unavailable while the first frame is loading.
            time.sleep(2)
        if not rendered:
            raise RuntimeError("Release did not render the native shelf")
        time.sleep(5)
        if run("shell", "pidof", PACKAGE) != pid:
            raise RuntimeError("Release process exited/restarted after rendering")
        print("R8 release startup: native shelf rendered; process remains alive", flush=True)
    finally:
        run("shell", "rm", "-f", remote)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print(run("logcat", "-d", "-t", "100", "-s", "AndroidRuntime:E"), file=sys.stderr)
        raise
