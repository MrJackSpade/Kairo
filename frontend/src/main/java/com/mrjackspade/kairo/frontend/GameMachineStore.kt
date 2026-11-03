package com.mrjackspade.kairo.frontend

/**
 * Per-game machine settings kept apart from the catalog's overrides (Kairo#142), such as in a file in
 * the game's own folder (KairoX86's 86box.cfg). The game settings' machine rows ([MachineActivity.machineGroup])
 * read and write a [MachineOption] of a game through it: [read] gives the game's own value, or null when
 * it has none and takes the option's default; [write] keeps a value, and null clears it. A store that
 * cannot read or write throws with why, at the point of use, never a default in its place.
 */
interface GameMachineStore<E> {
    fun read(entry: E, option: MachineOption): String?
    fun write(entry: E, option: MachineOption, value: String?)
}
