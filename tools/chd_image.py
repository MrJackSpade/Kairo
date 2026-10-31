"""A CD kept as a CHD, read as the disc image it was made from, for the catalog tools.

The shared CHD reader (native/chd) built for the host as kairo_chd_image: a CHD made from a CloneCD
.img or a one-file BIN reads back as that file, so its content ID is the image's, as the apps
compute it (frontend ChdImage). The tool is built on first use with the llvm-mingw toolchain the
host core tests use (test-core.ps1), into shared/.build/chd-host.
"""

import hashlib
import shutil
import subprocess
import tempfile
from pathlib import Path

SHARED = Path(__file__).resolve().parents[1]
BUILD = SHARED / ".build" / "chd-host"
TOOLCHAIN = Path("F:/tools/llvm-mingw-20261006-ucrt-x86_64/bin")
CMAKE = Path("F:/android-sdk/cmake/3.22.1/bin/cmake.exe")
NINJA = Path("F:/android-sdk/cmake/3.22.1/bin/ninja.exe")
CHUNK = 1024 * 1024


def is_chd(name):
    return str(name).lower().endswith(".chd")


def tool():
    """kairo_chd_image, built when missing or older than its sources."""
    program = BUILD / "kairo_chd_image.exe"
    sources = [*(SHARED / "native" / "chd").iterdir()]
    if not program.is_file() or program.stat().st_mtime < max(source.stat().st_mtime for source in sources):
        if not (BUILD / "build.ninja").is_file():
            subprocess.run([str(CMAKE), "-S", str(SHARED / "native" / "chd"), "-B", str(BUILD), "-G", "Ninja",
                            "-DCMAKE_BUILD_TYPE=Release", f"-DCMAKE_MAKE_PROGRAM={NINJA.as_posix()}",
                            f"-DCMAKE_C_COMPILER={(TOOLCHAIN / 'clang.exe').as_posix()}"], check=True)
        subprocess.run([str(CMAKE), "--build", str(BUILD), "--target", "kairo_chd_image"], check=True)
    return program


def digest(path, kind):
    """The content ID of the CHD at path: the SHA-256 of its disc image."""
    process = subprocess.Popen([str(tool()), "image", str(path)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    sha = hashlib.sha256()
    while block := process.stdout.read(CHUNK):
        sha.update(block)
    error = process.stderr.read()
    if process.wait():
        raise RuntimeError(f"cannot read {Path(path).name}: {error.decode('utf-8', 'replace').strip()}")
    return f"sha256-{kind}-v1:" + sha.hexdigest()


def digest_stream(stream, kind):
    """The content ID of a CHD read from stream (an archive member), copied out first: the reader
    reads a CHD in place."""
    with tempfile.TemporaryDirectory() as folder:
        copy = Path(folder) / "member.chd"
        with copy.open("wb") as output:
            shutil.copyfileobj(stream, output, CHUNK)
        return digest(copy, kind)
