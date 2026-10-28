package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanProgressTest {
    private val messages = ArrayList<UiText>()
    private val progress = ScanProgress(toHash = 2, toConfirm = 3) { messages += it }

    private fun hashing(count: Int, path: String) = UiText.res(R.string.kairo_scan_hashing, count, 2, path)
    private fun confirming(count: Int, path: String) = UiText.res(R.string.kairo_scan_confirming, count, 3, path)
    private fun percent(label: UiText, percent: Int) = UiText.res(R.string.kairo_scan_percent, label, percent)

    @Test
    fun newFilesCountAgainstTheFilesToHashAndKnownOnesAgainstTheKnown() {
        progress.start("A/one.zip", hash = false)
        progress.start("B/new.st", hash = true)
        progress.start("A/two.zip", hash = false)
        progress.start("C/other.st", hash = true)
        assertEquals(listOf(confirming(1, "A/one.zip"), hashing(1, "B/new.st"),
            confirming(2, "A/two.zip"), hashing(2, "C/other.st")), messages)
    }

    @Test
    fun anArchiveMemberKeepsTheFilesCount() {
        progress.start("Games/set.zip", hash = true)
        progress.hashing("Games/set.zip/Disk 2.st")
        assertEquals(hashing(1, "Games/set.zip/Disk 2.st"), messages.last())
    }

    @Test
    fun aKnownFileHashedAfterAllSaysSo() {
        progress.start("Games/a.st", hash = false)
        progress.hashing("Games/a.st")
        assertEquals(UiText.res(R.string.kairo_scan_hashing_file, "Games/a.st"), messages.last())
    }

    @Test
    fun aLargeFileReportsEachPercentOnce() {
        progress.start("Towns/disc.bin", hash = true)
        val total = 400L * 1024 * 1024
        progress.read(64 * 1024, total)
        progress.read(128 * 1024, total)
        progress.read(total / 2, total)
        progress.read(total, total)
        val label = hashing(1, "Towns/disc.bin")
        assertEquals(listOf(label, percent(label, 0), percent(label, 50), percent(label, 100)), messages)
    }

    @Test
    fun aSmallFileReportsNoPercent() {
        progress.start("C64/game.d64", hash = true)
        progress.read(174_848, 174_848)
        assertEquals(listOf(hashing(1, "C64/game.d64")), messages)
    }
}
