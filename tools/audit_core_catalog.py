#!/usr/bin/env python3
"""Fail closed if public/bundled catalogs or binary assets include excluded catalog data."""
import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path


def audit(root, product, artifacts=()):
    policy=json.loads((root/'catalog/core-review-v1.json').read_text('utf8'))
    excluded=json.loads((root/'catalog/excluded-ids-v1.json').read_text('utf8'))
    ids=set(excluded['ids']); images=set(excluded['artwork'])
    titles={v['title'] for v in policy['excluded'].values()}
    approved=policy['approvedArtwork']
    app='kairodos' if product=='dos' else 'kairo98'
    catalog=root/app/'src/main/assets/catalog'
    expected={f.relative_to(catalog).as_posix():f.read_bytes() for f in catalog.rglob('*') if f.is_file()}
    optional=root/'catalog/optional'/f'{app}-adult-v1.zip'
    manifest=json.loads(optional.with_suffix('.meta.json').read_text('utf8'))
    assert hashlib.sha256(optional.read_bytes()).hexdigest()==manifest['sha256']
    assert optional.stat().st_size==manifest['size']
    with zipfile.ZipFile(optional) as archive:
        header=json.loads(archive.read('catalog.json'))
        assert header['id']==manifest['id'] and header['product']==product and header['revision']==manifest['revision']
        assert set(archive.namelist())=={'catalog.json',*header['files']}
        for name,spec in header['files'].items():
            data=archive.read(name)
            assert len(data)==spec['size'] and hashlib.sha256(data).hexdigest()==spec['sha256'],name
        optional_data=json.loads(archive.read('data.json'))
        if product=='pc98':
            assert optional_data==json.loads((root/'catalog/optional/data-v1.json').read_text('utf8')), 'Stale optional metadata'
        optional_ids=set(optional_data['games'])
        if product=='pc98': optional_ids.update(optional_data['nameIndex']['games'])
        assert optional_ids==ids, 'Optional catalog does not preserve all excluded identities'
    forbidden=[x.encode() for x in ids|images|{header['updateManifest'],header['id'],header['name']}]
    # Hash-shaped IDs also appear inside compact artwork references; detect those separately.
    art_ids={path.split('/')[4] for path in images if product=='dos'}
    def inspect(name,data):
        if not name.endswith('.json'): return
        value=json.loads(data)
        def visit(v):
            if isinstance(v,dict):
                for key,item in v.items():
                    assert key not in ids,(name,key)
                    if key=='title': assert item not in titles and '♥' not in item,(name,item)
                    if key=='heart': assert item is not True,(name,'adult marker')
                    if key=='tags': assert '♥' not in item,(name,'adult marker')
                    if key=='artwork' and isinstance(item,dict):
                        assert item.get('id') not in art_ids,(name,'excluded artwork')
                    visit(item)
            elif isinstance(v,list):
                for x in v: visit(x)
            elif isinstance(v,str):
                assert v not in ids and v not in images,(name,v)
                assert header['updateManifest'] not in v and header['name'] not in v,(name,'optional discovery')
        visit(value)
    for name,data in expected.items(): inspect(name,data)
    if product=='dos':
        for path in [root/'catalog/online-v1.zip',root/'catalog/core-v2.zip',root/'shared/catalog/dos/online-v1.zip',root/'shared/catalog/dos/core-v2.zip']:
            with zipfile.ZipFile(path) as archive:
                assert set(archive.namelist())=={x.removeprefix('dos/') for x in expected}
                for name in archive.namelist():
                    inspect(name,archive.read(name))
                    assert json.loads(archive.read(name))==json.loads(expected['dos/'+name])
    else:
        for name in ('source-v1.json','online-v1.json','core-v2.json'):
            inspect(name,(root/'catalog'/name).read_bytes())
        assert (root/'catalog/online-v1.json').read_bytes()==(root/'catalog/core-v2.json').read_bytes()
    art_root=root/app/('src/main/assets' if product=='dos' else 'src/withImages/assets')
    actual_art={f.relative_to(art_root).as_posix():f.read_bytes() for f in (art_root/'art').rglob('*') if f.is_file()}
    def check_art(files):
        for name,data in files.items():
            if name=='art/catalog-provenance-v1.json': inspect(name,data); continue
            assert name in approved,('Unreviewed artwork',name)
            assert hashlib.sha256(data).hexdigest()==approved[name],('Changed reviewed artwork',name)
        assert set(approved)<=files.keys(),'Missing approved artwork'
    check_art(actual_art)
    for artifact in artifacts:
        with zipfile.ZipFile(artifact) as archive:
            prefix='base/' if artifact.suffix=='.aab' else ''
            files={x[len(prefix+'assets/'):]:archive.read(x) for x in archive.namelist() if x.startswith(prefix+'assets/') and not x.endswith('/')}
            actual={name[len('catalog/'):]:data for name,data in files.items() if name.startswith('catalog/')}
            assert actual==expected,(artifact,'bundled metadata differs from clean core')
            art={name:data for name,data in files.items() if name.startswith('art/')}
            if art: check_art(art)
            for name,data in files.items():
                inspect(name,data)
                assert not name.endswith('.zip'),(artifact,'Unexpected archive asset',name)
                assert not any(token in data for token in forbidden),(artifact,'excluded data or optional source',name)
            for name in archive.namelist():
                if name.endswith('.dex'):
                    data=archive.read(name)
                    assert not any(token in data for token in forbidden),(artifact,'excluded data or optional source in code')
        print(f'{artifact}: clean-core assets verified')
    print(f'{product}: {len(ids)} excluded identities; {len(approved)} checksum-pinned core images; all core paths clean')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('product',choices=('dos','pc98'));parser.add_argument('artifacts',nargs='*',type=Path)
    args=parser.parse_args();audit(Path.cwd(),args.product,args.artifacts)
