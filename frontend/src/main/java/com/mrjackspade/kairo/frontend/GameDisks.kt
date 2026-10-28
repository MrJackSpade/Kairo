package com.mrjackspade.kairo.frontend

import android.content.Context
import java.util.Locale

/**
 * A drive's disk choice while a game runs (Kairo#47): Empty, the running game's own disks, and
 * Other, a page of every disk in the library the drive takes. A player rarely needs a disk from
 * outside the game, and a library can hold thousands; Other is a long page, so it is a recycled
 * list (RecycledRows).
 */
object GameDisks {
    const val EMPTY = "empty"

    /**
     * The disks of [current]'s game among [entries]: itself, the other images of its archive, the
     * disks of its catalog set ([setDisks] by catalog content ID, [matchId] giving each entry's),
     * disks beside it in its folder under the same title, and what the drives hold now ([mounted]).
     */
    fun <E : LibraryItem> own(current: E, entries: List<E>, mounted: Collection<E?>, setDisks: Set<String>,
                              matchId: (E) -> String?, takes: (E) -> Boolean): List<E> {
        val folder = current.path.substringBeforeLast('/', "")
        val title = titleOf(current)
        return entries.filter { entry ->
            takes(entry) && (entry.id == current.id ||
                (entry.zipEntry != null && entry.uri == current.uri) ||
                entry in mounted ||
                (entry.path.substringBeforeLast('/', "") == folder && titleOf(entry) == title) ||
                (setDisks.isNotEmpty() && matchId(entry) in setDisks))
        }
    }

    /**
     * The title a disk's file names, before its first tag: "Betrayal (1991)(Rainbird)(Disk 2 of 2).zip"
     * is "betrayal", so the disks of one game in a folder of many games find each other.
     */
    fun titleOf(entry: LibraryItem): String {
        val name = entry.path.substringAfterLast('/').substringBeforeLast('.')
        val tag = name.indexOfAny(charArrayOf('(', '['))
        return (if (tag > 0) name.substring(0, tag) else name).trim().lowercase(Locale.ROOT)
    }

    /**
     * Options for [entries]: a loose disk by its file name, one in an archive by the archive's, and
     * one of several images in an archive by both.
     */
    fun <E : LibraryItem> options(entries: List<E>): List<ChoiceOption> {
        val images = entries.groupingBy { it.uri }.eachCount()
        return entries.map { entry ->
            val archive = entry.zipEntry?.let { entry.path.substringAfterLast('/') }
            ChoiceOption(entry.id, when {
                archive == null -> UiText.fixed(entry.displayName)
                images.getValue(entry.uri) > 1 -> UiText.res(R.string.kairo_disk_in_archive, archive, entry.displayName)
                else -> UiText.fixed(archive)
            })
        }
    }

    /**
     * The choice: [own] and [all] are read each time the pages show, [selected] is the inserted
     * disk's id (or [EMPTY]), and [insert] takes the picked disk, null to eject.
     */
    fun <E : LibraryItem> choice(context: Context, title: String, scope: () -> String?, own: () -> List<E>, all: () -> List<E>,
                                 selected: () -> String?, insert: (E?) -> Unit,
                                 actions: () -> List<SettingsRow> = { emptyList() }): ChoiceSpec {
        val other = ChoiceFolder("other", context.getString(R.string.kairo_disks_other)) {
            listOf(ChoiceGroup(null, options(all())))
        }
        return ChoiceSpec(title, scope, {
            val found = own()
            listOfNotNull(ChoiceGroup(null, listOf(ChoiceOption(EMPTY, UiText.res(R.string.kairo_empty)))),
                found.takeIf { it.isNotEmpty() }?.let { ChoiceGroup(context.getString(R.string.kairo_disks_this_game), options(it)) },
                ChoiceGroup(null, emptyList(), folders = listOf(other)))
        }, selected, { id ->
            if (id == EMPTY) insert(null)
            else (own().firstOrNull { it.id == id } ?: all().firstOrNull { it.id == id })?.let(insert)
        }, actions = actions, cycles = false)
    }
}
