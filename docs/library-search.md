# Library search latency

Kairo #9, RGDS, October 2 2026. Same installed libraries and query sequence
(`a`, `al`, `ali`, `alic`, `alice`, then backspace to empty), fresh host
process, eight seconds after launch. Measurements use the real host library
through `LibrarySearchFixture`, without the method sampler enabled.

| App | Entries | Before: first edit / draw | After: first edit / draw | After: subsequent nonempty draws |
| --- | ---: | ---: | ---: | ---: |
| KairoDos | 49 | 4572 / 4672 ms | 31 / 35 ms | 19–22 ms |
| Kairo98 | 215 | 3630 / 3732 ms | 36 / 40 ms | 19–21 ms |

Clearing the query drew in 68 ms (DOS) and 61 ms (PC98). Separate checks
waited for the expected result IDs and UI idle: DOS 29–99 ms; PC98 199–339 ms.
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
files, saved controls, or games.

Validated APK SHA-256:

- DOS: `d86fb859c0907a7413e9e25ea6edcf57df0d37c263920bdfa7b5491ef61f0db8`
- PC98: `dd6c7fbd2f9da5f93bff092c8fbd73067450c8ec24a6d5ed3a16d20ef986e5ee`
