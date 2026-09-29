package com.mrjackspade.kairo.frontend

import java.io.File

/** Four content-ID slots. The core's state, disk copies, and thumbnail commit together. */
class StateSlotStore(root: File, contentId: String, private val stateName: String,
                     private val legacySuffix: String? = null) {
    data class Slot(val index: Int, val savedAt: Long?, val directory: File,
                    val stateFile: File, val thumbnailFile: File)

    private val base = File(root, contentId.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    init {
        require(contentId.isNotBlank() && base.name !in setOf(".", ".."))
        require(stateName.matches(Regex("[A-Za-z0-9._-]+")) &&
            stateName != "." && !stateName.contains(".."))
        require(legacySuffix == null || legacySuffix.matches(Regex("\\.[A-Za-z0-9]+")))
    }

    @Synchronized fun slots(): List<Slot> = (1..SLOT_COUNT).map(::slot)

    @Synchronized fun slot(index: Int): Slot {
        requireIndex(index)
        recover(index)
        val directory = directory(index)
        val modern = File(directory, stateName)
        val legacy = legacySuffix?.let { File(base, "slot$index$it") }
        val state = if (!modern.isFile && legacy?.isFile == true) legacy else modern
        val thumbnail = if (state == legacy) File(base, "slot$index.png")
            else File(directory, THUMBNAIL_FILE)
        return Slot(index, state.takeIf(File::isFile)?.lastModified(), directory, state, thumbnail)
    }

    @Synchronized fun beginSave(index: Int): File {
        requireIndex(index)
        recover(index)
        val scratch = scratch(index)
        check(!scratch.exists() || scratch.deleteRecursively()) { "Could not clear incomplete save" }
        check(scratch.mkdirs()) { "Could not create save-state storage" }
        return scratch
    }

    fun stateFileIn(scratch: File): File = File(scratch, stateName)

    @Synchronized fun commitSave(index: Int, scratch: File,
                                 thumbnail: ((File) -> Unit)? = null) {
        requireScratch(index, scratch)
        check(stateFileIn(scratch).isFile && stateFileIn(scratch).length() > 0) {
            "The emulator did not write a complete state"
        }
        thumbnail?.invoke(File(scratch, THUMBNAIL_FILE))
        recover(index)
        val target = directory(index)
        val previous = previous(index)
        if (target.exists() && !target.renameTo(previous)) error("Could not replace the old save")
        if (!scratch.renameTo(target)) {
            val restored = !previous.exists() || previous.renameTo(target)
            error(if (restored) "Could not store the save" else
                "Could not store the save; previous save retained for recovery")
        }
        previous.deleteRecursively()
        // Older DOS builds used flat slot files. Retire them only after a successful commit.
        legacySuffix?.let { File(base, "slot$index$it").delete(); File(base, "slot$index.png").delete() }
    }

    @Synchronized fun abandonSave(index: Int, scratch: File) {
        requireScratch(index, scratch)
        scratch.deleteRecursively()
    }

    private fun recover(index: Int) {
        val previous = previous(index)
        if (!previous.exists()) return
        // Recover the flat backup left by an interrupted save in an older DOS build.
        if (legacySuffix != null && previous.isFile) {
            val legacy = File(base, "slot$index$legacySuffix")
            check(if (legacy.isFile) previous.delete() else previous.renameTo(legacy)) {
                "Could not recover previous save"
            }
            return
        }
        val target = directory(index)
        if (File(target, stateName).isFile) {
            check(previous.deleteRecursively()) { "Could not clear previous save" }
        } else {
            check(File(previous, stateName).isFile) { "Previous save is incomplete" }
            check(!target.exists() || target.deleteRecursively()) { "Could not recover previous save" }
            check(previous.renameTo(target)) { "Could not recover previous save" }
        }
    }

    private fun requireScratch(index: Int, candidate: File) {
        requireIndex(index)
        require(candidate.canonicalFile == scratch(index).canonicalFile) { "Invalid save transaction" }
    }
    private fun requireIndex(index: Int) = require(index in 1..SLOT_COUNT) { "Invalid save slot" }
    private fun directory(index: Int) = File(base, "slot$index")
    private fun scratch(index: Int) = File(base, "slot$index.part")
    private fun previous(index: Int) = File(base, "slot$index.old")

    companion object {
        const val SLOT_COUNT = 4
        private const val THUMBNAIL_FILE = "thumbnail.png"
    }
}
