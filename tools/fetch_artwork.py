#!/usr/bin/env python3
"""Fills an app's catalog/artwork with the images its catalog references, for the withImages build.

Usage: fetch_artwork.py <app root> [--workers N] [--check]

Every LaunchBox image in catalog/parts (art.json and the restricted art.nsfw.json) is fetched from
its URL and written to catalog/artwork/<path> as the app's bundled image: WebP, shrunk to fit 960
by 360 pixels (never enlarged), as Kairo98's bundled artwork is. Images already present are kept,
so an interrupted run continues where it stopped; an image no catalog part references is removed.
A failed download or an unreadable image stops the run with its URL; nothing is written for it.
A PNG's damaged ancillary chunks (such as a colour profile) are left out, as the PNG specification
allows, and named in the output.
"""
import argparse
import importlib.util
import io
import json
import sys
import urllib.request
import zlib
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_package import image_paths

# The bundled image's largest size and encoding, as Kairo98's import_art.py writes them.
BOX = (960, 360)
QUALITY = 75
KINDS = (("boxArt", "boxArtUrl"), ("preview", "previewUrl"))
PARTS = ("art.json", "art.nsfw.json")


def load_config(root):
    spec = importlib.util.spec_from_file_location("catalog_config", root / "tools/catalog_config.py")
    config = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(config)
    return config


def references(value, expand, found):
    """Every image path in the documents, with its source URL."""
    if isinstance(value, dict):
        for key, child in value.items():
            if key == "artwork":
                art = expand(child)
                for kind, url in KINDS:
                    if art.get(kind):
                        if not art.get(url):
                            raise SystemExit(f"{art[kind]} has no source URL")
                        found[art[kind]] = art[url]
            else:
                references(child, expand, found)
    return found


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "Kairo catalog artwork"})
    with urllib.request.urlopen(request) as response:
        return response.read()


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def without_damaged_ancillary_chunks(raw, url):
    """A PNG without its ancillary chunks (lower-case first letter, such as a colour profile)
    whose checksums are wrong, which the PNG specification lets a decoder ignore; LaunchBox
    serves a few such files, which browsers show. A damaged critical chunk is left for the
    decoder to reject."""
    if not raw.startswith(PNG_SIGNATURE):
        return raw
    out, position, dropped = bytearray(PNG_SIGNATURE), len(PNG_SIGNATURE), []
    while position + 12 <= len(raw):
        length = int.from_bytes(raw[position:position + 4], "big")
        end = position + 12 + length
        kind = raw[position + 4:position + 8]
        crc = int.from_bytes(raw[end - 4:end], "big")
        if end <= len(raw) and kind[:1].islower() and zlib.crc32(raw[position + 4:end - 4]) != crc:
            dropped.append(kind.decode("latin-1"))
        else:
            out += raw[position:end]
        position = end
    out += raw[position:]
    if dropped:
        print(f"{url}: left out damaged {', '.join(dropped)}", flush=True)
    return bytes(out)


def bundle(raw, target, url):
    # Only fetching needs Pillow; --check runs where it is not installed, as on CI.
    from PIL import Image

    raw = without_damaged_ancillary_chunks(raw, url)
    with Image.open(io.BytesIO(raw)) as original:
        image = original.convert("RGBA" if original.mode in ("RGBA", "LA", "P") else "RGB")
        image.thumbnail(BOX, Image.Resampling.LANCZOS)
        output = io.BytesIO()
        image.save(output, format="WEBP", quality=QUALITY, method=6)
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_name(target.name + ".part")
    partial.write_bytes(output.getvalue())
    partial.replace(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", type=Path)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--check", action="store_true",
                        help="fetch nothing; fail when a referenced image is not bundled")
    args = parser.parse_args()
    root = args.root.resolve()
    config = load_config(root)
    documents = {part: json.loads((root / "catalog/parts" / part).read_text(encoding="utf-8")) for part in PARTS}
    images = references(documents, config.ARTWORK.expand, {})
    assert set(images) == image_paths(documents, config.ARTWORK.expand)
    artwork = root / "catalog/artwork"
    missing = sorted(path for path in images if not (artwork / path).is_file())
    unreferenced = sorted(path for path in artwork.rglob("*") if path.is_file()
                          and path.relative_to(artwork).as_posix() not in images)
    if args.check:
        if missing:
            raise SystemExit(f"{len(missing)} of {len(images)} referenced images are not bundled, such as "
                             f"{missing[0]}: run shared/tools/fetch_artwork.py")
        if unreferenced:
            raise SystemExit(f"{len(unreferenced)} bundled images are not referenced, such as "
                             f"{unreferenced[0].relative_to(artwork).as_posix()}: run shared/tools/fetch_artwork.py")
        print(f"all {len(images)} referenced images are bundled, and nothing else")
        return
    # The artwork APK bundles the whole folder, so an image the catalog no longer names leaves it.
    for path in unreferenced:
        path.unlink()
        for folder in path.parents:
            if folder == artwork or any(folder.iterdir()):
                break
            folder.rmdir()
    if unreferenced:
        print(f"removed {len(unreferenced)} unreferenced images", flush=True)
    print(f"{len(images)} images referenced, {len(missing)} to fetch", flush=True)

    def one(path):
        url = images[path]
        try:
            bundle(fetch(url), artwork / path, url)
        except Exception as error:
            raise RuntimeError(f"{url} ({path}): {error}") from error
        return path

    with ThreadPoolExecutor(args.workers) as pool:
        for count, _ in enumerate(pool.map(one, missing), 1):
            if count % 200 == 0 or count == len(missing):
                print(f"{count}/{len(missing)}", flush=True)


if __name__ == "__main__":
    main()
