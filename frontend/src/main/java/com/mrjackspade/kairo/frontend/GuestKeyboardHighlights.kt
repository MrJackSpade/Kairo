package com.mrjackspade.kairo.frontend

/**
 * Which keys the on-screen keyboard lights for the keys the guest holds. A chord that holds Shift
 * only to type another key's shifted symbol (a symbol key, or a binding such as Kairo64's D-pad up
 * as Shift and cursor down) lights that key, not Shift: Shift lights only while something holds it
 * as Shift, alone or with other modifiers.
 */
object GuestKeyboardHighlights {
    /**
     * [pressed] without the shift codes that every hold holding them chords with a non-modifier
     * key. [holds] are the router's holds ([InputRouter.holds]).
     */
    fun litKeys(pressed: Set<Int>, holds: List<List<Int>>, shiftCodes: Set<Int>, modifiers: Set<Int>): Set<Int> {
        val chordShifts = shiftCodes.filter { shift ->
            shift in pressed && holds.filter { shift in it }.all { hold -> hold.any { it !in modifiers } }
        }
        return pressed - chordShifts.toSet()
    }
}
