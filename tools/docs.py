#!/usr/bin/env python3
"""Writes the documents every Kairo repository shares, from docs/templates/.

Usage: docs.py <repository root> [--check]   (--check fails when a generated document is stale)

docs/templates/apps.json lists every Kairo app and the facts its documents use (system, package,
icon, Google Play, privacy details, external frontend names); the repository is identified by its
origin remote. For an app it writes:
  LICENSE.md                                   from templates/LICENSE.md
  <module>/src/main/assets/PRIVACY_POLICY.txt  from templates/PRIVACY_POLICY.md
  docs/privacy-policy.html                     the same policy, when the app has "privacyPage"
  docs/frontends.md                            from templates/frontends.md, when it has "frontends"
In every repository, including Kairo itself, its tracked README.md and docs/*.md may hold shared blocks:
  <!-- kairo:NAME -->
  ...replaced by templates/blocks/NAME.md...
  <!-- /kairo:NAME -->
The rest of each file is the repository's own. "family" is the grid of the other Kairo apps.

Templates use {{name}} (a value, which must exist), {{#name}}...{{/name}} (repeated for each
item of a list, kept once for any other true value) and {{^name}}...{{/name}} (kept when the value
is missing or false). A dotted name looks into an object. A section tag alone on its line takes
the line with it.
"""
import argparse
import html
import json
import re
import subprocess
from pathlib import Path

TEMPLATES = Path(__file__).resolve().parents[1] / "docs/templates"
BLOCK = re.compile(r"(<!-- kairo:([a-z-]+) -->\n)(.*?)(<!-- /kairo:\2 -->)", re.S)
STANDALONE = re.compile(r"^[ \t]*(\{\{[#^/][^}]+\}\})[ \t]*\n", re.M)
TAG = re.compile(r"\{\{([#^/]?)\s*([\w.]+)\s*\}\}")
GRID_COLUMNS = 3


def lookup(stack, name):
    for context in reversed(stack):
        value = context
        for part in name.split("."):
            if not isinstance(value, dict) or part not in value:
                break
            value = value[part]
        else:
            return True, value
    return False, None


def render(template, context):
    return _render(STANDALONE.sub(r"\1", template), [context])


def _render(template, stack):
    out, position = [], 0
    while True:
        match = TAG.search(template, position)
        if not match:
            out.append(template[position:])
            return "".join(out)
        out.append(template[position:match.start()])
        kind, name = match.groups()
        if kind == "":
            found, value = lookup(stack, name)
            if not found or isinstance(value, (dict, list)):
                raise SystemExit(f"template value {name!r} is missing")
            out.append(str(value))
            position = match.end()
            continue
        if kind == "/":
            raise SystemExit(f"unopened section {name!r}")
        end = _section_end(template, match.end(), name)
        body = template[match.end():end.start()]
        found, value = lookup(stack, name)
        if kind == "^":
            if not found or not value:
                out.append(_render(body, stack))
        elif isinstance(value, list):
            out.extend(_render(body, stack + [item]) for item in value)
        elif found and value:
            out.append(_render(body, stack + [value] if isinstance(value, dict) else stack))
        position = end.end()


def _section_end(template, start, name):
    depth = 1
    for match in TAG.finditer(template, start):
        if match.group(2) != name or match.group(1) == "":
            continue
        depth += 1 if match.group(1) in "#^" else -1
        if depth == 0:
            return match
    raise SystemExit(f"section {name!r} is not closed")


def repository_name(root):
    url = subprocess.run(["git", "-C", str(root), "remote", "get-url", "origin"], check=True,
                         capture_output=True, text=True).stdout.strip()
    return re.sub(r"\.git$", "", url).rstrip("/").rsplit("/", 1)[-1]


def contexts(registry):
    owner, frontend = registry["owner"], registry["frontend"]
    raw = f"https://raw.githubusercontent.com/{owner}"
    base = {key: value for key, value in registry.items() if key != "apps"}
    base["discordBanner"] = f"{raw}/{frontend}/main/docs/assets/discord-banner.svg"
    base["frontendUrl"] = f"https://github.com/{owner}/{frontend}"
    apps = []
    for app in registry["apps"]:
        url = f"https://github.com/{owner}/{app['repo']}"
        hosts = [f"`{host}`" for host in app["artworkHosts"]]
        apps.append({**app, "url": url, "releasesUrl": f"{url}/releases", "issuesUrl": f"{url}/issues",
                     "iconUrl": f"{raw}/{app['repo']}/main/{app['icon']}", "emulatorId": app["repo"].upper(),
                     "artworkHostList": hosts[0] if len(hosts) == 1 else ", ".join(hosts[:-1]) + f" or {hosts[-1]}",
                     **({"playUrl": f"https://play.google.com/store/apps/details?id={app['package']}"}
                        if app["googlePlay"] else {})})
    return base, apps


