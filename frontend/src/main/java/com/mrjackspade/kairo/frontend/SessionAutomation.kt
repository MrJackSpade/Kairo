package com.mrjackspade.kairo.frontend

import java.util.concurrent.atomic.AtomicBoolean

/**
 * A session's startup automation, apart from the activity that runs it (Kairo#110): the steps typed
 * as the game starts (the product's launch steps and the startup choices picked), which the player
 * taking over stops, and the disk swaps, watched until the session ends whatever the player does
 * (Kairo#83: opening the menu once must not stop a swap the game asks for later). A game started at
 * its prompt is the player's: nothing typed, no disk swapped (Kairo#89).
 */
class SessionAutomation<S>(private val machine: Machine<S>) {
    /** What the automation drives: the machine's typer and the session's swap watcher. */
    interface Machine<S> {
        /** Gives the typer [steps], after anything it has. */
        fun type(steps: List<S>)
        /** Takes back the steps the typer still has. */
        fun takeBackSteps()
        /** Follows the typer through [steps] until it is done or [cancelled]. */
        fun follow(steps: List<S>, cancelled: AtomicBoolean)
        /** The typer typed every step it was given, or there were none. */
        fun typed()
        /** Watches the screen for [rules] on a worker, until [stopped]. */
        fun watchSwaps(rules: List<DiskSwap>, stopped: () -> Boolean)
    }

    @Volatile private var cancelled = AtomicBoolean(false)
    /** Whether the typer still has steps it was given, which taking over takes back. */
    @Volatile private var typing = false

    /**
     * The session starts: [steps] are typed (read only when they are, as they name the choices),
     * and [swaps] watched while [current] (the session is still the one started).
     */
    fun start(atPrompt: Boolean, steps: () -> List<S>, swaps: List<DiskSwap>, current: () -> Boolean) {
        cancelled.set(true)
        val cancelled = AtomicBoolean(false)
        this.cancelled = cancelled
        val typed = if (atPrompt) emptyList() else steps()
        if (typed.isEmpty()) machine.typed()
        else {
            typing = true
            machine.type(typed)
            machine.follow(typed, cancelled)
        }
        if (!atPrompt && swaps.isNotEmpty()) machine.watchSwaps(swaps) { !current() }
    }

    /** The player took over (opening the menu): the typer stops; the swaps are still watched. */
    fun takeOver() {
        cancelled.set(true)
        if (typing) {
            typing = false
            machine.takeBackSteps()
        }
    }

    /** The typer finished its steps. */
    fun typingDone() { typing = false }
}
