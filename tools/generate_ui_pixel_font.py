"""Write the app UI's pixel-font asset from the pinned Spleen 8x16 ASCII table.

The table in third_party/spleen/spleen_ascii_8x16.h already ships in the native
library; this copies the same 95 glyphs (U+0020..U+007E, 16 row bytes each, MSB
is the leftmost pixel) into a 1520-byte asset the Kotlin UI can draw from.
"""

import re
from pathlib import Path

root = Path(__file__).resolve().parent.parent
source = (root / "third_party/spleen/spleen_ascii_8x16.h").read_text(encoding="ascii")
rows = re.findall(r"\{((?:0x[0-9a-f]{2}, ){15}0x[0-9a-f]{2})\}", source)
if len(rows) != 95:
    raise ValueError(f"Expected 95 glyphs, found {len(rows)}")
data = bytes(int(value, 16) for row in rows for value in row.split(", "))
target = root / "frontend/src/main/assets/ui/spleen-8x16-ascii.bin"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(data)
print(f"Wrote {len(data)} bytes to {target.relative_to(root)}")
