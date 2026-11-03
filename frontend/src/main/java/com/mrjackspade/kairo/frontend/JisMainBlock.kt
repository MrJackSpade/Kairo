package com.mrjackspade.kairo.frontend

import android.view.KeyEvent

/**
 * The main block of the JIS keyboard: Esc, the digit row, the letter rows, Return and Space, in each
 * machine's own codes ([Codes]). The constants are the numbering the FM Towns and the X68000 share
 * ([TOWNS]): Esc 0x01, the digit row from 0x02, the letter rows, Return 0x1D, and Space 0x35. Each
 * machine adds its own modifier, function, cursor and keypad keys.
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

    /** Digits 1 to 0, and the letter rows. */
    private const val DIGITS = "1234567890"
    private val LETTER_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    /**
     * A machine's code for each main-block key: the digit row from [digits], each letter row from
     * its first key ([letterRows], the Q, A and Z rows), and every other key on its own.
     */
    class Codes(
        val esc: Int, val digits: Int, val minus: Int, val hat: Int, val yen: Int, val backspace: Int, val tab: Int,
        val letterRows: List<Int>, val at: Int, val leftBracket: Int, val returnKey: Int, val semicolon: Int,
        val colon: Int, val rightBracket: Int, val comma: Int, val dot: Int, val slash: Int, val underscore: Int,
        val space: Int,
        /** The key left of right Shift's legends: _ alone on the FM Towns and the X68000, \ and _ on the PC. */
        val underscoreLabel: String = "_", val underscoreShifted: String? = null
    ) {
        init { require(letterRows.size == LETTER_ROWS.size) }

        fun digit(char: Char) = digits + DIGITS.indexOf(char).also { require(it >= 0) { "$char is no digit" } }

        fun letter(char: Char): Int {
            val row = LETTER_ROWS.indexOfFirst { char in it }
            require(row >= 0) { "$char is no letter" }
            return letterRows[row] + LETTER_ROWS[row].indexOf(char)
        }
    }

    /** The FM Towns's and the X68000's numbering: the constants above. */
    val TOWNS = Codes(ESC, 0x02, MINUS, HAT, YEN, BACKSPACE, TAB, listOf(0x11, 0x1E, 0x2A), AT, LEFT_BRACKET, RETURN,
        SEMICOLON, COLON, RIGHT_BRACKET, COMMA, DOT, SLASH, UNDERSCORE, SPACE)

    fun digit(char: Char) = TOWNS.digit(char)

    fun letter(char: Char) = TOWNS.letter(char)

    private fun key(char: Char, code: Int, shifted: String? = null) = JisKana.key(char, code, shifted = shifted)

    /** The alphabet page: the JIS rows with the machine's [ctrl] and [shift], over its [bottom] row. */
    fun alphabetRows(ctrl: Int, shift: Int, bottom: List<KeyboardKey>): List<List<KeyboardKey>> =
        alphabetRows(TOWNS, listOf(KeyboardKey("Esc", ESC, 1.3f)), KeyboardKey("Ctrl", ctrl, 1.4f), shift, bottom)

    /**
     * The alphabet page in [codes]: [lead] before the 1 key, [homeLead] before the A key, the
     * machine's [shift], over its [bottom] row.
     */
    fun alphabetRows(codes: Codes, lead: List<KeyboardKey>, homeLead: KeyboardKey, shift: Int,
                     bottom: List<KeyboardKey>): List<List<KeyboardKey>> = listOf(
        lead + listOf("!", "\"", "#", "$", "%", "&", "'", "(", ")", null).mapIndexed { index, shifted ->
                key(DIGITS[index], codes.digit(DIGITS[index]), shifted)
            } +
            listOf(key('-', codes.minus, "="), key('^', codes.hat, "~"), key('¥', codes.yen, "|"),
                KeyboardKey("BS", codes.backspace, 1.5f)),
        listOf(KeyboardKey("Tab", codes.tab, 1.3f)) + "qwertyuiop".map { key(it, codes.letter(it)) } +
            listOf(key('@', codes.at, "`"), key('[', codes.leftBracket, "{"), KeyboardKey("Return", codes.returnKey, 1.5f)),
        listOf(homeLead) + "asdfghjkl".map { key(it, codes.letter(it)) } +
            listOf(key(';', codes.semicolon, "+"), key(':', codes.colon, "*"), key(']', codes.rightBracket, "}")),
        listOf(KeyboardKey("Shift", shift, 1.5f)) + "zxcvbnm".map { key(it, codes.letter(it)) } +
            listOf(key(',', codes.comma, "<"), key('.', codes.dot, ">"), key('/', codes.slash, "?"),
                // Its kana is ロ whatever its legends.
                KeyboardKey(codes.underscoreLabel, codes.underscore, shifted = codes.underscoreShifted,
                    kana = JisKana.of('_')?.first),
                KeyboardKey("Shift", shift, 1.5f)),
        bottom
    )

    /** The symbol page: each symbol on its own key, Shift chorded where the symbol needs it, in [codes]. */
    fun symbolRows(codes: Codes = TOWNS): List<List<KeyboardKey>> = SymbolPage.rows(
        digits = DIGITS.map { KeyboardKey("$it", codes.digit(it)) },
        delete = KeyboardKey("BS", codes.backspace, 1.5f),
        shiftedDigits = listOf("!", "\"", "#", "$", "%", "&", "'", "(", ")")
            .mapIndexed { index, label -> KeyboardKey(label, codes.digit(DIGITS[index]), chordShift = true) },
        symbols = listOf("-" to codes.minus, "^" to codes.hat, "¥" to codes.yen, "@" to codes.at, "[" to codes.leftBracket,
            "]" to codes.rightBracket, ";" to codes.semicolon, ":" to codes.colon, "," to codes.comma, "." to codes.dot,
            "/" to codes.slash)
            .map { (label, code) -> KeyboardKey(label, code) },
        shiftedSymbols = listOf("=" to codes.minus, "~" to codes.hat, "|" to codes.yen, "`" to codes.at,
            "{" to codes.leftBracket, "}" to codes.rightBracket, "+" to codes.semicolon, "*" to codes.colon,
            "<" to codes.comma, ">" to codes.dot, "?" to codes.slash)
            .map { (label, code) -> KeyboardKey(label, code, chordShift = true) } +
            listOfNotNull(KeyboardKey(codes.underscoreLabel, codes.underscore),
                codes.underscoreShifted?.let { KeyboardKey(it, codes.underscore, chordShift = true) }),
        space = KeyboardKey("Space", codes.space, 5f),
        enter = KeyboardKey("Return", codes.returnKey, 1.5f),
        escape = KeyboardKey("Esc", codes.esc, 1.3f),
        tab = KeyboardKey("Tab", codes.tab))

    /** The name of a main-block key in [codes], or null for another code. */
    fun label(code: Int, codes: Codes = TOWNS): String? = when (code) {
        codes.esc -> "Esc"
        in codes.digits until codes.digits + DIGITS.length -> "${DIGITS[code - codes.digits]}"
        codes.minus -> "-"
        codes.hat -> "^"
        codes.yen -> "¥"
        codes.backspace -> "BS"
        codes.tab -> "Tab"
        codes.at -> "@"
        codes.leftBracket -> "["
        codes.returnKey -> "Return"
        codes.semicolon -> ";"
        codes.colon -> ":"
        codes.rightBracket -> "]"
        codes.comma -> ","
        codes.dot -> "."
        codes.slash -> "/"
        codes.underscore -> codes.underscoreLabel
        codes.space -> "Space"
        else -> LETTER_ROWS.indices.firstNotNullOfOrNull { row ->
            LETTER_ROWS[row].getOrNull(code - codes.letterRows[row])?.takeIf { code >= codes.letterRows[row] }
                ?.uppercaseChar()?.toString()
        }
    }

    /** The main-block key an Android key types in [codes], US punctuation by position on the JIS rows; null for others. */
    fun fromAndroid(key: Int, codes: Codes = TOWNS): Int? {
        if (key in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) return codes.letter('a' + (key - KeyEvent.KEYCODE_A))
        if (key == KeyEvent.KEYCODE_0) return codes.digit('0')
        if (key in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) return codes.digit('1' + (key - KeyEvent.KEYCODE_1))
        return when (key) {
            KeyEvent.KEYCODE_ESCAPE -> codes.esc
            KeyEvent.KEYCODE_DEL -> codes.backspace
            KeyEvent.KEYCODE_TAB -> codes.tab
            KeyEvent.KEYCODE_ENTER -> codes.returnKey
            KeyEvent.KEYCODE_SPACE -> codes.space
            KeyEvent.KEYCODE_MINUS -> codes.minus
            KeyEvent.KEYCODE_EQUALS -> codes.hat
            KeyEvent.KEYCODE_BACKSLASH, KeyEvent.KEYCODE_YEN -> codes.yen
            KeyEvent.KEYCODE_LEFT_BRACKET -> codes.at
            KeyEvent.KEYCODE_RIGHT_BRACKET -> codes.leftBracket
            KeyEvent.KEYCODE_SEMICOLON -> codes.semicolon
            KeyEvent.KEYCODE_APOSTROPHE -> codes.colon
            KeyEvent.KEYCODE_COMMA -> codes.comma
            KeyEvent.KEYCODE_PERIOD -> codes.dot
            KeyEvent.KEYCODE_SLASH -> codes.slash
            KeyEvent.KEYCODE_RO -> codes.underscore
            else -> null
        }
    }
}
