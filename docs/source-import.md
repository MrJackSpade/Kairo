# Source provenance

The frontend code originated in the first-party [Kairo98](https://github.com/MrJackSpade/Kairo98) project. Kairo98 and [KairoDos](https://github.com/MrJackSpade/KairoDos) consume pinned commits of this repository. No emulator core is vendored here.

The Spleen 8x16 BDF and BSD-2-Clause license in `third_party/spleen/` come from upstream commit `57f9219328c9f5873085320fe8bc8f7dd34b8791`. `third_party/unifont/unifont_all-17.0.05.hex.gz` is GNU Unifont 17.0.05 from https://ftp.gnu.org/gnu/unifont/unifont-17.0.05/ (SHA-256 `7b182454966046d35482469b979edce7d262fab5c53c2180e9b1fbb5d0b5e574`), with the `OFL-1.1.txt` from that release's source archive. `tools/generate_ui_pixel_font.py` produces the UI font asset from Spleen's BDF and, for the blocks Spleen lacks (kana, CJK, Hangul, full-width forms), Unifont.

The CHD disc image reader (`native/chd/`) is built on [libchdr](https://github.com/rtissera/libchdr) at commit `47d794151b2b64f6aba60304e6480f4f5a2bc24b` (2026-10-11), copied into `third_party/libchdr/` without its tests, docs and contrib folders: its `CMakeLists.txt`, `cmake/`, `include/`, `src/` and `deps/` (LZMA SDK 26.02, miniz 3.1.2, the Zstandard 1.5.7 single-file decompressor), unmodified. Apps whose cores read CHDs build it through `cmake/KairoChd.cmake`; the catalog tools build the host `kairo_chd_image` from `native/chd/CMakeLists.txt` (`tools/chd_image.py`).

The Gradle 8.13 wrapper JAR has SHA-256 `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`, matching [Gradle's checksum list](https://gradle.org/release-checksums/). The Apache-2.0 license and notices are preserved in `third_party/gradle-wrapper/LICENSE`.

The RetroArch Slang runtime is [librashader](https://github.com/SnowflakePowered/librashader) v0.11.0 at commit `bd2b8789a8d571add408d46590b3a2ff7e11886f`, copied into `third_party/librashader/` from that Git tag, except its `test/` folder (sample shaders, images and the C API test projects; no workspace member or build step reads it, and its Xcode user files' paths were too long for a default Windows checkout). Its `Cargo.lock` fixes the dependency versions and registry checksums. `third_party/cargo-crates/` holds the 129 corresponding crates.io source archives for the Android arm64 OpenGL dependency closure; `SHA256SUMS.txt` was verified against `Cargo.lock`. Gradle builds `librashader-capi` with Rust 1.90.0 for `aarch64-linux-android`, `--no-default-features --features runtime-opengl`.

Three built-in presets and their shader files use six unmodified files from [Libretro's Slang collection](https://github.com/libretro/slang-shaders) commit `e1d75632a205c70f14a4cc947c46d5abb7b3f7f1`. The exact copied files, SHA-256 hashes, and per-shader license findings are in the pinned [shader snapshot](../third_party/slang-shaders/README.md). The frontend packages only those files; additional user-imported ZIPs are not redistributed by the app.

## zlib and libpng

`cmake/KairoLibraries.cmake` builds these for any app's core that links them (`kairo_zlib`, `kairo_png`), so the apps share one copy instead of each carrying its own.

| Library | Release | Archive | Archive SHA-256 | Released |
| --- | --- | --- | --- | --- |
| zlib (`third_party/zlib/`) | 1.3.2 | GitHub release `v1.3.2` of `madler/zlib`, `zlib-1.3.2.tar.gz` | `bb329a0a2cd0274d05519d61c667c062e06990d72e125ee2dfa8de64f0119d16` | 2026-02-17 |
| libpng (`third_party/libpng/`) | 1.6.59 | <https://codeload.github.com/pnggroup/libpng/tar.gz/refs/tags/v1.6.59> | `2540302a1844ad2b2b501977abecfa850f265f97b78f065a712ab4074a89f5b5` | 2026-09-28 |

Both were copied on 2026-11-02 from KairoMSX's snapshots (imported 2026-10-22, zlib's archive checked against Mark Adler's release signature), file for file. Each archive is whole except for files no build uses: zlib's `amiga/`, `contrib/`, `doc/`, `msdos/`, `os400/`, `qnx/`, `test/`, `watcom/` and `win32/`; libpng's `ci/`. zlib is under the zlib license (`third_party/zlib/LICENSE`), libpng under the PNG Reference Library License version 2 (`third_party/libpng/LICENSE`).
