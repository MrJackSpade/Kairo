"""A game's disk sets, so the library shows one entry per game instead of one per disk.

A set is the disks of one archive, or archives that differ only from their disk tag on, as
TOSEC names each disk "Title (Year)(Publisher)(Disk 2 of 3)(Side B).zip". In a set:
  - alternate dumps (in an `alts/` folder or tagged `[Alt N]`), user disks (the blank disk a
    game saves to), utility disks and data disks (the app's [data_disk]: a disk nothing on it can
    start, as a game's second disk read sector by sector) never start the game;
  - the entry disk is the set's first by its disk tag, preferring the kinds the app boots from
    first (a CD before a floppy), and a disk that works over one the app's [broken_disk] names
    (a disk that stops on every system the app has); a set of only broken disks keeps its order;
  - every other disk is hidden behind the entry (`setDisks`), and one companion disk can be
    inserted beside it at launch (`media`), by the app's [companion] rule.
Alternate dumps of a whole set ("Title [FD] [Set 2].7z") keep their own entries, but the first
set's entry hides them, so the library shows one entry while it has that set and the next otherwise.
Likewise a game's other releases in its folder (a demo, a music disk, a hack, another version, a
course or data volume) hide behind its main release, the one whose name adds the least to the
title; they stay on the game's Disks page, which offers the disks of its folder.
"""
import re

# "(Disk 2)", "(Disk B)", "(Disk 2 of 3)", "(Side A)", "(FD1)"
DISK_TAG = re.compile(r"\(\s*(?:disk|disc|side|fd)\s*([0-9]{1,2}|[a-z])\b(?:\s*of\s*\d+)?[^)]*\)", re.I)
USER_DISK = re.compile(r"user\s*disk|\+\s*userdisk|save\s*disk", re.I)
# Untagged disks by the role in their name: the disk a game starts from, its opening, then the rest.
ROLES = [re.compile(r"\b(?:boot|system|program|master|main|game|player)\b", re.I),
         re.compile(r"\b(?:opening|title)\b", re.I), None,
         re.compile(r"\bscenario\b", re.I), re.compile(r"\bdata\b", re.I)]
# A tagged disk named as the one the game starts from beside its number: "(Disk 3) (Boot)".
BOOT_NOTE = re.compile(r"\(\s*(?:boot|system|program)(?:\s+disk)?\s*\)", re.I)
ALTERNATE = re.compile(r"(?:^|/)alts/|\[alt\b", re.I)
UTILITY = re.compile(r"\(\s*utility\b", re.I)
# A note that marks a disk as a damaged or altered copy: "(invincibility hack)", "[bad sectors]", "[b]".
FLAW = re.compile(r"\s*[(\[][^)\]]*\b(?:hack|cheats?|invincib\w*|trainer|crack(?:ed)?|bad sectors)\b[^)\]]*[)\]]|"
                  r"\s*\[b\d*\]", re.I)
# A note that names a disk as one, as "(Map disk)" does.
ROLE_DISK = re.compile(r"\([^)]*\bdisks?\b[^)]*\)", re.I)
DEMO = re.compile(r"\([^)]*\b(?:demo|demonstration|trial|sample)\b[^)]*\)", re.I)
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
    """Sorts a disk the game starts from first: an untagged boot, system or program disk, or a tagged one
    whose name says so ("(Disk 3) (Boot)"); then by disk tag: numbers by value, letters by position;
    other untagged names after, by role. A damaged or altered copy follows the clean disk of its place,
    then names break ties."""
    flawed = bool(FLAW.search(name))
    match = DISK_TAG.search(name)
    if not match:
        role = _role(name)
        return (0 if role == 0 else 2, role, flawed, name.lower())
    if BOOT_NOTE.search(name):
        return (0, 0, flawed, name.lower())
    token = match.group(1).lower()
    return (1, int(token) if token.isdigit() else ord(token) - ord("a") + 1, flawed, name.lower())


def _clean(name):
    """A disk's name without its flaw notes, as a damaged or altered copy names the disk it copies."""
    return FLAW.sub("", name)


def _title(name):
    """A tagged disk's name up to its disk tag, which every disk of one set shares."""
    match = DISK_TAG.search(name)
    return name[:match.start()].strip().lower() if match else None


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


