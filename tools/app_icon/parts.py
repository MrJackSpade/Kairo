"""The machine's parts: boxes (monitor, tower, desktop unit, mouse), the keyboard, and the
details printed or moulded on their front faces.

Every part's coordinates are in the icon's 1000 x 1000 space. A box is its front face
`box: [x, y, w, h]` plus `depth: [dx, dy]`, the receding edge (dx > 0 shows the right side,
dx < 0 the left, dy > 0 the top). Details sit on the front face in fractions of it
(0..1 across, 0..1 down)."""

from . import glyphs
from .svg import bilinear, group, lerp, polygon, rect, tag
from .theme import material, mix

OUTLINE = 5.0


class Context:
    def __init__(self, theme, ids, screen_href):
        self.theme = theme
        self.ids = ids
        self.defs = []
        self.screen_href = screen_href
        self.gradients = {}

    def vertical_gradient(self, top, bottom):
        key = (top, bottom)
        if key not in self.gradients:
            gid = self.ids("vg")
            self.defs.append(tag("linearGradient",
                                 tag("stop", offset="0", stop_color=top) + tag("stop", offset="1", stop_color=bottom),
                                 id=gid, x1="0", y1="0", x2="0", y2="1"))
            self.gradients[key] = gid
        return f"url(#{self.gradients[key]})"


def faces(ctx, mat, x, y, w, h, dx, dy, radius):
    out = []
    stroke = dict(stroke=mat.outline, stroke_width=OUTLINE, stroke_linejoin="round")
    if dy:
        out.append(polygon([(x + radius * 0.5, y), (x + w - radius * 0.5, y), (x + w + dx, y - dy), (x + dx, y - dy)],
                           fill=ctx.vertical_gradient(mix(mat.top, mat.side, 0.25), mat.top), **stroke))
    if dx > 0:
        out.append(polygon([(x + w, y + radius * 0.5), (x + w + dx, y - dy), (x + w + dx, y + h - dy), (x + w, y + h)],
                           fill=ctx.vertical_gradient(mat.side, mix(mat.side, mat.outline, 0.25)), **stroke))
    elif dx < 0:
        out.append(polygon([(x, y + radius * 0.5), (x + dx, y - dy), (x + dx, y + h - dy), (x, y + h)],
                           fill=ctx.vertical_gradient(mat.side, mix(mat.side, mat.outline, 0.25)), **stroke))
    out.append(rect(x, y, w, h, radius, fill=ctx.vertical_gradient(mix(mat.front, mat.top, 0.5), mix(mat.front, mat.side, 0.35)),
                    **stroke))
    # The moulded lip just inside the front face.
    inset = min(w, h) * 0.025
    out.append(rect(x + inset, y + inset, w - 2 * inset, h - 2 * inset, max(radius - inset, 0), fill="none",
                    stroke=mix(mat.front, "#ffffff", 0.35), stroke_width=2.0, stroke_opacity=0.6))
    return out


def text(ctx, spec, fx, fy, color):
    size = spec["size"]
    weight, width = spec.get("weight", 700), spec.get("width", 100)
    span = glyphs.advance(spec["text"], size, weight, width, spec.get("tracking", 0.0))
    anchor = spec.get("anchor", "start")
    start = fx - (span / 2 if anchor == "middle" else span if anchor == "end" else 0)
    return tag("path", d=glyphs.path(spec["text"], start, fy, size, weight, width, spec.get("slant", 0.0),
                                       spec.get("tracking", 0.0)), fill=spec.get("color", color))


