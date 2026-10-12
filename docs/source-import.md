# Source provenance

The frontend code originated in the first-party [Kairo98](https://github.com/MrJackSpade/Kairo98) project. Kairo98 and [KairoDos](https://github.com/MrJackSpade/KairoDos) consume pinned commits of this repository. No emulator core is vendored here.

The Spleen 8x16 BDF and BSD-2-Clause license in `third_party/spleen/` come from upstream commit `57f9219328c9f5873085320fe8bc8f7dd34b8791`. `tools/generate_ui_pixel_font.py` produces the UI font asset from the pinned ASCII table.

The Gradle 8.13 wrapper JAR has SHA-256 `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`, matching [Gradle's checksum list](https://gradle.org/release-checksums/). The Apache-2.0 license and notices are preserved in `third_party/gradle-wrapper/LICENSE`.

The RetroArch Slang runtime is [librashader](https://github.com/SnowflakePowered/librashader) v0.11.0 at commit `bd2b8789a8d571add408d46590b3a2ff7e11886f`, copied into `third_party/librashader/` from that Git tag. Its `Cargo.lock` fixes the dependency versions and registry checksums. `third_party/cargo-crates/` holds the 129 corresponding crates.io source archives for the Android arm64 OpenGL dependency closure; `SHA256SUMS.txt` was verified against `Cargo.lock`. Gradle builds `librashader-capi` with Rust 1.90.0 for `aarch64-linux-android`, `--no-default-features --features runtime-opengl`.

Three built-in presets and their shader files use six unmodified files from [Libretro's Slang collection](https://github.com/libretro/slang-shaders) commit `e1d75632a205c70f14a4cc947c46d5abb7b3f7f1`. The exact copied files, SHA-256 hashes, and per-shader license findings are in the pinned [shader snapshot](../third_party/slang-shaders/README.md). The frontend packages only those files; additional user-imported ZIPs are not redistributed by the app.
