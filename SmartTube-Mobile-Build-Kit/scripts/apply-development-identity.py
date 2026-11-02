#!/usr/bin/env python3
"""Isolate the experimental SmartTube Mobile build from SmarterTube release IDs.

Changes only the existing stmobile product flavor and mobile UI resources.
Disables inherited release checks and fork-operated usage/crash telemetry.
The public upstream/fork attribution links remain intact.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

GRADLE = Path('smarttubetv/build.gradle')
STRINGS = Path('smarttubetv/src/stmobile/res/values/strings.xml')
HOME_ACTIVITY = Path('smarttubetv/src/stmobile/java/com/liskovsoft/smartyoutubetv2/mobile/ui/browse/MobileBrowseActivity.java')
ABOUT_ACTIVITY = Path('smarttubetv/src/stmobile/java/com/liskovsoft/smartyoutubetv2/mobile/ui/about/MobileAboutActivity.java')
MANIFEST = Path('smarttubetv/src/stmobile/AndroidManifest.xml')
ROOT = Path(__file__).resolve().parent.parent
ICON_SIZES = ('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi')
NEW_ID = 'dev.smarttubemobile.unofficial'
OLD_ID = 'com.codesculptor.smartertube'
NAME = 'SmartTube Mobile Dev'


def once(source: str, old: str, new: str, label: str) -> str:
    if old == new:
        raise ValueError(f'{label}: empty change')
    if new in source and old not in source:
        return source
    if source.count(old) != 1:
        raise ValueError(f'{label}: expected one source anchor, got {source.count(old)}')
    return source.replace(old, new, 1)


def patch_gradle(s: str) -> str:
    # Restrict modifications to the stmobile stanza; never change TV flavors.
    begin = s.index('        stmobile {')
    end = s.index('\n        }\n    }\n}', begin)
    section = s[begin:end]
    section = once(section,
                   f'applicationId "{OLD_ID}"',
                   f'applicationId "{NEW_ID}"', 'mobile app ID')
    section = once(section,
                   'versionName "v0.9.10-beta.1+st32.68"',
                   'versionName "v0.1.0-alpha.3+st32.68"', 'dev version name')
    # Retain versionCode as source provenance. Separate app ID means no upstream upgrade conflict.
    return s[:begin] + section + s[end:]


def patch_strings(s: str) -> str:
    s = once(s,
             '<string name="app_name" translatable="false">SmarterTube</string>',
             f'<string name="app_name" translatable="false">{NAME}</string>', 'launcher label')
    s = once(s,
             '<string name="mobile_about_title">About SmarterTube</string>',
             '<string name="mobile_about_title">About SmartTube Mobile Dev</string>', 'about title')
    s = once(s,
             '<string name="mobile_about_drawer_entry">About SmarterTube</string>',
             '<string name="mobile_about_drawer_entry">About this app</string>', 'drawer title')
    s = once(s,
             '<string name="mobile_about_body">SmarterTube is a phone and tablet fork of SmartTube by yuliskov. The YouTube client code is upstream\\\'s work, merged from upstream on every release. This fork adds the phone/tablet UI on top.\\n\\nLicensed under MIT, same as upstream.</string>',
             '<string name="mobile_about_body">Independent experimental build. Mobile interface based on SmarterTube by CodeSculptor; engine from SmartTube by yuliskov. Not affiliated with either developer. Original MIT attribution and license remain in the source project.\\n\\nDevelopment build; see included reports for verified features.</string>',
             'derivative attribution')
    # Prevent this separately branded build from sending even opt-in stats to the original fork.
    s = once(s,
             '<string name="mobile_stats_endpoint" translatable="false">https://smartertube-stats.codesculptor.workers.dev</string>',
             '<string name="mobile_stats_endpoint" translatable="false"></string>', 'telemetry endpoint')
    ET.fromstring(s)
    return s


def patch_home_activity(s: str) -> str:
    return once(s,
                '        dialogs.add(done -> LaunchUpdateNotices.onHomeCreated(this, done));',
                '        // Experimental build: never offer an incompatible upstream-signed APK.\n'
                '        // No automatic update source is configured for this independent package.',
                'upstream update prompt')


def patch_about_activity(s: str) -> str:
    return once(s,
                '        findViewById(R.id.btn_check_updates).setOnClickListener(v -> checkForUpdates());',
                '        // Upstream releases use another package/signature and cannot update this build.\n'
                '        findViewById(R.id.btn_check_updates).setVisibility(android.view.View.GONE);',
                'incompatible manual update option')


def patch_manifest(s: str) -> str:
    s = once(s, 'android:icon="@mipmap/ic_launcher"',
             'android:icon="@mipmap/smarttube_mobile_dev_icon"', 'unique launcher icon')
    s = once(s, 'android:roundIcon="@mipmap/ic_launcher_round"',
             'android:roundIcon="@mipmap/smarttube_mobile_dev_icon"', 'unique round icon')
    ET.fromstring(s)
    return s


def apply(root: Path, check_only: bool = False) -> None:
    targets = ((GRADLE, patch_gradle), (STRINGS, patch_strings),
               (HOME_ACTIVITY, patch_home_activity), (ABOUT_ACTIVITY, patch_about_activity),
               (MANIFEST, patch_manifest))
    changes = []
    # Transactional preflight: refuse every write if one target doesn't match.
    for rel, func in targets:
        full = root / rel
        if not full.is_file():
            raise ValueError(f'Missing source file: {full}')
        before = full.read_text(encoding='utf-8')
        after = func(before)
        changes.append((full, before, after, rel))
    icon_changes = []
    icon_sources = [(density + '.png', 'mipmap-' + density) for density in ICON_SIZES]
    icon_sources.append(('smarttube_mobile_dev_icon.xml', 'mipmap-anydpi'))
    for filename, folder in icon_sources:
        src = ROOT / 'overlays/dev-icons' / filename
        dst = root / 'smarttubetv/src/stmobile/res' / folder / ('smarttube_mobile_dev_icon' + src.suffix)
        payload = src.read_bytes()
        if dst.exists() and dst.read_bytes() != payload:
            raise ValueError(f'Existing launcher icon collision: {dst}')
        icon_changes.append((dst, payload))
    for full, before, after, rel in changes:
        print(f'{"WOULD PATCH" if check_only else "PATCH" if before != after else "UNCHANGED"}: {rel}')
        if not check_only and before != after:
            full.write_text(after, encoding='utf-8')
    for dst, payload in icon_changes:
        print(f'{"WOULD INSTALL" if check_only else "INSTALL"}: {dst.relative_to(root)}')
        if not check_only:
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_bytes(payload)
    print('Identity isolated; distinct launcher icon; inherited telemetry and incompatible updates disabled.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source_dir', type=Path)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    try:
        apply(args.source_dir.resolve(), args.check)
    except (OSError, ValueError, ET.ParseError) as exc:
        sys.exit(f'ERROR: {exc}')
