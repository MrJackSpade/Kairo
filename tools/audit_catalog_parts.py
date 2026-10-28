#!/usr/bin/env python3
"""Verify catalog parts, core feeds and exact distribution-specific assets."""
import argparse
import hashlib
import json
import sys
import subprocess
import zipfile
from pathlib import Path
from catalog_parts import APPS, adult_descriptions, artwork_split, merge, split, indexed_document
from catalog_package import compact
from catalog_package import image_paths
from catalog_binary import verify_part

def audit(root, product, artifacts=()):
    app = next(name for name, spec in APPS.items() if spec.product == product)
    spec = APPS[app]
    tracked = set(subprocess.check_output(['git', 'ls-files'], cwd=root, text=True).splitlines())
    def repository_file(path):
        assert path.relative_to(root).as_posix() in tracked, f'Catalog file is not tracked: {path}'
        assert path.is_file(), f'Missing catalog file: {path}'
    # Check every public snapshot and optional update manifest against committed bytes.
    # The optional packages and every published update: core-v4 and each language's, and the
    # frozen core-v3 earlier app versions read (Kairo#77).
    manifests = list((root/'catalog/optional').glob('*.meta.json')) + sorted((root/'catalog').glob('core-v*.meta.json'))
    assert root/'catalog/core-v4.meta.json' in manifests, 'The app publishes no update' 
    prefix = f'https://raw.githubusercontent.com/MrJackSpade/{spec.repo}/main/'
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
    # The app's artwork references: its catalog_config's (shared app_catalog), else its own module (Kairo98).
    if (root/'tools/catalog_config.py').is_file():
        from app_catalog import load_config
        expand = load_config(root).ARTWORK.expand
    else:
        from artwork_references import expand
    policy = json.loads((root/'catalog/core-review-v1.json').read_text('utf8'))
    parts = {p.name:p.read_bytes() for p in (root/'catalog/parts').glob('*.json')}
    assert set(parts) == {'data.json','controls.json','art.json','art.nsfw.json','data.nsfw.json'}
    documents = {name:json.loads(data)['documents'] for name,data in parts.items()}
    for name, value in documents.items():
        repository_file(root/'catalog/parts'/name)
        repository_file(root/'catalog/parts'/name.replace('.json', '.idx'))
        payload, index = indexed_document(value)
        assert payload == parts[name]
        filename = name.removesuffix('.json')+'.idx'
        assert (root/'catalog/parts'/filename).read_bytes() == compact(index)
    data, controls, safe, extra = [documents[k] for k in ('data.json','controls.json','art.json','art.nsfw.json')]
    adult, adult_translations = adult_descriptions(documents['data.nsfw.json'])
    assert set(adult) <= set(data.get('name-index-v1.json', {}).get('games', {})), 'Adult descriptions for unknown games'
    assert not image_paths(data, expand) and not image_paths(controls, expand)
    assert not split(data)[1], 'Controls leaked into display metadata'
    assert split(safe)[:2] == ({}, {}) and split(extra)[:2] == ({}, {})
    approved, expected_withheld = artwork_split(policy, image_paths(safe,expand) | image_paths(extra,expand))
    assert image_paths(safe,expand) == approved
    withheld = image_paths(extra,expand)
    assert withheld == expected_withheld
    assert withheld == set(json.loads((root/'catalog/artwork-exclusions-v1.json').read_text('utf8'))['artwork'])
    expected_core = merge(data, controls, safe)
    # An app that also keeps its generated documents as asset files (Kairo98) keeps them equal to its parts.
    assets = root/app/'src/main/assets/catalog'
    if assets.is_dir():
        legacy = {p.relative_to(assets).as_posix():json.loads(p.read_text('utf8')) for p in assets.rglob('*.json')}
        assert expected_core == legacy, 'Parts do not reconstruct generated core metadata'
    assert policy['excluded'].keys() <= expected_core['name-index-v1.json']['games'].keys()
    published = [json.loads((root/'catalog/source-v1.json').read_text('utf8'))]
    if (root/'catalog/core-v3.json').is_file():
        published.append(json.loads((root/'catalog/core-v3.json').read_text('utf8')))
    with zipfile.ZipFile(root/'catalog/core-v4.zip') as update:
        published += [json.loads(update.read(name)) for name in update.namelist()]
    for document in published:
        assert image_paths(document, expand) <= approved, 'A published catalog names withheld artwork' 
    # Approved images are pinned by checksum; an app listing adult images instead pins none.
    for path, sha in policy.get('approvedArtwork', {}).items():
        assert hashlib.sha256((root/'catalog/artwork'/path).read_bytes()).hexdigest() == sha
    package_name = spec.package
    package = root/f'catalog/optional/{package_name}.zip'
    manifest = json.loads(package.with_suffix('.meta.json').read_text('utf8'))
    release = json.loads((root/'catalog/artwork-release-v1.json').read_text('utf8'))
    assert manifest['revision'] == release['revision']
    base = f'https://raw.githubusercontent.com/MrJackSpade/{spec.repo}/main/catalog/'
    assert manifest['archive'] == base + f'optional/{package_name}.zip'
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
            for name,entry in header['files'].items():
                payload = z.read(name)
                assert len(payload) == entry['size'] and hashlib.sha256(payload).hexdigest() == entry['sha256']
            runtime = json.loads(z.read('runtime.json'))
            assert runtime == {k:v for k,v in header.items() if k != 'files'}
            indexed = [line for line in z.read('artwork.idx').decode('utf8').splitlines() if line]
            assert indexed == [], 'Artwork payloads stay in catalog/artwork, not the import file'
            assert header['updateManifest'] == base + f'optional/{package_name}.meta.json'
            assert set(header['files']) == {'data.json', 'runtime.json', 'artwork.idx'}
            optional = json.loads(z.read('data.json'))
            assert image_paths(optional,expand) == withheld
            def artwork_only(records):
                for record in records.values():
                    assert record.keys() <= {'artwork','variants'}
                    artwork_only(record.get('variants',{}))
            artwork_only(optional['games'])
            # A name-index game also carries its adult description, the one data.nsfw holds.
            games = optional['nameIndex']['games']
            artwork_only({k: {f: v for f, v in r.items() if f != 'description'} for k, r in games.items()})
            assert {k: {'description': r['description']} for k, r in games.items() if 'description' in r} == adult
            # And the adult translations, the documents data.nsfw holds.
            assert optional.get('documents', {}) == adult_translations
    for artifact in artifacts:
        play = artifact.suffix == '.aab'
        with zipfile.ZipFile(artifact) as z:
            prefix = 'base/assets/' if play else 'assets/'
            files = {n[len(prefix):]:z.read(n) for n in z.namelist() if n.startswith(prefix) and not n.endswith('/')}
            selected = {n.removeprefix('catalog/'):v for n,v in files.items() if n.startswith('catalog/')}
            # Builds ship each part compiled to <part>.bin with a schema-2 <part>.idx (catalog-binary.gradle).
            shipped = [n.removesuffix('.json') for n in documents if not(play and '.nsfw.' in n)]
            assert set(selected) == {p+suffix for p in shipped for suffix in ('.idx','.bin')}, (artifact,'Wrong catalog partition payload')
            for part in shipped:
                verify_part(selected[part+'.idx'], selected[part+'.bin'], documents[part+'.json'])
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
                        assert b'.nsfw' not in payload and b'/catalog/optional/' not in payload
        print(f'{artifact}: distribution-specific catalog payload verified')
    print(f'{product}: all metadata retained; {len(approved)} main artwork paths, {len(withheld)} adult paths, '
          f'{len(adult)} adult descriptions')

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('product', choices=sorted(spec.product for spec in APPS.values())); parser.add_argument('artifacts',nargs='*',type=Path)
    args=parser.parse_args(); audit(Path.cwd(),args.product,args.artifacts)
