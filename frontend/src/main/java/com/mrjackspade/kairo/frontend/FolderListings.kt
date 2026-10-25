package com.mrjackspade.kairo.frontend

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * How much of the games folder a scan reads again. Listing a folder through its document provider
 * is the slow part of a scan (a flat folder of 7,669 files takes over 20 seconds on an SD card),
 * while a folder's own last-modified time is one cheap row and changes whenever an entry in it is
 * added, removed or renamed.
 */
enum class ScanDepth(val relist: Boolean, val rehash: Boolean) {
    /** A launch: a folder whose time is unchanged keeps its remembered listing; known files keep their identity. */
    CHANGED_FOLDERS(relist = false, rehash = false),

    /** Refresh: every folder is listed again; only files the library has not seen are hashed. */
    EVERY_FOLDER(relist = true, rehash = false),

    /** Recheck all files: every folder is listed and every file hashed again. */
    EVERY_FILE(relist = true, rehash = true)
}

/**
 * The games folder's listings as the last scan read them (DocumentTreeWalker), for one tree: each
 * folder's last-modified time and its children. Like the library cache it only saves time, so a
 * scan that cannot read it lists every folder.
 */
class FolderListings(private val file: File) {
    class Child(val id: String, val name: String, val folder: Boolean, val size: Long, val modified: Long)
    class Listing(val modified: Long, val children: List<Child>)

    /** The listings remembered for [tree]; empty when there are none, or they were for another tree. */
    fun read(tree: String): Map<String, Listing> {
        if (!file.isFile) return emptyMap()
        val root = JSONObject(file.readText())
        require(root.getInt("version") == VERSION) { "Unknown folder listing version" }
        if (root.getString("tree") != tree) return emptyMap()
        val folders = root.getJSONObject("folders")
        return folders.keys().asSequence().associateWith { id ->
            val folder = folders.getJSONObject(id)
            val children = folder.getJSONArray("children")
            Listing(folder.getLong("modified"), (0 until children.length()).map { index ->
                val child = children.getJSONArray(index)
                Child(child.getString(0), child.getString(1), child.getInt(2) == 1, child.getLong(3), child.getLong(4))
            })
        }
    }

    /** Replaces what is remembered with [listings], the folders a whole scan of [tree] read. */
    fun write(tree: String, listings: Map<String, Listing>) {
        val folders = JSONObject()
        for ((id, listing) in listings) {
            folders.put(id, JSONObject().put("modified", listing.modified).put("children", JSONArray().apply {
                for (child in listing.children)
                    put(JSONArray().put(child.id).put(child.name).put(if (child.folder) 1 else 0).put(child.size).put(child.modified))
            }))
        }
        val temporary = File(file.parentFile, file.name + ".part")
        temporary.writeText(JSONObject().put("version", VERSION).put("tree", tree).put("folders", folders).toString())
        java.nio.file.Files.move(temporary.toPath(), file.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        const val VERSION = 1
    }
}
