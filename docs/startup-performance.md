# Startup performance (Kairo #15)

## Measurement

RGDS, Android, 2026-10-02. Both applications have their real saved library and
downloaded catalog active: 215 PC-98 entries and 49 DOS entries. Same device,
one app at a time. No game launched. Catalog bytes were not changed.

The shared `StartupFixture` measures `onCreate`, the first library draw, and
the first draw containing actual game rows. Run 0 uses a fresh app process;
runs 1/2 recreate the activity in the same process. These are **not** cold
filesystem-cache or resume measurements. Timings below have tracing disabled.
Three observations establish this device's result, not a statistical guarantee
for other devices/libraries.

On this dual-display device, a plain `am start -W` can time the redirect
activity on display 0 instead of the frontend on display 2. The fixture targets
the non-default display explicitly and observes the actual library view.

## Before/after, milliseconds

| App / run | onCreate before | after | First frame before | after | Populated library before | after |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| PC98 fresh process | 14559 | 5005 | 15323 | 5781 | 16786 | 7313 |
| PC98 warm process 1 | 10820 | 3253 | 11259 | 3669 | 12544 | 5064 |
| PC98 warm process 2 | 9566 | 3199 | 9983 | 3611 | 11089 | 4917 |
| DOS fresh process | 2328 | 2329 | 2941 | 2953 | 4875 | 4868 |
| DOS warm process 1 | 1512 | 1519 | 1773 | 1765 | 2749 | 2750 |
| DOS warm process 2 | 1422 | 1477 | 1656 | 1704 | 2479 | 2433 |

PC98's fresh-process populated-library delay fell 56%. DOS is effectively
unchanged. This does not claim startup is instantaneous: PC98 still decodes
its monolithic downloaded JSON and restores a larger installed library.

## Phase attribution

Separate 5 ms ART sampling runs, 96 MiB buffer, no overflow. These timings are
approximate inclusive wall time; nested phases overlap and must not be added.
They include instrumentation overhead, unlike the table above.

| Phase | PC98 before | PC98 after | DOS baseline |
| --- | ---: | ---: | ---: |
| MainActivity.onCreate | 16.094 s | 5.203 s | 2.423 s |
| Catalog constructor | 14.150 s | 3.284 s | 1.628 s |
| Read downloaded JSON, including old duplicate validation | 11.894 s | 3.182 s | ZIP adapter; no monolithic JSON parse |
| Full duplicate schema validation on main thread | 11.873 s | none | none |
| Bundled asset JSON reads, including later shards | 2.675 s | 0.471 s | Shards loaded as needed |
| Cached library restore including presentation | 1.277 s | 1.313 s | 0.747 s |
| Cached PC98 library file decode | 0.586 s | 0.616 s | Included in restore above |
| First row construction, accumulated samples | 0.184 s | 0.262 s | 1.571 s |
| Catalog metadata HTTP check, worker thread | 0.544 s | 0.540 s | 0.651 s |
| Startup archive download | none | none | none |
| Artwork bitmap decode, separate workers | 0.011 / 0.016 s | Same shared loader | 0.011 / 0.017 s |
| Native library load | 0.011 s | 0.005 s | 0.018 s |
| Emulator session initialization | not started | not started | not started |

Before PC98 onCreate spent 14.150 of 16.094 seconds constructing its catalog;
the remaining 1.944 seconds includes other activity/UI construction. After,
that remainder is 1.919 seconds. Cached-list restoration is posted afterward.
Artwork is decoded asynchronously and is not responsible for the blank startup
delay. Metadata checks likewise run on the catalog worker.

The final DOS trace remained comparable: onCreate 2.475 s, catalog constructor
1.652 s, cached restore 0.762 s, row construction 1.595 s, and native library
load 0.016 s. Its metadata check took 0.567 s on the worker. No duplicate schema
validation appeared on its main thread.

## Changes and boundaries

1. `CatalogSnapshotStore.activeFile()` already guarantees a checksum-verified
   snapshot accepted by this APK's validator. PC98 now decodes that JSON without
   repeating full schema validation on every activity construction. Validation
   remains in the shared store's download and post-APK reactivation paths.
2. PC98's bundled filename fallback index is lazy. Hash matches and downloaded
   name matches do not need it. A bundled-only filename match still loads it.
3. Timing, tracing, and startup observations live in shared test infrastructure;
   format-specific catalog decoding remains in the host adapter. No emulator,
   user controls, generated catalogs, or display configuration changed.

## Validation and reproduction

Both signed app updates installed over the existing RGDS packages. Both passed
`SnapshotActivationFixture`: unchanged revisions avoid archive downloads and
repeat validation; compatible saved snapshots reactivate after APK updates,
including offline; corrupt files are repaired; incompatible revisions remain
inactive; rejected downloads preserve the prior snapshot. Actual downloaded
controller metadata was active in both hosts before timing.

PC98 startup also checks a separate catalog instance: bundled fallback is not
eagerly initialized; a downloaded filename match wins without loading it;
removing that in-memory update produces the same bundled filename record.
This test does not edit catalog files or user overrides.

Build the app and Android test APK. Stop the other app, then invoke:

```text
adb shell am instrument -w -e snapshotActivation true <runner>
adb shell am force-stop <target-package>
adb shell am instrument -w -e startup true <runner>
```

Runners:

- PC98: `com.loxifi.kairo98.test/com.mrjackspade.kairo.frontend.LibraryUiInstrumentation`
- DOS: `com.loxifi.kairodos.test/com.mrjackspade.kairodos.CatalogUpdateInstrumentation`

For a separate phase trace, add `-e traceStartup true`; pull
`/data/data/<target-package>/files/startup-profile.trace`. It uses one launch
and must not replace untraced timing. Trace files and local measurement logs
remain ignored workstation artifacts.

Installed APK SHA-256:

- PC98: `5c11bce39997d96a4751f7d111297b88b330027b6a2136f808813ed7a1584ecc`
- DOS: `bd2dd39a836d35a85457fca5ef119a2c37b1886d37626fbf30f47f98d522b18a`
