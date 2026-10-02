# Parallel library inspection

`LibraryFlow` runs a scan off the UI thread. `LibraryScanPipeline` discovers the
work list, then `ParallelScanWork` inspects independent archives or games using
`min(availableProcessors, 4)` workers. There are only that many submitted tasks;
each claims the next entry, so large libraries do not allocate a future or open
a stream for every entry at once.

- Results retain discovery order, even when tasks finish out of order.
- Hash counters are atomic and progress callbacks are serialized.
- Existing fingerprints still skip unchanged media; force-rehash behavior is unchanged.
- One unreadable game is converted to the existing per-game error entry.
- Cancellation or a fatal failure stops scheduling, signals and interrupts workers,
  and drains them before another scan can start. A provider that ignores both
  cancellation and interruption can delay draining; it cannot race a replacement scan.
- Sorting, cache publication, and post-commit pruning run once on the scan thread
  after all workers finish. Cancelled scans do not reach that commit step.

Directory enumeration remains serial. Parallelism is across games/archives, not
within one archive or disk image. Kairo98's existing archive-copy lock remains;
independent image hashing and ZIP inspection can run concurrently after copying.
DOS temporary archive copies use unique filenames. Both backends create their
digest objects and read buffers per operation.

`ParallelScanWorkTest` verifies four overlapping workers, the concurrency bound,
stable output ordering, cancellation/draining, fatal-error propagation, and
empty/pre-cancelled work. Host tests establish concurrency correctness, not a
handheld speedup: storage and document-provider throughput still matter.
