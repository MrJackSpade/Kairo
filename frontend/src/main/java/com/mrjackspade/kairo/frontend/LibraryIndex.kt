package com.mrjackspade.kairo.frontend

/**
 * The library's entries by document, by loose images' folder and by ID (content and catalog), so a
 * game's settings find what belongs with it by lookup instead of scanning the whole library
 * (Kairo#195). Each lookup gives its entries in library order.
 *
 * The library replaces its list on every change ([LibraryFlow.entries]), so [of] builds an index once
 * per list and keeps it while that list is the library's.
 */
class LibraryIndex<E : LibraryItem> private constructor(val entries: List<E>) {
    // Positions in entries, ascending. An archive's members are its document's entries (byUri).
    private val byUri = HashMap<String, MutableList<Int>>()
    private val looseByFolder = HashMap<String, MutableList<Int>>()
    private val byId = HashMap<String, MutableList<Int>>()

    init {
        entries.forEachIndexed { at, entry ->
            byUri.getOrPut(entry.uri) { ArrayList() }.add(at)
            if (entry.zipEntry == null) looseByFolder.getOrPut(folder(entry)) { ArrayList() }.add(at)
            entry.contentId?.let { byId.getOrPut(it) { ArrayList() }.add(at) }
            entry.catalogId?.takeIf { it != entry.contentId }?.let { byId.getOrPut(it) { ArrayList() }.add(at) }
        }
    }

    /** The entries read from the document [uri]. */
    fun fromDocument(uri: String): List<E> = byUri[uri].orEmpty().map(entries::get)

    /**
     * The entries [sameSource] says share [entry]'s source (its archive's members, or the loose images
     * of its folder), and those whose content or catalog ID is one of [ids]: each once, in library order.
     */
    fun sameSourceOrIds(entry: LibraryItem, ids: Collection<String>): List<E> {
        val positions = java.util.TreeSet<Int>()
        if (entry.zipEntry != null) byUri[entry.uri]?.forEach { if (entries[it].zipEntry != null) positions.add(it) }
        else looseByFolder[folder(entry)]?.let(positions::addAll)
        for (id in ids) byId[id]?.let(positions::addAll)
        return positions.map(entries::get)
    }

    companion object {
        /** The folder [sameSource] compares loose images by. */
        private fun folder(entry: LibraryItem): String = entry.path.substringBeforeLast('/', "")

        /** The index of [entries]: [last] again when it is that list's. */
        fun <E : LibraryItem> of(entries: List<E>, last: LibraryIndex<E>?): LibraryIndex<E> =
            last?.takeIf { it.entries === entries } ?: LibraryIndex(entries)
    }
}
