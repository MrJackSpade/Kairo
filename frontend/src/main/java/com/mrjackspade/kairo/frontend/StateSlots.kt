package com.mrjackspade.kairo.frontend

import java.io.File

/**
 * Where one game's save-state slots are stored. The emulator core reads and writes local
 * files, so a save is written to a local scratch directory and then committed.
 */
interface StateSlots {
    class Summary(val index: Int, val savedAt: Long?, val thumbnail: ByteArray?)

    fun summaries(): List<Summary>
    fun beginSave(index: Int): File
    fun thumbnailFileIn(scratch: File): File
    fun commitSave(index: Int, scratch: File)
    fun abandonSave(index: Int, scratch: File)

    /** Runs [load] with a local directory holding the slot's files. */
    fun <T> withSlot(index: Int, load: (File) -> T): T
}
