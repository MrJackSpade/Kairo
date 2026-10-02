# Secondary library artwork (Kairo #8)

Both hosts now pass a metadata snapshot, artwork path and stream opener to
SecondaryDisplayCoordinator. They no longer own duplicate decode executors,
generation counters, bitmap publication or teardown logic.

LibraryArtworkLoader, owned by the shared coordinator, provides:

- An 8 MiB bitmap cache keyed by immutable catalog artwork paths. A cache hit
  publishes the bitmap and text together, without first clearing the image.
- One worker with superseded queued requests canceled and removed. The latest
  selection does not wait behind a backlog of obsolete queued images.
- Generation checks preventing a completed old load from replacing a newer
  selection or restoring a cleared page.
- Cancellation and worker shutdown on coordinator stop; resume retries the
  current request if needed. Completed cached images remain reusable.
- Stable reserved artwork bounds for pending/failed images. Failures are not
  cached, allowing a later retry. Absent artwork uses the text-only layout.

The stable image container and description width were introduced with #1.
This follow-up removes repeated decoding on revisits and consolidates the
remaining asynchronous/lifecycle code. PC98's prior preview executor also
lacked teardown; the shared owner now handles it for both apps.

## RGDS verification, October 2, 2026

LibraryArtworkFixture runs inside the actual app and uses its real secondary
coordinator and bottom-screen views. It passed in **both DOS and PC98**:

- A generated uncached image appears asynchronously without changing reserved
  description width.
- A cached image appears immediately, without an empty intermediate update;
  its source is opened exactly once across revisits.
- A deliberately blocked old image cannot replace the latest cached image.
- An obsolete queued image is never opened.
- Absent artwork removes the image slot; an I/O failure shows no old image and
  does not reflow the reserved layout.
- A decode deliberately completing after stop, clear, and resume cannot
  resurrect the cleared page. Cached artwork remains usable after resume.

Commands:

```text
am instrument -w -e libraryArtwork true com.loxifi.kairodos.test/com.mrjackspade.kairodos.CatalogUpdateInstrumentation
am instrument -w -e libraryArtwork true com.loxifi.kairo98.test/com.mrjackspade.kairo.frontend.LibraryUiInstrumentation
```

Both APKs update the existing packages on RGDS. Installed hashes matched the
local tested artifacts:

- DOS: `0afef030ef8037e4be2a6df63eb28a6e0a5375ed2ab2f6c95219f1efee4f5079`
- PC98: `9fcfc7732a915477764126e0ed556e000ca54521d16d434e761faa7a6bd144fc`

Real-library forward/reverse navigation and bottom-screen screenshots also
confirmed the correct selected-game artwork and metadata in both apps.

Uncached artwork still requires decoding; this change does not claim zero load
latency. The page retains its layout during that work and never displays a
different selection's completed image.
