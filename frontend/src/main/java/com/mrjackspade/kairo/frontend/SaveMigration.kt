package com.mrjackspade.kairo.frontend

import java.io.File

/**
 * Moves every save from one location to another. A save present in both places is a
 * conflict the user resolves before anything moves.
 */
class SaveMigration(private val from: SaveLocation, private val to: SaveLocation,
                    private val names: Set<String>, private val staging: File) {
    class Conflict(val unit: SaveUnit, val currentSavedAt: Long, val newSavedAt: Long)

    private fun saves() = from.units(names).filter { !from.isEmpty(it) }
    private fun inDestination(unit: SaveUnit) = to.exists(unit) && !to.isEmpty(unit)

    fun conflicts(): List<Conflict> = saves().filter(::inDestination).map {
        Conflict(it, from.savedAt(it), to.savedAt(it))
    }

    /**
     * [keepNew] lists the conflicting saves whose copy in the new location wins; every other
     * conflict keeps the current save. [switch] makes the new location current once every save
     * is there. The old copies are deleted afterwards.
     */
    fun run(keepNew: Set<SaveUnit>, progress: (Int, Int) -> Unit, switch: () -> Unit) {
        val saves = saves()
        val conflicts = saves.filter(::inDestination).toSet()
        check(keepNew.all { it in conflicts }) { "The saves changed while choosing; choose the save folder again" }
        val fresh = saves.filter { it !in conflicts }
        val replacing = conflicts.filter { it !in keepNew }
        val total = fresh.size + replacing.size
        val copied = ArrayList<SaveUnit>()
        try {
            for (unit in fresh) { copy(unit); copied += unit; progress(copied.size, total) }
            // Replacing discards the new location's copy, as the user chose, so it goes last.
            for ((index, unit) in replacing.withIndex()) { copy(unit); progress(fresh.size + index + 1, total) }
        } catch (error: Exception) {
            // The current location is untouched and stays in use. Remove the copies this run added.
            for (unit in copied) try { to.delete(unit) } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            throw error
        }
        switch()
        for (unit in from.units(names)) try { from.delete(unit) } catch (error: Exception) {
            throw IllegalStateException("Saves moved, but the old copy of ${unit.game}/${unit.name} " +
                "could not be removed: ${error.message}", error)
        }
    }

    private fun copy(unit: SaveUnit) {
        val temporary = File(staging, "save-migration")
        check(!temporary.exists() || temporary.deleteRecursively()) { "Could not clear an earlier move" }
        try {
            from.export(unit, temporary)
            to.import(unit, temporary)
        } finally {
            temporary.deleteRecursively()
        }
    }
}
