package com.mrjackspade.kairo.frontend

data class KeyboardKey(
    val label: String,
    val code: Int,
    val width: Float = 1f,
    val shifted: String? = null,
    val chordShift: Boolean = false,
    /** The legend while the layout's kana lock is on, and with Shift as well. */
    val kana: String? = null,
    val kanaShifted: String? = null,
    /** The legend while the layout's Num Lock is off, as a keypad key's Home or arrow. */
    val numLockOff: String? = null,
    /** Rows the key spans downward, as a keypad's tall + and Enter; later rows leave its columns free. */
    val rows: Int = 1
)

/**
 * [label] is the page's tab legend, such as ABC or ?123; [pickerLabel] names the page on the
 * button target key picker, where it may differ, such as Symbols.
 */
data class GuestKeyboardPage(val label: String, val rows: List<List<KeyboardKey>>,
                             val pickerLabel: UiText = UiText.fixed(label))

/** The guest owns codes and legends; the frontend owns drawing and input behavior. */
data class GuestKeyboardLayout(
    val brand: String,
    val pages: List<GuestKeyboardPage>,
    val modifiers: Set<Int>,
    val shiftCodes: Set<Int>,
    val capsCode: Int?,
    val chordShiftCode: Int,
    /** A lock key that switches the legends to [KeyboardKey.kana]. */
    val kanaCode: Int? = null,
    /** A lock key whose off state shows [KeyboardKey.numLockOff] legends. */
    val numLockCode: Int? = null,
    /** Further keys the guest toggles on each press, lit while on, such as Scroll Lock. */
    val otherLockCodes: Set<Int> = emptySet()
) {
    /** Keys the guest toggles on each press. Give these to the app's [InputRouter]. */
    val lockCodes: Set<Int> = setOfNotNull(capsCode, kanaCode, numLockCode) + otherLockCodes

    init {
        require(pages.isNotEmpty() && pages.all { it.rows.isNotEmpty() })
        require(shiftCodes.all { it in modifiers })
        require(lockCodes.all { it in modifiers })
    }

    fun isLock(code: Int) = code in lockCodes
}
