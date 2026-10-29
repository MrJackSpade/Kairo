#!/usr/bin/env python3
"""Files a new Kairo app's tickets: one milestone per version in init/, each holding its tickets.

Usage: init_system.py --repo MrJackSpade/<App> --app <App> --system "<System>" [--module <module>] [--dry-run]
       init_system.py --checklist [--check]

--checklist writes docs/new-system.md, the same tickets as a checklist to read; with --check it
fails when that file is stale instead. The tickets in init/ are the one source of both.

init/<version>/<NN-slug>.md is one ticket: a front matter block (title, kind) and the body. Each
version folder is a milestone of that name in the app's repository, filed in version order, and
its tickets in file order. Titles and bodies use {{app}}, {{system}}, {{module}} and {{repo}}, and
{{ref:<slug>}} for another ticket's number (#N), which must be filed before the ticket naming it.
Each ticket gets the label of its kind (implement, validation, performance, translation); a
performance ticket that is not done gets `deferred` (docs/new-system.md).

A milestone or issue that already exists by its title is kept as it is, so a run that stopped
part way can be run again; the run lists what it kept. --dry-run prints the plan and files
nothing. Needs the GitHub CLI (`gh`), signed in.
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INIT = ROOT / "init"
CHECKLIST = ROOT / "docs/new-system.md"
CHECKLIST_VALUES = {"app": "<App>", "system": "<System>", "module": "<module>", "repo": "MrJackSpade/<App>"}
CHECKLIST_HEAD = """# Adding a new system

Everything a new Kairo app needs built, checked, assessed or translated. Each line is a ticket in
`init/<version>/`, and each version is a milestone, in version order. A ticket's body says when it
applies, what kind it is, what to do and how it closes, so not every ticket means work: a
validation builds nothing unless it fails, and a performance ticket is done only where it gains.

`tools/init_system.py` files them in the new app's repository (this file is written from them by
`init_system.py --checklist`; edit the tickets, not this file):

