package com.mrjackspade.kairo.frontend

import java.io.File

/**
 * Save-state slots in the user's save folder: `<folder>/<game key's storage name>/slot<N>/`. The core writes
 * a save to [staging]; committing copies it into the folder and replaces the old slot.
 */
class FolderStateSlots(private val folder: FolderSaveLocation, key: GameKey,
                       private val stateName: String, staging: File) : StateSlots {
    private val game = key.storageName
    private val local = File(staging, game)

    override fun summaries(): List<StateSlots.Summary> = (1..StateSlotStore.SLOT_COUNT).map { index ->
        val unit = unit(index)
        if (!folder.has(unit, stateName)) StateSlots.Summary(index, null, null)
        else StateSlots.Summary(index, folder.savedAt(unit), folder.read(unit, THUMBNAIL_FILE))
    }

    override fun beginSave(index: Int): File {
        val scratch = scratch(index)
        check(!scratch.exists() || scratch.deleteRecursively()) { "Could not clear incomplete save" }
        check(scratch.mkdirs()) { "Could not create save-state staging" }
        return scratch
    }

    override fun thumbnailFileIn(scratch: File): File = File(scratch, THUMBNAIL_FILE)

    override fun commitSave(index: Int, scratch: File) {
        requireScratch(index, scratch)
        val state = File(scratch, stateName)
        check(state.isFile && state.length() > 0) { "The emulator did not write a complete state" }
        folder.import(unit(index), scratch)
    }

    override fun abandonSave(index: Int, scratch: File) {
        requireScratch(index, scratch)
        check(!scratch.exists() || scratch.deleteRecursively()) { "Could not clear the staged save" }
    }

    override fun <T> withSlot(index: Int, load: (File) -> T): T {
        val unit = unit(index)
        check(folder.has(unit, stateName)) { "This slot is empty" }
        val copy = File(local, "load$index")
        check(!copy.exists() || copy.deleteRecursively()) { "Could not clear an earlier load" }
        try {
            folder.export(unit, copy)
            return load(copy)
        } finally {
            copy.deleteRecursively()
        }
    }

    private fun unit(index: Int): SaveUnit {
        require(index in 1..StateSlotStore.SLOT_COUNT) { "Invalid save slot" }
        return SaveUnit(game, "slot$index")
    }

    private fun requireScratch(index: Int, scratch: File) {
        unit(index)
        require(scratch.canonicalFile == scratch(index).canonicalFile) { "Invalid save transaction" }
    }

    private fun scratch(index: Int) = File(local, "slot$index.part")

    companion object {
        private const val THUMBNAIL_FILE = "thumbnail.png"
        val UNITS = (1..StateSlotStore.SLOT_COUNT).map { "slot$it" }.toSet()
    }
}
