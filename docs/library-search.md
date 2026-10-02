# Library search latency

Kairo #9, RGDS, October 2 2026. Same installed libraries and query sequence
(`a`, `al`, `ali`, `alic`, `alice`, then backspace to empty), fresh host
process, eight seconds after launch. Measurements use the real host library
through `LibrarySearchFixture`, without the method sampler enabled.

| App | Entries | Before: first edit / draw | After: first edit / draw | After: subsequent nonempty draws |
| --- | ---: | ---: | ---: | ---: |
| KairoDos | 49 | 4572 / 4672 ms | 31 / 35 ms | 19â€“22 ms |
| Kairo98 | 215 | 3630 / 3732 ms | 36 / 40 ms | 19â€“21 ms |

Clearing the query drew in 68 ms (DOS) and 61 ms (PC98). Separate checks
waited for the expected result IDs and UI idle: DOS 29â€“99 ms; PC98 199â€“339 ms.
Those include list layout and instrumentation idle synchronization and are
not measurements of filtering CPU time. Character rendering and settled
results are deliberately reported separately.

A separate sampled baseline DOS trace attributed 5.314 seconds of inclusive
main-thread CPU to `applyFilter`, including 4.836 seconds in catalog resolve
and 4.340 seconds in bundled shard loading. These overlapping times must not
be added. Search resolved every entry synchronously, thrashing the catalog's
small shard cache. Sampling increased elapsed latency, so the comparison
above uses untraced runs.

## Shared implementation

`LibrarySearchIndex` resolves each snapshot's metadata and caches its title
and filename on one worker. Warming starts after the first library frame.
Queries reuse those strings and keep the existing case-insensitive matching.
The UI receives only results for its current snapshot and query. Replacing
entries or refreshing metadata invalidates the index; clearing a query or
detaching the screen invalidates pending results. Obsolete queued searches
are discarded. The blank query still restores the complete library and its
Continue entry immediately.

`SearchIndexFixture` deliberately blocks catalog resolution and submits 50
obsolete queries before the final query. It verifies off-main-thread
resolution, one resolution per row, catalog-title matching, case handling,
snapshot replacement, clearing, and closing. Both app instrumentation
runners passed this fixture and real-library result-ID checks on RGDS.

Run the host instrumentation with `-e librarySearch true`; add
`-e traceSearch true` only for an attribution trace. Tracing writes
`files/search-profile.trace` in the target app. Tests do not alter catalog
downloads, saved controls, or games. The expanded test temporarily changes one
title override and restores the original override record and file bytes in `finally`.

Validated APK SHA-256:

- DOS: `d86fb859c0907a7413e9e25ea6edcf57df0d37c263920bdfa7b5491ef61f0db8`
- PC98: `dd6c7fbd2f9da5f93bff092c8fbd73067450c8ec24a6d5ed3a16d20ef986e5ee`

## KairoDos #50 acceptance follow-up

RGDS, October 2 2026, production shared source `65b1bd4` (search fix originally
`3152acd`). The expanded fixture closes the prior search snapshot, clears the
host's actual LRU catalog caches under its catalog lock, refreshes metadata, and
immediately types/backspaces without the old 100 ms delay between keys. The warm
pass waits for indexing to finish. No game/core is running.

| Host | Entries / distinct shard prefixes | Cold nonempty edit / draw range | Warm nonempty edit / draw range | Empty-query draw cold / warm |
| --- | --- | --- | --- | --- |
| KairoDos | 49 / 44 | 18–38 / 22–45 ms | 19–30 / 24–38 ms | 68 / 82 ms |
| Kairo98 | 215 / 133 | 19–43 / 24–48 ms | 21–45 / 27–55 ms | 109 / 92 ms |

Cold indexing still had 1826 ms (DOS) / 2712 ms (PC98) left after rapid typing;
this background work did not block character rendering. Settled result checks
were 41–122 ms / 209–345 ms, including UI idle/layout. They matched independently
computed case-insensitive catalog-title OR filename results, including empty
matches. These measurements describe this device/library, not all storage or
library sizes.

Both hosts also passed actual metadata override invalidation while a query was
active: a temporary unique title became searchable after `refreshArtwork`, then
restoring the title and publishing the same entries through `showEntries` removed
the obsolete match. DOS title edits and successful catalog downloads both call
`showEntries`; the test exercises that same refresh boundary without publishing
fake downloaded metadata. `SearchIndexFixture` continues to assert off-main-thread
resolution, one resolve per row per snapshot, 50 superseded queries, replacement,
clear and close. No additional production fix was required for #50.

Raw instrumented results are in `search-issue50-dos.txt` and
`search-issue50-pc98.txt` next to this report. Both test runners passed on the RGDS.
