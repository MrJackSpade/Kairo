#!/usr/bin/env python3
"""Copy the MAME sources an app's drivers need into its vendored snapshot, third_party/mame.

The device set comes from MAME's own scripts/build/makedep.py (the logic behind
`make SOURCES=...`). Device options are resolved to files through the
scripts/src/*.lua file lists, then the set is closed over #include so headers
and their companion sources are present. Whole 3rdparty libraries are copied
when any selected file includes them, so their license files come along. An upstream
repository's git files (.gitignore, .gitattributes, .gitmodules, .github/) are left out.

Usage: import_mame.py <app root> <mame-source-root> [<destination>]

The app lists what is its own in tools/mame_import.py: DRIVERS, the driver sources relative to the
MAME root (src/mame/sharp/x68k.cpp); OSD_FILES, the OSD platform files it builds (optional); and
EXTRA_3RDPARTY, 3rdparty libraries included through their own include directories (optional). An
app whose tools/patches.py patches MAME (BASE "mame") gets its changes applied to the copy. The
destination is the app's third_party/mame unless given, and must not exist.
"""

import importlib.util
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import local_patches  # noqa: E402

OPTION_RE = re.compile(r'^(BUSES|CPUS|MACHINES|SOUNDS|VIDEOS|FORMATS)\["([^"]+)"\] = true$')
IF_RE = re.compile(r'^if (.*) then\s*$')
# An option reference: CPUS["Z80"], or opt_tool(CPUS, "Z80") in blocks also built for tools.
REF_RE = re.compile(r'(BUSES|CPUS|MACHINES|SOUNDS|VIDEOS|FORMATS)(?:\["([^"]+)"\]|\s*,\s*"([^"]+)"\))')
FILE_RE = re.compile(r'MAME_DIR\s*\.\.\s*"([^"]+)"')
LOCAL_RE = re.compile(r'^local (\w+)\s*=\s*(.*)$')


def option_refs(text):
    return {(kind, square or call) for kind, square, call in REF_RE.findall(text)}
INCLUDE_RE = re.compile(r'^\s*#\s*include\s*"([^"]+)"', re.M)

# The include directories MAME's build passes to device, emulator and driver code.
INCLUDE_DIRS = [
    'src/osd', 'src/emu', 'src/devices', 'src/mame', 'src/mame/shared', 'src/lib',
    'src/lib/util', 'src/lib/netlist', '3rdparty',
]

# Always-present parts of the emulator core.
BASE_TREES = ['src/emu', 'src/lib/util']
BASE_FILES = ['COPYING', 'README.md']
BASE_DIRS_FLAT = ['src/osd']
LEGAL_TREE = 'docs/legal'

# 3rdparty libraries the core links whether or not a source includes them directly.
BASE_3RDPARTY = ['expat', 'zlib', 'lzma', 'zstd', 'utf8proc', 'flac', 'softfloat3', 'rapidjson', 'asio']

SOURCE_EXTS = ('.cpp', '.c', '.ipp', '.hxx')


def run_makedep(root, drivers):
    out = subprocess.run(
        [sys.executable, '-I', 'scripts/build/makedep.py', '-r', '.', 'sourcesproject',
         '-t', 'kairo', '-l', 'src/mame/mame.lst', *drivers],
        cwd=root, check=True, capture_output=True, text=True).stdout
    options = set()
    for line in out.splitlines():
        m = OPTION_RE.match(line.strip())
        if m:
            options.add((m.group(1), m.group(2)))
    # The generated project lists include directories first; driver sources follow `files{`.
    files = set(FILE_RE.findall(out.split('files{', 1)[1]))
    return out, options, files


def lua_option_files(root, options):
    """Files from every `if <option refs> then ... end` block with an enabled option. A condition
    may name a local the script set from option references (`local want_disasm_z80 =
    opt_tool(CPUS, "Z80")`), which stands for those references."""
    files = set()
    for name in ('bus', 'cpu', 'machine', 'sound', 'video', 'formats'):
        path = os.path.join(root, 'scripts', 'src', name + '.lua')
        with open(path, encoding='utf-8') as f:
            lines = f.read().splitlines()
        aliases = {}
        active = None
        depth = 0
        for line in lines:
            stripped = line.strip()
            local = LOCAL_RE.match(stripped)
            if local:
                aliases[local.group(1)] = option_refs(local.group(2))
            if active is None:
                m = IF_RE.match(stripped)
                if m:
                    refs = option_refs(m.group(1))
                    for word in re.findall(r'\w+', m.group(1)):
                        refs |= aliases.get(word, set())
                    active = bool(refs & options)
                    depth = 1
                continue
            if IF_RE.match(stripped):
                depth += 1
            elif stripped == 'end':
                depth -= 1
                if depth == 0:
                    active = None
                    continue
            if active:
                files.update(FILE_RE.findall(line))
    return without_include_dirs(root, files)


def without_include_dirs(root, paths):
    """Lua blocks also name include directories; only files are copied by path."""
    return {p for p in paths if not os.path.isdir(os.path.join(root, p))}


def unconditional_formats(root):
    """Files the device scripts list outside any option block: the floppy and format core in
    formats.lua, and the always-built devices (NVRAM, RAM, floppy drives, volume filters and the
    like) at the top of machine.lua, sound.lua and video.lua."""
    files = set()
    for name in ('bus', 'cpu', 'machine', 'sound', 'video', 'formats'):
        path = os.path.join(root, 'scripts', 'src', name + '.lua')
        with open(path, encoding='utf-8') as f:
            head = f.read().split('\nif ', 1)[0]
        files.update(FILE_RE.findall(head))
    return without_include_dirs(root, files)


