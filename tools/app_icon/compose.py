"""The whole icon: frame, halftone speed stripes, the machine, the "Kairo" logo with the app's
tag, and the bar under it."""

import base64
import io
from pathlib import Path

import numpy as np
from PIL import Image

from . import glyphs, parts
from .svg import SIZE, Ids, group, polygon, rect, tag
from .theme import rgb, theme as make_theme

HERE = Path(__file__).parent
SLANT = 0.36  # the shear of the logo, the stripes and the bar


def duotone_href(path, dark, light, gamma=1.0):
    """The picture recoloured from its own brightness: black to `dark`, white to `light`."""
    image = Image.open(path).convert("L")
    lum = np.asarray(image, dtype=np.float64) / 255.0
    lo, hi = np.percentile(lum, 1), np.percentile(lum, 99)
    if hi <= lo:
        raise ValueError(f"{path} has no tonal range")
    t = np.clip((lum - lo) / (hi - lo), 0, 1) ** gamma
    a, b = np.array(rgb(dark)), np.array(rgb(light))
    out = (a + (b - a) * t[..., None]) * 255
    buffer = io.BytesIO()
    Image.fromarray(out.round().astype(np.uint8), "RGB").save(buffer, "PNG")
    return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode("ascii")


def halftone(ids, defs, color, pitch=11.0, radius=3.6):
    pid = ids("ht")
    defs.append(tag("pattern", tag("circle", cx=pitch / 2, cy=pitch / 2, r=radius, fill=color)
                    + tag("circle", cx=0, cy=0, r=radius, fill=color) + tag("circle", cx=pitch, cy=0, r=radius, fill=color)
                    + tag("circle", cx=0, cy=pitch, r=radius, fill=color) + tag("circle", cx=pitch, cy=pitch, r=radius, fill=color),
                    id=pid, width=pitch, height=pitch, patternUnits="userSpaceOnUse", patternTransform="rotate(45)"))
    return f"url(#{pid})"


def sheared(x0, x1, y0, y1, slant=SLANT):
    """A parallelogram leaning right: its bottom edge starts `slant` * height left of its top."""
    run = (y1 - y0) * slant
    return [(x0 + run, y0), (x1 + run, y0), (x1, y1), (x0, y1)]


def frame(th, ids, defs, spec):
    outer = spec.get("radius", 150)
    border = spec.get("border", 34)
    edge = ids("edge")
    defs.append(tag("linearGradient",
                    tag("stop", offset="0", stop_color=th.frame) + tag("stop", offset="1", stop_color=_shade(th.frame, 0.55)),
                    id=edge, x1="0", y1="0", x2="0", y2="1"))
    glow = ids("bg")
    defs.append(tag("radialGradient",
                    tag("stop", offset="0", stop_color=_shade(th.background, 1.9))
                    + tag("stop", offset="1", stop_color=th.background),
                    id=glow, cx="0.5", cy="0.4", r="0.7"))
    inner = outer - border * 0.8
    return [rect(4, 4, SIZE - 8, SIZE - 8, outer, fill=f"url(#{edge})", stroke=th.ink, stroke_width=6),
            rect(border, border, SIZE - 2 * border, SIZE - 2 * border, inner, fill=f"url(#{glow})",
                 stroke=th.ink, stroke_width=5)], (border, inner)


def _shade(color, factor):
    r, g, b = rgb(color)
    return "#%02x%02x%02x" % tuple(round(max(0, min(1, c * factor)) * 255) for c in (r, g, b))


def stripes(th, ids, defs, spec):
    dots = halftone(ids, defs, th.accent)
    out = []
    for band in spec:
        pts = sheared(*band["x"], *band["y"])
        solid = band.get("solid", 0.45)  # the share of the band, from its inner end, painted solid
        x0, x1 = band["x"]
        inner_end = x1 if band.get("side", "left") == "left" else x0
        outer_end = x0 if inner_end == x1 else x1
        gid = ids("sg")
        defs.append(tag("linearGradient",
                        tag("stop", offset="0", stop_color="#ffffff", stop_opacity="1")
                        + tag("stop", offset=f"{solid:.2f}", stop_color="#ffffff", stop_opacity="1")
                        + tag("stop", offset=f"{min(solid + 0.12, 1):.2f}", stop_color="#ffffff", stop_opacity="0"),
                        id=gid, gradientUnits="userSpaceOnUse", x1=inner_end, y1=0, x2=outer_end, y2=0))
        mid = ids("sm")
        defs.append(tag("mask", polygon(pts, fill=f"url(#{gid})"), id=mid))
        out.append(polygon(pts, fill=dots))
        out.append(group([polygon(pts, fill=th.accent)], mask=f"url(#{mid})"))
    return out