# A release named as other than the game itself.
SIDE_RELEASE = re.compile(r"\b(?:demo|trial|sample|preview|hack|cheat|trainer|invincibility|patch|music|sound|"
                          r"gallery|opening|bonus|data|utility|tool|editor|course|scenario)\b|\bvol(?:ume)?\.?\s*\d",
                          re.I)


def _release_rank(name):
    """Orders a game's releases in one folder by how much their name adds to the title: names with
    no side-release word first, then fewer parenthesized notes, then the shorter name."""
    notes = re.findall(r"\(([^)]*)\)", name)
    return (bool(SIDE_RELEASE.search(" ".join(notes))), len(notes), len(name), name)


def set_name(archive):
    """The name a multi-disk set's archives share: the file name up to its disk tag, without dump
    flags or extension, in its folder. An archive without a disk tag is its own set."""
    folder, _, base = archive.rpartition("/")
    base = base.rsplit(".", 1)[0] if "." in base else base
    match = DISK_TAG.search(base)
    if match:
        base = base[:match.start()]
    base = DUMP_SET.sub("", base).strip()
    return f"{folder}/{base}" if folder else base


def group(archive):
    """The game an archive belongs to by its name: a disk of a set (one with a disk tag) by its set's
    name, so every disk of an unmatched set is one game; any other archive by its file name."""
    base = archive.rpartition("/")[2]
    return set_name(base) if DISK_TAG.search(base) else base


def ungrouped_sets(archives, games):
    """The multi-disk sets of a scan whose disks are still more than one library entry: for each,
    (its set name, the archives whose disks show). [archives] are a scan's ready archives, each
    {path, disks: [{contentId}]}; [games] the research's games, with their diskSets and diskHidden."""
    hidden = set()
    for game in games.values():
        for fields in game.get("diskSets", {}).values():
            hidden.update(fields["setDisks"])
        hidden.update(game.get("diskHidden", []))
    sets = {}
    for archive in archives:
        if DISK_TAG.search(archive["path"].rpartition("/")[2]):
            sets.setdefault(set_name(archive["path"]), []).append(archive)
    result = []
    for name, members in sorted(sets.items()):
        # Archives that are byte-identical copies of one disk ("Side A" and "Side B" holding the
        # same image) are one disk, not a set.
        if len({disk["contentId"] for archive in members for disk in archive["disks"]}) < 2:
            continue
        shown = [archive["path"] for archive in members
                 if any(disk["contentId"] not in hidden for disk in archive["disks"])]
        if len(shown) > 1:
            result.append((name, shown))
    return result