def detail(ctx, mat, spec, x, y, w, h):
    kind = spec["type"]
    px, py = x + spec.get("x", 0) * w, y + spec.get("y", 0) * h
    pw, ph = spec.get("w", 0) * w, spec.get("h", 0) * h
    if kind == "text":
        return [text(ctx, spec, px, py, mat.detail)]
    if kind == "rect":
        return [rect(px, py, pw, ph, spec.get("r", 0), fill=spec.get("color", mat.recess),
                     stroke=mat.outline, stroke_width=spec.get("stroke", 2.5))]
    if kind == "panel":  # a recessed panel, such as a drive bay
        return [rect(px, py, pw, ph, spec.get("r", 2), fill=ctx.vertical_gradient(mix(mat.recess, mat.outline, 0.25), mat.recess),
                     stroke=mat.outline, stroke_width=3.0),
                rect(px + 2, py + ph - 3, pw - 4, 2, fill=mix(mat.front, "#ffffff", 0.3), opacity=0.5)]
    if kind == "slot":  # a disk slot: a dark gap with a lit lower lip
        return [rect(px, py, pw, ph, ph / 2, fill=mat.outline),
                rect(px + ph / 2, py + ph * 0.62, pw - ph, ph * 0.3, fill=mix(mat.front, "#ffffff", 0.25), opacity=0.55)]
    if kind == "led":
        color = spec.get("color", "#47e85a")
        return [rect(px - 3, py - 3, pw + 6, ph + 6, 3, fill=color, opacity=0.35),
                rect(px, py, pw, ph, 2, fill=color, stroke=mat.outline, stroke_width=2.0),
                rect(px + pw * 0.15, py + ph * 0.15, pw * 0.4, ph * 0.3, 1, fill="#ffffff", opacity=0.55)]
    if kind == "vents":
        count = spec["count"]
        out = []
        if spec.get("dir", "v") == "v":
            step = pw / count
            for i in range(count):
                out.append(rect(px + i * step + step * 0.2, py, step * 0.55, ph, step * 0.2, fill=mat.outline, opacity=0.8))
        else:
            step = ph / count
            for i in range(count):
                out.append(rect(px, py + i * step + step * 0.25, pw, step * 0.45, step * 0.2, fill=mat.outline, opacity=0.8))
        return out
    if kind == "stripes":  # coloured bands, such as a rainbow badge
        colors = spec["colors"]
        out = []
        if spec.get("dir", "h") == "h":
            step = ph / len(colors)
            out += [rect(px, py + i * step, pw, step + 0.5, fill=c) for i, c in enumerate(colors)]
        else:
            step = pw / len(colors)
            out += [rect(px + i * step, py, step + 0.5, ph, fill=c) for i, c in enumerate(colors)]
        out.append(rect(px, py, pw, ph, fill="none", stroke=mat.outline, stroke_width=2.5))
        return out
    raise ValueError(f"unknown detail type {kind!r}")


def screen(ctx, spec, x, y, w, h):
    """The CRT: a recess in the bezel, the picture in the theme's colors, scanlines and glass."""
    l, t, r, b = spec.get("inset", [0.08, 0.08, 0.08, 0.2])
    sx, sy, sw, sh = x + l * w, y + t * h, w * (1 - l - r), h * (1 - t - b)
    radius = spec.get("radius", 0.07) * min(sw, sh)
    lip = spec.get("lip", 0.035) * w
    out = [rect(sx - lip, sy - lip, sw + 2 * lip, sh + 2 * lip, radius + lip * 0.6,
                fill=ctx.vertical_gradient("#16161c", "#3a3a44"), stroke="#0b0b10", stroke_width=OUTLINE)]
    clip = ctx.ids("scr")
    ctx.defs.append(tag("clipPath", rect(sx, sy, sw, sh, radius), id=clip))
    th = ctx.theme
    vignette = ctx.ids("vig")
    ctx.defs.append(tag("radialGradient",
                        tag("stop", offset="0.55", stop_color="#000000", stop_opacity="0")
                        + tag("stop", offset="1", stop_color="#000000", stop_opacity="0.35"),
                        id=vignette, cx="0.5", cy="0.5", r="0.72"))
    glass = ctx.ids("gl")
    ctx.defs.append(tag("linearGradient",
                        tag("stop", offset="0", stop_color="#ffffff", stop_opacity="0.16")
                        + tag("stop", offset="0.45", stop_color="#ffffff", stop_opacity="0"),
                        id=glass, x1="0", y1="0", x2="0.6", y2="1"))
    scan = ctx.ids("sl")
    pitch = spec.get("scanline", 5.0)
    ctx.defs.append(tag("pattern", rect(0, pitch * 0.55, pitch, pitch * 0.45, fill="#000000", opacity=0.16),
                        id=scan, width=pitch, height=pitch, patternUnits="userSpaceOnUse"))
    picture = [rect(sx, sy, sw, sh, fill=th.screen_dark)]
    if ctx.screen_href:
        picture.append(tag("image", href=ctx.screen_href, x=sx, y=sy, width=sw, height=sh,
                           preserveAspectRatio=spec.get("fit", "xMidYMid slice")))
    picture += [rect(sx, sy, sw, sh, fill=f"url(#{scan})"),
                rect(sx, sy, sw, sh, fill=f"url(#{vignette})"),
                rect(sx, sy, sw, sh, fill=f"url(#{glass})")]
    out.append(group(picture, clip_path=f"url(#{clip})"))
    out.append(rect(sx, sy, sw, sh, radius, fill="none", stroke="#050507", stroke_width=3.0))
    return out


