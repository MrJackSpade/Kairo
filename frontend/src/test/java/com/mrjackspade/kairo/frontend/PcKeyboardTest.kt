package com.mrjackspade.kairo.frontend

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each PC keyboard model's on-screen layout: its own keys, legends and codes (KairoX86#65). */
class PcKeyboardTest {
    private fun layout(model: PcKeyboardModel) = PcKeyboard.layout("PC", model) { it.scanCode }
    private fun keys(model: PcKeyboardModel) = layout(model).pages.flatMap { page -> page.rows.flatten() }
    private fun codes(model: PcKeyboardModel) = keys(model).map { it.code }.toSet()
    private fun key(model: PcKeyboardModel, code: Int, page: Int = 0) =
        layout(model).pages[page].rows.flatten().first { it.code == code }

    @Test
    fun everyKeyIsAPcKey() {
        for (model in PcKeyboardModel.entries)
            for (code in codes(model)) assertTrue("$model $code", PcKey.fromScanCode(code) != null)
    }

    @Test
    fun theXtAndAtHaveOnlyTheirKeys() {
        for (model in listOf(PcKeyboardModel.XT, PcKeyboardModel.AT)) {
            // No E0 keys (right Ctrl and Alt, the cursor block, the keypad's / and Enter), no F11 or F12.
            assertTrue(model.toString(), codes(model).none { it >= 0x100 })
            assertFalse(PcKey.F11.scanCode in codes(model))
        }
        assertTrue(PcKey.SYS_REQ.scanCode in codes(PcKeyboardModel.AT))
        assertFalse(PcKey.SYS_REQ.scanCode in codes(PcKeyboardModel.XT))
    }

    @Test
    fun theIsoKeyboardHasItsKeyBesideShift() {
        assertTrue(PcKey.ISO_BACKSLASH.scanCode in codes(PcKeyboardModel.ISO))
        assertFalse(PcKey.ISO_BACKSLASH.scanCode in codes(PcKeyboardModel.ANSI))
    }

    @Test
    fun theJisKeyboardHasItsLegendsAndKana() {
        val jis = PcKeyboardModel.JIS
        assertEquals("@", key(jis, PcKey.LEFT_BRACKET.scanCode).label)
        assertEquals("^", key(jis, PcKey.EQUALS.scanCode).label)
        assertEquals(":", key(jis, PcKey.APOSTROPHE.scanCode).label)
        assertEquals("]", key(jis, PcKey.BACKSLASH.scanCode).label)
        assertEquals("¥", key(jis, PcKey.YEN.scanCode).label)
        assertEquals("\\", key(jis, PcKey.RO.scanCode).label)
        assertEquals("_", key(jis, PcKey.RO.scanCode).shifted)
        assertEquals("ロ", key(jis, PcKey.RO.scanCode).kana)
        assertEquals("チ", key(jis, PcKey.A.scanCode).kana)
        for (extra in listOf(PcKey.KANA, PcKey.HENKAN, PcKey.MUHENKAN)) assertTrue(extra.scanCode in codes(jis))
        assertEquals(PcKey.KANA.scanCode, layout(jis).kanaCode)
        assertNull(layout(PcKeyboardModel.ANSI).kanaCode)
    }

    @Test
    fun theKoreanAndBrazilianKeyboardsHaveTheirKeys() {
        assertTrue(PcKey.HANJA.scanCode in codes(PcKeyboardModel.KSC))
        assertTrue(PcKey.HANGUL.scanCode in codes(PcKeyboardModel.KSC))
        val abnt2 = PcKeyboardModel.ABNT2
        assertEquals("ç", key(abnt2, PcKey.SEMICOLON.scanCode).label)
        assertEquals("/", key(abnt2, PcKey.RO.scanCode).label)
        assertEquals("?", key(abnt2, PcKey.RO.scanCode).shifted)
        assertTrue(PcKey.PAD_SEPARATOR.scanCode in codes(abnt2))
    }

    @Test
    fun theUsLayoutIsTheDefault() {
        assertEquals(PcKeyboard.layout("PC") { it.scanCode }, layout(PcKeyboardModel.ANSI))
        assertEquals(PcKeyboardModel.JIS, PcKeyboardModel.of("106"))
    }

    @Test
    fun japaneseAndroidKeysTypeTheirPcKeys() {
        assertEquals(PcKey.YEN, PcKey.fromAndroid(KeyEvent.KEYCODE_YEN))
        assertEquals(PcKey.RO, PcKey.fromAndroid(KeyEvent.KEYCODE_RO))
        assertEquals(PcKey.GRAVE, PcKey.fromAndroid(KeyEvent.KEYCODE_ZENKAKU_HANKAKU))
        assertEquals(PcKey.HENKAN, PcKey.fromAndroid(KeyEvent.KEYCODE_HENKAN))
    }

    @Test
    fun theTownsNumberingIsUnchanged() {
        assertEquals(0x11, JisMainBlock.letter('q'))
        assertEquals(0x2A, JisMainBlock.letter('z'))
        assertEquals("Return", JisMainBlock.label(JisMainBlock.RETURN))
        assertEquals(JisMainBlock.AT, JisMainBlock.fromAndroid(KeyEvent.KEYCODE_LEFT_BRACKET))
    }
}
