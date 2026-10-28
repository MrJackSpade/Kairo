package com.mrjackspade.kairo.frontend

import android.content.Context
import java.nio.ByteBuffer

/**
 * The UI's bitmap font (assets/ui/pixel-font.bin, written by tools/generate_ui_pixel_font.py):
 * Spleen 8x16 for Latin, Greek and Cyrillic, and GNU Unifont for kana, CJK and Hangul, which are
 * 16 pixels wide. Each glyph is [HEIGHT] rows of 1 byte (width 8) or 2 (width 16), the most
 * significant bit the leftmost pixel.
 */
class PixelFont(bytes: ByteArray) {
    private val codes: IntArray
    private val widths: ByteArray
    private val offsets: IntArray
    private val rows: ByteArray

    init {
        val buffer = ByteBuffer.wrap(bytes)
        check(buffer.int == MAGIC) { "Not a pixel font" }
        val count = buffer.int
        codes = IntArray(count) { buffer.int }
        widths = ByteArray(count).also(buffer::get)
        offsets = IntArray(count)
        var offset = 0
        for (index in 0 until count) {
            offsets[index] = offset
            offset += widths[index] / 8 * HEIGHT
        }
        rows = ByteArray(offset).also(buffer::get)
        check(!buffer.hasRemaining()) { "The pixel font has ${buffer.remaining()} bytes after its glyphs" }
    }

    /** One glyph: [width] pixels wide; [bits] tells whether a pixel is set. */
    inner class Glyph(private val index: Int) {
        val width: Int get() = widths[index].toInt()

        fun bits(row: Int, column: Int): Boolean {
            val byte = rows[offsets[index] + row * (width / 8) + column / 8].toInt()
            return byte and (0x80 shr (column % 8)) != 0
        }
    }

    /** The glyph of [codePoint], or the font's ? for one it has none of. */
    fun glyph(codePoint: Int): Glyph = Glyph(codes.binarySearch(codePoint).takeIf { it >= 0 }
        ?: codes.binarySearch('?'.code))

    companion object {
        const val HEIGHT = 16
        private const val MAGIC = 0x4b504631 // "KPF1"
        @Volatile private var loaded: PixelFont? = null

        fun of(context: Context): PixelFont = loaded
            ?: PixelFont(context.assets.open("ui/pixel-font.bin").use { it.readBytes() }).also { loaded = it }
    }
}
