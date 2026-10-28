package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FirmwareSetTest {
    @get:Rule val folder = TemporaryFolder()

    private fun part(name: String, required: Boolean = true) =
        FirmwareSet.Part(name, UiText.fixed(name), UiText.fixed("$name for tests"), setOf(name), required)

    /** Each missing requirement as the names of the parts that would meet it. */
    private fun missing(set: FirmwareSet) = set.missingRequirements.map { group -> group.map { it.name } }

    @Test
    fun eachRequiredPartIsItsOwnRequirementByDefault() {
        val bios = part("bios.rom")
        val font = part("font.rom")
        val set = FirmwareSet(folder.root, listOf(bios, font, part("extra.rom", required = false)))
        assertEquals(listOf(listOf("bios.rom"), listOf("font.rom")), missing(set))
        folder.newFile("bios.rom")
        assertEquals(listOf(listOf("font.rom")), missing(set))
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
        assertEquals(listOf(listOf("ic18.rom", "bios.rom"), listOf("ic18.rom", "music.rom"), listOf("font.rom")), missing(set))
        folder.newFile("bios.rom")
        folder.newFile("music.rom")
        assertEquals(listOf(listOf("font.rom")), missing(set))
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

    @Test
    fun aStandInIsOptionalAndCoversTheFilesItReplaces() {
        val eprom = part("ic18.rom")
        val bios = part("bios.rom")
        val music = part("music.rom")
        val font = part("font.rom")
        val extra = part("extra.rom", required = false)
        val set = FirmwareSet(folder.root, listOf(eprom, bios, music, font, extra),
            listOf(listOf(eprom, bios), listOf(eprom, music), listOf(font)))
        assertEquals(listOf(false, true, true, true, false), listOf(eprom, bios, music, font, extra).map(set::isRequired))
        assertEquals(UiText.res(R.string.kairo_missing), set.state(bios))
        folder.newFile("ic18.rom")
        assertEquals(UiText.res(R.string.kairo_installed), set.state(eprom))
        assertEquals(UiText.res(R.string.kairo_firmware_covered_by, UiText.fixed("ic18.rom")), set.state(bios))
        assertEquals(UiText.res(R.string.kairo_missing), set.state(font))
    }
}
