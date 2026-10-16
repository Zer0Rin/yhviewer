#!/usr/bin/env python3
"""Check Python flavor boundaries and exact upstream bytes in the final APK."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

def verify(apk, edition):
    if edition not in ("strict", "standard"):
        raise ValueError("Expected strict or standard")
    manifest = json.loads((Path(__file__).resolve().parent.parent / "tools/pyodide/provenance.json").read_text())
    with zipfile.ZipFile(apk) as archive:
        entries = [name for name in archive.namelist() if name.startswith("assets/pyodide/")]
        if edition == "strict":
            if entries:
                raise ValueError("Strict APK must contain no Pyodide assets")
        else:
            required = {name: info["sha256"] for name, info in manifest["files"].items()}
            required.update({name: info["sha256"] for name, info in manifest["licenses"].items()})
            expected = {"assets/pyodide/" + name for name in required}
            if set(entries) != expected or len(entries) != len(expected):
                raise ValueError("Unexpected or missing Standard runtime assets")
            for name, digest in required.items():
                actual = hashlib.sha256(archive.read("assets/pyodide/" + name)).hexdigest()
                if actual != digest:
                    raise ValueError("Incorrect bundled runtime asset: " + name)
    print(f"{edition} APK Python assets verified")

if __name__ == "__main__":
    try:
        if len(sys.argv) != 3:
            raise ValueError("Usage: verify-python-assets.py APK strict|standard")
        verify(sys.argv[1], sys.argv[2])
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
