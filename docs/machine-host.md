# Machine host

Products whose emulator core runs one guest frame at a time and draws a software frame (Kairo88,
and the Kairo68, KairoTowns and KairoMSX apps to come) share their whole app around the core.
A product supplies the core and what is specific to its machine; everything else is here.

## Native: `frontend/src/main/cpp/kairo/`

| File | What it does |
| --- | --- |
| `frame_machine.h` | `FrameMachine<Core>`: runs a session's core on its own thread, one frame per slot on a grid paced by the audio device's clock, with pause, fast-forward, mute, and commands (`post`, `call`) that run between frames. Frames go to a presenter thread that draws through the Slang shader when one is set, or copies them to the window. The `Core` interface is documented at the top of the file. |
| `audio_output.h`, `audio_ring.h` | The AAudio stream and the lock-free queue between the emulation thread and the device callback. |

A product's JNI starts a session with a factory that builds its core on the emulation thread, and
forwards input and media changes with `post` and `call`. `call` waits for the session to answer or
end; it has no time limit.

## Kotlin: `frontend/src/main/java/com/mrjackspade/kairo/frontend/`

| Class | What it does |
| --- | --- |
| `KairoActivity` | The app every product runs: library and game page, the session's screen, flyout and pages, controllers, touch, keyboards, the second screen, game settings, first-run setup, and the activity lifecycle. A product supplies its games, catalog and guest through the abstract members; KairoDos subclasses it directly with its DOS session. |
| `MachineActivity` | `KairoActivity` for a frame-driven machine: a `MachineBridge` session, a `LayeredGameCatalog`, save states, and game settings kept as catalog overrides. Kairo98 and Kairo88 subclass it. |
| `MachineBridge`, `MachineStatus` | What the activity needs from the native machine, and its status line. |
| `LayeredGameCatalog` | The catalog layers (shipped, updated, installed, user catalog, user overrides) with the common fields. A product's `Schema` adds its own fields, media roles, content IDs and update URLs. |
| `DiskImageLibrary` | Disk images in the ROM folder, loose or in ZIP and 7z archives, named by content ID, played from working copies. A product's `Format` lists its image extensions and kinds. |
| `FirmwareSet` | Firmware files imported from a folder the player chooses, matched by name and checked by size, with a settings row. |
| `MachineFirstRun` | The first-run ROM folder and firmware steps. |
| `FolderStateSlots`, `LaunchBoxArtworkReferences` | Save-state slots in a custom save folder, and catalog artwork references. |

Kairo98 and KairoDos predate these classes and keep their own activities.
