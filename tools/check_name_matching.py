"""Fails when first-party run-time Kotlin matches games or disks by name (Kairo#105).

Run time finds games and disks by content hash only; name heuristics (Side A, Disk 1, a boot disk
by its file name) belong in catalog generation, baked into the catalog. Two 1.4.2 bugs were exactly
this and were found only by reading code: Kairo98#110 paired boot floppies by the pattern
"boot disk", KairoDos#126 picked a game's record by its folder's name (`variants`).

    python3 shared/tools/check_name_matching.py <app root>

It reads every src/main Kotlin file under the root (an app's modules and its shared frontend; not
tools/, tests or the UI tests) with comments removed, and reports each line that
  - calls contains/startsWith/endsWith/equals/matches on a library entry's or file's name
    (displayName, title, fileName, zipEntry), or gives one to a regex;
  - holds a string with a disk-number, disk-side or boot-disk pattern (Disk 1, Side A, (Boot, boot disk);
  - picks a catalog record by a name (a JSON lookup keyed by fileName, displayName, title or a
    variant name).
A use that only shows or searches a name goes in ALLOWED (shared) or the app's
tools/name_matching_allowed.txt, as `<path>|<code on the line>|<why>`, so a new match has to be
justified. Each allowed entry must still match a line: a stale one fails too.
"""
import re
import sys
from pathlib import Path

NAME = r"\b(?:displayName|title|fileName|zipEntry)\b"
RULES = [
    ("a name compared", re.compile(NAME + r"\s*(?:\?\.|\.|!!\.)\s*(?:lowercase\(\)\s*\.\s*|uppercase\(\)\s*\.\s*)?"
                                   r"(?:contains|startsWith|endsWith|equals|matches)\(")),
    ("a name given to a regex", re.compile(r"\.(?:containsMatchIn|matchEntire|matches|find|findAll)\([^)]*" + NAME)),
    ("a disk-number, side or boot-disk pattern", re.compile(
        r'"(?:[^"\\]|\\.)*(?i:disk[\s_\-\\\[\]sd*?.]*(?:\d|\\d)|side[\s_\-\\\[\]s*?]*(?:[ab]\b|\[ab\])|\(boot|boot[\s_\-\\\[\]s*?]+dis[kc])(?:[^"\\]|\\.)*"')),
    ("a record picked by a name", re.compile(r"\b(?:opt|get)JSON(?:Object|Array)\([^)]*\b(?:fileName|displayName|title|folderName|variantName)\b|\bvariantName\b")),
]

# Uses in the shared frontend that only show or search a name, or that serve a developer; paths
# within the shared checkout.
ALLOWED = [
    ("frontend/src/main/java/com/mrjackspade/kairo/frontend/LibraryScreen.kt",
     "it.displayName.equals(needle, ignoreCase = true)",
     "the debug ADB selector and debug launch: a developer names the game to open"),
    ("frontend/src/main/java/com/mrjackspade/kairo/frontend/LibraryScreen.kt",
     "metadata(it).title.equals(needle, ignoreCase = true)",
     "the debug ADB selector and debug launch: a developer names the game to open"),
    ("frontend/src/main/java/com/mrjackspade/kairo/frontend/LibraryScreen.kt",
     "entries[it].displayName.contains(needle, ignoreCase = true)",
     "the debug ADB selector: a developer names the game to open"),
    ("frontend/src/main/java/com/mrjackspade/kairo/frontend/LibraryScreen.kt",
     "it.displayName.contains(needle, ignoreCase = true)",
     "debug launch: a developer names the game to start"),
    ("frontend/src/main/java/com/mrjackspade/kairo/frontend/LibrarySearchIndex.kt",
     "row.fileName.contains(query, true)",
     "library search: the player's own query, matched against what each row shows"),
]

SKIPPED_PARTS = {"build", ".build", "third_party", "uitest", "test", "androidTest", "tools", ".gradle"}


def strip_comments(text):
    """The text with // and /* */ comments blanked, strings and line numbers kept."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if text.startswith('"""', i):
            end = text.find('"""', i + 3)
            end = n if end < 0 else end + 3
            out.append(text[i:end])
            i = end
        elif c == '"':
            j = i + 1
            while j < n and text[j] != '"' and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            out.append(text[i:j + 1])
            i = j + 1
        elif c == "'" and i + 2 < n and (text[i + 2] == "'" or text[i + 1] == "\\"):
            j = text.find("'", i + 2 if text[i + 1] == "\\" else i + 1)
            out.append(text[i:j + 1])
            i = j + 1
        elif text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j < 0 else j
            i = j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append("\n" * text.count("\n", i, j))
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


def sources(root):
    for path in sorted(root.rglob("*.kt")):
        parts = path.relative_to(root).parts
        if "src" not in parts or parts[parts.index("src") + 1] != "main":
            continue
        if SKIPPED_PARTS.intersection(parts[:parts.index("src")]) or any(part.startswith(".") for part in parts):
            continue
        yield path


def allowed_entries(root):
    # In an app the shared checkout is shared/; the shared repository is its own root.
    shared = "shared/" if (root / "shared" / "frontend").is_dir() else "" if (root / "frontend").is_dir() else None
    entries = [] if shared is None else [(shared + path, code, why) for path, code, why in ALLOWED]
    own = root / "tools" / "name_matching_allowed.txt"
    if own.exists():
        for number, line in enumerate(own.read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip() or line.startswith("#"):
                continue
            fields = line.split("|")
            if len(fields) != 3 or not all(field.strip() for field in fields):
                raise SystemExit(f"{own}:{number}: an entry is <path>|<code on the line>|<why>")
            entries.append(tuple(field.strip() for field in fields))
    return entries


def check(root):
    """The findings under [root] that no allowed entry covers, and the allowed entries nothing matched."""
    allowed = allowed_entries(root)
    used = set()
    findings = []
    for path in sources(root):
        name = path.relative_to(root).as_posix()
        code = strip_comments(path.read_text(encoding="utf-8"))
        for number, line in enumerate(code.splitlines(), 1):
            for what, rule in RULES:
                if not rule.search(line):
                    continue
                covering = [entry for entry in allowed if entry[0] == name and entry[1] in line]
                if covering:
                    used.update(covering)
                else:
                    findings.append(f"{name}:{number}: {what}: {line.strip()}")
    stale = [entry for entry in allowed if entry not in used]
    return findings, stale


def main(argv):
    if len(argv) != 2:
        raise SystemExit("usage: check_name_matching.py <app root>")
    root = Path(argv[1]).resolve()
    findings, stale = check(root)
    for finding in findings:
        print(finding, file=sys.stderr)
    for path, code, _ in stale:
        print(f"{path}: an allowed name match no longer matches any line: {code}", file=sys.stderr)
    if findings:
        print("Run time finds games and disks by content hash only (Kairo#105); name heuristics belong in the "
              "catalog generator. A use that only shows or searches a name goes in the allowlist with its reason.",
              file=sys.stderr)
    return 1 if findings or stale else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
