# Source provenance

The frontend code originated in the first-party [Kairo98](https://github.com/MrJackSpade/Kairo98) project. Kairo98 and [KairoDos](https://github.com/MrJackSpade/KairoDos) consume pinned commits of this repository. No emulator core is vendored here.

The Spleen 8x16 BDF and BSD-2-Clause license in `third_party/spleen/` come from upstream commit `57f9219328c9f5873085320fe8bc8f7dd34b8791`. `tools/generate_ui_pixel_font.py` produces the UI font asset from the pinned ASCII table.

The Gradle 8.13 wrapper JAR has SHA-256 `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`, matching [Gradle's checksum list](https://gradle.org/release-checksums/). The Apache-2.0 license and notices are preserved in `third_party/gradle-wrapper/LICENSE`.
