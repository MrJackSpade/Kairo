"""Split generated catalog documents by responsibility without changing record identity."""
import json
from pathlib import Path
from typing import NamedTuple
from catalog_package import compact, filter_artwork, image_paths, build_package

class App(NamedTuple):
    """How an app publishes its catalog. package/identity name its importable artwork package;
    label is that package's display name as published. Apps whose records name games in a name
    index ship that index's artwork; KairoDos keys records directly and has folders instead."""
    repo: str
    product: str
    package: str
    identity: str
    label: str
    name_index: bool


# Kairo98's v2 records name their games' shared records; its v1 package stays frozen for app
# versions that read the repeated records.
APPS = {
    'kairodos': App('KairoDos', 'dos', 'kairodos-art.nsfw', 'kairodos-art', 'Kairodos artwork', False),
    'kairo98': App('Kairo98', 'pc98', 'kairo98-art-v2.nsfw', 'kairo98-art-v2', 'Kairo98 artwork', True),
    'kairo88': App('Kairo88', 'pc88', 'kairo88-art.nsfw', 'kairo88-art', 'Kairo88 artwork', True),
    'kairo68': App('Kairo68', 'x68000', 'kairo68-art.nsfw', 'kairo68-art', 'Kairo68 artwork', True),
    'kairotowns': App('KairoTowns', 'fmtowns', 'kairotowns-art.nsfw', 'kairotowns-art', 'KairoTowns artwork', True),
    'kairomsx': App('KairoMSX', 'msx', 'kairomsx-art.nsfw', 'kairomsx-art', 'KairoMSX artwork', True),
    'kairost': App('KairoST', 'atarist', 'kairost-art.nsfw', 'kairost-art', 'KairoST artwork', True),
}

CONTROL_FIELDS = {'controller', 'input', 'machine', 'launch', 'startupChoices', 'diskSwaps', 'media'}

def artwork_split(policy, all_images):
    """The images of the main artwork catalog (art.json) and of the adult one (art.nsfw.json).
    An app lists either its adult images ("adultArtwork": {path: game title}), and every other
    image is main, or the images approved as main ("approvedArtwork", Kairo98 and KairoDos)."""
    if 'adultArtwork' in policy:
        assert 'approvedArtwork' not in policy, 'list adultArtwork or approvedArtwork, not both'
        withheld = set(policy['adultArtwork'])
        unknown = withheld - all_images
        assert not unknown, f'adultArtwork names images the catalog does not have: {sorted(unknown)[:5]}'
        return all_images - withheld, withheld
    approved = set(policy['approvedArtwork'])
    return approved, all_images - approved

def split(value):
    if not isinstance(value, dict):
        return value, {}, {}
    data, controls, art = {}, {}, {}
    for key, child in value.items():
        if key == 'artwork': art[key] = child
        elif key in CONTROL_FIELDS or key.endswith('controller-profiles-v1.json'): controls[key] = child
        elif isinstance(child, dict):
            d, c, a = split(child)
            if d or not child: data[key] = d
            if c: controls[key] = c
            if a: art[key] = a
        else: data[key] = child
    return data, controls, art

def merge(*parts):
    result = {}
    for part in parts:
        for key, value in part.items():
            result[key] = merge(result.get(key, {}), value) if isinstance(value, dict) else (result.get(key, 0) | value if key == 'kinds' else value)
    return result

def generate_parts(root, app, full_documents, optional_data, expand, compact_art, check=False):
    policy = json.loads((root/'catalog/core-review-v1.json').read_text('utf8'))
    all_images = image_paths(full_documents, expand)
    approved, withheld = artwork_split(policy, all_images)
    data, controls, _ = split(full_documents)
    safe = split(filter_artwork(full_documents, approved, expand, compact_art))[2]
    extra = split(filter_artwork(full_documents, withheld, expand, compact_art))[2]
    outputs = {'data.json':data, 'controls.json':controls, 'art.json':safe, 'art.nsfw.json':extra}
    directory = root/'catalog/parts'
    directory.mkdir(parents=True, exist_ok=True)
    for name, value in outputs.items():
        payload, index = indexed_document(value)
        for filename, content in ((name,payload),(name.removesuffix('.json')+'.idx',compact(index))):
            if check: assert (directory/filename).read_bytes() == content, f'Stale {filename}'
            else: (directory/filename).write_bytes(content)
    assert merge(data, controls, safe, extra) == full_documents
    # The importable catalog contributes only artwork. Metadata and controls are always shipped.
    restricted = filter_artwork(optional_data, withheld, expand, compact_art)
    _, _, art = split(restricted)
    spec = APPS[app]
    if not spec.name_index:
        payload = {'schemaVersion':1,'games':art.get('games',{}),'folders':{},
                   'controllers':{'schemaVersion':1,'profiles':{},'presets':{},'assignments':{}}}
    else:
        games = art.get('nameIndex',{}).get('games',{})
        payload = {'schemaVersion':1,'games':art.get('games',{}),'nameIndex':{'schemaVersion':1,
            'games':games,'names':{k:v for k,v in optional_data['nameIndex']['names'].items() if v in games}}}
    if not check:
        release = json.loads((root/'catalog/artwork-release-v1.json').read_text('utf8'))
        assert release['schemaVersion'] == 1 and release['revision'] > 0
        base = f'https://raw.githubusercontent.com/MrJackSpade/{spec.repo}/main/catalog/optional/'
        build_package(payload, set(), root/'catalog/artwork',
            root/f'catalog/optional/{spec.package}.zip', product=spec.product,
            identity=spec.identity,name=spec.label,revision=release['revision'],
            source=base+f'{spec.package}.meta.json', archive_url=base+f'{spec.package}.zip', stored=True)
        (root/'catalog/artwork-exclusions-v1.json').write_bytes(compact({'schemaVersion':1,'artwork':sorted(withheld)}))
    print(f'{app}: {len(all_images)} artwork paths; {len(approved)} main; {len(withheld)} adult')

def indexed_document(documents):
    """Byte ranges let Android load one shard without parsing the complete DOS catalog."""
    payload = bytearray(b'{"documents":{')
    index = {}
    for i, (name, document) in enumerate(sorted(documents.items())):
        if i: payload.extend(b',')
        payload.extend(compact(name)+b':')
        content=compact(document)
        index[name]=[len(payload),len(content)]
        payload.extend(content)
    payload.extend(b'},"schemaVersion":1}')
    return bytes(payload), {'schemaVersion':1,'documents':index}
