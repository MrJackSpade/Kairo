# Shared documents

Every Kairo repository's common documents come from this folder; [`tools/docs.py`](../../tools/docs.py) writes them and `docs.py <repository> --check` fails when one is stale.

- [`apps.json`](apps.json) lists every Kairo app and the facts its documents use: system, module, package, Google Play, privacy details and, for apps whose frontend names are known, the external frontend setup. The family grid in every README is built from it.
- [`LICENSE.md`](LICENSE.md), [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md) and [`frontends.md`](frontends.md) are whole documents. The privacy policy becomes the app's `PRIVACY_POLICY.txt` asset and, when `privacyPage` is set, `docs/privacy-policy.html`.
- [`blocks/`](blocks) holds the shared parts of a README or other `docs/*.md` file, placed between `<!-- kairo:NAME -->` and `<!-- /kairo:NAME -->`. The rest of the file is the repository's own.
- [`../assets/`](../assets) holds shared images, such as the Discord banner, which READMEs link from this repository.

To add a console, add its entry to `apps.json`, put the blocks it uses in its README (`discord`, `community`, `family`, `clone` and `license` at least), run `docs.py` in the new repository, and run it in every other repository to add the console to their grids.
