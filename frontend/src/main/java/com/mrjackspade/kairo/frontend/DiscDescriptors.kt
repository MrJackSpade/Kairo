package com.mrjackspade.kairo.frontend

import java.util.Locale

/**
 * Disc images described by a sheet (a CUE or CCD) whose track data is a file beside it under the
 * sheet's name. [data] maps each sheet extension to the extensions its track data may have, first
 * match first, such as cue to bin then img. A CUE without that file names its track data in its
 * FILE lines instead (Redump keeps one BIN per track): [filesOf]. A sheet's content ID hashes its
 * track data, its files joined in the sheet's order, as the catalog tools do (content_ids.py
 * track_files) and as a CHD of the disc reads back.
 */
class DiscDescriptors(val data: Map<String, List<String>>) {
    private fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun isDescriptor(name: String) = extension(name) in data

    /** The names [name]'s track data may have, in order; just [name] for an image without a descriptor. */
    fun dataNames(name: String): List<String> =
        data[extension(name)]?.map { name.substringBeforeLast('.') + ".$it" } ?: listOf(name)

    /** [name]'s track data among [present] (lower-case names to items), or null when none is there. */
    fun <T> dataOf(name: String, present: Map<String, T>): T? =
        dataNames(name).firstNotNullOfOrNull { present[it.lowercase(Locale.ROOT)] }

    /** Whether [name] is a CUE, which names its track data in FILE lines when it has none under its own name. */
    fun isCue(name: String) = extension(name) == "cue"

    /**
     * The track data the CUE [sheet] names in [text], in its order, among [present] (lower-case
     * paths to items), resolved beside the sheet; null when it names none or one is missing.
     */
    fun <T> filesOf(sheet: String, text: String, present: Map<String, T>): List<T>? {
        val folder = sheet.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val files = sheetFiles(text)
        if (files.isEmpty()) return null
        return files.map { present[(folder + it.replace('\\', '/')).lowercase(Locale.ROOT)] ?: return null }
    }

    companion object {
        private val CUE_FILE = Regex("""^\s*FILE\s+(?:"([^"]+)"|(\S+))""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        /** The files a cue sheet's FILE lines name, in its order. */
        fun sheetFiles(text: String): List<String> =
            CUE_FILE.findAll(text).map { match -> match.groupValues[1].ifEmpty { match.groupValues[2] } }.toList()
    }
}
