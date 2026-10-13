package com.mrjackspade.kairo.frontend

import java.io.File
import java.io.IOException

/**
 * Imported Slang presets as folders, read once from the import root. Each import's own
 * directory is merged away. A folder that holds no presets and one folder joins it, so a
 * pack's wrapping directory, or a chain like Mega_Bezel/Presets, costs no extra page.
 */
internal class ShaderPresetTree private constructor(val folders: List<Folder>, val presets: List<File>) {
    class Folder(val path: String, val label: String, val folders: List<Folder>, val presets: List<File>)

    val isEmpty: Boolean get() = folders.isEmpty() && presets.isEmpty()

    /** The absolute path of every preset in the tree. */
    val paths: Set<String> by lazy {
        HashSet<String>().also { set ->
            fun add(folders: List<Folder>, presets: List<File>) {
                presets.forEach { set += it.absolutePath }
                folders.forEach { add(it.folders, it.presets) }
            }
            add(folders, presets)
        }
    }

    private class Node {
        val folders = HashMap<String, Node>()
        val presets = ArrayList<File>()
        val joins get() = presets.isEmpty() && folders.size == 1
    }

    companion object {
        fun read(root: File): ShaderPresetTree {
            if (!root.exists()) return ShaderPresetTree(emptyList(), emptyList())
            val packs = root.listFiles() ?: throw IOException("Could not list imported shaders")
            val top = Node()
            packs.filter { it.isDirectory }.forEach { pack ->
                pack.walkTopDown().filter { it.isFile && it.extension.equals("slangp", ignoreCase = true) }
                    .forEach { file ->
                        var node = top
                        file.parentFile!!.relativeTo(pack).path.split(File.separatorChar)
                            .filter { it.isNotEmpty() }
                            .forEach { node = node.folders.getOrPut(it, ::Node) }
                        node.presets += file
                    }
            }
            var start = top
            while (start.joins) start = start.folders.values.single()
            return ShaderPresetTree(folders("", start), presets(start))
        }

        private fun folders(parent: String, node: Node): List<Folder> = node.folders.map { (name, child) ->
            var label = name
            var current = child
            while (current.joins) {
                val (next, nextNode) = current.folders.entries.single()
                label += "/$next"
                current = nextNode
            }
            val path = if (parent.isEmpty()) label else "$parent/$label"
            Folder(path, label, folders(path, current), presets(current))
        }.sortedBy { it.label.lowercase() }

        private fun presets(node: Node) = node.presets.sortedBy { it.name.lowercase() }
    }
}
