package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PixelFontTest {
    private val font = PixelFont(File("src/main/assets/ui/pixel-font.bin").readBytes())

    private fun pixels(code: Int): List<Boolean> = font.glyph(code).let { glyph ->
        (0 until PixelFont.HEIGHT).flatMap { row -> (0 until glyph.width).map { glyph.bits(row, it) } }
    }

    @Test
    fun latinAndCyrillicAreHalfWidthAndKanaHangulAndKanjiFullWidth() {
        assertEquals(listOf(8, 8, 8, 8, 16, 16, 16, 16), "AÓЖßあカ中한".codePoints().toArray().map { font.glyph(it).width })
    }

    @Test
    fun everyLetterTheTranslationsUseHasAGlyphOfItsOwn() {
        val question = pixels('?'.code)
        for (text in listOf("CONFIGURACIÓN", "ÜBERSICHT", "НАСТРОЙКИ", "設定", "コントローラー", "설정", "PENGATURAN", "AÇÃO"))
            text.codePoints().forEach { assertTrue("U+%04X".format(it), pixels(it) != question || it == '?'.code) }
    }

    @Test
    fun aCodePointWithoutAGlyphDrawsTheQuestionMark() {
        assertEquals(pixels('?'.code), pixels(0x0627))
    }
}