def privacy_documents(markdown):
    """The policy as the app's plain-text asset and as a web page, from its Markdown."""
    blocks = markdown.strip("\n").split("\n\n")
    title, date, paragraphs = blocks[0].removeprefix("# "), blocks[1], blocks[2:]
    # Plain text: the title over its date, then each heading directly over its first paragraph.
    text, page, heading = [f"{title}\n{date}"], [f'    <p class="date">{html.escape(date)}</p>'], None
    for block in paragraphs:
        if block.startswith("## "):
            heading = block[3:]
            page.append(f"    <h2>{html.escape(heading)}</h2>")
            continue
        plain = block.replace("`", "")
        text.append(f"{heading}\n{plain}" if heading else plain)
        heading = None
        page.append(f"    <p>{inline_html(block)}</p>")
    body = "\n\n".join(text) + "\n"
    head = (f'<!doctype html>\n<html lang="en">\n<head>\n  <meta charset="utf-8">\n'
            f'  <meta name="viewport" content="width=device-width, initial-scale=1">\n'
            f"  <title>{html.escape(title)} — Loxifi LLC</title>\n  <style>\n"
            "    body { background: #10131b; color: #f1f3f8; font: 1.05rem/1.6 system-ui, sans-serif; "
            "margin: 0 auto; max-width: 48rem; padding: 2rem 1.25rem 4rem; }\n"
            "    h1, h2 { line-height: 1.2; }\n    h2 { margin-top: 2rem; }\n    a { color: #9fc4ff; }\n"
            "    .date { color: #bfc6d4; }\n  </style>\n</head>\n<body>\n  <main>\n")
    page_html = head + f"    <h1>{html.escape(title)}</h1>\n" + "\n".join(page) + "\n  </main>\n</body>\n</html>\n"
    return body, page_html


def inline_html(text):
    escaped = html.escape(text, quote=False)
    escaped = re.sub(r"`([^`]+)`", r"<code>\1</code>", escaped)
    return re.sub(r"([\w.+-]+@[\w-]+\.[\w.]+\w)", r'<a href="mailto:\1">\1</a>', escaped)


def documents(root):
    """Every generated document of the repository at root, {relative path: text}."""
    registry = json.loads((TEMPLATES / "apps.json").read_text(encoding="utf-8"))
    base, apps = contexts(registry)
    name = repository_name(root)
    current = next((app for app in apps if app["repo"] == name), None)
    if current is None and name != registry["frontend"]:
        raise SystemExit(f"{name} is not in {TEMPLATES / 'apps.json'}")
    others = [app for app in apps if app["repo"] != name]
    context = {**base, **(current or {"repo": name, "url": base["frontendUrl"]}),
               "isFrontend": current is None,
               "familyRows": [{"apps": others[start:start + GRID_COLUMNS]}
                              for start in range(0, len(others), GRID_COLUMNS)]}
    template = lambda path: (TEMPLATES / path).read_text(encoding="utf-8")
    out = {}
    if current:
        out["LICENSE.md"] = render(template("LICENSE.md"), context)
        text, page = privacy_documents(render(template("PRIVACY_POLICY.md"), context))
        out[f"{current['module']}/src/main/assets/PRIVACY_POLICY.txt"] = text
        if current["privacyPage"]:
            out["docs/privacy-policy.html"] = page
        if "frontends" in current:
            out["docs/frontends.md"] = render(template("frontends.md"), context)
    # Only the repository's own documents: local, ignored notes in docs/ are not its documents.
    tracked = subprocess.run(["git", "-C", str(root), "ls-files", "--", "README.md", "docs/*.md"], check=True,
                             capture_output=True, text=True).stdout.split()
    for relative in sorted(path for path in tracked if path == "README.md" or path.count("/") == 1):
        path = root / relative
        if relative in out:
            continue
        original = read(path)

        def block(match):
            block_template = TEMPLATES / "blocks" / f"{match.group(2)}.md"
            if not block_template.is_file():
                raise SystemExit(f"{relative}: no shared block {match.group(2)!r}")
            return match.group(1) + render(block_template.read_text(encoding="utf-8"), context) + match.group(4)

        updated = BLOCK.sub(block, original)
        if updated != original:
            out[relative] = updated
    return out


def read(path):
    return path.read_text(encoding="utf-8").replace("\r\n", "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    stale = []
    for relative, text in documents(root).items():
        path = root / relative
        if path.is_file() and read(path) == text:
            continue
        stale.append(relative)
        if not args.check:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8", newline="\n")
    if args.check and stale:
        raise SystemExit(f"stale shared documents: {', '.join(stale)}; run shared/tools/docs.py")
    print(f"{'stale' if args.check else 'wrote'}: {', '.join(stale)}" if stale else "shared documents are current")


if __name__ == "__main__":
    main()