def walk(root, rel):
    """The files under rel, without an upstream repository's git files (.gitignore, .github/)."""
    base = os.path.join(root, rel)
    if not os.path.isdir(base):
        raise SystemExit(f'directory missing from MAME tree: {rel}')
    for dirpath, dirs, names in os.walk(base):
        dirs[:] = [d for d in dirs if not d.startswith('.git')]
        for n in names:
            if not n.startswith('.git'):
                yield os.path.relpath(os.path.join(dirpath, n), root).replace('\\', '/')


def resolve_include(root, from_file, inc):
    candidates = [os.path.join(os.path.dirname(from_file), inc)]
    candidates += [os.path.join(d, inc) for d in INCLUDE_DIRS]
    for c in candidates:
        c = os.path.normpath(c).replace('\\', '/')
        if os.path.isfile(os.path.join(root, c)):
            return c
    return None


def companions(root, header):
    stem, _ = os.path.splitext(header)
    for ext in SOURCE_EXTS:
        if os.path.isfile(os.path.join(root, stem + ext)):
            yield stem + ext


def generated_inputs(root, inc):
    """Sources for generated includes: layouts (.lh from .lay) and fonts (.fh)."""
    stem, ext = os.path.splitext(os.path.basename(inc))
    if ext == '.lh':
        for d in ('src/emu/layout', 'src/mame/layout'):
            p = f'{d}/{stem}.lay'
            if os.path.isfile(os.path.join(root, p)):
                yield p
    elif ext == '.fh':
        for dirpath, _, names in os.walk(os.path.join(root, 'src', 'emu')):
            for n in names:
                if os.path.splitext(n)[0] == stem:
                    yield os.path.relpath(os.path.join(dirpath, n), root).replace('\\', '/')


def close_over_includes(root, seed, extra_libs):
    selected = set(seed)
    libs = set(BASE_3RDPARTY) | set(extra_libs)
    queue = list(selected)
    while queue:
        f = queue.pop()
        if not f.endswith(('.cpp', '.c', '.h', '.hpp', '.hxx', '.ipp', '.inc')):
            continue
        try:
            with open(os.path.join(root, f), encoding='utf-8', errors='replace') as fh:
                text = fh.read()
        except FileNotFoundError:
            raise SystemExit(f'listed source missing from MAME tree: {f}')
        for inc in INCLUDE_RE.findall(text):
            found = resolve_include(root, f, inc)
            new = []
            if found is None:
                new.extend(generated_inputs(root, inc))
            elif found.startswith('3rdparty/'):
                libs.add(found.split('/')[1])
                continue
            else:
                new.append(found)
                if found.startswith(('src/devices/', 'src/mame/', 'src/lib/formats/')):
                    new.extend(companions(root, found))
            for n in new:
                if n not in selected:
                    selected.add(n)
                    queue.append(n)
    return selected, libs


def load_app(app):
    spec = importlib.util.spec_from_file_location('mame_import', app / 'tools' / 'mame_import.py')
    config = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(config)
    return config


def main():
    if len(sys.argv) not in (3, 4):
        raise SystemExit(__doc__)
    app, root = Path(sys.argv[1]).resolve(), sys.argv[2]
    dest = sys.argv[3] if len(sys.argv) == 4 else str(app / 'third_party' / 'mame')
    config = load_app(app)
    drivers = list(config.DRIVERS)
    patches = local_patches.load(app) if (app / 'tools' / 'patches.py').is_file() else None
    if patches is not None and getattr(patches, 'BASE', '') != 'mame':
        patches = None
    project, options, driver_files = run_makedep(root, drivers)

    seed = set(driver_files) | lua_option_files(root, options) | unconditional_formats(root)
    for t in BASE_TREES:
        seed.update(walk(root, t))
    seed.update(getattr(config, 'OSD_FILES', ()))
    for d in BASE_DIRS_FLAT:
        seed.update(f'{d}/{n}' for n in os.listdir(os.path.join(root, d))
                    if os.path.isfile(os.path.join(root, d, n)))
    selected, libs = close_over_includes(root, seed, getattr(config, 'EXTRA_3RDPARTY', ()))

    selected.update(BASE_FILES)
    selected.update(walk(root, LEGAL_TREE))
    for lib in sorted(libs):
        selected.update(walk(root, f'3rdparty/{lib}'))

    if os.path.exists(dest):
        raise SystemExit(f'destination exists: {dest}')
    for rel in sorted(selected):
        src = os.path.join(root, rel)
        if not os.path.isfile(src):
            raise SystemExit(f'selected file missing from MAME tree: {rel}')
        out = os.path.join(dest, rel)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        shutil.copy2(src, out)
    if patches is not None:
        local_patches.apply(patches.PATCHES, dest, getattr(patches, 'ENCODING', 'utf-8'))

    with open(os.path.join(dest, 'KAIRO_IMPORT.txt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('Generated by shared/tools/import_mame.py\n')
        f.write('Drivers: ' + ' '.join(drivers) + '\n')
        f.write('3rdparty libraries: ' + ' '.join(sorted(libs)) + '\n')
        f.write(f'Files: {len(selected)}\n')
        if patches is not None:
            f.write('Local changes (tools/patches.py): ' + ' '.join(patches.PATCHES) + '\n')
        f.write('\n')
        f.write('makedep.py sourcesproject output:\n\n')
        f.write(project)
    print(f'{len(selected)} files, 3rdparty: {" ".join(sorted(libs))}')


if __name__ == '__main__':
    main()
