package com.mrjackspade.kairo.frontend

import java.io.File

/**
 * A game's four slots in app storage, `<root>/<savePath>.state<N>/` (Kairo#57), the layout
 * [AppSaveLocation] reads. The core's state, disk copies, and thumbnail commit together.
 */
class StateSlotStore(root: File, private val game: GameKey, private val stateName: String) : StateSlots {
    data class Slot(val index: Int, val savedAt: Long?, val directory: File,
                    val stateFile: File, val thumbnailFile: File)

    private val saves = AppSaveLocation(root)
    init {
        require(stateName.matches(Regex("[A-Za-z0-9._-]+")) &&
            stateName != "." && !stateName.contains(".."))
    }

    @Synchronized fun slots(): List<Slot> = (1..SLOT_COUNT).map(::slot)

    @Synchronized fun slot(index: Int): Slot {
        requireIndex(index)
        recover(index)
        val directory = directory(index)
        val state = File(directory, stateName)
        return Slot(index, state.takeIf(File::isFile)?.lastModified(), directory, state, File(directory, THUMBNAIL_FILE))
    }

    override fun summaries(): List<StateSlots.Summary> = slots().map { slot ->
        StateSlots.Summary(slot.index, slot.savedAt,
            slot.thumbnailFile.takeIf { slot.savedAt != null && it.isFile }?.readBytes())
    }

    override fun <T> withSlot(index: Int, load: (File) -> T): T {
        val slot = slot(index)
        check(slot.savedAt != null) { "This slot is empty" }
        return load(slot.directory)
    }

    @Synchronized override fun beginSave(index: Int): File {
        requireIndex(index)
        recover(index)
        val scratch = scratch(index)
        check(!scratch.exists() || scratch.deleteRecursively()) { "Could not clear incomplete save" }
        check(scratch.mkdirs()) { "Could not create save-state storage" }
        return scratch
    }

    fun stateFileIn(scratch: File): File = File(scratch, stateName)
    override fun thumbnailFileIn(scratch: File): File = File(scratch, THUMBNAIL_FILE)

    override fun commitSave(index: Int, scratch: File) = commitSave(index, scratch, null)

    @Synchronized fun commitSave(index: Int, scratch: File, thumbnail: ((File) -> Unit)?) {
        requireScratch(index, scratch)
        check(stateFileIn(scratch).isFile && stateFileIn(scratch).length() > 0) {
            "The emulator did not write a complete state"
        }
        thumbnail?.invoke(thumbnailFileIn(scratch))
        recover(index)
        val target = directory(index)
        val previous = previous(index)
        DurableFiles.sync(scratch)
        if (target.exists() && !target.renameTo(previous)) error("Could not replace the old save")
        if (!scratch.renameTo(target)) {
            val restored = !previous.exists() || previous.renameTo(target)
            error(if (restored) "Could not store the save" else
                "Could not store the save; previous save retained for recovery")
        }
        previous.deleteRecursively()
    }

    @Synchronized override fun abandonSave(index: Int, scratch: File) {
        requireScratch(index, scratch)
        scratch.deleteRecursively()
        // An abandoned first save leaves no empty folders behind.
        var parent = scratch.parentFile
        while (parent != null && parent != saves.root && parent.list()?.isEmpty() == true) {
            check(parent.delete()) { "Could not remove the empty folder ${parent.name}" }
            parent = parent.parentFile
        }
    }

    private fun recover(index: Int) {
        val previous = previous(index)
        if (!previous.exists()) return
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
    private fun directory(index: Int) = saves.directory(SaveUnit.of(game, kind(index)))
    private fun scratch(index: Int) = File(directory(index).path + ".part")
    private fun previous(index: Int) = File(directory(index).path + ".old")

    companion object {
        const val SLOT_COUNT = 4
        private const val THUMBNAIL_FILE = "thumbnail.png"
        /** A slot's save kind: `state<N>`, as emulators name a state. */
        fun kind(index: Int) = "state$index"
        /** Each slot's kind, and the kind v1.4.0 and earlier gave it. */
        val KINDS: Map<String, String> = (1..SLOT_COUNT).associate { kind(it) to "slot$it" }
    }
}
