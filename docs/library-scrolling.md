# Library scrolling: RGDS validation, October 2, 2026

Kairo issue #1, with the artwork-space change also covering the reflow mechanism
reported in #8. Both applications use the same frontend implementation.

## Measured causes and changes

- Selection previously notified the entire adapter, resolving catalog data and
  rebinding every visible row. Selection now updates row activation only.
- Artwork completion updates only rows still bound to that artwork path.
- A library snapshot retains resolved metadata records; scan/catalog/edit
  snapshots and artwork refresh invalidate it. The selected-game preview uses
  the same records, avoiding a second catalog lookup and shard-cache churn.
- Row backgrounds and unchanged secondary-panel text/layout parameters are
  reused.
- Android still laid out the full description synchronously despite maxLines.
  Ten layouts of Duke's 1,650-character description took 634.9 ms. Simple line
  breaking took 619.5 ms and PrecomputedText 586.1 ms, so neither was the fix.
  LibraryDescriptionView prepares the bounded StaticLayout on a worker and
  draws the immutable result. Pending requests coalesce; a generation check
  prevents stale selection results, and detach invalidates pending work.
  Full description text remains available to accessibility.
- The secondary artwork column is reserved from catalog metadata before decode.
  Image arrival no longer changes description width or line limits. The image
  container has fixed bounds rather than requesting intrinsic-size reflow.

## Controlled device comparison

Device: RGDS (`RG DS`), Android dual-screen setup, both displays 640×480.
CPU observed at its configured 1.992 GHz maximum. Same installed game libraries
and catalog data, same application/native configuration, no method profiler.
The baseline restores the original LibraryScreen, SecondaryDisplayCoordinator,
and host MainActivity from the pre-fix revisions; other source is unchanged.
The candidate is the implementation described above.

Each run stops the other app, starts a fresh process, waits five seconds, and
warms the list with five Down long-press commands on logical display 2. Reset
gfxinfo, issue five Up and five Down long-press commands, then collect framestats.
Reset again and issue five 400 ms upward swipes followed by five downward swipes
between (350,400) and (350,130), then collect framestats. APK install updates the
existing package; it does not clear application data.

| App / input | Baseline frames | Candidate frames | Baseline p90 | Candidate p90 | Baseline p99 | Candidate p99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| DOS / D-pad | 78 | 70 | 450 ms | 73 ms | 500 ms | 129 ms |
| DOS / touch | 101 | 188 | 150 ms | 40 ms | 300 ms | 300 ms |
| PC98 / D-pad | 85 | 213 | 250 ms | 65 ms | 1300 ms | 150 ms |
| PC98 / touch | 156 | 246 | 101 ms | 40 ms | 200 ms | 65 ms |

These are Android gfxinfo process-level measurements across both app windows,
not guest FPS or an assertion of perfect 60 Hz UI rendering. Frame counts differ
because the implementation changes redraw/async update behavior. Touch still
has occasional cold-row stalls (DOS p99 remains 300 ms); median/percentile values
are workload-specific. The large repeated selection stall is substantially
reduced in both apps. Earlier diagnostic runs mixed startup and method sampling
and must not be used as the controlled speedup comparison.

Local raw evidence is `.tmp/scroll-comparison/{dos,pc98}-{baseline,candidate}-{dpad,touch}.txt`
in the KairoDos checkout. `.tmp/MeasureLibraryScroll.ps1` contains the exact input
sequence. Earlier sampled traces attributed 6.64 seconds inclusive to gameView
and 5.61 seconds to DOS catalog resolution. Later traces isolated description
layout; after the final metadata reuse, catalog resolution was no longer a
material main-thread attribution. Inclusive trace times overlap.

## Functional validation in both installed applications

Shared LibraryScrollFixture passed in DOS and PC98 on RGDS:

- Moving between visible rows performs no extra catalog resolutions.
- Recycled rows have exactly one correct activation; returning to visited rows
  reuses metadata, while a new snapshot invalidates it.
- Secondary description width is unchanged when pending artwork arrives.
- Rapid description replacement publishes the latest text, respects the line
  limit and current width, preserves accessibility text, and survives reattach.

Real library D-pad and touch workloads completed in both apps. Screenshots
confirmed the catalog title, exact source filename, artwork and description
remain rendered. The DOS description screenshot before/after asynchronous
layout matched visually. The separate held-repeat viewport bug (#2), menu/search
issues and other open tickets are not claimed fixed by this work.

Installed candidate SHA-256 values were checked against the local tested APKs:

- DOS: `0f22ce9d9a4fce31396100d1cc36b05d873286cbf8de28ff142fe9ee48673de2`
- PC98: `eb14509e70974dd85be13a23aeddadbb999f895b2892996f6db0fb660cd00ea4`

Baseline APK hashes:

- DOS: `f5b8d87eeebdf3eae158e1b42f374540b9aaae6cd19978455e3e42f8efa3841c`
- PC98: `54e1ed25db0134deb66801c91e3de95e08ec53512eba2bc11367d82fb1186646`
