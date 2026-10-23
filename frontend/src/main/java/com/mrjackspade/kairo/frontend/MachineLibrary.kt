package com.mrjackspade.kairo.frontend

import android.net.Uri
import java.util.concurrent.atomic.AtomicBoolean

/** A product's game files: what [MachineActivity] needs to list, scan, and manage them. */
interface MachineLibrary<E : LibraryItem> {
    /** Images hashed by the last scan, for the library status. */
    val hashCount: Int
    fun cached(tree: Uri): List<E>
    fun scan(tree: Uri, depth: ScanDepth, cancelled: AtomicBoolean, progress: (String) -> Unit): List<E>
    /** Saves a library edited without a scan, such as after deleting a source. */
    fun store(tree: Uri, entries: List<E>)
    /** Reads one file a frontend app sent, which need not be in the ROM folder. */
    fun inspectExternal(file: ExternalGameFile, cancelled: AtomicBoolean): List<E>
    /** Deletes [entry]'s source document, with every entry read from it. */
    fun deleteSource(entry: E, entries: List<E>)
    /** Whether the game has a working copy holding what it wrote to its disks. */
    fun hasWorkingImage(entry: E): Boolean
    fun resetWorkingImage(entry: E)
    /** The file the machine opens for [entry]: its working copy, extracted from an archive when needed. */
    fun prepare(entry: E, cancelled: AtomicBoolean, progress: (String) -> Unit): java.io.File
    /** The entry whose working copy is [path], as a machine reports its media after a state loads. */
    fun entryForWorkingImage(path: String, entries: List<E>): E?
}
