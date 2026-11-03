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

    /** The legend [key] shows with Shift held or latched ([shifted]) and the guest's [locks] on. */
    fun legend(key: KeyboardKey, shifted: Boolean, locks: Set<Int>): String {
        val caps = capsCode?.let { it in locks } == true
        val kana = kanaCode?.let { it in locks } == true
        val numLockOff = numLockCode?.let { it !in locks } == true
        val letter = key.label.length == 1 && key.label[0] in 'a'..'z'
        return when {
            numLockOff && key.numLockOff != null -> key.numLockOff
            kana && key.kana != null -> if (shifted) key.kanaShifted ?: key.kana else key.kana
            letter && (shifted xor caps) -> key.label.uppercase()
            key.shifted != null && shifted -> key.shifted
            else -> key.label
        }
    }

    /**
     * The guest keys a press of [key] sends with [locks] on: Shift chorded for the legend it shows,
     * so not while the kana lock shows its kana legend, which the key types without Shift (a JIS
     * MSX's _ key is ろ in kana).
     */
    fun keysFor(key: KeyboardKey, locks: Set<Int>): List<Int> =
        if (key.chordShift && !(key.kana != null && kanaCode?.let { it in locks } == true)) listOf(chordShiftCode, key.code)
        else listOf(key.code)
}
