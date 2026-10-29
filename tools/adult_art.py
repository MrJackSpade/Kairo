#!/usr/bin/env python3
"""Move games' images to an app's adult artwork catalog.

Usage: adult_art.py <app root> <game key> [<game key> ...]

Adds each game's current box art and screenshot to "adultArtwork" in catalog/core-review-v1.json,
the list of images that go to the adult catalog (catalog/parts/art.nsfw.json) instead of the main
one, then rebuilds the app's catalog with app_catalog.py.
"""
import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from app_catalog import metadata
from research import load_config
import catalog_files


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    root = Path(sys.argv[1]).resolve()
    config = load_config(root)
    games = catalog_files.read(root / "catalog/research/games-v1.json.gz")["games"]
    path = root / "catalog/core-review-v1.json"
    review = json.loads(path.read_text(encoding="utf-8"))
    adult = review["adultArtwork"]
    for key in sys.argv[2:]:
        if key not in games:
            raise SystemExit(f"unknown game {key}")
        art = config.ARTWORK.expand(metadata(key, games[key], config).get("artwork", {}))
        images = [art[kind] for kind in ("boxArt", "preview") if kind in art]
        if not images:
            raise SystemExit(f"{key} ({games[key]['title']}) has no artwork")
        for image in images:
            adult[image] = games[key]["title"]
        print(f"{key} {games[key]['title']}: {', '.join(images)}")
    review["adultArtwork"] = dict(sorted(adult.items()))
    path.write_text(json.dumps(review, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    subprocess.run([sys.executable, str(Path(__file__).resolve().parent / "app_catalog.py"), str(root)], check=True)


if __name__ == "__main__":
    main()
