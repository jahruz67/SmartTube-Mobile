#!/usr/bin/env python3
"""Apply the test-tooling fixes verified on Windows; retain production dependencies."""
import argparse
from pathlib import Path

COMMON_BEFORE = "    testImplementation 'org.robolectric:robolectric:' + robolectricVersion\n"
COMMON_AFTER = COMMON_BEFORE + """    // The mobile regression tests run on the PC's JDK 17.
    testStmobileImplementation 'org.robolectric:robolectric:4.10.3'
"""
APP_BEFORE = "    androidTestImplementation 'androidx.test.espresso:espresso-core:' + espressoVersion\n"
APP_AFTER = APP_BEFORE + """    // Mobile test activities must declare exported state for target SDK 31+.
    androidTestStmobileImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestStmobileImplementation 'androidx.test:runner:1.5.2'
    androidTestStmobileImplementation 'androidx.test:rules:1.5.0'
    androidTestStmobileImplementation 'androidx.test.espresso:espresso-core:3.5.1'
"""
CACHE_DELETE = '@rem My tweak. Cleanup cache (fix build errors)\nrd /s /q "%DIRNAME%\\.gradle"\n\n'


def apply(root: Path, check_only: bool) -> None:
    changes = []
    for relative, before, after in (
        ('common/build.gradle', COMMON_BEFORE, COMMON_AFTER),
        ('smarttubetv/build.gradle', APP_BEFORE, APP_AFTER),
        ('gradlew.bat', CACHE_DELETE, ''),
    ):
        path = root / relative
        original = path.read_text(encoding='utf-8')
        if after and original.count(after) == 1:
            result = original
        elif relative == 'gradlew.bat' and before not in original:
            if 'rd /s /q' in original.lower():
                raise ValueError('Unexpected cache deletion in Windows wrapper')
            result = original
        else:
            if original.count(before) != 1:
                raise ValueError(f'Unexpected integration anchor: {relative}')
            result = original.replace(before, after, 1)
        changes.append((path, original, result))
    for path, original, result in changes:
        print(f'{"CHECK" if check_only else "PATCH" if original != result else "UNCHANGED"}: {path}')
        if not check_only and original != result:
            path.write_text(result, encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source_dir', type=Path)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    try:
        apply(args.source_dir.resolve(), args.check)
    except (OSError, ValueError) as error:
        parser.exit(1, f'ERROR: {error}\n')
