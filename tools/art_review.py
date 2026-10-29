#!/usr/bin/env python3
"""A local page for reviewing every game's bundled artwork by eye.

Usage: art_review.py <app root> [<app root> ...] [--port N] [--open]

Serves http://localhost:<port>/ (8765 by default). For each app it lists every catalog game that
has artwork, with the box art and screenshot shown at their bundled size, and records verdicts
in that app's catalog/research/art-review-v1.json (art_verdicts.py): an image clicked is
rejected, a page marked reviewed records its remaining images as fine, and a game can be marked
as matched to the wrong game. The page reads and writes JSON only.

Rejections take effect when the app's catalog is rebuilt: research.py (which picks the next
candidate image), app_catalog.py, then fetch_artwork.py.
"""
import argparse
import json
import mimetypes
import sys
import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import art_verdicts
from app_catalog import metadata
from research import load_config
import catalog_files

PAGE = Path(__file__).resolve().parent / "art_review.html"
LAUNCHBOX_PAGE = "https://gamesdb.launchbox-app.com/games/details/{}"
MOBYGAMES_PAGE = "https://www.mobygames.com/game/{}/"
KINDS = (("boxArt", "Box"), ("preview", "Screenshot"))
# Windows' Python registry has no WebP type.
mimetypes.add_type("image/webp", ".webp")


class App:
    """One app's games with artwork, and its review file."""

    def __init__(self, root):
        self.root = root.resolve()
        self.config = load_config(self.root)
        self.name = self.config.ARTWORK.self_hosted_base.split("/")[4]
        self.artwork = self.root / "catalog/artwork"
        self.lock = threading.Lock()
        self.review = art_verdicts.read(self.root)
        research = catalog_files.read(self.root / "catalog/research/games-v1.json.gz")
        moby = self.root / "catalog/research/moby-artwork-v1.json"
        # A self-hosted MobyGames image is judged by its MobyGames source URL.
        moby_sources = ({path: record["sourceUrl"] for path, record in
                         json.loads(moby.read_text(encoding="utf-8"))["images"].items()} if moby.is_file() else {})
        self.games = []
        for key, entry in sorted(research["games"].items(), key=lambda item: item[1]["title"].casefold()):
            art = self.config.ARTWORK.expand(metadata(key, entry, self.config).get("artwork", {}))
            images = []
            for kind, label in KINDS:
                if kind not in art:
                    continue
                path = art[kind]
                if not (self.artwork / path).is_file():
                    raise SystemExit(f"{self.name}: {path} is not bundled; run shared/tools/fetch_artwork.py")
                images.append({"kind": label, "path": path, "source": moby_sources.get(path, art[kind + "Url"])})
            if not images:
                continue
            links = []
            if entry.get("launchbox"):
                links.append({"label": "LaunchBox", "url": LAUNCHBOX_PAGE.format(entry["launchbox"])})
            if entry.get("mobygames"):
                links.append({"label": "MobyGames", "url": MOBYGAMES_PAGE.format(entry["mobygames"])})
            self.games.append({"key": key, "title": entry["title"], "year": entry.get("year"),
                               "files": [group.rsplit(":", 1)[-1] for group in entry.get("sourceGroups", [])][:3],
                               "links": links, "images": images, "adult": bool(entry.get("adult"))})

    def summary(self):
        verdicts = self.review["images"]
        sources = [image["source"] for game in self.games for image in game["images"]]
        return {"id": self.name, "games": len(self.games),
                "reviewed": sum(all(image["source"] in verdicts for image in game["images"]) for game in self.games),
                "rejected": sum(verdicts.get(source) == "rejected" for source in sources),
                "wrongGame": len(self.review["wrongGame"])}

    def state(self):
        return {"games": self.games, "verdicts": self.review["images"], "wrongGame": self.review["wrongGame"]}

    def update(self, change):
        with self.lock:
            images = self.review["images"]
            for source, verdict in change.get("images", {}).items():
                if verdict is None:
                    images.pop(source, None)
                elif verdict in ("ok", "rejected"):
                    images[source] = verdict
                else:
                    raise ValueError(f"invalid verdict {verdict!r}")
            for key, wrong in change.get("wrongGame", {}).items():
                if wrong:
                    self.review["wrongGame"][key] = True
                else:
                    self.review["wrongGame"].pop(key, None)
            art_verdicts.write(self.root, self.review)
        return self.summary()


def handler(apps):
    class Handler(BaseHTTPRequestHandler):
        def send(self, status, body, content_type):
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def json(self, value, status=200):
            self.send(status, json.dumps(value, ensure_ascii=False).encode("utf-8"), "application/json")

        def app(self, name):
            if name not in apps:
                self.json({"error": f"unknown app {name}"}, 404)
                return None
            return apps[name]

        def do_GET(self):
            parts = self.path.split("?", 1)[0].split("/")
            if self.path == "/":
                self.send(200, PAGE.read_bytes(), "text/html; charset=utf-8")
            elif parts[1:] == ["api", "apps"]:
                self.json([app.summary() for app in apps.values()])
            elif len(parts) == 4 and parts[1:3] == ["api", "games"]:
                app = self.app(parts[3])
                if app:
                    self.json(app.state())
            elif len(parts) > 3 and parts[1] == "art":
                app = self.app(parts[2])
                if not app:
                    return
                file = (app.artwork / "/".join(parts[3:])).resolve()
                if not file.is_relative_to(app.artwork) or not file.is_file():
                    self.json({"error": "no such image"}, 404)
                    return
                self.send(200, file.read_bytes(), mimetypes.guess_type(file.name)[0] or "application/octet-stream")
            else:
                self.json({"error": "not found"}, 404)

        def do_POST(self):
            parts = self.path.split("/")
            if len(parts) != 4 or parts[1:3] != ["api", "review"]:
                self.json({"error": "not found"}, 404)
                return
            app = self.app(parts[3])
            if app:
                change = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                self.json(app.update(change))

        def log_message(self, *args):
            pass

    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("roots", nargs="+", type=Path)
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--open", action="store_true", help="open the page in the browser once serving")
    args = parser.parse_args()
    apps = {}
    for root in args.roots:
        app = App(root)
        apps[app.name] = app
        print(f"{app.name}: {len(app.games)} games with artwork", flush=True)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), handler(apps))
    print(f"Review at http://localhost:{args.port}/", flush=True)
    if args.open:
        webbrowser.open(f"http://localhost:{args.port}/")
    server.serve_forever()


if __name__ == "__main__":
    main()