def logo(th, spec):
    word, tagline = "Kairo", spec["tag"]
    weight, width = spec.get("weight", 800), spec.get("width", 125)
    tracking = spec.get("tracking", -0.02)
    size = spec.get("size", 235)
    max_width = spec.get("max_width", 860)
    span = glyphs.advance(word + tagline, size, weight, width, tracking)
    if span > max_width:
        size *= max_width / span
        span = max_width
    baseline = spec.get("baseline", 880)
    x = spec.get("center", 492) - span / 2 + glyphs.cap_height(size, weight, width) * SLANT * 0.5
    word_width = glyphs.advance(word, size, weight, width, tracking) + tracking * size
    paths = [(glyphs.path(word, x, baseline, size, weight, width, SLANT * 0.55, tracking), th.logo),
             (glyphs.path(tagline, x + word_width, baseline, size, weight, width, SLANT * 0.55, tracking), th.accent)]
    out = []
    for d, _ in paths:  # the drop shadow and the heavy outline under both words
        out.append(tag("path", d=d, fill=th.ink, stroke=th.ink, stroke_width=22, stroke_linejoin="round",
                       transform="translate(5 8)"))
    for d, _ in paths:
        out.append(tag("path", d=d, fill=th.ink, stroke=th.ink, stroke_width=14, stroke_linejoin="round"))
    for d, color in paths:
        out.append(tag("path", d=d, fill=color))
    return out, (x, baseline, span, size)


def bar(th, spec, logo_box):
    x, baseline, span, size = logo_box
    y0 = baseline + spec.get("gap", 22)
    y1 = y0 + spec.get("height", 28)
    left = spec.get("left", x - 10)
    right = spec.get("right", x + span + 10)
    out = []
    dash, gap, count = spec.get("dash", 30), spec.get("dash_gap", 12), spec.get("dashes", 4)
    cursor = left
    for _ in range(count):
        out.append(polygon(sheared(cursor, cursor + dash, y0, y1), fill=th.accent, stroke=th.ink, stroke_width=3))
        cursor += dash + gap
    out.append(polygon(sheared(cursor + gap, right, y0, y1), fill=th.accent, stroke=th.ink, stroke_width=3))
    rainbow = spec.get("rainbow")
    if rainbow:  # thin coloured lines over the bar's right end
        rx0 = right - spec.get("rainbow_width", 260)
        step = (y1 - y0) / len(rainbow)
        for i, color in enumerate(rainbow):
            out.append(polygon(sheared(rx0 + (len(rainbow) - i) * step * SLANT, right + (len(rainbow) - i) * step * SLANT,
                                       y0 - (y1 - y0) * 1.1 + i * step, y0 - (y1 - y0) * 1.1 + (i + 1) * step), fill=color))
    return out


def grain(ids, defs, strength, seed):
    fid = ids("grain")
    defs.append(tag("filter",
                    tag("feTurbulence", type="fractalNoise", baseFrequency="0.85", numOctaves="2", seed=str(seed), result="noise")
                    + tag("feColorMatrix", in_="noise", type="matrix",
                          values=f"0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  {strength * 3:.3f} 0 0 0 {-strength * 1.2:.3f}", result="speck")
                    + tag("feComposite", in_="speck", in2="SourceAlpha", operator="in", result="specks")
                    + tag("feMerge", tag("feMergeNode", in_="SourceGraphic") + tag("feMergeNode", in_="specks")),
                    id=fid, x="0", y="0", width=str(SIZE), height=str(SIZE), filterUnits="userSpaceOnUse"))
    return f"url(#{fid})"


def shadow(ids, defs):
    fid = ids("shadow")
    defs.append(tag("filter", tag("feDropShadow", dx="0", dy="10", stdDeviation="9", flood_color="#000000", flood_opacity="0.75"),
                    id=fid, x="-0.1", y="-0.1", width="1.2", height="1.3"))
    return f"url(#{fid})"


def icon_svg(preset, preset_dir):
    th = make_theme(preset["theme"])
    ids = Ids()
    defs = []
    screen_path = preset.get("screen", {}).get("image", "mascot.png")
    screen_file = (preset_dir / screen_path) if not Path(screen_path).is_absolute() else Path(screen_path)
    if not screen_file.exists():
        screen_file = HERE / screen_path
    href = duotone_href(screen_file, th.screen_dark, th.screen_light, preset.get("screen", {}).get("gamma", 0.6))

    body, (border, inner) = frame(th, ids, defs, preset.get("frame", {}))
    clip = ids("inner")
    defs.append(tag("clipPath", rect(border, border, SIZE - 2 * border, SIZE - 2 * border, inner), id=clip))
    inside = stripes(th, ids, defs, preset.get("stripes", DEFAULT_STRIPES))

    ctx = parts.Context(th, ids, href)
    machine = []
    for part in preset["machine"]:
        machine += parts.draw(ctx, part)
    defs += ctx.defs
    inside.append(group([group(machine, filter=grain(ids, defs, 0.3, 7))], filter=shadow(ids, defs)))

    logo_items, logo_box = logo(th, preset["logo"])
    inside += logo_items
    inside += bar(th, preset.get("bar", {}), logo_box)
    body.append(group(inside, clip_path=f"url(#{clip})"))
    overall = group(body, filter=grain(ids, defs, 0.08, 3))
    content = tag("defs", "".join(defs)) + overall
    return (f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
            f'width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}">{content}</svg>')


DEFAULT_STRIPES = [
    {"x": [40, 175], "y": [235, 460], "side": "left"},
    {"x": [40, 150], "y": [480, 640], "side": "left"},
    {"x": [835, 965], "y": [275, 470], "side": "right"},
    {"x": [820, 960], "y": [490, 650], "side": "right"},
]
