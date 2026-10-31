package com.mrjackspade.kairo.frontend

import java.util.Locale

/**
 * Disc images described by a sheet (a CUE or CCD) whose track data is a file beside it under the
 * sheet's name. [data] maps each sheet extension to the extensions its track data may have, first
 * match first, such as cue to bin then img. A sheet's content ID hashes its track data.
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
}
