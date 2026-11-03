package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A disk the core holds, named by its working copy's path, is found as its library entry whichever
 * name of the app's files the core gives it (Kairo68#69: MAME reports /data/data/<package>).
 */
class WorkingImageMatchTest {
    private val directory = "/data/user/0/com.loxifi.kairo68/files/media"
    private val canonical = "/data/data/com.loxifi.kairo68/files/media"
    private val entries = listOf("k1-aaaa.d88", "k1-bbbb.dim")

    private fun match(path: String) =
        DiskImageLibrary.entryForWorkingImage(path, entries, directory, canonical) { "$directory/$it" }

    @Test
    fun theAppsOwnNameMatches() = assertEquals("k1-bbbb.dim", match("$directory/k1-bbbb.dim"))

    @Test
    fun theCanonicalNameMatches() = assertEquals("k1-aaaa.d88", match("$canonical/k1-aaaa.d88"))

    @Test
    fun anotherPackageDoesNotMatch() {
        assertNull(match("/data/data/com.loxifi.kairo88/files/media/k1-aaaa.d88"))
        assertNull(match("/data/user/0/com.loxifi.kairo88/files/media/k1-aaaa.d88"))
    }

    @Test
    fun aPathOutsideTheWorkingCopiesDoesNotMatch() {
        assertNull(match("/storage/emulated/0/Games/k1-aaaa.d88"))
        assertNull(match("/data/data/com.loxifi.kairo68/files/k1-aaaa.d88"))
        // A folder whose name only starts like the working copies' is another folder.
        assertNull(match("${canonical}2/k1-aaaa.d88"))
    }

    @Test
    fun anUnknownWorkingCopyDoesNotMatch() = assertNull(match("$canonical/k1-cccc.d88"))
}
