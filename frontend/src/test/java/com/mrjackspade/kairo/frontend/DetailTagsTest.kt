package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailTagsTest {
    /** Kairo#131: DOOM II's year came once from the catalog and again from its file name. */
    @Test fun aFileTagTheCatalogShowsAppearsOnce() {
        assertEquals(listOf("DOS game", "1994", "Action; First Person Shooter", "Multiplayer"),
            detailTags("DOS game", listOf("1994", "Action; First Person Shooter", "Multiplayer"),
                "DOS/DOOM II (1994) - Installed.zip", null))
    }

    @Test fun fileTagsThatTellRevisionsApartStillShow() {
        assertEquals(listOf("Floppy", "1993", "T-En by xJeePx v1.0", "FD"),
            detailTags("Floppy", listOf("1993"), "PC98/Game (1993)[T-En by xJeePx v1.0](FD).zip", null))
    }

    @Test fun theZipEntrysTagsShowWhenThePathHasNone() {
        assertEquals(listOf("CD-ROM", "Jp-En"), detailTags("CD-ROM", emptyList(), "Towns/game.zip", "Game (Jp-En).cue"))
    }

    @Test fun aTagDiffersFromTheCatalogsOnlyByCaseShowsOnce() {
        assertEquals(listOf("Disk", "Jp-En"), detailTags("Disk", listOf("Jp-En"), "Game (jp-en).zip", null))
    }
}
