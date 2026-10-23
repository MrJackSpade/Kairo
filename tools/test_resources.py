"""Packs and unpacks an app's UI test resources: the games and BIOS files its device tests use.

They are copyrighted, so the repository holds them only encrypted, as uitest/TestResources.part1.dat.
The key comes from the LOCAL_BUILD_TOKEN environment variable, which exists only on the machines
that may build the tests. Unpacking happens at build time (shared/gradle/kairo-uitest.gradle) when
the files are not there yet; the test APK carries them as assets.

    test_resources.py pack <app repository>      reads uitest/resources.json, writes the bundle
    test_resources.py unpack <bundle> <folder>   writes the files, unless the folder already holds them

resources.json maps each path in the bundle (games/..., bios/...) to the file on this machine:
    {"schemaVersion": 1, "files": {"games/Thunder Force/Thunder Force [FD].7z": "<local path>", ...}}

Format: b"KTR1", a 16-byte salt, a 12-byte nonce, then AES-256-GCM over a ZIP of the files, keyed by
PBKDF2-HMAC-SHA256 of the token (300 000 iterations).
"""
import hashlib
import io
import json
import os
import secrets
import sys
import zipfile
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.hashes import SHA256
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC

MAGIC = b"KTR1"
TOKEN = "LOCAL_BUILD_TOKEN"
MARKER = ".bundle-sha256"


def key(salt):
    token = os.environ.get(TOKEN)
    if not token:
        raise SystemExit(f"{TOKEN} is not set: the UI test resources cannot be decrypted on this machine")
    return PBKDF2HMAC(algorithm=SHA256(), length=32, salt=salt, iterations=300_000).derive(token.encode())


def pack(repository):
    manifest = json.loads((repository / "uitest/resources.json").read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != 1:
        raise SystemExit("unsupported resources.json")
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_STORED) as archive:
        for name, source in sorted(manifest["files"].items()):
            if not (name.startswith("games/") or name.startswith("bios/")):
                raise SystemExit(f"{name}: bundle paths start with games/ or bios/")
            archive.write(source, name)
    salt, nonce = secrets.token_bytes(16), secrets.token_bytes(12)
    sealed = MAGIC + salt + nonce + AESGCM(key(salt)).encrypt(nonce, buffer.getvalue(), MAGIC)
    output = repository / "uitest/TestResources.part1.dat"
    output.write_bytes(sealed)
    print(f"{output}: {len(manifest['files'])} files, {len(sealed)} bytes")


def unpack(bundle, folder):
    sealed = bundle.read_bytes()
    digest = hashlib.sha256(sealed).hexdigest()
    marker = folder / MARKER
    if marker.is_file() and marker.read_text() == digest:
        print(f"{folder}: already holds {bundle.name}")
        return
    if sealed[:4] != MAGIC:
        raise SystemExit(f"{bundle} is not a UI test resource bundle")
    salt, nonce = sealed[4:20], sealed[20:32]
    plain = AESGCM(key(salt)).decrypt(nonce, sealed[32:], MAGIC)
    if folder.exists():
        for path in sorted(folder.rglob("*"), reverse=True):
            path.unlink() if path.is_file() else path.rmdir()
    folder.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(plain)) as archive:
        for entry in archive.infolist():
            target = (folder / entry.filename).resolve()
            if not str(target).startswith(str(folder.resolve()) + os.sep):
                raise SystemExit(f"{entry.filename} escapes {folder}")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(entry))
    marker.write_text(digest)
    print(f"{folder}: unpacked {len(archive.infolist())} files")


def main():
    if len(sys.argv) == 3 and sys.argv[1] == "pack":
        pack(Path(sys.argv[2]))
    elif len(sys.argv) == 4 and sys.argv[1] == "unpack":
        unpack(Path(sys.argv[2]), Path(sys.argv[3]))
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
