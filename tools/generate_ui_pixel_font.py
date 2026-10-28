"""Write the app UI's pixel-font asset from the pinned Spleen 8x16 and GNU Unifont glyphs.

Every glyph Spleen draws (Latin, Latin-1, Latin Extended-A, Greek, Cyrillic and symbols) keeps
the app's Spleen look; Unifont supplies what Spleen lacks in the blocks the UI's languages use:
punctuation and symbols, kana and CJK, Hangul, and full-width forms. Arabic is drawn in the system
font (PixelTextView), so it has no glyphs here.

frontend/src/main/assets/ui/pixel-font.bin, big-endian:
  "KPF1", glyph count N (u32), N code points (u32, ascending), N widths (u8: 8 or 16 pixels),
  then each glyph's 16 rows in that order, 1 byte per row for width 8 and 2 for width 16,
  the most significant bit the leftmost pixel.
"""

import gzip
import re
import struct
from pathlib import Path

root = Path(__file__).resolve().parent.parent
SPLEEN = root / "third_party/spleen/spleen-8x16.bdf"
UNIFONT = root / "third_party/unifont/unifont_all-17.0.05.hex.gz"
TARGET = root / "frontend/src/main/assets/ui/pixel-font.bin"
HEIGHT = 16

# Blocks Unifont fills where Spleen has no glyph.
UNIFONT_BLOCKS = [
    (0x0080, 0x024F),  # Latin-1 Supplement to Latin Extended-B
    (0x0370, 0x04FF),  # Greek and Cyrillic
    (0x1E00, 0x1EFF),  # Latin Extended Additional
    (0x2000, 0x2BFF),  # punctuation, symbols, arrows
    (0x3000, 0x30FF),  # CJK punctuation, hiragana, katakana
    (0x31F0, 0x31FF),  # katakana extensions
    (0x3200, 0x33FF),  # enclosed CJK letters and compatibility
    (0x4E00, 0x9FFF),  # CJK unified ideographs
    (0xAC00, 0xD7A3),  # Hangul syllables
    (0xFF00, 0xFFEF),  # half-width and full-width forms
]


def spleen():
    """Each Spleen code point's 16 row bytes; every Spleen glyph is a full 8x16 cell."""
    glyphs, code, rows = {}, None, None
    for line in SPLEEN.read_text(encoding="ascii").splitlines():
        if line.startswith("ENCODING "):
            code = int(line.split()[1])
        elif line.startswith("BBX ") and line.split()[1:3] != ["8", "16"]:
            raise ValueError(f"U+{code:04X} is not an 8x16 cell: {line}")
        elif line == "BITMAP":
            rows = []
        elif line == "ENDCHAR":
            if len(rows) != HEIGHT:
                raise ValueError(f"U+{code:04X} has {len(rows)} rows")
            glyphs[code] = (8, bytes(rows))
            rows = None
        elif rows is not None:
            rows.append(int(line, 16))
    return glyphs


def unifont():
    glyphs = {}
    with gzip.open(UNIFONT, "rt", encoding="ascii") as source:
        for line in source:
            code, bits = line.strip().split(":")
            code = int(code, 16)
            if not any(first <= code <= last for first, last in UNIFONT_BLOCKS):
                continue
            if len(bits) not in (32, 64):
                raise ValueError(f"U+{code:04X} is {len(bits)} hex digits")
            # 32 hex digits are 8 pixels by 16 rows, 64 are 16 by 16.
            glyphs[code] = (len(bits) * 4 // HEIGHT, bytes.fromhex(bits))
    return glyphs


def main():
    glyphs = unifont()
    own = spleen()
    glyphs.update(own)  # Spleen wins wherever it has a glyph.
    # The ASCII table the native library ships is the same Spleen.
    ascii_rows = re.findall(r"\{((?:0x[0-9a-f]{2}, ){15}0x[0-9a-f]{2})\}",
                            (root / "third_party/spleen/spleen_ascii_8x16.h").read_text(encoding="ascii"))
    for index, row in enumerate(ascii_rows):
        expected = bytes(int(value, 16) for value in row.split(", "))
        if own[0x20 + index][1] != expected:
            raise ValueError(f"Spleen's BDF and ASCII table differ at U+{0x20 + index:04X}")
    codes = sorted(glyphs)
    data = bytearray(b"KPF1")
    data += struct.pack(">I", len(codes))
    data += struct.pack(f">{len(codes)}I", *codes)
    data += bytes(glyphs[code][0] for code in codes)
    for code in codes:
        width, rows = glyphs[code]
        if len(rows) != width // 8 * HEIGHT:
            raise ValueError(f"U+{code:04X} has {len(rows)} bytes for width {width}")
        data += rows
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_bytes(data)
    print(f"Wrote {len(codes)} glyphs, {len(data)} bytes, to {TARGET.relative_to(root)}")


if __name__ == "__main__":
    main()
