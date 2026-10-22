"""A game's disk sets, so the library shows one entry per game instead of one per disk.

A set is the disks of one archive, or archives that differ only from their disk tag on, as
TOSEC names each disk "Title (Year)(Publisher)(Disk 2 of 3)(Side B).zip". In a set:
  - alternate dumps (in an `alts/` folder or tagged `[Alt N]`), user disks (the blank disk a
    game saves to) and utility disks never start the game;
  - the entry disk is the set's first by its disk tag, preferring the kinds the app boots from
    first (a CD before a floppy);
  - every other disk is hidden behind the entry (`setDisks`), and one companion disk can be
    inserted beside it at launch (`media`), by the app's [companion] rule.
Alternate dumps of a whole set ("Title [FD] [Set 2].7z") keep their own entries, but the first
set's entry hides them, so the library shows one entry while it has that set and the next otherwise.
"""
import re

# "(Disk 2)", "(Disk B)", "(Disk 2 of 3)", "(Side A)", "(FD1)"
DISK_TAG = re.compile(r"\(\s*(?:disk|disc|side|fd)\s*([0-9]{1,2}|[a-z])\b(?:\s*of\s*\d+)?[^)]*\)", re.I)
USER_DISK = re.compile(r"user\s*disk|\+\s*userdisk|save\s*disk", re.I)
# Untagged disks by the role in their name: the disk a game starts from, its opening, then the rest.
ROLES = [re.compile(r"\b(?:boot|system|program|master|main|game|player)\b", re.I),
         re.compile(r"\b(?:opening|title)\b", re.I), None,
         re.compile(r"\bscenario\b", re.I), re.compile(r"\bdata\b", re.I)]
ALTERNATE = re.compile(r"(?:^|/)alts/|\[alt\b", re.I)
UTILITY = re.compile(r"\(\s*utility\b", re.I)
# Neo Kobe's "[Set 2]" and TOSEC's dump flags: [a] alternate, [b] bad, [h] hack, [t] trainer,
# [f] fix, [o] overdump, [cr] crack, each with an optional number or note.
DUMP_SET = re.compile(r"\s*\[(set\s*\d+|(?:a|b|h|t|f|o|cr)(?:\d+|\s[^\]]*)?)\]", re.I)
# How far a flag is from a clean dump: a numbered set by its number, then alternates, fixes and bad dumps last.
DUMP_RANK = {"a": 100, "f": 200, "o": 300, "t": 400, "h": 500, "cr": 600, "b": 900}


def kind(content_id):
    """The media kind of a content ID such as sha256-fd-v1:..., here fd."""
    return content_id.split("-")[1]


def _role(name):
    """The rank of an untagged disk's role in [ROLES]; 2 for a name that names none."""
    return next((rank for rank, role in enumerate(ROLES) if role and role.search(name)), 2)


def _order(name):
    """Sorts by disk tag: numbers by value, letters by position; untagged names after, by role then name."""
    match = DISK_TAG.search(name)
    if not match:
        return (1, _role(name), name.lower())
    token = match.group(1).lower()
    return (0, int(token) if token.isdigit() else ord(token) - ord("a") + 1, name.lower())


def _set_key(archive):
    """(folder, name): the archive's name up to its disk tag, with the dump flags TOSEC writes after it."""
    folder, _, base = archive.rpartition("/")
    match = DISK_TAG.search(base)
    if not match:
        return folder, base.strip().lower()
    flags = "".join(f"[{flag}]" for flag in DUMP_SET.findall(base[match.end():]))
    return folder, (base[:match.start()].strip() + flags).lower()


def _dump_set(key):
    """(the set's name without its dump flags, how far it is from a clean dump: 0 for none) for a set key."""
    folder, base = key
    rank = 0
    for flag in DUMP_SET.findall(base):
        flag = flag.lower()
        if flag.startswith("set"):
            rank += int(flag[3:].strip())
        else:
            letter = "cr" if flag.startswith("cr") else flag[0]
            digits = re.match(r"[a-z]+(\d*)", flag).group(1)
            rank += DUMP_RANK[letter] + (int(digits) if digits else 0)
    return (folder, DUMP_SET.sub("", base)), rank


def disk_sets(disks, boot_kinds, companion):
    """The disk sets of one game's [disks], each {archive, member, contentId}. [boot_kinds] lists
    the kinds the app starts from, preferred first; [companion] maps an entry's kind to (the kind
    of disk inserted beside it, its media role, whether a user disk may be that disk). Returns
    {entry content ID: {"setDisks": [...], "media": [...]}} for each entry that hides other disks."""
    sets = {}
    for disk in disks:
        sets.setdefault(_set_key(disk["archive"]), []).append(disk)
    found = {}
    dump_sets = {}  # each set name's [(dump number, entry)]
    for set_key, members in sets.items():
        name = lambda disk: disk["member"] or disk["archive"].rpartition("/")[2]
        alternates = [d for d in members if ALTERNATE.search(name(d))]
        users = [d for d in members if d not in alternates and USER_DISK.search(name(d))]
        mains = [d for d in members if d not in alternates and d not in users and not UTILITY.search(name(d)) and
                 kind(d["contentId"]) in boot_kinds]
        if not mains:
            continue
        mains.sort(key=lambda d: (boot_kinds.index(kind(d["contentId"])), _order(name(d))))
        entry = mains[0]["contentId"]
        group, number = _dump_set(set_key)
        dump_sets.setdefault(group, []).append((number, entry))
        record = found.setdefault(entry, {"setDisks": set(), "media": []})
        record["setDisks"] |= {disk["contentId"] for disk in members} - {entry}
        rule = companion.get(kind(entry))
        if rule and not record["media"]:
            other_kind, role, user_allowed = rule
            # Another disk the game starts from is another version of it, never its second disk.
            starts = lambda d: not DISK_TAG.search(name(d)) and _role(name(d)) == 0
            candidates = [d for d in sorted(mains[1:], key=lambda d: _order(name(d)))
                          if kind(d["contentId"]) == other_kind and d["contentId"] != entry and
                          not (starts(d) and starts(mains[0]))]
            if user_allowed:
                candidates += [d for d in sorted(users, key=lambda d: _order(name(d)))
                               if kind(d["contentId"]) == other_kind]
            if candidates:
                record["media"] = [{"role": role, "contentId": candidates[0]["contentId"]}]
    # The first dump set's entry hides the other sets' entries.
    firsts = set()
    for group in dump_sets.values():
        group.sort()
        firsts.add(group[0][1])
        found[group[0][1]]["setDisks"] |= {entry for _, entry in group[1:]}
    # A disk that starts a first set is never hidden by another set.
    result = {}
    for entry, record in sorted(found.items()):
        hidden = sorted(record["setDisks"] - firsts - {entry})
        if hidden or record["media"]:
            result[entry] = {"setDisks": hidden, **({"media": record["media"]} if record["media"] else {})}
    return result
