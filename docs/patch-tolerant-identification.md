# Patch-tolerant game identification

Status: plan, agreed 2026-10-26. Nothing below is implemented yet.

The catalog recognizes a disk only by the SHA-256 of its exact bytes (`sha256-<kind>-v1`). Any change to a disk gives it a new identity, and the player sees an unrecognized game. That rules out every fan translation the catalog has never hashed, every new version of a translation, and a player's own copy that holds saved games or was installed differently.

This plan adds a second way to recognize a disk: by content that patches leave alone, chosen automatically for every game in the catalog.

## Why

Korean and Chinese players have reported translated PC-98 games the catalog doesn't recognize. The catalog knows only the English translations in `nec-pc-9801-translations`.

### Research (2026-10-26)

- **Korean.** [RetroDB](https://retrodb.info/krpatch?platform=PC98) indexes Korean patches and lists 98 PC-98 entries, about 55 games once old versions are counted out. It hosts patches only, never disk images. Groups such as 2KOR LAB, 찌니짱, mcpads and 아톨 release new versions weekly, and each version gives a disk new hashes.
- **Chinese.** There is no index and no pack. Releases are spread across Baidu Tieba, Bilibili and Baidu netdisk links, and several 2026 releases are machine-assisted. Some "Chinese versions" are ports to DOS or to phone viewer apps rather than PC-98 disks; those are out of scope.
- **Spanish, French and others.** Almost nothing exists for PC-98.
- **Fonts.** Korean and Chinese patches ship their own `FONT.BMP`. Kairo98's default font setting ("Game folder") already uses one beside the game.

### The halted approach

The first approach downloaded each patch, applied it to our copy of the original, hashed the result, added the hashes to the game's catalog entry, and deleted the files. It is halted because:

- every new patch version needs the whole cycle again;
- it covers only patches we find and can download;
- it does nothing for players' own modified copies.

Two pieces of it remain useful for validation (see [Validation](#validation)):

- Downloading from RetroDB needs `~/.claude/allow-krpatch-downloads.ps1` run once. The auto-mode classifier blocks following RetroDB's download links otherwise. `-Remove` undoes it.
- Patchers distributed as Windows programs run only inside Windows Sandbox, never on the host. Enabling the feature is unconfirmed: the elevated enable command exited with code 1 and its log was empty.

## Platforms

| App | Platform | Media | File systems to read |
| --- | --- | --- | --- |
| Kairo98 | NEC PC-9801 | HDI hard disks, floppies (FDI, D88, NFD, FDD, DCP/DCU, HDM, XDF), CloneCD | FAT12/16 floppies; NEC partition table, then FAT, on hard disks; ISO 9660 on CDs; many floppies have no DOS file system |
| Kairo88 | NEC PC-8801 | D88 floppies, tapes | N88-DISK BASIC file system on floppies; many floppies boot their own format with no file system; tapes have none |
| KairoTowns | Fujitsu FM Towns | CDs, floppies, hard disks | ISO 9660 on the CD data track; FAT on floppies and hard disks |

**Pending decision: Kairo68 (X68000) and KairoMSX (MSX).** The Sharp X68000 was sold only in Japan. MSX's library is mostly Japanese and has an active fan-translation scene. Both are candidates for the same treatment. Because the design lives in `shared/`, adding them later means adding their file-system readers and running the scanner.

Keys are chosen and matched within one platform's catalog only. A file that a PC-98 game shares with its FM Towns port does not count against either.

## Design

### Order of identification

Each game gets the strongest kind of key its media supports. A disk is identified by the first rule that matches:

1. **Exact disk hash**, as today.
2. **File key:** distinctive files, plus the names of files around them.
3. **Chunk key:** distinctive chunks of raw bytes.
4. **Not recognized**, as today.

### File keys

PC-98, PC-88 and FM Towns games open their files by name, so a patch keeps the names. The folder a game sits in does vary: the root of a floppy, or anywhere on a hard disk install, such as `C:\GAMES\ELF\DOUKYU\`. So a file key describes one folder and never its path.

A file key is:

- **anchor files:** distinctive files whose contents are hashed;
- **sibling names:** names that must also be present in the same folder, whose contents are never read.

Matching a folder:

1. The folder listing contains the anchor names and the sibling names. This step reads no file contents.
2. Only then are the anchor files hashed and compared.

Hashing only after the names match keeps library scanning cheap. Sibling names also separate games that share an anchor's contents, and stop matches on common names such as `DATA.DAT`.

Exceptions to stable names, measured rather than assumed:

- a patch adds files (a font, translated scripts, a loader that patches the game in memory) — harmless, because only the listed names must be present;
- a patch renames or replaces the main program, or adds a launcher under its name — sibling names are chosen from names that survive patching in the translated pairs;
- Japanese file names are stored in Shift-JIS — patches don't rename them.

The main program file is a poor anchor. It is the file translations rewrite most, since text and font routines live there, and many games share one engine program file across titles. It works well as a sibling name.

### Chunk keys

Chunk keys cover two cases:

- every distinctive file of a game is modified, such as when a game keeps everything in one or two archives and the translation re-inserts translated graphics;
- the disk has no file system at all.

The raw bytes of a disk are split into chunks whose boundaries come from the content itself (content-defined chunking, as rsync and backup tools use). Because the boundaries follow the content, an insertion or a replaced archive member changes only the chunks it touches. Distinctive chunks are selected and matched the same way as anchor files.

Chunking needs no file system, so it also covers self-booting floppies and PC-88 disks. The app already reads every byte of a disk to hash it, and computes chunk hashes in the same pass.

Hard disk installs can fragment a file across allocation units. Chunk size, or alignment to those units, must survive that; the measurement settles the parameters.

The scanner computes both kinds of key. If measurement shows chunk keys alone are accurate enough, file keys become only a speed shortcut.

### Match rule

- **Several keys per game.** Each game gets several independent keys: anchors from different folders and file types, plus chunks. A disk matches when it carries most of a game's keys, so one modified or missing file never prevents a match. A patch would have to change most of a game's distinctive content to break recognition.
- **The most specific game wins.** When keys point to more than one game, the game whose full key set matches most wins. A key that would identify two games is never selected (see [Selection](#selection)).
- **The result records how the disk was identified:** exact hash, file key or chunk key.

**Safety property.** A wrong match can only come from content shared by two games. That is checked directly over the whole library, independent of any prediction about patches. A wrong guess about which files survive a patch loses recognition of a translated copy, which then appears unrecognized exactly as it does today. It can never show another game's title or artwork.

## Selection

Selection is automatic for every game. Each platform has more than 2000 games, and per-game manual work is not acceptable.

1. **Scan.** For every disk in the library (NeoKobe and the translation collections), read the file system and record each file's folder-relative name, size and SHA-256. Record chunk hashes of the raw bytes.
2. **Group.** The catalog already records which disks belong to which game. That grouping is the ground truth.
3. **Count.** For each file hash and chunk hash, count the games that contain it. Content found in exactly one game is distinctive. DOS system files, shared drivers and shared engine programs drop out automatically.
4. **Prefer content patches leave alone.** Rank distinctive candidates by the evidence in [Knowing what patches touch](#knowing-what-patches-touch). Prefer large files.
5. **Choose sibling names.** Use names present in an anchor's folder in every known release of the game.
6. **Emit keys.** Write each game's keys and the kind of key each game reached.

Selection reports, for review:

- **games with no key:** pure reuse of another game's data with no distinctive content;
- **games whose content sits entirely inside another's,** such as a fan disc included whole in a compilation. Content cannot tell these apart. Usually the catalog should treat one as a release of the other; otherwise only exact hashes separate them;
- **collisions between catalog entries:** two entries sharing all their data usually means a duplicate, a compilation or a mis-grouped release. This doubles as an audit of the catalog's grouping.

## Knowing what patches touch

Translations exist for under 10% of the PC-98 library and fewer for the other platforms. For about 90% of games there is no direct evidence of what a translation would change. The design therefore never depends on that prediction: several keys per game and the safety property hold without it. Prediction only orders candidates.

### Evidence

- **Translated pairs.** PC-98 has 173 English translations beside their Japanese originals. Comparing their files shows which names survive and which files patches rewrite.
- **Japanese text inside a file.** A file containing readable Shift-JIS text holds text a translator must change: scripts, message files, programs with embedded strings. Such files are excluded as anchors. The test only excludes. Translators also redraw images with Japanese lettering (title logos, menus), which no byte-level test can see, so passing it proves nothing.
- **Engine families.** Translated games cluster on popular engines, and the engine program file is often identical across titles. A game on the same engine as translated games very likely has the same files touched. The scanner reports how many untranslated games share an engine with at least one translated game.

### Confidence gate

A heuristic decides anything only after measurement shows it is very reliable. The bar is set by the user after seeing the numbers. Until a heuristic passes:

- it only reorders candidates;
- each game falls back to several keys with no assumption about which files patches touch.

## Validation

- **Collisions.** No selected key resolves to two games.
- **Held-out translated pairs.** Choose keys from part of the translated pairs and test on the pairs held out. Each held-out translated disk must be identified as the right game from its keys alone, ignoring its known exact hash.
- **Korean patches as a second test set.** Different translators and tools, and likely different habits with fonts and graphics. Download a sample through RetroDB, apply it to our originals (Windows programs only in Windows Sandbox), test, and delete the patched files.
- **Releases of the same game.** Keys chosen from one release recognize the game's other releases.
- **Bias.** Translated games lean towards visual novels and famous titles. Results are broken down by engine and genre, so one weak category cannot hide behind a good total.
- **Per kind of key.** Report how many games reached each kind of key, and the accuracy of each.

PC-88 and FM Towns need their own translated test sets. None has been gathered yet.

## Open decisions

- **Launch settings on a file or chunk match.** These keys identify the game, not the exact disk. Title, description and artwork are safe to apply. Disk-specific settings may not fit a patched disk: typed startup commands, startup choices keyed to screen hashes, and `setDisks`. Recommendation: apply them only on an exact-hash match unless decided otherwise.
- **Kairo68 and KairoMSX.** Whether to include them (see [Platforms](#platforms)).
- **The confidence bar** for heuristics (see [Confidence gate](#confidence-gate)).

## Phases

1. **Measure** (catalog tools only, no app change).
   - A scanner in `shared/tools/` beside `content_ids.py` that records file manifests and chunk hashes, with file-system readers for FAT (with the NEC partition table), ISO 9660 and N88-DISK BASIC.
   - Key selection and the validation above.
   - Output: the numbers for each platform. Nothing ships until they are reviewed.
2. **Catalog.**
   - A key index added to each app's catalog. Older app versions ignore it.
   - Builders emit it from the selection output.
3. **App.**
   - Shared Kotlin file-system readers, matching that is shared across apps in the library scan, and the identification order above.
   - Every app gets the same shared code.
