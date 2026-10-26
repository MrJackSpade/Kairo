package com.mrjackspade.kairo.frontend

/**
 * The entries a library shows as games, by id: all of [items] but archives without a game, the
 * disks a present entry point stands for (its set disks), and support disks the catalog hides.
 * The library lists these, and a game started from a file starts one of them, so the disk a
 * game starts from is the same everywhere. Null when [stop] says to give up.
 */
fun <E : LibraryItem> entryPoints(items: List<E>, catalog: LibraryCatalog, stop: () -> Boolean = { false }): Set<String>? {
    // Records live in catalog shards named by their content ID's first two digits. Walking the
    // entries in that order reads each shard once; in library order, the shard cache turned over
    // for nearly every entry and a library of thousands showed nothing for seconds after its cache
    // was read (Kairo#46).
    val byShard = items.map { entry -> entry to (catalog.matchId(entry)?.substringAfter(':')?.take(2) ?: "") }
        .sortedBy { it.second }.map { it.first }
    // A set's entry point stands for its other disks only while it is in the library.
    val setDisks = HashSet<String>()
    for (entry in byShard) {
        if (stop()) return null
        if (entry.playable) setDisks.addAll(catalog.setDisks(entry))
    }
    val shown = HashSet<String>()
    for (entry in byShard) {
        if (stop()) return null
        if (entry.holdsGame && catalog.matchId(entry)?.let { it in setDisks } != true &&
            !catalog.hiddenFromLibrary(entry)) shown += entry.id
    }
    return shown
}

/**
 * The disk to start of [playable] disks from one file: the one entry point, else the one entry
 * point the catalog has a record for, else [preferred]'s pick of the entry points; null when the
 * player has to choose.
 */
fun <E : LibraryItem> startDisk(playable: List<E>, catalog: LibraryCatalog, preferred: (List<E>) -> E?): E? {
    val shown = entryPoints(playable, catalog) ?: return null
    val candidates = playable.filter { it.id in shown }.ifEmpty { playable }
    return candidates.singleOrNull()
        ?: candidates.filter(catalog::knows).singleOrNull()
        ?: preferred(candidates)
}
