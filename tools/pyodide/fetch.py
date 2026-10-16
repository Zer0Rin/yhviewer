#!/usr/bin/env python3
"""Reproduce the pinned standard-flavor assets; fail closed on hash changes."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
MANIFEST = json.loads((HERE / "provenance.json").read_text())
DEST = ROOT / "app/src/standard/assets/pyodide"


def checked(data, digest, name):
    if hashlib.sha256(data).hexdigest() != digest:
        raise ValueError(f"SHA-256 mismatch: {name}")
    return data


def download(url, path):
    subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error",
                    "--retry", "3", "--max-time", "300", url, "--output", str(path)], check=True)


def verify():
    for name, info in MANIFEST["files"].items():
        data = (DEST / name).read_bytes()
        checked(data, info["sha256"], name)
        if len(data) != info["bytes"]:
            raise ValueError("Size mismatch: " + name)
    for name, info in MANIFEST["licenses"].items():
        checked((HERE / name).read_bytes(), info["sha256"], name)
        checked((DEST / name).read_bytes(), info["sha256"], "bundled " + name)
    expected = set(MANIFEST["files"]) | set(MANIFEST["licenses"])
    if {p.name for p in DEST.iterdir()} != expected:
        raise ValueError("Unexpected runtime assets")
    print(f"Verified Pyodide {MANIFEST['version']}, CPython {MANIFEST['python']}: "
          f"{len(MANIFEST['files'])} runtime files, {sum(i['bytes'] for i in MANIFEST['files'].values()):,} bytes; licenses verified")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true", help="Verify checked-in files without downloading")
    args = parser.parse_args()
    if args.verify:
        verify()
        return
    with tempfile.TemporaryDirectory(prefix="yhviewer-pyodide-") as work:
        archive = Path(work) / "core.tar.bz2"
        download(MANIFEST["source"], archive)
        checked(archive.read_bytes(), MANIFEST["archive_sha256"], archive.name)
        # Extract only fixed files, never trust archive paths or unpack optional wheels.
        payloads = {}
        with tarfile.open(archive) as tar:
            for name, info in MANIFEST["files"].items():
                data = tar.extractfile("pyodide/" + name).read()
                payloads[name] = checked(data, info["sha256"], name)
        for name, info in MANIFEST["licenses"].items():
            target = Path(work) / name
            download(info["url"], target)
            payloads[name] = checked(target.read_bytes(), info["sha256"], name)
        DEST.mkdir(parents=True, exist_ok=True)
        for name in MANIFEST["files"]:
            (DEST / name).write_bytes(payloads[name])
        for name in MANIFEST["licenses"]:
            (HERE / name).write_bytes(payloads[name])
            (DEST / name).write_bytes(payloads[name])
    verify()


if __name__ == "__main__":
    main()
