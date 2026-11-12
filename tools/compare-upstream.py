#!/usr/bin/env python3
"""Report upstream file changes against this fork; local Git reads only, no writes."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent


def git(checkout: Path, *args: str) -> bytes:
    result = subprocess.run(
        ['git', '--no-optional-locks', '-c', 'core.fsmonitor=false',
         '-C', str(checkout), *args],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
        # Partial clones must fail on missing objects instead of fetching them.
        env={**os.environ, 'GIT_NO_LAZY_FETCH': '1', 'GIT_ALLOW_PROTOCOL': '',
             'GIT_TERMINAL_PROMPT': '0'},
    )
    if result.returncode:
        raise ValueError(result.stderr.decode('utf-8', errors='replace').strip())
    return result.stdout


def commit(checkout: Path, ref: str) -> str:
    return git(checkout, 'rev-parse', '--verify', '--end-of-options',
               ref + '^{commit}').decode('ascii').strip()


def changes(checkout: Path, base: str, target: str):
    raw = git(checkout, 'diff', '--raw', '-z', '--no-abbrev', '--no-renames',
              '--no-ext-diff', '--no-textconv', base, target, '--')
    fields = raw.split(b'\0')
    for index in range(0, len(fields) - 1, 2):
        old_mode, new_mode, old_id, new_id, status = fields[index].decode('ascii').split()
        yield (fields[index + 1].decode('utf-8'), old_mode.lstrip(':'),
               new_mode, old_id, new_id, status)


def mapped_path(source: str, path_map: dict) -> str | None:
    for prefix in sorted(path_map, key=len, reverse=True):
        if not prefix or source == prefix or source.startswith(prefix + '/'):
            suffix = source[len(prefix):].lstrip('/')
            return '/'.join(part for part in (path_map[prefix], suffix) if part)
    return None


def blob_id(data: bytes, length: int) -> str:
    algorithm = 'sha1' if length == 40 else 'sha256'
    digest = hashlib.new(algorithm)
    digest.update(b'blob ' + str(len(data)).encode('ascii') + b'\0')
    digest.update(data)
    return digest.hexdigest()


def classify(local: str | None, old_mode: str, new_mode: str,
             old_id: str, new_id: str) -> str:
    if local is None:
        return 'UNMAPPED: inspect relevance and path mapping'
    if '160000' in (old_mode, new_mode):
        return f'DEPENDENCY: review pinned commits {old_id} -> {new_id}'
    if any(mode not in ('000000', '100644', '100755')
           for mode in (old_mode, new_mode)):
        return 'SPECIAL FILE: manual review'
    path = ROOT / local
    if path.is_symlink() or not path.resolve().is_relative_to(ROOT):
        return 'SPECIAL PATH: manual review'
    if path.exists() and not path.is_file():
        return 'DIRECTORY: manual review'
    local_id = blob_id(path.read_bytes(), len(new_id)) if path.exists() else None
    old_id = None if set(old_id) == {'0'} else old_id
    new_id = None if set(new_id) == {'0'} else new_id
    if old_id == new_id and old_mode != new_mode:
        return 'FILE MODE: manual review'
    if local_id == new_id:
        return 'TARGET MATCH: content already matches'
    if local_id == old_id:
        return 'BASE MATCH: review upstream change'
    return 'OVERLAP: local content differs from both snapshots'


def cell(value: str) -> str:
    return (value.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
            .replace('|', '\\|').replace('`', '&#96;')
            .replace('\r', '\\r').replace('\n', '\\n'))


def report(args, component: dict) -> str:
    checkout = args.checkout.resolve(strict=True)
    top = Path(git(checkout, 'rev-parse', '--show-toplevel').decode('utf-8').strip())
    if top.resolve() != checkout:
        raise ValueError('--checkout must be the root of the upstream Git checkout')
    base_ref = (args.base or component['reviewed_through_commit']
                or component['source_baseline_commit'])
    if not base_ref:
        raise ValueError('No recorded baseline. Supply --base <ref> and document its provenance; '
                         'a version string alone does not establish a source baseline.')
    base = commit(checkout, base_ref)
    target = commit(checkout, args.target)
    rows = []
    counts = Counter()
    for source, old_mode, new_mode, old_id, new_id, status in changes(checkout, base, target):
        local = mapped_path(source, component['path_map'])
        assessment = classify(local, old_mode, new_mode, old_id, new_id)
        counts[assessment.split(':', 1)[0]] += 1
        rows.append('| ' + ' | '.join(map(cell, (
            source, local or '(unmapped)', f'{status}: {old_mode} -> {new_mode}', assessment,
        ))) + ' |')
    lines = [
        f'# Upstream comparison: {args.component}', '',
        f'- Expected repository: {component["repository"] or "unknown; establish provenance"}',
        f'- Upstream checkout: {cell(str(checkout))}',
        f'- Base commit: `{base}`',
        f'- Target commit: `{target}`',
        f'- Fork HEAD: `{commit(ROOT, "HEAD")}` (comparison uses current working-tree files)',
        '- Base selection: ' + ('explicit --base; provenance needs review' if args.base
                                else 'recorded review cursor or source baseline'),
        '',
        'This is a content comparison, not a merge plan or proof of behavioral equivalence. '
        'Read docs/UPSTREAM_SYNC.md and carry forward unresolved decision-log entries. '
        'No network, checkout changes, builds, or tests were performed by this helper.',
        '',
        'Only changed upstream paths are listed. Renames appear as deletion/addition pairs. '
        'Matches compare raw file bytes; line endings or Git filters can cause conservative '
        'OVERLAP results. Content matches do not check file permissions or dependent callers.',
        '',
        f'Changed paths: {len(rows)}. ' + ', '.join(f'{key}: {value}' for key, value in sorted(counts.items())),
        '',
        '| Upstream path | Fork path | Change / modes | Assessment |',
        '| --- | --- | --- | --- |',
        *rows,
    ]
    if not rows:
        lines.append('\nNo paths changed between these snapshots; this does not establish fork parity.')
    return '\n'.join(lines) + '\n'


def main() -> int:
    try:
        state = json.loads((ROOT / 'docs/upstream-state.json').read_text(encoding='utf-8'))
        if state['schema_version'] != 1:
            raise ValueError('Unsupported upstream-state.json schema_version')
        parser = argparse.ArgumentParser(description=__doc__)
        parser.add_argument('--component', choices=state['components'], required=True)
        parser.add_argument('--checkout', type=Path, required=True,
                            help='existing local upstream Git checkout; never fetched')
        parser.add_argument('--base', help='explicit base ref, overriding recorded commits')
        parser.add_argument('--target', required=True, help='explicit local release tag or commit')
        args = parser.parse_args()
        output = report(args, state['components'][args.component])
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stdout.write(output)
        return 0
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f'Comparison failed: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
