#!/usr/bin/env python3
"""Install Android instrumentation smoke test only into the stmobile test source set."""
from pathlib import Path
import argparse

ROOT = Path(__file__).resolve().parent.parent
REL = Path('smarttubetv/src/androidTestStmobile/java/dev/smarttubemobile/tests/MobileBottomNavigationInstrumentationTest.java')


def install(source_dir: Path) -> None:
    original = ROOT / 'overlays/MobileBottomNavigationInstrumentationTest.java'
    dst = source_dir / REL
    payload = original.read_bytes()
    if dst.exists() and dst.read_bytes() != payload:
        raise ValueError(f'Unexpected existing test file: {dst}')
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_bytes(payload)
    print(f'Installed instrumented resource smoke test: {REL}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source_dir', type=Path)
    args = parser.parse_args()
    install(args.source_dir.resolve())
