#!/usr/bin/env python3
"""Common APK/AAB payload and ELF alignment checks (not a runtime/license audit)."""
import argparse
import hashlib
import struct
import zipfile


def inspect(path, prefix):
    with zipfile.ZipFile(path) as archive:
        payload = {}
        native = 0
        for name in archive.namelist():
            relative = name.removeprefix(prefix)
            if not name.startswith(prefix) or name.endswith('/'):
                continue
            if not relative.startswith(('assets/', 'lib/')):
                continue
            data = archive.read(name)
            payload[relative] = hashlib.sha256(data).hexdigest()
            if relative.startswith('lib/'):
                assert relative.startswith('lib/arm64-v8a/'), name
                assert data[:6] == b'\x7fELF\x02\x01', name
                assert struct.unpack_from('<H', data, 18)[0] == 183, name
                phoff = struct.unpack_from('<Q', data, 32)[0]
                entsize, count = struct.unpack_from('<HH', data, 54)
                loads = 0
                for i in range(count):
                    kind, _, offset, vaddr, _, _, _, alignment = struct.unpack_from(
                        '<IIQQQQQQ', data, phoff + i * entsize)
                    if kind == 1:
                        loads += 1
                        assert alignment >= 16384, f'{name}: ELF alignment {alignment}'
                        assert (vaddr - offset) % 16384 == 0, name
                assert loads, name
                native += 1
        assert native, f'No native libraries in {path}'
        for name in ('assets/THIRD_PARTY_NOTICES.txt', 'assets/PRIVACY_POLICY.txt'):
            assert name in payload, f'Missing {name}'
        assert not any(n.startswith('assets/art/') for n in payload), 'Unreviewed bundled artwork'
        print(f'{path}: {native} ARM64 libraries, 16 KB ELF alignment verified')
        return payload


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    parser.add_argument('aab')
    args = parser.parse_args()
    assert inspect(args.apk, '') == inspect(args.aab, 'base/'), 'APK/AAB payload mismatch'
    print('APK and Play bundle have identical assets and native libraries')
