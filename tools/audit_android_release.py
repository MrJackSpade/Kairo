#!/usr/bin/env python3
"""Common APK/AAB payload and ELF alignment checks (not a runtime/license audit)."""
import argparse
import hashlib
import struct
import zipfile

# A bundle keeps the baseline profile as metadata; bundletool installs it as the APK's assets/dexopt/.
BUNDLE_PROFILES = 'BUNDLE-METADATA/com.android.tools.build.profiles/'


def inspect(path, prefix, allow_art=False):
    with zipfile.ZipFile(path) as archive:
        payload = {}
        native = 0
        for name in archive.namelist():
            if prefix == 'base/' and name.startswith(BUNDLE_PROFILES) and not name.endswith('/'):
                payload['assets/dexopt/' + name.removeprefix(BUNDLE_PROFILES)] = hashlib.sha256(archive.read(name)).hexdigest()
                continue
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
        # The app's own notices and policy, and the frontend's notices About -> Licenses appends.
        for name in ('assets/THIRD_PARTY_NOTICES.txt', 'assets/PRIVACY_POLICY.txt', 'assets/FRONTEND_NOTICES.txt',
                     'assets/NDK_NOTICES.txt', 'assets/SLANG_RUNTIME_NOTICES.txt', 'assets/ANDROIDX_NOTICES.txt'):
            assert name in payload, f'Missing {name}'
        artwork = {n for n in payload if n.startswith('assets/art/')}
        if allow_art:
            assert any(n.endswith(('.webp', '.png', '.jpg')) for n in artwork), 'No bundled images'
            print(f'{path}: {len(artwork)} bundled artwork files')
        else:
            assert not artwork, 'Unexpected bundled artwork'
        print(f'{path}: {native} ARM64 libraries, 16 KB ELF alignment verified')
        return payload


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    parser.add_argument('aab')
    parser.add_argument('with_images_apk', nargs='?')
    args = parser.parse_args()
    common = inspect(args.apk, '')
    assert {n:v for n,v in common.items() if not n.startswith('assets/catalog/art.nsfw.')} == inspect(args.aab, 'base/'), 'APK/AAB payload mismatch'
    if args.with_images_apk:
        with_images = inspect(args.with_images_apk, '', allow_art=True)
        # The compiled baseline profile records its own variant's dex checksums, so it differs with the dex.
        derived = ('assets/art/', 'assets/dexopt/')
        assert {n: v for n, v in with_images.items() if not n.startswith(derived)} == {
            n: v for n, v in common.items() if not n.startswith(derived)}, 'Image variant changes non-artwork payload'
    print('APK and Play bundle share code and core assets; only the optional artwork catalog differs')
