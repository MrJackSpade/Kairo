package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Shipped data installs once per app version, through a copy renamed into place, so an interrupted
 * install leaves the old copy or none, and no marker (Kairo#96, Kairo#113).
 */
class ShippedAssetsTest {
    @get:Rule val folder = TemporaryFolder()

    private val file get() = File(folder.root, "data/font.bin")
    private val tree get() = File(folder.root, "data/system")
    private val marker get() = File(folder.root, "data/.font.bin.installed")
    private var writes = 0

    private fun installFile(version: String, text: String) =
        ShippedAssets.install(version, file, {}) { copy -> writes++; copy.writeText(text) }

    private fun installTree(version: String, text: String, failAfterFirst: Boolean = false) =
        ShippedAssets.install(version, tree, {}) { copy ->
            writes++
            File(copy, "machines").mkdirs()
            File(copy, "machines/a.xml").writeText(text)
            if (failAfterFirst) throw IOException("interrupted")
            File(copy, "machines/b.xml").writeText(text)
        }

    @Test
    fun theFirstCallInstallsAndASecondDoesNothing() {
        var started = 0
        ShippedAssets.install("1/100", file, { started++ }) { it.writeText("v1") }
        assertEquals("v1", file.readText())
        assertEquals("1/100", marker.readText())
        ShippedAssets.install("1/100", file, { started++ }) { error("written again") }
        assertEquals("the progress note for the first install alone", 1, started)
    }

    @Test
    fun aFolderInstallsWhole() {
        installTree("1/100", "v1")
        assertEquals(listOf("a.xml", "b.xml"), File(tree, "machines").list()!!.sorted())
        assertEquals("1/100", File(folder.root, "data/.system.installed").readText())
    }

    @Test
    fun aNewAppVersionInstallsAgain() {
        installFile("1/100", "v1")
        installFile("2/200", "v2")
        assertEquals("v2", file.readText())
        assertEquals("2/200", marker.readText())
        installTree("1/100", "v1")
        installTree("1/300", "v1 reinstalled")
        assertEquals("a reinstall of the same version", "v1 reinstalled", File(tree, "machines/a.xml").readText())
    }

    @Test
    fun aMissingTargetInstallsAgainEvenWithItsMarker() {
        installFile("1/100", "v1")
        file.delete()
        installFile("1/100", "v1")
        assertEquals("v1", file.readText())
        assertEquals(2, writes)
    }

    @Test
    fun anInterruptedFileCopyLeavesTheOldOne() {
        installFile("1/100", "old")
        val failure = runCatching {
            ShippedAssets.install("2/200", file, {}) { copy -> copy.writeText("half"); throw IOException("interrupted") }
        }.exceptionOrNull()
        assertTrue("the interruption reaches the caller", failure is IOException)
        assertEquals("old", file.readText())
        assertEquals("no marker for the new version", "1/100", marker.readText())
        installFile("2/200", "new")
        assertEquals("new", file.readText())
        assertEquals("2/200", marker.readText())
    }

    @Test
    fun anInterruptedFolderCopyLeavesTheOldOne() {
        installTree("1/100", "old")
        assertTrue(runCatching { installTree("2/200", "new", failAfterFirst = true) }.exceptionOrNull() is IOException)
        assertEquals("old", File(tree, "machines/a.xml").readText())
        assertEquals("old", File(tree, "machines/b.xml").readText())
        installTree("2/200", "new")
        assertEquals("new", File(tree, "machines/b.xml").readText())
    }

    @Test
    fun anInterruptedFirstCopyLeavesNoTargetAndNoMarker() {
        assertTrue(runCatching { installTree("1/100", "new", failAfterFirst = true) }.exceptionOrNull() is IOException)
        assertFalse(tree.exists())
        assertFalse(File(folder.root, "data/.system.installed").exists())
        installTree("1/100", "new")
        assertEquals("new", File(tree, "machines/b.xml").readText())
    }
}
