package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Creating page's progress (Kairo#184): a percent of all the bytes, and the file being written. */
class CreateProgressTest {
    private fun run(sizes: List<Long>, steps: CreateGameFlow.Tally.() -> Unit): List<String> {
        val seen = ArrayList<String>()
        CreateGameFlow.Tally(sizes) { seen += "${it.percent}:${it.file}" }.steps()
        return seen
    }

    @Test
    fun aPercentOfEveryFilesBytes() {
        // A small config given as 0, then a 300-byte image and a 100-byte track
        val seen = run(listOf(0, 300, 100)) {
            start("86box.cfg", 0); written(40)
            start("game.bin", 300); written(150); written(300)
            start("game.cue", 100); written(100)
        }
        assertEquals(listOf("0:86box.cfg", "0:game.bin", "37:game.bin", "75:game.bin", "75:game.cue", "100:game.cue"), seen)
    }

    @Test
    fun noPercentWithNoSizes() {
        assertEquals(listOf("null:disk.img"), run(listOf(0)) { start("disk.img", 0); written(1_000_000) })
    }

    @Test
    fun aFileCountsNoMoreThanItsSize() {
        assertEquals(listOf("0:a", "50:a", "50:b", "100:b"), run(listOf(10, 10)) { start("a", 10); written(50); start("b", 10); written(10) })
    }
}
