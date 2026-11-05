package com.mrjackspade.kairo.frontend

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Per-game machine settings kept apart from the catalog's overrides (Kairo#142), such as in a file in
 * the game's own folder (KairoX86's 86box.cfg). The game settings' machine rows ([MachineActivity.machineGroup])
 * read and write a [MachineOption] of a game through it: [read] gives the game's own value, or null when
 * it has none and takes the option's default; [write] keeps a value, and null clears it. A store that
 * cannot read or write throws with why, at the point of use, never a default in its place.
 *
 * A store may make a game's settings only when the game first needs them, which can take long
 * (KairoX86#227: an eXoWin9x game's machine folder, made from its collection): until [ready], the game
 * has no machine settings to show and [read] is not called; a launch first runs [prepare] off the UI
 * thread, with the launch's progress.
 */
interface GameMachineStore<E> {
    fun read(entry: E, option: MachineOption): String?
    fun write(entry: E, option: MachineOption, value: String?)

    /** Whether [entry]'s settings exist, so [read] and [write] are quick; cheap to ask on the UI thread. */
    fun ready(entry: E): Boolean = true

    /** Makes [entry]'s settings, off the UI thread, telling [progress] what it does; then [ready]. */
    fun prepare(entry: E, cancelled: AtomicBoolean, progress: (String) -> Unit) {}
}
