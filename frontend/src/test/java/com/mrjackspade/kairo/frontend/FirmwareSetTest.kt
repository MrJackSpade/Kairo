package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FirmwareSetTest {
    @get:Rule val folder = TemporaryFolder()

    private fun part(name: String, required: Boolean = true) = FirmwareSet.Part(name, name, setOf(name), required)

    @Test
    fun eachRequiredPartIsItsOwnRequirementByDefault() {
        val bios = part("bios.rom")
        val font = part("font.rom")
        val set = FirmwareSet(folder.root, listOf(bios, font, part("extra.rom", required = false)))
        assertEquals("bios.rom, font.rom", set.missingText)
        folder.newFile("bios.rom")
        assertEquals("font.rom", set.missingText)
        folder.newFile("font.rom")
        assertTrue(set.missingRequirements.isEmpty())
    }

    @Test
    fun aRequirementIsMetByAnyOfItsParts() {
        // A whole-EPROM dump stands in for each separate ROM it holds, as openMSX loads either.
        val eprom = part("ic18.rom")
        val bios = part("bios.rom")
        val music = part("music.rom")
        val font = part("font.rom")
        val set = FirmwareSet(folder.root, listOf(eprom, bios, music, font),
            listOf(listOf(eprom, bios), listOf(eprom, music), listOf(font)))
        assertEquals("ic18.rom or bios.rom, ic18.rom or music.rom, font.rom", set.missingText)
        folder.newFile("bios.rom")
        folder.newFile("music.rom")
        assertEquals("font.rom", set.missingText)
        folder.newFile("font.rom")
        assertTrue(set.missingRequirements.isEmpty())
    }

    @Test
    fun theWholeDumpAloneMeetsItsRequirements() {
        val eprom = part("ic18.rom")
        val bios = part("bios.rom")
        val music = part("music.rom")
        val set = FirmwareSet(folder.root, listOf(eprom, bios, music), listOf(listOf(eprom, bios), listOf(eprom, music)))
        folder.newFile("ic18.rom")
        assertTrue(set.missingRequirements.isEmpty())
    }
}
