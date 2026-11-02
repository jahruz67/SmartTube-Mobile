#!/usr/bin/env python3
"""Validate produced APK files; does not claim behavioral/device verification."""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile


def inspect(apk: Path, expected_package: str) -> dict:
    if not apk.is_file():
        raise ValueError(f'No APK found at {apk}')
    with zipfile.ZipFile(apk) as z:
        names = set(z.namelist())
        needed = ('AndroidManifest.xml', 'resources.arsc', 'classes.dex')
        missing = [name for name in needed if name not in names]
        if missing:
            raise ValueError(f'APK missing mandatory files: {missing}')
        if z.testzip() is not None:
            raise ValueError('APK contains a corrupt ZIP entry')
        if not any(name.startswith('lib/') and name.endswith('.so') for name in names):
            print('NOTE: APK has no native .so entries; inspect ABI packaging if playback fails.')
    sha = hashlib.sha256()
    with apk.open('rb') as f:
        for part in iter(lambda: f.read(1024 * 1024), b''):
            sha.update(part)
    result = {'apk': str(apk), 'sha256': sha.hexdigest(), 'bytes': apk.stat().st_size}
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '')
    buildtools = sorted((sdk / 'build-tools').glob('*'), reverse=True) if sdk.is_dir() else []
    aapt_name = 'aapt.exe' if os.name == 'nt' else 'aapt'
    signer_name = 'apksigner.bat' if os.name == 'nt' else 'apksigner'
    aapt = next((d / aapt_name for d in buildtools if (d / aapt_name).is_file()), None)
    apksigner = next((d / signer_name for d in buildtools if (d / signer_name).is_file()), None)
    if aapt:
        output = subprocess.run([str(aapt), 'dump', 'badging', str(apk)],
                                capture_output=True, text=True, check=True).stdout
        package = re.search(r"^package: name='([^']+)'", output, re.MULTILINE)
        if not package or package.group(1) != expected_package:
            raise ValueError('Unexpected package ID: ' + (package.group(1) if package else 'missing'))
        result['package'] = package.group(1)
        sdkline = re.search(r"^targetSdkVersion:'([^']+)'", output, re.MULTILINE)
        result['targetSdkVersion'] = sdkline.group(1) if sdkline else 'unknown'
    else:
        print('NOTE: Android aapt unavailable; package identity not verified.')
    if apksigner:
        signed = subprocess.run([str(apksigner), 'verify', '--verbose', '--print-certs', str(apk)],
                                capture_output=True, text=True)
        if signed.returncode != 0:
            raise ValueError('APK signature verification failed: ' + signed.stderr.strip())
        result['signature'] = 'valid (see apksigner report for certificate identity)'
        print(signed.stdout)
    else:
        print('NOTE: apksigner unavailable; APK certificate unverified.')
    return result


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('apk', type=Path, nargs='+')
    p.add_argument('--package', default='com.codesculptor.smartertube')
    args = p.parse_args()
    try:
        for filename in args.apk:
            print(inspect(filename, args.package))
    except (ValueError, zipfile.BadZipFile, subprocess.CalledProcessError) as exc:
        sys.exit(f'ERROR: {exc}')
