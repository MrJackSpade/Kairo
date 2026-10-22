"""An app's manual artwork review: catalog/research/art-review-v1.json.

  {"schemaVersion": 1,
   "images": {"<source image URL>": "ok" | "rejected"},
   "wrongGame": {"<game key>": true}}

Verdicts name an image by its source URL (LaunchBox, or MobyGames for self-hosted images), so a
verdict follows the image, not the game: research.py never picks a rejected image and takes the
game's next candidate instead, and a game whose images changed is unreviewed again. "wrongGame"
marks a game whose images show a different game, which means its match is wrong.
tools/art_review.py records the verdicts.
"""
import json
import os

PATH = "catalog/research/art-review-v1.json"


def read(root):
    path = root / PATH
    if not path.is_file():
        return {"schemaVersion": 1, "images": {}, "wrongGame": {}}
    review = json.loads(path.read_text(encoding="utf-8"))
    if review.get("schemaVersion") != 1 or set(review) != {"schemaVersion", "images", "wrongGame"}:
        raise SystemExit(f"unsupported artwork review: {path}")
    if set(review["images"].values()) - {"ok", "rejected"}:
        raise SystemExit(f"invalid image verdict in {path}")
    return review


def rejected(root):
    return {url for url, verdict in read(root)["images"].items() if verdict == "rejected"}


def write(root, review):
    path = root / PATH
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    with open(temporary, "w", encoding="utf-8") as file:
        file.write(json.dumps(review, ensure_ascii=False, indent=1, sort_keys=True) + "\n")
        file.flush()
        os.fsync(file.fileno())
    temporary.replace(path)