def box(ctx, spec):
    mat = material(spec.get("material", "beige"))
    x, y, w, h = spec["box"]
    dx, dy = spec.get("depth", [0, 0])
    radius = spec.get("radius", 0.03) * min(w, h)
    out = []
    if "cable" in spec:
        d = "M" + " L".join(f"{px:.1f},{py:.1f}" for px, py in spec["cable"])
        out += [tag("path", d=d, fill="none", stroke=mat.outline, stroke_width=11, stroke_linecap="round", stroke_linejoin="round"),
                tag("path", d=d, fill="none", stroke=mix(mat.side, mat.outline, 0.4), stroke_width=5, stroke_linecap="round",
                    stroke_linejoin="round")]
    if "stand" in spec:  # the neck a monitor stands on
        sx, sw, sh = spec["stand"]
        out += faces(ctx, mat, x + sx * w, y + h - 4, sw * w, sh, dx * 0.4, 0, 2)
    out += faces(ctx, mat, x, y, w, h, dx, dy, radius)
    if "screen" in spec:
        out += screen(ctx, spec["screen"], x, y, w, h)
    for item in spec.get("details", []):
        out += detail(ctx, mat, item, x, y, w, h)
    return out


# Keyboard rows, in key units. The main block is 15 units wide.
MAIN_ROWS = [
    [1] * 13 + [2],
    [1.5] + [1] * 12 + [1.5],
    [1.75] + [1] * 11 + [2.25],
    [2.25] + [1] * 10 + [2.75],
    [1.5, 1, 1.5, 7, 1.5, 1, 1.5],
]
FUNCTION_ROW = [1.5] * 10
SECTIONS = {"main": 15.0, "nav": 3.0, "numpad": 4.0, "cursor": 3.0, "fkeys": 1.5}


def section_rows(name, rows):
    if name == "main":
        return MAIN_ROWS[:rows]
    if name == "numpad":
        return [[1, 1, 1, 1]] * rows
    if name == "nav":
        return [[1, 1, 1]] * rows
    if name == "fkeys":  # a column of wide function keys (C64)
        return [[1.5]] * rows
    if name == "cursor":  # an inverted T under blank space
        return [[]] * (rows - 2) + [[(1, 1)], [1, 1, 1]] if rows >= 2 else [[1, 1, 1]]
    raise ValueError(f"unknown keyboard section {name!r}")


