package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.Driver
import com.mrjackspade.kairo.uitest.Views
import com.mrjackspade.kairo.uitest.Expect

/** Checks the suites make of any screen. */
internal object Checks {
    // The word, not a file name's extension such as FMT_SYS.ROM or a drive's name such as CD-ROM.
    private val rom = Regex("(?<![.\\w-])ROMs?\\b")

    /**
     * No text on screen calls games or BIOS files "ROMs". A document the app quotes (id document),
     * such as a license's "installed in ROM", is its authors' words, not the app's.
     */
    fun noRom(driver: Driver, expect: Expect, where: String) {
        driver.idle()
        val quoted = driver.onMain { driver.resolve(listOf(By.id("document"))).flatMap(Views::texts).toSet() }
        val found = driver.visibleTexts().filter { it !in quoted && rom.containsMatchIn(it) }
        expect.that("$where never says \"ROM\"", found.isEmpty()) { found.joinToString(" | ") }
    }

    /** What a crawl checks on every page: plain words, and something to read. */
    fun page(driver: Driver, expect: Expect, page: String) {
        noRom(driver, expect, "the \"$page\" page")
        expect.that("the \"$page\" page shows something", driver.visibleTexts().isNotEmpty())
    }
}
