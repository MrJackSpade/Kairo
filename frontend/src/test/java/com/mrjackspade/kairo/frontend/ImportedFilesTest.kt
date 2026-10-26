package com.mrjackspade.kairo.frontend

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedFilesTest {
    private val directory: File = Files.createTempDirectory("imported-files").toFile()

    private fun files(validate: (File) -> Unit = {}) = ImportedFiles(directory, "font", ".bmp", 8, "too large", validate) { file, name ->
        ImportedFiles.Described(name ?: file.name, if (name == "first.bmp") 0 else 1)
    }

    @Test
    fun theSameContentIsKeptOnceUnderOneId() {
        val files = files()
        val first = files.add("abc".byteInputStream(), "a.bmp")
        val again = files.add("abc".byteInputStream(), "b.bmp")
        assertEquals(first.id, again.id)
        assertEquals(1, files.items().size)
        // The name it was first imported under stays.
        assertEquals("a.bmp", files.items().single().label)
        assertTrue(files.idPattern.matches(first.id))
        assertEquals(first.file, files.file(first.id))
    }

    @Test
    fun aRejectedFileIsNotKept() {
        val files = files { require(false) { "not a font" } }
        val failure = assertThrows(IllegalArgumentException::class.java) { files.add("abc".byteInputStream(), "a.bmp") }
        assertEquals("not a font", failure.message)
        assertTrue(files.items().isEmpty())
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun aFileOverTheLimitIsNotKept() {
        val files = files()
        val failure = assertThrows(IllegalArgumentException::class.java) { files.add("123456789".byteInputStream()) }
        assertEquals("too large", failure.message)
        assertTrue(files.items().isEmpty())
    }

    @Test
    fun filesListInTheirOrderThenByLabel() {
        val files = files()
        files.add("2".byteInputStream(), "second.bmp")
        files.add("1".byteInputStream(), "first.bmp")
        assertEquals(listOf("first.bmp", "second.bmp"), files.items().map { it.label })
    }

    @Test
    fun anIdOfAnotherKindOrNotKeptNamesNoFile() {
        val files = files()
        files.add("abc".byteInputStream())
        assertNull(files.file("tos:0123456789abcdef"))
        assertNull(files.file("font:0123456789abcdef"))
    }
}
