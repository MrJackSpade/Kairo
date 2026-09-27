# Licensing

The first-party Kairo frontend source is GPL-2.0-or-later; the full GPL-2.0 text is in `COPYING`. This repository remains private while distribution and asset audits are completed. The GPL license applies to first-party code, not to third-party materials under their own notices.

The bundled Spleen 8x16 font is BSD-2-Clause. Its source and full notice are in `third_party/spleen/`. The generated 1,520-byte `frontend/src/main/assets/ui/spleen-8x16-ascii.bin` derives from that snapshot. Each consuming app must include the Spleen notice in its distributed license information.

Before distributing either product, audit the exact combined artifact and provide the complete corresponding source for that build, including the pinned frontend commit. KairoDos's planned DOSBox Pure integration also requires its full source and notices.
