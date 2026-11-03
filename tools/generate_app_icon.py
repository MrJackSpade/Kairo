#!/usr/bin/env python3
"""Draw a Kairo app icon from its preset (tools/app_icon/presets/<app>.json): the frame and
stripes in the app's colors, its machine with the mascot on the screen, and "Kairo" + its tag.

    python tools/generate_app_icon.py kairost --out <dir>
        writes <dir>/kairost.svg, kairost-1024.png and kairost-512.png
    python tools/generate_app_icon.py kairost --app ../KairoST
        also writes the app's icon art (<module>/src/main/res/drawable-nodpi/product_icon_art.png,
        1024 px) and its Play icon (docs/play-assets/app-icon.png, 512 px)

A preset is JSON: "theme" (accent, optional frame/background/logo colors), "logo" (tag),
"machine" (parts, see app_icon/parts.py), optional "stripes", "bar", "frame" and "screen"
(image: a picture shown on the CRT in the theme's colors; default app_icon/mascot.png).
Requires fontTools, numpy, Pillow and resvg-py.
"""

import argparse
import json
import sys
from pathlib import Path

import resvg_py

sys.path.insert(0, str(Path(__file__).parent))
from app_icon.compose import icon_svg  # noqa: E402

PRESETS = Path(__file__).parent / "app_icon" / "presets"
ICON_ART = "src/main/res/drawable-nodpi/product_icon_art.png"
PLAY_ICON = "docs/play-assets/app-icon.png"


def load_preset(name):
    path = Path(name)
    if not path.suffix:
        path = PRESETS / f"{name}.json"
    with open(path, encoding="utf-8") as handle:
        return json.load(handle), path.parent


def render(svg, size):
    return bytes(resvg_py.svg_to_bytes(svg_string=svg, width=size, height=size, skip_system_fonts=True))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("preset", help="a preset name (tools/app_icon/presets) or a JSON file")
    parser.add_argument("--out", type=Path, help="directory for the SVG and PNG renders")
    parser.add_argument("--size", type=int, action="append", help="PNG sizes for --out (default 1024 and 512)")
    parser.add_argument("--app", type=Path, help="an app checkout whose icon art and Play icon to write")
    args = parser.parse_args()
    if not args.out and not args.app:
        parser.error("give --out, --app or both")

    preset, preset_dir = load_preset(args.preset)
    svg = icon_svg(preset, preset_dir)
    name = Path(args.preset).stem
    if args.out:
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / f"{name}.svg").write_text(svg, encoding="utf-8")
        for size in args.size or [1024, 512]:
            target = args.out / f"{name}-{size}.png"
            target.write_bytes(render(svg, size))
            print(target.resolve())
    if args.app:
        module = args.app / preset["module"]
        if not module.is_dir():
            raise SystemExit(f"{module} is not the app's module (preset \"module\")")
        art, play = module / ICON_ART, args.app / PLAY_ICON
        # A new app has no docs/play-assets yet.
        play.parent.mkdir(parents=True, exist_ok=True)
        art.write_bytes(render(svg, 1024))
        play.write_bytes(render(svg, 512))
        print(art.resolve())
        print(play.resolve())


if __name__ == "__main__":
    main()
