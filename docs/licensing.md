# Licensing

The first-party Kairo frontend source is GPL-2.0-or-later; the full GPL-2.0 text is in `COPYING`. This repository provides the corresponding frontend source for product builds that pin it. The GPL license applies to first-party code, not to third-party materials under their own notices.

The bundled Spleen 8x16 font is BSD-2-Clause. Its source and full notice are in `third_party/spleen/`. The generated 1,520-byte `frontend/src/main/assets/ui/spleen-8x16-ascii.bin` derives from that snapshot. Each consuming app must include the Spleen notice in its distributed license information.

Before distributing either product, audit the exact combined artifact and provide the complete corresponding source for that build, including the pinned frontend commit. KairoDos's planned DOSBox Pure integration also requires its full source and notices.
## Source distribution inventory

The tracked frontend contains first-party Kotlin and Android UI code, two first-party vector icons, a generated Spleen font asset, and the Gradle wrapper. It contains no emulator engine, game, operating system, BIOS, ROM, catalog artwork, or signing material. This inventory covers the shared source repository; each consuming app still needs an audit of its exact release artifacts.

The Gradle 8.13 wrapper JAR and scripts are Apache-2.0 build tooling, not app runtime code. The wrapper JAR SHA-256 is `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`, matching [Gradle's published checksum](https://gradle.org/release-checksums/). The Gradle 8.13 license and bundled notices are in `third_party/gradle-wrapper/LICENSE`. The pinned Spleen license remains in `third_party/spleen/LICENSE`.
