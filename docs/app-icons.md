# App icons

Every app's icon is drawn by `tools/generate_app_icon.py` from a preset in
`tools/app_icon/presets/<product>.json`, so a new app's icon is a new preset, not new artwork.

    pip install fonttools numpy pillow resvg-py
    python tools/generate_app_icon.py kairost --out <dir>     # SVG + 1024 and 512 px PNGs
    python tools/generate_app_icon.py kairost --app ../KairoST  # writes the app's icon art and Play icon

`--app` writes `<module>/src/main/res/drawable-nodpi/product_icon_art.png` (1024 px) and
`docs/play-assets/app-icon.png` (512 px).

## A preset

- `theme`: `accent` (stripes, tag, bar and the screen's picture), and optionally `frame`
  (the border; derived from the accent when left out), `background`, `logo` (the "Kairo" color).
- `logo`: `tag`, the text after "Kairo" (`ST`, `64`, `Towns`). The logo shrinks to fit.
- `machine`: parts drawn in order, in a 1000 x 1000 space (see `tools/app_icon/parts.py`):
  - `box`: a front face `box: [x, y, w, h]` with `depth: [dx, dy]` (side and top), `material`
    (`beige`, `charcoal`, `grey`, or a dictionary of colors), optional `screen` (a CRT, `inset` in
    fractions of the face), `stand`, `cable`, and `details` on the face.
  - `keyboard`: its top face `quad` (back-left, back-right, front-right, front-left), `thickness`,
    `rows`, `function_row`, `sections` (`main`, `nav`, `cursor`, `numpad`, `fkeys`) and `keys`
    colors, with `highlight` rows.
  - Details: `text`, `rect`, `panel`, `slot`, `led`, `vents`, `stripes`, placed in fractions of the face.
- `stripes`: the halftone bands behind the machine (`x`, `y` ranges and the `side` they fade toward).
- `bar`: the dashes and bar under the logo; `rainbow` adds colored lines over its right end.
- `screen`: `image`, the picture on the CRT (default `tools/app_icon/mascot.png`), recolored from
  its brightness into the accent; `gamma` brightens it below 1.

The font is Saira (SIL Open Font License, `tools/app_icon/fonts/OFL.txt`); text is drawn as
paths, so a render needs no installed fonts.
