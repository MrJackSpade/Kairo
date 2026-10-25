package com.mrjackspade.kairo.frontend

import android.view.KeyEvent

/**
 * The main block of the JIS keyboard as the FM Towns and the X68000 both number it: Esc 0x01, the
 * digit row from 0x02, the letter rows, Return 0x1D, and Space 0x35. Each machine adds its own
 * modifier, function, cursor and keypad keys.
 */
object JisMainBlock {
    const val ESC = 0x01
    const val MINUS = 0x0C
    const val HAT = 0x0D
    const val YEN = 0x0E
    const val BACKSPACE = 0x0F
    const val TAB = 0x10
    const val AT = 0x1B
    const val LEFT_BRACKET = 0x1C
    const val RETURN = 0x1D
    const val SEMICOLON = 0x27
    const val COLON = 0x28
    const val RIGHT_BRACKET = 0x29
    const val COMMA = 0x31
    const val DOT = 0x32
    const val SLASH = 0x33
    const val UNDERSCORE = 0x34
    const val SPACE = 0x35

    /** Digits 1 to 0, and the letter rows, by key position. */
    private const val DIGITS = "1234567890"
    private val letterRows = listOf("qwertyuiop" to 0x11, "asdfghjkl" to 0x1E, "zxcvbnm" to 0x2A)

    fun digit(char: Char) = 0x02 + DIGITS.indexOf(char)

    fun letter(char: Char): Int {
        val (row, base) = letterRows.first { char in it.first }
        return base + row.indexOf(char)
    }

    private fun key(char: Char, code: Int, shifted: String? = null) = JisKana.key(char, code, shifted = shifted)

    /** The alphabet page: the JIS rows with the machine's [ctrl] and [shift], over its [bottom] row. */
    fun alphabetRows(ctrl: Int, shift: Int, bottom: List<KeyboardKey>): List<List<KeyboardKey>> = listOf(
        listOf(KeyboardKey("Esc", ESC, 1.3f)) +
            listOf("!", "\"", "#", "$", "%", "&", "'", "(", ")", null).mapIndexed { index, shifted ->
                key(DIGITS[index], digit(DIGITS[index]), shifted)
            } +
            listOf(key('-', MINUS, "="), key('^', HAT, "~"), key('¥', YEN, "|"),
                KeyboardKey("BS", BACKSPACE, 1.5f)),
        listOf(KeyboardKey("Tab", TAB, 1.3f)) + "qwertyuiop".map { key(it, letter(it)) } +
            listOf(key('@', AT, "`"), key('[', LEFT_BRACKET, "{"), KeyboardKey("Return", RETURN, 1.5f)),
        listOf(KeyboardKey("Ctrl", ctrl, 1.4f)) + "asdfghjkl".map { key(it, letter(it)) } +
            listOf(key(';', SEMICOLON, "+"), key(':', COLON, "*"), key(']', RIGHT_BRACKET, "}")),
        listOf(KeyboardKey("Shift", shift, 1.5f)) + "zxcvbnm".map { key(it, letter(it)) } +
            listOf(key(',', COMMA, "<"), key('.', DOT, ">"), key('/', SLASH, "?"), key('_', UNDERSCORE),
                KeyboardKey("Shift", shift, 1.5f)),
        bottom
    )

    /** The symbol page: each symbol on its own key, Shift chorded where the symbol needs it. */
    fun symbolRows(): List<List<KeyboardKey>> = SymbolPage.rows(
        digits = DIGITS.map { KeyboardKey("$it", digit(it)) },
        delete = KeyboardKey("BS", BACKSPACE, 1.5f),
        shiftedDigits = listOf("!", "\"", "#", "$", "%", "&", "'", "(", ")")
            .mapIndexed { index, label -> KeyboardKey(label, digit(DIGITS[index]), chordShift = true) },
        symbols = listOf("-" to MINUS, "^" to HAT, "¥" to YEN, "@" to AT, "[" to LEFT_BRACKET, "]" to RIGHT_BRACKET,
            ";" to SEMICOLON, ":" to COLON, "," to COMMA, "." to DOT, "/" to SLASH)
            .map { (label, code) -> KeyboardKey(label, code) },
        shiftedSymbols = listOf("=" to MINUS, "~" to HAT, "|" to YEN, "`" to AT, "{" to LEFT_BRACKET, "}" to RIGHT_BRACKET,
            "+" to SEMICOLON, "*" to COLON, "<" to COMMA, ">" to DOT, "?" to SLASH)
            .map { (label, code) -> KeyboardKey(label, code, chordShift = true) } +
            KeyboardKey("_", UNDERSCORE),
        space = KeyboardKey("Space", SPACE, 5f),
        enter = KeyboardKey("Return", RETURN, 1.5f),
        escape = KeyboardKey("Esc", ESC, 1.3f),
        tab = KeyboardKey("Tab", TAB))

    /** The name of a main-block key, or null for another code. */
    fun label(code: Int): String? = when (code) {
        ESC -> "Esc"
        in 0x02..0x0B -> "${DIGITS[code - 0x02]}"
        MINUS -> "-"
        HAT -> "^"
        YEN -> "¥"
        BACKSPACE -> "BS"
        TAB -> "Tab"
        AT -> "@"
        LEFT_BRACKET -> "["
        RETURN -> "Return"
        SEMICOLON -> ";"
        COLON -> ":"
        RIGHT_BRACKET -> "]"
        COMMA -> ","
        DOT -> "."
        SLASH -> "/"
        UNDERSCORE -> "_"
        SPACE -> "Space"
        else -> letterRows.firstNotNullOfOrNull { (row, base) ->
            row.getOrNull(code - base)?.takeIf { code >= base }?.uppercaseChar()?.toString()
        }
    }

    /** The main-block key an Android key types, US punctuation by position on the JIS rows; null for others. */
    fun fromAndroid(key: Int): Int? {
        if (key in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) return letter('a' + (key - KeyEvent.KEYCODE_A))
        if (key == KeyEvent.KEYCODE_0) return digit('0')
        if (key in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) return digit('1' + (key - KeyEvent.KEYCODE_1))
        return when (key) {
            KeyEvent.KEYCODE_ESCAPE -> ESC
            KeyEvent.KEYCODE_DEL -> BACKSPACE
            KeyEvent.KEYCODE_TAB -> TAB
            KeyEvent.KEYCODE_ENTER -> RETURN
            KeyEvent.KEYCODE_SPACE -> SPACE
            KeyEvent.KEYCODE_MINUS -> MINUS
            KeyEvent.KEYCODE_EQUALS -> HAT
            KeyEvent.KEYCODE_BACKSLASH, KeyEvent.KEYCODE_YEN -> YEN
            KeyEvent.KEYCODE_LEFT_BRACKET -> AT
            KeyEvent.KEYCODE_RIGHT_BRACKET -> LEFT_BRACKET
            KeyEvent.KEYCODE_SEMICOLON -> SEMICOLON
            KeyEvent.KEYCODE_APOSTROPHE -> COLON
            KeyEvent.KEYCODE_COMMA -> COMMA
            KeyEvent.KEYCODE_PERIOD -> DOT
            KeyEvent.KEYCODE_SLASH -> SLASH
            KeyEvent.KEYCODE_RO -> UNDERSCORE
            else -> null
        }
    }
}
