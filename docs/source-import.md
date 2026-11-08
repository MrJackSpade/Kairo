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

## MIDI synths and the default SoundFont

`cmake/KairoMidi.cmake` builds the MIDI synths for any app's core that plays a game's MIDI music (`MT32Emu::mt32emu`, `FluidSynth::libfluidsynth`), so the apps share one version of each. The snapshots came from KairoDos's pinned archives (`third_party/staging-deps/sources/`), on 2026-11-08.

| Library | Release | Archive | Archive SHA-256 | Licence |
| --- | --- | --- | --- | --- |
| Munt libmt32emu (`third_party/munt/`) | 2.7.3 | GitHub tag `libmt32emu_2_7_3` of `munt/munt` | `e51f3475771c9d07116e6cb5ae2e095ef3b11b3107d92c01d3a1dc03be13ff98` | LGPL-2.1-or-later |
| FluidSynth (`third_party/fluidsynth/`) | 2.5.2 | GitHub tag `v2.5.2` of `FluidSynth/fluidsynth` | `1dcb13308b3aa383db658c60a7b63d73d0ff4601ccee589582ba7a816475410e` | LGPL-2.1-or-later |
| GCEM (`third_party/gcem/`), FluidSynth's maths | 1.18.0 | GitHub tag `v1.18.0` of `kthohr/gcem` | `8e71a9f5b62956da6c409dda44b483f98c4a98ae72184f3aa4659ae5b3462e61` | Apache-2.0 |

Each archive is whole except for files no build uses:

- Munt: only `mt32emu/` (the library) and the top `README.md`. Its other programs and drivers are left out.
- FluidSynth: `sf2/`, `test-android/` and `contrib/` are left out, and so is the empty `gcem/` submodule folder. Its tests' CMake stays, because the top `CMakeLists.txt` adds it.
- GCEM: its `tests/`, `docs/`, `notebooks/` and `binder/` are left out.

`cmake/KairoMidi.cmake` builds both from their source lists, as `KairoLibraries.cmake` builds zlib and libpng, not with their own CMake projects. Those add install, uninstall, test, documentation and CPack targets and global options to the project that adds them, and a core's other libraries have targets of the same names: SDL2's `uninstall` stopped KairoDos's configure. The lists, generated headers and settings follow theirs: Munt's `mt32emu/CMakeLists.txt` (every API, static, its own resampler), and FluidSynth's `CMakeLists.txt` and `src/CMakeLists.txt`. FluidSynth is built for its synthesis API only: C++11 threads, native DLS, and no audio or MIDI drivers, file rendering, libsndfile, glib, OpenMP or network.

Munt is unchanged. FluidSynth has one change, in `src/utils/fluid_sys_cpp11.cpp` (KairoX86#306). Its per-thread values (`fluid_private_*`) were a `thread_local std::map`, which registers a destructor in the library on the first thread that touches it. KairoX86 loads its core afresh for each run and unloads it after, while the thread that closed the synth lives on. On Windows the destructor then ran from unmapped code at exit and crashed. On Android, bionic would keep each run's copy of the library loaded for as long as that thread lives. The values are now behind a plain `thread_local` pointer: the map is made when a thread first sets one and freed when its last one goes, and no destructor is registered.

`third_party/generaluser-gs/` holds the default SoundFont, GeneralUser GS 2.0.3 by S. Christian Collins. It is `GeneralUser-GS.sf2` (SHA-256 `9575028c7a1f589f5770fccc8cff2734566af40cd26ed836944e9a5152688cfe`) and `documentation/LICENSE.txt`, both from commit `684543d5e5efaef08d02be50dcda8d552478fa60` of `mrbumpy409/GeneralUser-GS` (2026-02-23). Its licence lets software ship and modify it.
