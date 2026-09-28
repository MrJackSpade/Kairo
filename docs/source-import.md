# Source provenance

The UI code in `frontend/` was extracted from the first-party Kairo98 repository. Kairo98 and KairoDos consume pinned commits of this repository; no emulator core is vendored here.

The Spleen 8x16 font source and BSD-2-Clause license in `third_party/spleen/` are copied from Kairo98's pinned snapshot at upstream commit `57f9219328c9f5873085320fe8bc8f7dd34b8791`. `tools/generate_ui_pixel_font.py` produces `frontend/src/main/assets/ui/spleen-8x16-ascii.bin` from the pinned ASCII table. The glyph bytes and notice must accompany apps that package this library.
The checked-in Gradle 8.13 wrapper JAR has SHA-256 `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`, matching the official [Gradle release checksum list](https://gradle.org/release-checksums/). Its Apache-2.0 license and included-component notices were copied from Gradle's `v8.13.0` `LICENSE` file into `third_party/gradle-wrapper/LICENSE`.
