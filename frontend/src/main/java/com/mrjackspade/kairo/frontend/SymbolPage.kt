package com.mrjackspade.kairo.frontend

/**
 * The on-screen keyboard's number and symbol page ("?123"), one layout for every machine: the
 * digits, the symbols Shift gives the digits, the machine's punctuation unshifted and then shifted,
 * and a bottom row with Space and the page's one Return (Kairo#49). A machine supplies its keys;
 * a shifted symbol is a key with chordShift, sent with Shift held.
 */
object SymbolPage {
    fun rows(
        digits: List<KeyboardKey>,
        /** Delete or Backspace, at the end of the digit row. */
        delete: KeyboardKey,
        shiftedDigits: List<KeyboardKey>,
        symbols: List<KeyboardKey>,
        shiftedSymbols: List<KeyboardKey>,
        space: KeyboardKey,
        enter: KeyboardKey,
        /** Esc, at the start of the digit row, for a machine that has one. */
        escape: KeyboardKey? = null,
        /** Tab, before Space, for a machine that has one. */
        tab: KeyboardKey? = null,
        /** Keys after Return on the bottom row, such as Ins and Del. */
        bottomEnd: List<KeyboardKey> = emptyList()
    ): List<List<KeyboardKey>> = listOf(
        listOfNotNull(escape) + digits + delete,
        shiftedDigits,
        symbols,
        shiftedSymbols,
        listOfNotNull(tab) + space + enter + bottomEnd
    )
}
