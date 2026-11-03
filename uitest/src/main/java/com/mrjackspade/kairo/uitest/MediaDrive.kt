package com.mrjackspade.kairo.uitest

/**
 * One drive of the running game's media page ([page], the game menu's row that opens it), named
 * [slot] as the page and the probe name it ("Drive 2", "Floppy A", "CD-ROM"): what it holds, and
 * its choice of disks. MediaTests and StateTests change disks through it as a player does.
 */
class MediaDrive(private val driver: Driver, private val screens: Screens, val slot: String,
                 private val page: String = "f-media") {
    /** The drive's choice page, opened from the game menu's media page. */
    fun openChoice(): Element {
        screens.openGameMenu().find(By.item(page)).tap()
        driver.find(By.anyItem().withText(slot)).tap()
        return driver.page(slot).waitUntilShown()
    }

    /**
     * Picks the [disk] option from the drive's [choices]: among the game's own disks, or else on
     * Other's page, scrolled to; returns once the choice has closed.
     */
    fun pick(choices: Element, disk: By) {
        val own = choices.find(disk)
        if (own.exists()) own.tap()
        else {
            choices.find(By.item("f:other")).tap()
            val other = driver.page("Other").waitUntilShown()
            other.find(disk).revealIn(rows(other)).tap()
        }
        driver.waitUntil("the disk choice to close") { !driver.page(slot).exists() && !driver.page("Other").exists() }
    }

    /** Empties the drive from its [choices]. */
    fun eject(choices: Element) {
        choices.find(By.item("o:empty")).tap()
        choices.waitUntilGone()
    }

    /** The rows of a choice [page]: a recycled list on a long page, a scroll view on a short one. */
    fun rows(page: Element): Element {
        val list = page.find(By.id("page.list"))
        val scroll = page.find(By.id("page.scroll"))
        driver.waitUntil("the rows of ${page.description}") { list.exists() || scroll.exists() }
        return if (list.exists()) list else scroll
    }

    /** What the drive holds, as the app shows it (the probe's media.<slot>); empty when nothing. */
    fun holds(): String = driver.probeNow().details["media.$slot"].orEmpty()

    fun awaitHolds(what: String, holds: (String) -> Boolean) =
        driver.waitUntil("$slot to hold $what") { holds(holds()) }

    /** What every drive holds now, empty drives left out. */
    fun allMedia(): List<String> =
        driver.probeNow().details.filterKeys { it.startsWith("media.") }.values.filter { it.isNotEmpty() }

    companion object {
        /**
         * Disk options of a choice page, by their ids ("o:<entry id>", the path below the games folder
         * and inside an archive): a row's label names an archive of one disk by the archive alone.
         */
        fun diskWith(part: String) = By.idMatching(Regex("o:.*${Regex.escape(part)}.*"))

        /** The option of the disk image the machine reports as [file]. */
        fun disk(file: String) = By.idMatching(Regex("o:(.*/)?${Regex.escape(file)}"))
    }
}
