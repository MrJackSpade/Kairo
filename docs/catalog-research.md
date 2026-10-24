# Catalog research tools

The tools in `tools/` gather and assemble catalog data for every Kairo app. Each app keeps its own research records, record rules and generated catalog; these modules are the parts that do not depend on the platform.

| Module | Purpose |
| --- | --- |
| `launchbox.py` | LaunchBox Games Database research. `snapshot` streams chosen platforms out of LaunchBox's `Metadata.zip`; `listing` caches a platform's public listing pages; `enrich` fills missing box art and screenshot URLs of match entries from their game pages. Also title matching (`key`, `candidates`), public page URLs, and the box art and screenshot choice for new match entries. |
| `mobygames.py` | Dumps MobyGames records, cover and screenshot lists and images for chosen platforms. Game lists are saved as `<platform>-listing.json`: MobyPlus exports, or the `listing` pass's equivalent built from the API's game list. The API key comes from `MOBYGAMES_API_KEY`. |
| `content_ids.py` | Hashes game media in a folder of files and ZIPs into content IDs (`sha256-<kind>-v1:<hex>`). The app describes its kinds, CD data files and size limits with a `Media`. `scan_media.py` writes an app's scan index with them; `--top-level` leaves out subfolders. |
| `launchbox_artwork.py` | Compacts LaunchBox image URLs into catalog artwork references and expands them back to URLs and cache paths. Each app binds its platform codes and repository with `References`. |
| `catalog_documents.py` | Assembles a reviewed catalog source into content-ID shards and the downloadable snapshot, using the app's record validator. |
| `fetch_artwork.py` | Fills an app's `catalog/artwork` with every image its catalog parts reference, for the withImages build: WebP, shrunk to fit 960 by 360 pixels as Kairo98's bundled artwork is. Images already there are kept, so a stopped run continues. |
| `art_review.py` | Serves a local page (`art_review.py <app root>... `, then http://localhost:8765/) listing every game's bundled box art and screenshot at their bundled size. Clicking an image rejects it, "Page reviewed" records the rest as fine, and "Wrong game" flags a bad match. Verdicts go to `catalog/research/art-review-v1.json` by source image URL (`art_verdicts.py`); `research.py` never picks a rejected image and takes the next candidate, so rebuild with `research.py`, `app_catalog.py` and `fetch_artwork.py` afterwards. |
| `catalog_parts.py` | Splits generated documents into the data, controls and artwork parts, and builds the importable artwork package. `APPS` describes each app's repository, product and package. Two parts are adult: `art.nsfw` (the images `core-review-v1.json` lists under `adultArtwork`) and `data.nsfw` (the frank descriptions of adult games in `catalog/research/descriptions-adult-v1.json`, which replace their main descriptions). Play builds leave both out; the importable package carries both. |

## Platforms

| Slug | App | LaunchBox platform | MobyGames ID | Artwork code |
| --- | --- | --- | --- | --- |
| `pc88` | Kairo88 | NEC PC-8801 | 94 | 88 |
| `pc98` | Kairo98 | NEC PC-9801 | 95 | 98 |
| `x68000` | Kairo68 | Sharp X68000 | 106 | 68 |
| `fmtowns` | KairoTowns | Fujitsu FM Towns Marty | 102 | 86 |
| `msx`, `msx2`, `msx2plus` | KairoMSX | Microsoft MSX, MSX2, MSX2+ | 57 (all generations) | 80 |
| `atarist` | KairoST | Atari ST | 24 | 83 |

Artwork codes are permanent once a catalog using them is published, because installed apps derive cache paths from them.

## Source text

LaunchBox and MobyGames records are reference material. Catalog descriptions are written for each app; neither source's description text is copied.
