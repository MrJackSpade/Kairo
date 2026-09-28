# Licensing

The first-party Kairo frontend is GPL-2.0-or-later; see [COPYING](../COPYING). Kairo98 and KairoDos pin this source and provide corresponding source for their combined builds.

The Spleen 8x16 font is BSD-2-Clause. Its source and full notice are in `third_party/spleen/`; the generated `frontend/src/main/assets/ui/spleen-8x16-ascii.bin` derives from it. Each consuming app includes the notice in its distributed license information.

The Gradle wrapper is Apache-2.0 build tooling, not app runtime code. Its license and notices are in `third_party/gradle-wrapper/LICENSE`. The shared repository contains no emulator engine, game, operating system, BIOS, ROM, catalog artwork, or signing material. `catalog/dos/online-v1.zip` is a public feed of hash identities, game titles, factual tags, launch settings, artwork paths, and controller profiles. The export removes eXoDOS/LaunchBox descriptions and contains no artwork. Third-party core and bundled catalog obligations belong to each product's combined build; see their licensing documents.
