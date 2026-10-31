package com.mrjackspade.kairo.frontend

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Files that a rename makes live (a disk's working copy, a save, a save state, an imported file)
 * reach storage before the rename. Without it a crash or hard restart can keep the rename and lose
 * the data: a working copy of Cat's Part-1 came back as a full-size file of zeros, which MAME could
 * not identify on every launch after (Kairo68#91).
 */
object DurableFiles {
    /** Writes [file]'s data to storage: every file under it, when it is a folder. */
    fun sync(file: File) {
        file.walkTopDown().filter { it.isFile }.forEach { item ->
            FileChannel.open(item.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
        }
    }
}
