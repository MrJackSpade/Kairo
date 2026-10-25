#!/usr/bin/env python3
"""Audits a Kairo app's release files (app-release.yml): the release APK, the Play bundle and the
APK with bundled artwork share code and assets apart from the adult catalog parts and the artwork;
they carry the app's own assets as committed, its native library and the notices, 16 KB aligned
ARM64 libraries, and no firmware or disk images."""
import argparse
import hashlib
import re
import struct
import subprocess
import zipfile
from pathlib import Path

# The adult catalog parts (art.nsfw, data.nsfw), which Play builds leave out.
ADULT_PART = re.compile(r'assets/catalog/[^/]+\.nsfw\.')

# Machine firmware and game disk images, which no Kairo app ships: BIOS images; Commodore's
# KERNAL, BASIC, character and drive ROMs under the names players and VICE give them; and every
# system's disk, tape, cartridge and program images. Free system software an app carries, such as
# KairoST's EmuTOS image or Kairo64's Open ROMs (kernal_generic.rom), has its own names.
FORBIDDEN = re.compile(r'(?i)(^|/)bios\d*\.(rom|bin)$'
                       r'|(^|/)(c64_?)?(kernal|basic|chargen|char|dos15\d\d|drive-15\d\d\w*)(\.bin)?$'
                       r'|\.(hdi|fdi|d88|d77|nfd|hdm|xdf|dim|fdd|iso|chd|cue|dsk|st|msa|stx|adf|ipf'
                       r'|d64|d71|d81|g64|g71|p64|x64|t64|tap|crt|prg|p00)$')

# A bundle keeps the baseline profile as metadata; bundletool installs it as the APK's assets/dexopt/.
BUNDLE_PROFILES = 'BUNDLE-METADATA/com.android.tools.build.profiles/'


def inspect(path, prefix, module, allow_art=False):
    with zipfile.ZipFile(path) as archive:
        payload = {}
        native = 0
        for name in archive.namelist():
            assert not FORBIDDEN.search(name), f'{path}: firmware or disk image {name}'
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
        assert f'lib/arm64-v8a/lib{module}.so' in payload, f'{path}: no lib{module}.so'
        assert any(n.startswith('assets/catalog/') for n in payload), f'{path}: no catalog'
        # The app's own assets (src/main/assets, its catalog built from catalog/parts instead), as committed.
        source = f'{module}/src/main/assets/'
        for file in subprocess.check_output(['git', 'ls-files', '-z', source], text=True).split('\0'):
            relative = file.removeprefix(source)
            if file and not relative.startswith('catalog/'):
                digest = hashlib.sha256(subprocess.check_output(['git', 'show', f'HEAD:{file}'])).hexdigest()
                assert payload.get('assets/' + relative) == digest, f'{path}: assets/{relative} differs from {file}'
        # The app's own notices and policy, and the frontend's notices About -> Licenses appends:
        # the shader runtime's exactly when the app carries the runtime.
        for name in ('assets/THIRD_PARTY_NOTICES.txt', 'assets/PRIVACY_POLICY.txt', 'assets/FRONTEND_NOTICES.txt',
                     'assets/NDK_NOTICES.txt', 'assets/ANDROIDX_NOTICES.txt'):
            assert name in payload, f'Missing {name}'
        shaders = any(n.endswith('/liblibrashader_capi.so') for n in payload)
        assert ('assets/SLANG_RUNTIME_NOTICES.txt' in payload) == shaders, 'Shader runtime and its notices disagree'
        artwork = {n for n in payload if n.startswith('assets/art/')}
        if allow_art:
            # The image variant bundles exactly the repository's catalog/artwork (kairo-app.gradle),
            # which holds no images for an app whose catalog has none yet.
            root = Path('catalog/artwork')
            expected = {'assets/' + p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file()} \
                if root.is_dir() else set()
            assert artwork == expected, \
                f'Bundled artwork differs from catalog/artwork: {len(artwork - expected)} extra, {len(expected - artwork)} missing'
            print(f'{path}: {len(artwork)} bundled artwork files, as catalog/artwork holds')
        else:
            assert not artwork, 'Unexpected bundled artwork'
        print(f'{path}: {native} ARM64 libraries, 16 KB ELF alignment verified')
        return payload


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('module', help="the app's Gradle module and library name, such as kairo88")
    parser.add_argument('apk')
    parser.add_argument('aab')
    parser.add_argument('with_images_apk')
    args = parser.parse_args()
    common = inspect(args.apk, '', args.module)
    assert {n:v for n,v in common.items() if not ADULT_PART.match(n)} == inspect(args.aab, 'base/', args.module), \
        'APK/AAB payload mismatch'
    with_images = inspect(args.with_images_apk, '', args.module, allow_art=True)
    # The compiled baseline profile records its own variant's dex checksums, so it differs with the dex.
    derived = ('assets/art/', 'assets/dexopt/')
    assert {n: v for n, v in with_images.items() if not n.startswith(derived)} == {
        n: v for n, v in common.items() if not n.startswith(derived)}, 'Image variant changes non-artwork payload'
    print('The APKs and the Play bundle share code and core assets; only the adult catalog parts and artwork differ')
