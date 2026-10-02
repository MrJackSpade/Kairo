"""Split generated catalog documents by responsibility without changing record identity."""
import json
from pathlib import Path
from catalog_package import compact, filter_artwork, image_paths, build_package

CONTROL_FIELDS = {'controller', 'input', 'machine', 'launch', 'startupChoices', 'diskSwaps', 'media'}

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
    approved = policy['approvedArtwork']
    all_images = image_paths(full_documents, expand)
    withheld = all_images - approved.keys()
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
    if app == 'kairodos':
        payload = {'schemaVersion':1,'games':art.get('games',{}),'folders':{},
                   'controllers':{'schemaVersion':1,'profiles':{},'presets':{},'assignments':{}}}
    else:
        games = art.get('nameIndex',{}).get('games',{})
        payload = {'schemaVersion':1,'games':art.get('games',{}),'nameIndex':{'schemaVersion':1,
            'games':games,'names':{k:v for k,v in optional_data['nameIndex']['names'].items() if v in games}}}
    if not check:
        build_package(payload, image_paths(payload, expand), root/'catalog/artwork',
            root/f'catalog/optional/{app}-art.nsfw.zip', product='dos' if app=='kairodos' else 'pc98',
            identity=app+'-art',name=app.replace('kairo','Kairo')+' artwork',revision=1,
            source=f'https://raw.githubusercontent.com/MrJackSpade/{"KairoDos" if app=="kairodos" else "Kairo98"}/main/catalog/optional/{app}-art.nsfw.meta.json',
            archive_url=f'https://github.com/MrJackSpade/{"KairoDos" if app=="kairodos" else "Kairo98"}/releases/download/v0.9.7/{app}-art.nsfw.zip', stored=True)
        (root/'catalog/artwork-exclusions-v1.json').write_bytes(compact({'schemaVersion':1,'artwork':sorted(withheld)}))
    print(f'{app}: {len(all_images)} artwork paths; {len(withheld)} restricted; {len(approved)} reviewed')

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
