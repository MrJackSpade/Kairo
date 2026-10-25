package com.mrjackspade.kairo.frontend

import java.io.File

/**
 * The library entries the last visibility pass hid (set disks behind their entry, records the
 * catalog hides), one id per line. A start shows the cached library without them at once, then
 * the pass that reads the catalog replaces that list; a stale file only shows a game too many or
 * too few for those seconds.
 */
class HiddenEntries(private val file: File) {
    /** The ids, or null before any pass has run. */
    fun read(): Set<String>? = if (file.isFile) file.readLines(Charsets.UTF_8).filter(String::isNotEmpty).toSet() else null

    fun write(ids: Set<String>) {
        val temporary = File(file.parentFile, file.name + ".part")
        temporary.writeText(ids.sorted().joinToString("\n"), Charsets.UTF_8)
        java.nio.file.Files.move(temporary.toPath(), file.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
}