def disk_sets(disks, boot_kinds, companion, data_disk=lambda content_id: False,
              broken_disk=lambda content_id: False):
    """The disk sets of one game's [disks], each {archive, member, contentId}. [boot_kinds] lists
    the kinds the app starts from, preferred first; [companion] maps an entry's kind to (the kind
    of disk inserted beside it, its media role, whether a user disk may be that disk, and optionally
    whether the set's one disk of that kind is the companion whatever its name, as a hard disk's
    set has its CD); [data_disk]
    tells a disk that can never start a game, by content ID, and [broken_disk] one that stops on
    every system the app has, which leads its set, its dump sets and its folder only when nothing
    else can. Returns
    {entry content ID: {"setDisks": [...], "media": [...]}} for each entry that hides other disks."""
    sets = {}
    for disk in disks:
        sets.setdefault(_set_key(disk["archive"]), []).append(disk)
    found = {}
    orphans = {}  # each folder's disks in archives that nothing starts from
    dump_sets = {}  # each set name's [(dump number, entry)]
    for set_key, members in sets.items():
        name = lambda disk: disk["member"] or disk["archive"].rpartition("/")[2]
        alternates = [d for d in members if ALTERNATE.search(name(d))]
        # A disk the set has only as alternate dumps (a game's system disk kept only in alts/) is
        # still one of its disks: its first alternate stands in for the missing clean dump. A disk
        # is its place in the set: its disk tag, or its role when it has none.
        place = lambda d: _order(name(d))[:2]
        clean = {place(d) for d in members if d not in alternates}
        standing_in = {}
        for disk in sorted(alternates, key=lambda d: _order(name(d))):
            standing_in.setdefault(place(disk), disk)
        alternates = [d for d in alternates if place(d) in clean or standing_in[place(d)] != d]
        users = [d for d in members if d not in alternates and USER_DISK.search(name(d))]
        mains = [d for d in members if d not in alternates and d not in users and not UTILITY.search(name(d)) and
                 kind(d["contentId"]) in boot_kinds and not data_disk(d["contentId"])]
        if not mains:
            # An archive of only a user, utility, data or alternate disk: it hides behind the folder's main release.
            orphans.setdefault(set_key[0], set()).update(disk["contentId"] for disk in members)
            continue
        mains.sort(key=lambda d: (broken_disk(d["contentId"]), boot_kinds.index(kind(d["contentId"])), _order(name(d))))
        entry = mains[0]["contentId"]
        group, number = _dump_set(set_key)
        dump_sets.setdefault(group, []).append((number, entry))
        record = found.setdefault(entry, {"setDisks": set(), "media": []})
        record["setDisks"] |= {disk["contentId"] for disk in members} - {entry}
        rule = companion.get(kind(entry))
        if rule and not record["media"]:
            other_kind, role, user_allowed = rule[:3]
            set_disk = len(rule) > 3 and rule[3]
            # A second disk is named by its disk tag or as a disk, and belongs to the entry's set. Another
            # disk the game starts from is another version of it; beside anything but a boot disk, an
            # untagged disk not named as one is another game or version; a disk tagged under another title
            # is another volume's; and an untagged demo, or a damaged or altered copy of a disk the set has
            # clean, is never inserted.
            untagged = lambda d: not DISK_TAG.search(name(d))
            starts = lambda d: untagged(d) and _role(name(d)) == 0
            clean_names = {_clean(name(d)) for d in members if not FLAW.search(name(d))}
            entry_title = _title(name(mains[0]))
            candidates = [d for d in sorted(mains[1:], key=lambda d: _order(name(d)))
                          if kind(d["contentId"]) == other_kind and d["contentId"] != entry and
                          not (starts(d) and starts(mains[0])) and
                          not (untagged(d) and DEMO.search(name(d))) and
                          not (untagged(d) and _role(name(d)) == 2 and not ROLE_DISK.search(name(d)) and
                               not starts(mains[0])) and
                          not (entry_title and not untagged(d) and _title(name(d)) != entry_title) and
                          not (FLAW.search(name(d)) and _clean(name(d)) in clean_names)]
            if set_disk:
                of_kind = [d for d in mains[1:] if kind(d["contentId"]) == other_kind]
                candidates = of_kind if len(of_kind) == 1 else []
            if user_allowed:
                candidates += [d for d in sorted(users, key=lambda d: _order(name(d)))
                               if kind(d["contentId"]) == other_kind]
            if candidates:
                record["media"] = [{"role": role, "contentId": candidates[0]["contentId"]}]
    # The first dump set's entry hides the other sets' entries.
    firsts = {}  # each release's first set entry, by (folder, release name)
    for group_key, group in dump_sets.items():
        # A dump set whose entry works comes before one whose entry is broken.
        group.sort(key=lambda item: (broken_disk(item[1]), item))
        firsts[group_key] = group[0][1]
        found[group[0][1]]["setDisks"] |= {entry for _, entry in group[1:]}
    # The main release in each folder hides the game's other releases there.
    mains = set()
    folders = {}
    for (folder, name), entry in firsts.items():
        # The kind the app starts from first wins, as a floppy release over a tape one.
        folders.setdefault(folder, []).append(((broken_disk(entry), boot_kinds.index(kind(entry)), *_release_rank(name)),
                                               entry))
    for folder, releases in folders.items():
        releases.sort()
        main = releases[0][1]
        mains.add(main)
        found[main]["setDisks"] |= {entry for _, entry in releases[1:] if entry != main}
        found[main]["setDisks"] |= orphans.get(folder, set()) - {main}
    # A disk that starts a main release is never hidden by another set.
    result = {}
    for entry, record in sorted(found.items()):
        hidden = sorted(record["setDisks"] - mains - {entry})
        if hidden or record["media"]:
            result[entry] = {"setDisks": hidden, **({"media": record["media"]} if record["media"] else {})}
    return result
