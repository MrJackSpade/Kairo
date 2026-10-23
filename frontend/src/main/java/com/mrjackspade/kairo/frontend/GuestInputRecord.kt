package com.mrjackspade.kairo.frontend

/**
 * The input the guest machine received, as the UI tests read it (shared/uitest): every key,
 * joystick control, mouse motion and button sent to it, newest last, and what is held now. It keeps
 * the last [CAPACITY] events; tests read what arrived after a sequence number they noted.
 */
class GuestInputRecord {
    /** One input: [kind] is "key", "joystick", "move" or "button"; [code] the key, control or button. */
    class Event(val sequence: Long, val kind: String, val code: Int, val down: Boolean, val dx: Int = 0, val dy: Int = 0) {
        override fun toString() = when (kind) {
            "move" -> "#$sequence move $dx,$dy"
            else -> "#$sequence $kind $code ${if (down) "down" else "up"}"
        }
    }

    private val events = ArrayDeque<Event>()
    private val heldKeys = HashSet<Int>()
    private val heldControls = HashSet<Int>()
    private var next = 1L

    @Synchronized fun key(code: Int, down: Boolean) {
        if (down) heldKeys += code else heldKeys -= code
        add(Event(next++, "key", code, down))
    }

    @Synchronized fun joystick(control: Int, down: Boolean) {
        if (down) heldControls += control else heldControls -= control
        add(Event(next++, "joystick", control, down))
    }

    @Synchronized fun move(dx: Int, dy: Int) = add(Event(next++, "move", 0, false, dx, dy))

    @Synchronized fun button(button: Int, down: Boolean) = add(Event(next++, "button", button, down))

    /** The sequence number of the newest event; 0 before any. */
    @get:Synchronized val latest: Long get() = next - 1

    /** Every kept event after [sequence], oldest first. */
    @Synchronized fun since(sequence: Long): List<Event> = events.filter { it.sequence > sequence }

    @get:Synchronized val keysHeld: Set<Int> get() = heldKeys.toSet()
    @get:Synchronized val controlsHeld: Set<Int> get() = heldControls.toSet()

    private fun add(event: Event) {
        events.addLast(event)
        while (events.size > CAPACITY) events.removeFirst()
    }

    private companion object { const val CAPACITY = 1024 }
}
