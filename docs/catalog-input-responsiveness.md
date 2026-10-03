# Catalog indexing must not block library input

The RGDS ANR traces from October 3, 2026 at 01:19:11 and 01:19:35 show the main thread waiting for the DOS catalog monitor in `resolve` and `hiddenFromLibrary`. The monitor owner was the background search index, parsing bundled catalog shards. Running indexing on a worker did not protect navigation because the UI still requested metadata through the same synchronized catalog.

Library rendering, selection previews and detail pages now consume UI-owned metadata snapshots. A cold row temporarily uses its filename. The index publishes metadata in batches as it resolves records, updating the selected preview/detail without changing selection. Visibility filtering also runs off the UI thread, retaining the displayed list until the replacement is ready. Generation checks reject superseded results; detachment cancels pending work.

Both apps built and passed `CatalogProgressFixture` on the RGDS. The fixture deliberately holds the real catalog monitor, clears cached row metadata, dispatches D-pad input, opens/closes details and refreshes the catalog. The final runs completed those UI actions in 127 ms for DOS and 39 ms for PC-98 without releasing the catalog lock first. Existing menu key/hat navigation, progress, failure, cancellation and unchanged-revision checks passed, as did `SearchIndexFixture` for stale queries and replaced/closed snapshots. No new device ANR appeared during verification.

The Retroid endpoint did not respond and mDNS advertised only the RGDS; device verification for this fix was on the RGDS.
