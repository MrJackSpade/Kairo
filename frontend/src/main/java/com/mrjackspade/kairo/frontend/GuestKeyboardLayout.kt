package com.mrjackspade.kairo.frontend

data class KeyboardKey(
    val label: String,
    val code: Int,
    val width: Float = 1f,
    val shifted: String? = null,
    val chordShift: Boolean = false,
    /** The legend while the layout's kana lock is on, and with Shift as well. */
    val kana: String? = null,
    val kanaShifted: String? = null
)

/** [pickerLabel] names the page on the button target key picker, where it may differ. */
data class GuestKeyboardPage(val label: String, val rows: List<List<KeyboardKey>>,
                             val pickerLabel: String = label)

/** The guest owns codes and legends; the frontend owns drawing and input behavior. */
data class GuestKeyboardLayout(
    val brand: String,
    val pages: List<GuestKeyboardPage>,
    val modifiers: Set<Int>,
    val shiftCodes: Set<Int>,
    val capsCode: Int?,
    val chordShiftCode: Int,
    /** A lock key that switches the legends to [KeyboardKey.kana]. */
    val kanaCode: Int? = null
) {
    init {
        require(pages.isNotEmpty() && pages.all { it.rows.isNotEmpty() })
        require(shiftCodes.all { it in modifiers })
        require(capsCode == null || capsCode in modifiers)
        require(kanaCode == null || kanaCode in modifiers)
    }

    /** Keys the guest toggles on each press. Give these to the app's [InputRouter]. */
    val lockCodes: Set<Int> get() = setOfNotNull(capsCode, kanaCode)

    fun isLock(code: Int) = code == capsCode || code == kanaCode
}
