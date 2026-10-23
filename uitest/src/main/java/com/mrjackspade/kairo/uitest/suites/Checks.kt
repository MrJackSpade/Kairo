package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.Driver
import com.mrjackspade.kairo.uitest.Expect

/** Checks the suites make of any screen. */
internal object Checks {
    // The word, not a file name's extension such as FMT_SYS.ROM or a drive's name such as CD-ROM.
    private val rom = Regex("(?<![.\\w-])ROMs?\\b")

    /** No text on screen calls games or BIOS files "ROMs". */
    fun noRom(driver: Driver, expect: Expect, where: String) {
        driver.idle()
        val found = driver.visibleTexts().filter(rom::containsMatchIn)
        expect.that("$where never says \"ROM\"", found.isEmpty()) { found.joinToString(" | ") }
    }

    /** What a crawl checks on every page: plain words, and something to read. */
    fun page(driver: Driver, expect: Expect, page: String) {
        noRom(driver, expect, "the \"$page\" page")
        expect.that("the \"$page\" page shows something", driver.visibleTexts().isNotEmpty())
    }
}
