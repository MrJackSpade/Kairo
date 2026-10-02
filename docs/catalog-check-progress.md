# Catalog check progress

Kairo #14, RGDS, October 2 2026.

Launch-time checks previously suppressed status, while manual status text lived
inside the menu. A shared library banner now shows checking, downloading and
applying stages. The interface remains usable during worker-thread fetching.
Success, unchanged metadata and failure stop the busy indicator and show a
short result for three seconds. Cancellation clears the banner immediately.
Banner layout changes preserve the selected library row's visibility.

`CatalogUpdateTask` carries cancellation and stage reports from the shared
snapshot downloader through both catalog adapters to `CatalogUpdateController`.
Each activity cancels its controller on destruction. Obsolete worker callbacks
cannot restore the indicator or refresh a destroyed screen. Reads and the
pre-activation boundary check cancellation; existing atomic snapshot writes and
validation remain in place.

Run host instrumentation with `-e catalogProgress true`. Both Kairo98 and
KairoDos passed `CatalogProgressFixture` on RGDS:

- checking, downloading, applying and updated states on the actual library;
- library selection still moves during a blocked fetch;
- duplicate checks retain the current progress display;
- result timeout, unchanged revision, failure, cancellation and a subsequent
  check all clear or replace the indicator correctly;
- canceled completion does not refresh the library;
- real `CatalogSnapshotStore` using isolated fixture responses: first revision
  fetches metadata and archive; unchanged revision fetches only metadata and
  performs no validation/archive work; canceled changed revision preserves the
  existing snapshot; a subsequent changed revision activates normally.

The fixture renders each host's 4:3 library to `cache/catalog-progress.png`;
both layouts were inspected. Fixture responses use a test-only URL protocol,
and isolated catalog files are removed afterward. Production catalog data and
game files are not modified by those assertions.

Installed/tested APK SHA-256:

- DOS: `d61054e7f1475b7a9520a7dcc438a708317dd75fe80a667d65dd4923ed184c42`
- PC98: `e688aae05955d02c5c2c5383dce5c42445d18e24a125656a56dc088ac8ab088c`

Separately discovered: Kairo #26 tracks saved snapshots becoming inactive after
an APK timestamp change despite unchanged metadata. This progress work does
not claim to fix that activation bug.
