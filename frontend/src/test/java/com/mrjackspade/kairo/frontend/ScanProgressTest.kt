package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanProgressTest {
    private val messages = ArrayList<String>()
    private val progress = ScanProgress(toHash = 2, toConfirm = 3) { messages += it }

    @Test
    fun newFilesCountAgainstTheFilesToHashAndKnownOnesAgainstTheKnown() {
        progress.start("A/one.zip", hash = false)
        progress.start("B/new.st", hash = true)
        progress.start("A/two.zip", hash = false)
        progress.start("C/other.st", hash = true)
        assertEquals(listOf("Confirming 1 of 3: A/one.zip", "Hashing 1 of 2: B/new.st",
            "Confirming 2 of 3: A/two.zip", "Hashing 2 of 2: C/other.st"), messages)
    }

    @Test
    fun anArchiveMemberKeepsTheFilesCount() {
        progress.start("Games/set.zip", hash = true)
        progress.hashing("Games/set.zip/Disk 2.st")
        assertEquals("Hashing 1 of 2: Games/set.zip/Disk 2.st", messages.last())
    }

    @Test
    fun aKnownFileHashedAfterAllSaysSo() {
        progress.start("Games/a.st", hash = false)
        progress.hashing("Games/a.st")
        assertEquals("Hashing Games/a.st", messages.last())
    }

    @Test
    fun aLargeFileReportsEachPercentOnce() {
        progress.start("Towns/disc.bin", hash = true)
        val total = 400L * 1024 * 1024
        progress.read(64 * 1024, total)
        progress.read(128 * 1024, total)
        progress.read(total / 2, total)
        progress.read(total, total)
        assertEquals(listOf("Hashing 1 of 2: Towns/disc.bin", "Hashing 1 of 2: Towns/disc.bin 0%",
            "Hashing 1 of 2: Towns/disc.bin 50%", "Hashing 1 of 2: Towns/disc.bin 100%"), messages)
    }

    @Test
    fun aSmallFileReportsNoPercent() {
        progress.start("C64/game.d64", hash = true)
        progress.read(174_848, 174_848)
        assertEquals(listOf("Hashing 1 of 2: C64/game.d64"), messages)
    }
}
