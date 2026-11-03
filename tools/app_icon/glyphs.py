"""Text as SVG paths, so an icon renders the same everywhere without installed fonts."""

import functools
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

FONT = Path(__file__).parent / "fonts" / "Saira-Italic-VF.ttf"


@functools.lru_cache(maxsize=None)
def instance(weight, width):
    return instancer.instantiateVariableFont(TTFont(FONT), {"wght": weight, "wdth": width})


def advance(text, size, weight=800, width=125, tracking=0.0):
    """The width of `text` at `size` (tracking is in ems, added between letters)."""
    font = instance(weight, width)
    cmap, metrics = font.getBestCmap(), font["hmtx"]
    scale = size / font["head"].unitsPerEm
    total = sum(metrics[cmap[ord(ch)]][0] for ch in text) * scale
    return total + tracking * size * (len(text) - 1)


def cap_height(size, weight=800, width=125):
    font = instance(weight, width)
    return font["OS/2"].sCapHeight * size / font["head"].unitsPerEm


def path(text, x, baseline, size, weight=800, width=125, slant=0.0, tracking=0.0):
    """The path data of `text` starting at (x, baseline); `slant` adds horizontal shear."""
    font = instance(weight, width)
    glyphs, cmap, metrics = font.getGlyphSet(), font.getBestCmap(), font["hmtx"]
    scale = size / font["head"].unitsPerEm
    pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
    cursor = x
    for ch in text:
        name = cmap[ord(ch)]
        glyphs[name].draw(TransformPen(pen, (scale, 0, scale * slant, -scale, cursor, baseline)))
        cursor += metrics[name][0] * scale + tracking * size
    return pen.getCommands()
