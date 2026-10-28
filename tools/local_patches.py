#!/usr/bin/env python3
"""Applies an app's local changes to its third-party snapshots, and checks them.

  local_patches.py <app root> [--check] [--group NAME ...]

The app lists its changes in tools/patches.py: PATCHES, {group name: [(path, old, new), ...]} in the
order they apply, each path under third_party/BASE (BASE "" by default, "mame" for Kairo68's MAME),
read and written in ENCODING ("utf-8" by default; VICE's sources are Latin-1), with the changes
written with LF line endings and matched to a CRLF file's own (MAME ships a few). Each change replaces
text that must occur exactly once in its file at that point, so an update that moves the code stops
instead of changing the wrong place; a later change may edit what an earlier one wrote. ADDED and
REMOVED (optional) name the files the app added to the snapshot (its own code, kept beside what it
patches) and the snapshot's files it took out; --check fails if one is missing or still there.

  (no option)  applies every change in order to a fresh snapshot, as an import does; a tree that
               already holds them all is left alone.
  --group      applies only those groups, in order, as when adding a new group to the committed tree.
  --check      writes nothing: undoing every change in reverse (each change's new text exactly once
               at that point) and redoing them must give back the tree byte for byte.
               shared/tools/check-app.sh runs it for every app.

docs/source-import.md of each app describes its changes.
"""
import argparse
import importlib.util
from pathlib import Path


def load(root):
    spec = importlib.util.spec_from_file_location("patches", root / "tools/patches.py")
    patches = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(patches)
    return patches


class Tree:
    """The patched files, read once and written back only when changed."""

    def __init__(self, base, encoding):
        self.base, self.encoding, self.texts, self.read = Path(base), encoding, {}, {}

    def __getitem__(self, rel):
        if rel not in self.texts:
            # newline="" keeps each file's line endings as they are.
            with open(self.base / rel, encoding=self.encoding, newline="") as f:
                self.texts[rel] = self.read[rel] = f.read()
        return self.texts[rel]

    def __setitem__(self, rel, text):
        self.texts[rel] = text

    def lines(self, rel, text):
        """A change's text with the file's line endings."""
        return text.replace("\n", "\r\n") if "\r\n" in self[rel] else text

    def write(self):
        for rel, text in self.texts.items():
            if text != self.read[rel]:
                with open(self.base / rel, "w", encoding=self.encoding, newline="") as f:
                    f.write(text)


def forward(tree, name, changes):
    for rel, old, new in changes:
        old, new = tree.lines(rel, old), tree.lines(rel, new)
        if tree[rel].count(old) != 1:
            raise SystemExit(f"patch {name}: {rel} does not contain the text to replace exactly once")
        tree[rel] = tree[rel].replace(old, new)


def check(patches, base, encoding):
    """The changes that don't undo from the tree, or [] when undoing and redoing all gives it back."""
    tree = Tree(base, encoding)
    problems = []
    for name, changes in reversed(list(patches.items())):
        for rel, old, new in reversed(changes):
            old, new = tree.lines(rel, old), tree.lines(rel, new)
            if tree[rel].count(new) != 1:
                problems.append(f"{name}: {rel}")
                continue
            tree[rel] = tree[rel].replace(new, old)
    if problems:
        return problems
    for name, changes in patches.items():
        forward(tree, name, changes)
    return [f"redo of {rel}" for rel in tree.texts if tree.texts[rel] != tree.read[rel]]


def files(patches, base):
    """ADDED files missing and REMOVED files still present, under base."""
    return ([f"added {rel} is missing" for rel in getattr(patches, "ADDED", ()) if not (base / rel).is_file()] +
            [f"removed {rel} is present" for rel in getattr(patches, "REMOVED", ()) if (base / rel).exists()])


def apply(patches, base, encoding="utf-8", groups=None):
    """Applies the groups (all of them for a fresh snapshot) in order; nothing is written unless every change applies."""
    if groups is None and not check(patches, base, encoding):
        print(f"all {len(patches)} patch groups are already applied")
        return
    tree = Tree(base, encoding)
    for name, changes in patches.items():
        if groups is None or name in groups:
            forward(tree, name, changes)
    tree.write()
    print(f"applied {len(groups) if groups is not None else len(patches)} patch groups to {len(tree.texts)} files")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", type=Path)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--group", action="append")
    args = parser.parse_args()
    root = args.root.resolve()
    patches = load(root)
    base = root / "third_party" / getattr(patches, "BASE", "")
    encoding = getattr(patches, "ENCODING", "utf-8")
    if args.group and set(args.group) - set(patches.PATCHES):
        raise SystemExit(f"no patch group {sorted(set(args.group) - set(patches.PATCHES))}; groups: {', '.join(patches.PATCHES)}")
    if args.check:
        problems = check(patches.PATCHES, base, encoding) + files(patches, base)
        if problems:
            raise SystemExit("not applied: " + ", ".join(problems))
        print(f"all {len(patches.PATCHES)} patch groups are applied")
        return
    apply(patches.PATCHES, base, encoding, args.group)


if __name__ == "__main__":
    main()
