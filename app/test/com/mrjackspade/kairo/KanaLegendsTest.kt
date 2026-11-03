package com.mrjackspade.kairo

import com.mrjackspade.kairo.frontend.GuestKeyboardLayout
import com.mrjackspade.kairo.frontend.KeyboardKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * A Japanese keyboard's kana legends (Kairo#81, KairoMSX#45, Kairo#126): with Kana off its JIS keys
 * show their Latin legends, with Kana on their kana as JIS lays them out (ヌフアウエオ… on the
 * number row), and each key sends what its legend says: a kana legend its key alone, a Latin one
 * with Shift chorded where the key needs it. An app with a Japanese layout runs it on each:
 *
 *     class MsxKanaLegendsTest : KanaLegendsTest() { override val layouts = listOf(MsxKeys.jisLayout) }
 */
abstract class KanaLegendsTest {
    /** The app's layouts with a kana lock, as its keyboard builds them. */
    protected abstract val layouts: List<GuestKeyboardLayout>

    private fun kanaKeys(layout: GuestKeyboardLayout) = layout.pages.flatMap { it.rows.flatten() }.filter { it.kana != null }

    private fun kanaLock(layout: GuestKeyboardLayout): Set<Int> =
        setOf(layout.kanaCode ?: throw AssertionError("${layout.brand} has no kana lock"))

    @Test
    fun eachLayoutHasAKanaLockAndKanaLegends() {
        for (layout in layouts) {
            assertNotNull("${layout.brand}'s kana lock", layout.kanaCode)
            assertEquals("${layout.brand} keys with kana legends", true, kanaKeys(layout).size >= JIS_KANA.size)
        }
    }

    @Test
    fun kanaOffShowsTheLatinLegends() {
        for (layout in layouts) for (key in kanaKeys(layout)) {
            assertEquals("${layout.brand} key ${key.code} with Kana off", key.label, layout.legend(key, false, emptySet()))
            key.shifted?.let { assertEquals("${layout.brand} key ${key.code} with Kana off and Shift", it, layout.legend(key, true, emptySet())) }
        }
    }

    @Test
    fun kanaOnShowsTheJisKana() {
        for (layout in layouts) {
            val kana = kanaLock(layout)
            val shown = kanaKeys(layout).associate { it.label.lowercase() to katakana(layout.legend(it, false, kana)) }
            val wrong = JIS_KANA.filter { (label, legend) -> shown[label] != legend }
                .map { (label, legend) -> "$label: ${shown[label]} (JIS $legend)" }
            assertEquals("${layout.brand}'s kana legends", "", wrong.joinToString())
        }
    }

    @Test
    fun eachLegendSendsWhatItShows() {
        for (layout in layouts) {
            val kana = kanaLock(layout)
            for (key in kanaKeys(layout)) {
                assertEquals("${layout.brand} key ${key.code} showing ${key.kana}", listOf(key.code), layout.keysFor(key, kana))
                assertEquals("${layout.brand} key ${key.code} showing ${key.label}",
                    if (key.chordShift) listOf(layout.chordShiftCode, key.code) else listOf(key.code), layout.keysFor(key, emptySet()))
            }
        }
    }

    private companion object {
        /** The JIS kana on each key, by its unshifted Latin legend (katakana; hiragana legends compare as katakana). */
        val JIS_KANA = mapOf(
            "1" to "ヌ", "2" to "フ", "3" to "ア", "4" to "ウ", "5" to "エ", "6" to "オ", "7" to "ヤ", "8" to "ユ",
            "9" to "ヨ", "0" to "ワ", "q" to "タ", "w" to "テ", "e" to "イ", "r" to "ス", "t" to "カ", "y" to "ン",
            "u" to "ナ", "i" to "ニ", "o" to "ラ", "p" to "セ", "a" to "チ", "s" to "ト", "d" to "シ", "f" to "ハ",
            "g" to "キ", "h" to "ク", "j" to "マ", "k" to "ノ", "l" to "リ", "z" to "ツ", "x" to "サ", "c" to "ソ",
            "v" to "ヒ", "b" to "コ", "n" to "ミ", "m" to "モ")

        fun katakana(text: String) = text.map { if (it in 'ぁ'..'ゖ') it + 0x60 else it }.joinToString("")
    }
}
