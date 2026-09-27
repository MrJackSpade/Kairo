package com.mrjackspade.kairo.frontend

data class KeyboardKey(
    val label: String,
    val code: Int,
    val width: Float = 1f,
    val shifted: String? = null,
    val chordShift: Boolean = false
)

data class GuestKeyboardPage(val label: String, val rows: List<List<KeyboardKey>>)

/** The guest owns codes and legends; the frontend owns drawing and input behavior. */
data class GuestKeyboardLayout(
    val brand: String,
    val pages: List<GuestKeyboardPage>,
    val modifiers: Set<Int>,
    val shiftCodes: Set<Int>,
    val capsCode: Int?,
    val chordShiftCode: Int
) {
    init {
        require(pages.isNotEmpty() && pages.all { it.rows.isNotEmpty() })
        require(shiftCodes.all { it in modifiers })
        require(capsCode == null || capsCode in modifiers)
    }
}
