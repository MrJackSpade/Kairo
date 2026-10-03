#!/usr/bin/env python3
"""Verify catalog parts, core feeds and exact distribution-specific assets."""
import argparse
import hashlib
import json
import sys
import subprocess
import zipfile
from pathlib import Path
from catalog_parts import merge, split, indexed_document
from catalog_package import compact
from catalog_package import image_paths

def audit(root, product, artifacts=()):
    app = 'kairodos' if product == 'dos' else 'kairo98'
    tracked = set(subprocess.check_output(['git', 'ls-files'], cwd=root, text=True).splitlines())
    def repository_file(path):
        assert path.relative_to(root).as_posix() in tracked, f'Catalog file is not tracked: {path}'
        assert path.is_file(), f'Missing catalog file: {path}'
    # Check every public snapshot and optional update manifest against committed bytes.
    manifests = list((root/'catalog/optional').glob('*.meta.json'))
    manifests += ([root/'catalog/core-v2.json', root/'catalog/online-v1.json'] if product == 'dos'
        else [root/'catalog/core-v2.meta.json', root/'catalog/online-v1.meta.json'])
    repo = 'KairoDos' if product == 'dos' else 'Kairo98'
    prefix = f'https://raw.githubusercontent.com/MrJackSpade/{repo}/main/'
    for path in manifests:
        repository_file(path)
        meta = json.loads(path.read_text('utf8'))
        address = meta['archive']
        if '://' in address:
            assert address.startswith(prefix), f'Non-repository catalog source: {address}'
            archive = root/address.removeprefix(prefix)
        else:
            archive = path.parent/address
        repository_file(archive)
        payload = archive.read_bytes()
        assert len(payload) == meta['size'] and hashlib.sha256(payload).hexdigest() == meta['sha256'], path
    sys.path.insert(0, str(root/'tools'))
    if product == 'dos': from dos_artwork import expand
    else: from artwork_references import expand
    policy = json.loads((root/'catalog/core-review-v1.json').read_text('utf8'))
    approved = policy['approvedArtwork']
    parts = {p.name:p.read_bytes() for p in (root/'catalog/parts').glob('*.json')}
    assert set(parts) == {'data.json','controls.json','art.json','art.nsfw.json'}
    documents = {name:json.loads(data)['documents'] for name,data in parts.items()}
    for name, value in documents.items():
        repository_file(root/'catalog/parts'/name)
        repository_file(root/'catalog/parts'/name.replace('.json', '.idx'))
        payload, index = indexed_document(value)
        assert payload == parts[name]
        filename = name.removesuffix('.json')+'.idx'
        assert (root/'catalog/parts'/filename).read_bytes() == compact(index)
    data, controls, safe, extra = [documents[k] for k in ('data.json','controls.json','art.json','art.nsfw.json')]
    assert not image_paths(data, expand) and not image_paths(controls, expand)
    assert not split(data)[1], 'Controls leaked into display metadata'
    assert split(safe)[:2] == ({}, {}) and split(extra)[:2] == ({}, {})
    assert image_paths(safe,expand) == approved.keys()
    withheld = image_paths(extra,expand)
    assert withheld.isdisjoint(approved)
    assert withheld == set(json.loads((root/'catalog/artwork-exclusions-v1.json').read_text('utf8'))['artwork'])
    expected_core = merge(data, controls, safe)
    assets = root/app/'src/main/assets/catalog'
    legacy = {p.relative_to(assets).as_posix():json.loads(p.read_text('utf8')) for p in assets.rglob('*.json')}
    assert expected_core == legacy, 'Parts do not reconstruct generated core metadata'
    if product == 'dos':
        ids = {k for n,v in legacy.items() if n.startswith('dos/') and 'games' in v for k in v['games']}
        assert policy['excluded'].keys() <= ids
        for path in [root/'catalog/online-v1.zip',root/'catalog/core-v2.zip',root/'shared/catalog/dos/online-v1.zip',root/'shared/catalog/dos/core-v2.zip']:
            with zipfile.ZipFile(path) as z:
                assert {'dos/'+n:json.loads(z.read(n)) for n in z.namelist()} == legacy
    else:
        assert policy['excluded'].keys() <= legacy['name-index-v1.json']['games'].keys()
        for name in ('source-v1.json','online-v1.json','core-v2.json'):
            assert image_paths(json.loads((root/'catalog'/name).read_text('utf8')),expand) <= approved.keys()
    for path, sha in approved.items():
        assert hashlib.sha256((root/'catalog/artwork'/path).read_bytes()).hexdigest() == sha
    package = root/f'catalog/optional/{app}-art.nsfw.zip'
    manifest = json.loads(package.with_suffix('.meta.json').read_text('utf8'))
    release = json.loads((root/'catalog/artwork-release-v1.json').read_text('utf8'))
    assert manifest['revision'] == release['revision']
    repo = 'KairoDos' if product == 'dos' else 'Kairo98'
    base = f'https://raw.githubusercontent.com/MrJackSpade/{repo}/main/catalog/'
    assert manifest['archive'] == base + f'optional/{app}-art.nsfw.zip'
    assert package.is_file(), 'Importable catalog must be committed in the repository'
    for path in image_paths(merge(safe, extra), expand):
        repository_file(root/'catalog/artwork'/path)
    if package.exists():
        assert manifest['size'] == package.stat().st_size
        assert manifest['sha256'] == hashlib.sha256(package.read_bytes()).hexdigest()
        with zipfile.ZipFile(package) as z:
            header = json.loads(z.read('catalog.json'))
            assert header['id'] == manifest['id'] and header['revision'] == manifest['revision']
            assert header['product'] == product and set(z.namelist()) == {'catalog.json', *header['files']}
            for name,spec in header['files'].items():
                payload = z.read(name)
                assert len(payload) == spec['size'] and hashlib.sha256(payload).hexdigest() == spec['sha256']
            runtime = json.loads(z.read('runtime.json'))
            assert runtime == {k:v for k,v in header.items() if k != 'files'}
            indexed = [line for line in z.read('artwork.idx').decode('utf8').splitlines() if line]
            assert indexed == [], 'Artwork payloads stay in catalog/artwork, not the import file'
            assert header['updateManifest'] == base + f'optional/{app}-art.nsfw.meta.json'
            assert set(header['files']) == {'data.json', 'runtime.json', 'artwork.idx'}
            optional = json.loads(z.read('data.json'))
            assert image_paths(optional,expand) == withheld
            def artwork_only(records):
                for record in records.values():
                    assert record.keys() <= {'artwork','variants'}
                    artwork_only(record.get('variants',{}))
            artwork_only(optional['games'])
            if product == 'pc98': artwork_only(optional['nameIndex']['games'])
    for artifact in artifacts:
        play = artifact.suffix == '.aab'
        with zipfile.ZipFile(artifact) as z:
            prefix = 'base/assets/' if play else 'assets/'
            files = {n[len(prefix):]:z.read(n) for n in z.namelist() if n.startswith(prefix) and not n.endswith('/')}
            selected = {n.removeprefix('catalog/'):v for n,v in files.items() if n.startswith('catalog/')}
            expected = {p.name:p.read_bytes() for p in (root/'catalog/parts').iterdir()
                if p.is_file() and not(play and p.name.startswith('art.nsfw.'))}
            assert selected == expected, (artifact,'Wrong catalog partition payload')
            art_files = {n for n in files if n.startswith('art/')}
            if art_files:
                assert art_files == {p.relative_to(root/'catalog/artwork').as_posix()
                    for p in (root/'catalog/artwork/art').rglob('*') if p.is_file()}, 'Incomplete artwork APK'
            for name,payload in files.items():
                assert not name.endswith('.zip'), (artifact,'Unexpected archive asset')
                if name.startswith('art/'):
                    assert not play, (artifact,'Play artwork payload')
                    assert payload == (root/'catalog/artwork'/name).read_bytes(), (artifact,name)
            if play:
                for name in z.namelist():
                    if name.endswith('.dex'):
                        payload=z.read(name)
                        assert manifest['archive'].encode() not in payload
                        assert b'art.nsfw' not in payload and b'/catalog/optional/' not in payload
        print(f'{artifact}: distribution-specific catalog payload verified')
    print(f'{product}: all metadata retained; {len(approved)} reviewed artwork paths, {len(withheld)} restricted paths')

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('product', choices=('dos','pc98')); parser.add_argument('artifacts',nargs='*',type=Path)
    args=parser.parse_args(); audit(Path.cwd(),args.product,args.artifacts)
