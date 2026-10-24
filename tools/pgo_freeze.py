"""Freezes a trained LLVM profile into the portable text and manifest that
shared/cmake/KairoPgo.cmake builds with (Kairo68#6, KairoDos#70).

The indexed profile is exported as text with the NDK's llvm-profdata, the checkout root in
function names is replaced with @KAIRO_SOURCE@/, and newlines are made LF with one final newline,
so the text's hash is stable in Git. Counts and hashes are unchanged. --sparse leaves out functions
that never ran, which LLVM then optimizes as it would without a profile.

usage: pgo_freeze.py --profdata <indexed> --llvm-profdata <exe> --root <checkout> --text <out>
                     --manifest <out> --ndk <version> [--sparse] [--note <text>] <raw profile>...
"""
import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--profdata', required=True, type=Path, help='the merged indexed profile')
    parser.add_argument('--llvm-profdata', required=True, type=Path, help="the NDK's llvm-profdata")
    parser.add_argument('--root', required=True, type=Path, help='the checkout the training build was built from')
    parser.add_argument('--text', required=True, type=Path, help='the portable text profile to write')
    parser.add_argument('--manifest', required=True, type=Path, help='its manifest to write')
    parser.add_argument('--ndk', required=True, help='the NDK version that built the training build')
    parser.add_argument('--sparse', action='store_true', help='leave out functions that never ran')
    parser.add_argument('--note', default='', help='how the profile was trained')
    parser.add_argument('raw', nargs='+', type=Path, help='the raw profiles merged into --profdata')
    args = parser.parse_args()

    version = subprocess.run([str(args.llvm_profdata), '--version'], check=True, capture_output=True, text=True).stdout
    match = re.search(r'LLVM version (\d+\.\d+\.\d+)', version)
    if not match:
        raise SystemExit(f'cannot read the LLVM version from: {version}')

    command = [str(args.llvm_profdata), 'merge', '--text']
    if args.sparse:
        command.append('--sparse')
    exported = subprocess.run(command + [str(args.profdata), '-o', '-'], check=True, capture_output=True).stdout
    text = exported.decode('utf-8').replace('\r\n', '\n')
    root = args.root.resolve().as_posix().rstrip('/') + '/'
    rewritten = text.count(root)
    if rewritten == 0:
        raise SystemExit(f'no function names hold the root {root}: was the training build made from this checkout?')
    text = text.replace(root, '@KAIRO_SOURCE@/').rstrip('\n') + '\n'
    args.text.parent.mkdir(parents=True, exist_ok=True)
    args.text.write_bytes(text.encode('utf-8'))

    revision = subprocess.run(['git', '-C', str(args.root), 'rev-parse', 'HEAD'], check=True,
                              capture_output=True, text=True).stdout.strip()
    manifest = {
        'format': 'LLVM IR instrumentation profile text' + (', sparse' if args.sparse else ', dense counts'),
        'compilerVersion': match.group(1),
        'ndkProvenance': args.ndk,
        'abi': 'arm64-v8a',
        'sourceRevision': revision,
        'portableTextSha256': sha256(args.text),
        'functionRecords': text.count('\n# Func Hash:\n'),
        'pathOccurrencesRewritten': rewritten,
        'normalization': 'The checkout root in function names is @KAIRO_SOURCE@/ (shared/cmake/KairoPgo.cmake puts '
                         'the actual root back before indexing). LF newlines, one final newline.',
        'training': args.note,
        'inputs': [{'name': raw.stem, 'rawProfileSha256': sha256(raw), 'weight': 1} for raw in args.raw],
    }
    args.manifest.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f"wrote {args.text} ({manifest['functionRecords']} functions) and {args.manifest}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