```
python shared/tools/init_system.py --repo MrJackSpade/<App> --app <App> --system "<System>" --dry-run
python shared/tools/init_system.py --repo MrJackSpade/<App> --app <App> --system "<System>"
```
"""
VERSION = re.compile(r"^\d+\.\d+\.\d+$")
TICKET = re.compile(r"^(\d+)-([a-z0-9-]+)\.md$")
PLACEHOLDER = re.compile(r"\{\{([a-z]+)(?::([a-z0-9-]+))?\}\}")
LABELS = {
    "implement": ("1d76db", "Build it when it applies"),
    "validation": ("0e8a16", "Check it; build only if it fails"),
    "performance": ("fbca04", "Assess it; done only where it gains"),
    "translation": ("5319e7", "One language, one function"),
    "deferred": ("c5def5", "Assessed and not done now"),
}


class Ticket:
    def __init__(self, version, path):
        match = TICKET.match(path.name)
        if not match:
            raise ValueError(f"{path}: not a <NN-slug>.md ticket file")
        self.version, self.order, self.slug = version, int(match.group(1)), match.group(2)
        text = path.read_text(encoding="utf-8")
        if not text.startswith("---\n"):
            raise ValueError(f"{path}: no front matter")
        head, self.body = text[4:].split("\n---\n", 1)
        fields = dict(line.split(": ", 1) for line in head.splitlines())
        if set(fields) != {"title", "kind"}:
            raise ValueError(f"{path}: front matter must hold title and kind, has {sorted(fields)}")
        if fields["kind"] not in LABELS or fields["kind"] == "deferred":
            raise ValueError(f"{path}: unknown kind {fields['kind']}")
        self.title, self.kind = fields["title"], fields["kind"]


def tickets():
    versions = sorted((d for d in INIT.iterdir() if d.is_dir()),
                      key=lambda d: tuple(int(p) for p in d.name.split(".")))
    found = []
    for folder in versions:
        if not VERSION.match(folder.name):
            raise ValueError(f"{folder}: not a x.y.z version folder")
        found += sorted((Ticket(folder.name, p) for p in folder.iterdir()), key=lambda t: t.order)
    slugs = [t.slug for t in found]
    duplicates = {s for s in slugs if slugs.count(s) > 1}
    if duplicates:
        raise ValueError(f"Slugs used twice: {sorted(duplicates)}")
    return found


def render(text, values, numbers, where):
    def replace(match):
        name, ref = match.groups()
        if name == "ref":
            if ref not in numbers:
                raise ValueError(f"{where}: {{{{ref:{ref}}}}} names no ticket filed before it")
            return f"#{numbers[ref]}"
        if name not in values or ref is not None:
            raise ValueError(f"{where}: unknown placeholder {match.group(0)}")
        return values[name]
    return PLACEHOLDER.sub(replace, text)


def gh(*args, payload=None):
    result = subprocess.run(["gh", "api", *args], input=None if payload is None else json.dumps(payload),
                            capture_output=True, text=True, encoding="utf-8")
    if result.returncode != 0:
        raise RuntimeError(f"gh api {' '.join(args)} failed: {result.stderr.strip()}")
    return json.loads(result.stdout) if result.stdout.strip() else None


def every(path):
    pages = gh("--paginate", "--slurp", path)
    return [item for page in pages for item in page]


def checklist(plan):
    lines = [CHECKLIST_HEAD]
    version = None
    for ticket in plan:
        if ticket.version != version:
            version = ticket.version
            lines.append(f"\n## {version}\n")
        lines.append(f"- [ ] {render(ticket.title, CHECKLIST_VALUES, {}, ticket.slug)} ({ticket.kind})")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repo", help="owner/name of the new app's repository")
    parser.add_argument("--app", help="the app's name, such as KairoST")
    parser.add_argument("--system", help="the system's name, such as Atari ST")
    parser.add_argument("--module", help="the app module (default: the app's name in lower case)")
    parser.add_argument("--dry-run", action="store_true", help="print the plan and file nothing")
    parser.add_argument("--checklist", action="store_true", help="write docs/new-system.md from the tickets")
    parser.add_argument("--check", action="store_true", help="with --checklist: fail when docs/new-system.md is stale")
    args = parser.parse_args()
    plan = tickets()
    if args.checklist:
        text = checklist(plan)
        if args.check:
            if CHECKLIST.read_text(encoding="utf-8") != text:
                raise ValueError(f"{CHECKLIST} is stale: run init_system.py --checklist")
        else:
            CHECKLIST.write_text(text, encoding="utf-8", newline="\n")
        return
    missing = [name for name in ("repo", "app", "system") if getattr(args, name) is None]
    if missing:
        parser.error("filing needs " + ", ".join(f"--{name}" for name in missing))
    values = {"app": args.app, "system": args.system, "module": args.module or args.app.lower(), "repo": args.repo}
    numbers = {}

    if args.dry_run:
        # Placeholder numbers stand in for the issues, so every reference is still checked.
        for index, ticket in enumerate(plan, 1):
            where = f"{ticket.version}/{ticket.slug}"
            title = render(ticket.title, values, numbers, where)
            render(ticket.body, values, numbers, where)
            numbers[ticket.slug] = f"<{index}>"
            print(f"{ticket.version}  [{ticket.kind}]  {title}")
        print(f"{len(plan)} tickets in {len({t.version for t in plan})} milestones; nothing filed (--dry-run)")
        return

    repo = args.repo
    labels = {label["name"] for label in every(f"repos/{repo}/labels?per_page=100")}
    for name, (color, description) in LABELS.items():
        if name not in labels:
            gh(f"repos/{repo}/labels", "--method", "POST", "--input", "-",
               payload={"name": name, "color": color, "description": description})
    milestones = {m["title"]: m["number"] for m in every(f"repos/{repo}/milestones?state=all&per_page=100")}
    issues = {i["title"]: i["number"] for i in every(f"repos/{repo}/issues?state=all&per_page=100")
              if "pull_request" not in i}
    kept = []
    for ticket in plan:
        where = f"{ticket.version}/{ticket.slug}"
        if ticket.version not in milestones:
            milestones[ticket.version] = gh(f"repos/{repo}/milestones", "--method", "POST", "--input", "-",
                                            payload={"title": ticket.version})["number"]
            print(f"milestone {ticket.version}")
        title = render(ticket.title, values, numbers, where)
        if title in issues:
            numbers[ticket.slug] = issues[title]
            kept.append(f"#{issues[title]} {title}")
            continue
        body = render(ticket.body, values, numbers, where)
        issue = gh(f"repos/{repo}/issues", "--method", "POST", "--input", "-",
                   payload={"title": title, "body": body, "milestone": milestones[ticket.version],
                            "labels": [ticket.kind]})
        numbers[ticket.slug] = issues[title] = issue["number"]
        print(f"#{issue['number']} {ticket.version} {title}")
    if kept:
        print(f"Kept {len(kept)} existing issues:", *kept, sep="\n  ")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError) as error:
        sys.exit(f"init_system.py: {error}")
