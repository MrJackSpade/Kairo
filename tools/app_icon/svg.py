"""Small SVG builders. Every icon is drawn in a 1000 x 1000 space."""

from xml.sax.saxutils import escape

SIZE = 1000


def attrs(**values):
    out = []
    for key, value in values.items():
        if value is None:
            continue
        name = key.rstrip("_").replace("_", "-")
        if isinstance(value, float):
            value = f"{value:.2f}".rstrip("0").rstrip(".")
        out.append(f'{name}="{escape(str(value), {chr(34): "&quot;"})}"')
    return " ".join(out)


def tag(name, content=None, **values):
    head = f"<{name} {attrs(**values)}".rstrip()
    if content is None:
        return head + "/>"
    return f"{head}>{content}</{name}>"


def points(pts):
    return " ".join(f"{x:.2f},{y:.2f}" for x, y in pts)


def polygon(pts, **values):
    return tag("polygon", points=points(pts), **values)


def rect(x, y, w, h, r=0, **values):
    return tag("rect", x=float(x), y=float(y), width=float(w), height=float(h),
               rx=float(r) if r else None, **values)


def group(items, **values):
    return tag("g", "".join(items), **values)


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def bilinear(quad, u, v):
    """A point inside a quad (back-left, back-right, front-right, front-left) at (u, v)."""
    bl, br, fr, fl = quad
    return lerp(lerp(bl, br, u), lerp(fl, fr, u), v)


class Ids:
    """Unique element ids within one icon."""

    def __init__(self):
        self.count = 0

    def __call__(self, prefix):
        self.count += 1
        return f"{prefix}{self.count}"