def keyboard(ctx, spec):
    """A keyboard slab: its top face is the quad [back-left, back-right, front-right, front-left]."""
    mat = material(spec.get("material", "beige"))
    quad = [tuple(p) for p in spec["quad"]]
    bl, br, fr, fl = quad
    t = spec.get("thickness", 30)
    stroke = dict(stroke=mat.outline, stroke_width=OUTLINE, stroke_linejoin="round")
    out = [polygon([fl, fr, (fr[0], fr[1] + t), (fl[0], fl[1] + t)],
                   fill=ctx.vertical_gradient(mat.front, mix(mat.front, mat.side, 0.6)), **stroke)]
    if spec.get("side", "right") == "right":
        out.append(polygon([br, fr, (fr[0], fr[1] + t), (br[0], br[1] + t)], fill=mat.side, **stroke))
    else:
        out.append(polygon([bl, fl, (fl[0], fl[1] + t), (bl[0], bl[1] + t)], fill=mat.side, **stroke))
    out.append(polygon(quad, fill=ctx.vertical_gradient(mat.top, mix(mat.top, mat.front, 0.7)), **stroke))

    u0, v0, u1, v1 = spec.get("keys_area", [0.04, 0.22, 0.96, 0.92])
    rows = spec.get("rows", 5)
    sections = spec.get("sections", ["main"])
    gap = spec.get("section_gap", 0.5)
    total = sum(SECTIONS[s] for s in sections) + gap * (len(sections) - 1)
    keys = spec.get("keys", {})
    key_top = keys.get("color", mix(mat.top, mat.outline, 0.25))
    key_side = keys.get("side", mix(key_top, mat.outline, 0.55))
    highlights = {(h["section"], h["row"]): h["color"] for h in keys.get("highlight", [])}
    all_rows = rows + (1 if spec.get("function_row") else 0)

    def at(u, v):
        return bilinear(quad, u0 + (u1 - u0) * u, v0 + (v1 - v0) * v)

    def key(ua, va, ub, vb, color, side):
        pad_u, pad_v = 0.08 / total, 0.1 / all_rows
        corners = [at(ua + pad_u, va + pad_v), at(ub - pad_u, va + pad_v), at(ub - pad_u, vb - pad_v), at(ua + pad_u, vb - pad_v)]
        drop = (corners[3][1] - corners[0][1]) * 0.32
        body = [(px, py + drop) for px, py in corners]
        top = [lerp(c, (sum(p[0] for p in corners) / 4, sum(p[1] for p in corners) / 4), 0.12) for c in corners]
        return [polygon([corners[0], corners[1], body[2], body[3]], fill=side, stroke=mat.outline, stroke_width=2.0,
                        stroke_linejoin="round"),
                polygon(top, fill=color)]

    cursor = 0.0
    for name in sections:
        width = SECTIONS[name]
        row_list = section_rows(name, rows)
        if spec.get("function_row"):
            row_list = ([FUNCTION_ROW] if name == "main" else [[]]) + row_list
        for r, row in enumerate(row_list):
            color = highlights.get((name, r), key_top)
            side = mix(color, mat.outline, 0.55) if (name, r) in highlights else key_side
            u = 0.0
            for item in row:
                if isinstance(item, tuple):  # (skip, width)
                    u += item[0]
                    item = item[1]
                ua, ub = (cursor + u) / total, (cursor + u + item) / total
                out += key(ua, r / all_rows, ub, (r + 1) / all_rows, color, side)
                u += item
        cursor += width + gap
    for item in spec.get("details", []):  # details on the top face, in its own fractions
        px, py = bilinear(quad, item.get("x", 0), item.get("y", 0))
        out += detail(ctx, mat, {**item, "x": 0, "y": 0}, px, py, 1, 1) if item["type"] == "text" else \
            detail(ctx, mat, {**item, "x": 0, "y": 0, "w": item["w"], "h": item["h"]}, px, py,
                   abs(br[0] - bl[0]), abs(fl[1] - bl[1]))
    return out


PARTS = {"box": box, "keyboard": keyboard}


def draw(ctx, spec):
    kind = spec.get("type", "box")
    if kind not in PARTS:
        raise ValueError(f"unknown part type {kind!r}")
    return PARTS[kind](ctx, spec)
