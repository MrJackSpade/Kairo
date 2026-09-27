# Kairo frontend

Shared Android frontend code for Kairo98 and KairoDos. Product repositories pin a specific commit of this repository as `shared/` and map `:frontend` to `shared/frontend` in Gradle.

The first-party frontend code is GPL-2.0-or-later; see `COPYING`. The repository remains private while distribution and asset audits are completed. The Spleen font has its own BSD-2-Clause notice in `third_party/spleen/LICENSE`.

The frontend contains UI widgets and Android input helpers. Emulator engines, product assets, package IDs, release workflows, and game catalogs belong to the consuming applications.
