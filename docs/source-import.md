# Source provenance

The UI code in `frontend/` was extracted from the first-party Kairo98 repository. Kairo98 and KairoDos consume pinned commits of this repository; no emulator core is vendored here.

The Spleen 8x16 font source and BSD-2-Clause license in `third_party/spleen/` are copied from Kairo98's pinned snapshot at upstream commit `57f9219328c9f5873085320fe8bc8f7dd34b8791`. `tools/generate_ui_pixel_font.py` produces `frontend/src/main/assets/ui/spleen-8x16-ascii.bin` from the pinned ASCII table. The glyph bytes and notice must accompany apps that package this library.
