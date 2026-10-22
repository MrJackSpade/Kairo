package com.mrjackspade.kairo.frontend

/**
 * The kana legends of a JIS keyboard, the same on every Japanese machine Kairo emulates. Each
 * belongs to a key by its unshifted character: the kana, then its Shift form where the key has
 * one. The yen key may be written ¥ or \.
 */
object JisKana {
    private val legends = mapOf(
        '1' to ("ヌ" to null), '2' to ("フ" to null), '3' to ("ア" to "ァ"), '4' to ("ウ" to "ゥ"),
        '5' to ("エ" to "ェ"), '6' to ("オ" to "ォ"), '7' to ("ヤ" to "ャ"), '8' to ("ユ" to "ュ"),
        '9' to ("ヨ" to "ョ"), '0' to ("ワ" to "ヲ"), '-' to ("ホ" to null), '^' to ("ヘ" to null),
        '¥' to ("ー" to null), '\\' to ("ー" to null),
        'q' to ("タ" to null), 'w' to ("テ" to null), 'e' to ("イ" to "ィ"), 'r' to ("ス" to null),
        't' to ("カ" to null), 'y' to ("ン" to null), 'u' to ("ナ" to null), 'i' to ("ニ" to null),
        'o' to ("ラ" to null), 'p' to ("セ" to null), '@' to ("゛" to null), '[' to ("゜" to "「"),
        'a' to ("チ" to null), 's' to ("ト" to null), 'd' to ("シ" to null), 'f' to ("ハ" to null),
        'g' to ("キ" to null), 'h' to ("ク" to null), 'j' to ("マ" to null), 'k' to ("ノ" to null),
        'l' to ("リ" to null), ';' to ("レ" to null), ':' to ("ケ" to null), ']' to ("ム" to "」"),
        'z' to ("ツ" to "ッ"), 'x' to ("サ" to null), 'c' to ("ソ" to null), 'v' to ("ヒ" to null),
        'b' to ("コ" to null), 'n' to ("ミ" to null), 'm' to ("モ" to null), ',' to ("ネ" to "、"),
        '.' to ("ル" to "。"), '/' to ("メ" to "・"), '_' to ("ロ" to null))

    /** The kana legend of the key whose unshifted character is [char], and its Shift form. */
    fun of(char: Char): Pair<String, String?>? = legends[char.lowercaseChar()]

    /** A key labelled [char] with its kana legends. */
    fun key(char: Char, code: Int, width: Float = 1f, shifted: String? = null, chordShift: Boolean = false) =
        KeyboardKey("$char", code, width, shifted, chordShift, of(char)?.first, of(char)?.second)
}
