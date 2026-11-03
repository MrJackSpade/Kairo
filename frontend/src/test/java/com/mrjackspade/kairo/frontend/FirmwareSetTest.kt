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

    private fun sha1(bytes: ByteArray) =
        java.security.MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun candidate(name: String, bytes: ByteArray) = FirmwareSet.Candidate(name, bytes.size.toLong()) { bytes.inputStream() }

    /** A part known by content alone, at a path in a ROM collection (KairoX86#67). */
    private fun hashed(path: String, bytes: ByteArray) = FirmwareSet.Part(path, UiText.fixed(path), UiText.fixed("$path for tests"),
        emptySet(), required = false, sizes = setOf(bytes.size.toLong()), sha1 = setOf(sha1(bytes)))

    @Test
    fun aCollectionFindsItsFilesByContentUnderAnyName() {
        val bios = "486 BIOS".toByteArray()
        val video = "VGA BIOS".toByteArray()
        val shared = "one ROM read at two places".toByteArray()
        val parts = listOf(hashed("machines/486ap4/bios.bin", bios), hashed("video/vga/vga.rom", video),
            hashed("machines/a/same.bin", shared), hashed("machines/b/same.bin", shared))
        val set = FirmwareSet(folder.root, parts, requirements = emptyList(), listed = false)
        val chosen = set.choose(listOf(candidate("renamed.dat", bios), candidate("SAME.BIN", shared),
            candidate("not firmware.txt", "other".toByteArray())), java.util.concurrent.atomic.AtomicBoolean(false))
        assertEquals(setOf("machines/486ap4/bios.bin" to "renamed.dat", "machines/a/same.bin" to "SAME.BIN",
            "machines/b/same.bin" to "SAME.BIN"), chosen.map { (part, file) -> part.name to file.name }.toSet())
        // Each file goes to its place in the collection.
        assertEquals(java.io.File(folder.root, "machines/486ap4/bios.bin"), set.file(parts[0]))
        assertTrue(set.missingRequirements.isEmpty())
    }
}
