"""Colors of one icon: the app's accent, the frame and background derived from it, and the
materials its machine is drawn in."""

import colorsys
from dataclasses import dataclass


def rgb(hex_color):
    value = hex_color.lstrip("#")
    if len(value) != 6:
        raise ValueError(f"color {hex_color!r} is not #RRGGBB")
    return tuple(int(value[i:i + 2], 16) / 255 for i in (0, 2, 4))


def hex_of(r, g, b):
    return "#%02x%02x%02x" % tuple(round(max(0.0, min(1.0, c)) * 255) for c in (r, g, b))


def with_hls(hex_color, lightness=None, saturation=None, hue_shift=0.0):
    h, l, s = colorsys.rgb_to_hls(*rgb(hex_color))
    return hex_of(*colorsys.hls_to_rgb((h + hue_shift) % 1.0,
                                       l if lightness is None else lightness,
                                       s if saturation is None else saturation))


def mix(a, b, t):
    ca, cb = rgb(a), rgb(b)
    return hex_of(*(x + (y - x) * t for x, y in zip(ca, cb)))


@dataclass(frozen=True)
class Material:
    front: str
    side: str
    top: str
    outline: str
    recess: str
    detail: str


MATERIALS = {
    # Warm computer plastic, the beige of the 8- and 16-bit machines.
    "beige": Material(front="#d6cfc1", side="#a59d90", top="#e9e3d7", outline="#24221f",
                      recess="#8f887c", detail="#4a463f"),
    # Dark grey plastic (X68000, MSX).
    "charcoal": Material(front="#3c3d4a", side="#2a2b36", top="#50515f", outline="#0c0c12",
                         recess="#22232c", detail="#9a9aa8"),
    # Light grey (FM Towns, late PCs).
    "grey": Material(front="#c9c9c6", side="#999996", top="#deded9", outline="#202022",
                     recess="#85857f", detail="#45454a"),
}


def material(spec):
    """A material by name, or a dictionary naming every color."""
    if isinstance(spec, str):
        return MATERIALS[spec]
    return Material(**spec)


@dataclass(frozen=True)
class Theme:
    accent: str
    frame: str
    background: str
    logo: str
    ink: str

    @property
    def screen_dark(self):
        return with_hls(self.accent, lightness=0.07)

    @property
    def screen_light(self):
        h, l, s = colorsys.rgb_to_hls(*rgb(self.accent))
        return hex_of(*colorsys.hls_to_rgb(h, max(l, 0.62), min(1.0, s * 1.1)))


def theme(spec):
    """The theme from a preset's "theme": the accent alone, or any color given explicitly."""
    accent = spec["accent"]
    return Theme(
        accent=accent,
        frame=spec.get("frame", with_hls(accent, lightness=0.26, saturation=0.45)),
        background=spec.get("background", "#0c0e17"),
        logo=spec.get("logo", "#f2ecdf"),
        ink=spec.get("ink", "#090a10"),
    )
