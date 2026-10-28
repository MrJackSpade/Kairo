package com.mrjackspade.kairo.frontend

import android.content.SharedPreferences
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Moves data saved before games had keys to the games it belongs to. Saves, settings and
 * overrides were named by content ID; each game with that content gets its own copy, and the
 * old data is removed once every copy is in place. Data was also named by an app's earlier
 * entry ID, which belongs to exactly one game.
 *
 * Data that no game claims stays where it is, for a later scan that finds its game. A failure
 * leaves that data in place and is reported for the games it belongs to.
 *
 * It moves the data to where v1.4.0 kept it, under each game's [GameKey.hashName], so
 * [GamePathMigration] then moves it on to the game's path like any other v1.4.0 data.
 */
class GameKeyMigration<T : LibraryItem>(
    private val preferences: SharedPreferences,
    private val contentStores: List<ContentStore>,
    private val entryStores: List<EntryStore<T>> = emptyList()
) {
    /** A game, and the ID its entry had before games had keys. */
    class Claim<T : LibraryItem>(val item: T, val entryId: String?) {
        val key: GameKey get() = item.key
        val contentId: String? get() = item.contentId
    }

    /** Data named by content ID. */
    interface ContentStore {
        /** Reads what this store holds; [has] and [isEmpty] answer from that reading. */
        fun refresh() {}
        fun has(contentId: String): Boolean
        /** Gives each game a copy of [contentId]'s data, then removes it. */
        fun move(contentId: String, games: List<GameKey>)
        /** True when nothing named by content ID is left. */
        fun isEmpty(): Boolean
    }

    /** Data named by an entry ID from before games had keys; each belongs to one game. */
    interface EntryStore<T : LibraryItem> {
        fun refresh() {}
        fun has(claim: Claim<T>): Boolean
        fun move(claim: Claim<T>)
        fun isEmpty(): Boolean
    }

    /** True until a claim finds nothing left to move. */
    val pending: Boolean get() = !preferences.getBoolean(DONE, false)

    /** Moves everything held for [games]. Returns an error message for each game whose data did not move. */
    @Synchronized fun claim(games: List<Claim<T>>): Map<GameKey, UiText> {
        if (!pending) return emptyMap()
        val errors = LinkedHashMap<GameKey, UiText>()
        for (store in entryStores) {
            store.refresh()
            for (game in games) {
                if (game.entryId == null || !store.has(game)) continue
                try { store.move(game) }
                catch (error: Exception) { errors.putIfAbsent(game.key, message(error)) }
            }
        }
        val byContent = games.filter { it.contentId != null }.groupBy { it.contentId!! }
        for (store in contentStores) {
            store.refresh()
            for ((contentId, group) in byContent) {
                if (!store.has(contentId)) continue
                val keys = group.map { it.key }.distinct()
                try { store.move(contentId, keys) }
                catch (error: Exception) { keys.forEach { errors.putIfAbsent(it, message(error)) } }
            }
        }
        if (errors.isEmpty() && entryStores.all { it.isEmpty() } && contentStores.all { it.isEmpty() })
            check(preferences.edit().putBoolean(DONE, true).commit()) { "Could not record moved game data" }
        return errors
    }

    private fun message(error: Exception) =
        UiText.res(R.string.kairo_migration_failed, error.message ?: error.javaClass.simpleName)

    /** Per-game preferences. [legacyPrefix] + content ID, then `_name` when [named]. */
    class PreferenceFamily(val legacyPrefix: String, val prefix: String, val named: Boolean)

    class PreferenceStore(private val preferences: SharedPreferences,
                          private val families: List<PreferenceFamily>) : ContentStore {
        /** Legacy preference names by content ID. */
        private var legacy: Map<String, List<Pair<PreferenceFamily, String>>> = emptyMap()

        override fun refresh() {
            legacy = preferences.all.keys.mapNotNull { name ->
                val family = families.firstOrNull { name.startsWith(it.legacyPrefix) } ?: return@mapNotNull null
                val rest = name.removePrefix(family.legacyPrefix)
                // Content IDs contain no underscore; the setting's own name follows the first one.
                val contentId = if (family.named) rest.substringBefore('_') else rest
                contentId to (family to name)
            }.groupBy({ it.first }, { it.second })
        }

        override fun has(contentId: String) = legacy.containsKey(contentId)

        override fun move(contentId: String, games: List<GameKey>) {
            val values = preferences.all
            val editor = preferences.edit()
            for ((family, name) in legacy[contentId].orEmpty()) {
                val suffix = if (family.named) "_" + name.removePrefix(family.legacyPrefix)
                    .substringAfter('_') else ""
                for (game in games) Preferences.put(editor, family.prefix + game.hashName + suffix, values[name])
                editor.remove(name)
            }
            check(editor.commit()) { "Could not store game settings" }
            legacy = legacy - contentId
        }

        override fun isEmpty() = legacy.isEmpty()
    }

    class OverrideStore(private val overrides: GameMetadataOverrides,
                        private val changed: () -> Unit) : ContentStore {
        private var legacy: Set<String> = emptySet()
        override fun refresh() { legacy = overrides.contentIdRecords() }
        override fun has(contentId: String) = contentId in legacy
        override fun move(contentId: String, games: List<GameKey>) {
            overrides.moveContentIdRecord(contentId, games)
            legacy = legacy - contentId
            changed()
        }
        override fun isEmpty() = legacy.isEmpty()
    }

    /**
     * A game's saved data in [location], one directory per unit. A unit already saved under a
     * game's key, as by another device sharing the save folder, is a conflict that [resolve]
     * settles: it returns the conflicts whose earlier copy replaces the newer one.
     */
    class SaveStore(private val location: () -> SaveLocation, private val names: Set<String>,
                    private val staging: File, private val journal: File,
                    private val resolve: (List<Conflict>) -> Set<Conflict>) : ContentStore {
        class Conflict(val game: GameKey, val unit: String, val earlierSavedAt: Long, val keyedSavedAt: Long)

        private var legacy: Set<String> = emptySet()

        override fun refresh() {
            legacy = location().v1Games().filterNot { GameKey.HASH_NAME.matches(it) }.toSet()
        }

        override fun has(contentId: String) = SaveLocation.contentIdName(contentId) in legacy

        override fun move(contentId: String, games: List<GameKey>) {
            val saves = location()
            val from = SaveLocation.contentIdName(contentId)
            val started = started()
            val units = names.map { SaveUnit.v1(from, it) }.filter(saves::exists)
            val conflicts = units.flatMap { unit ->
                games.mapNotNull { game ->
                    val target = SaveUnit.v1(game.hashName, unit.kind)
                    // A move interrupted after copying left this target; it is not a newer save.
                    if (unit.path in started || !saves.exists(target) || saves.isEmpty(target)) null
                    else Conflict(game, unit.kind, saves.savedAt(unit), saves.savedAt(target))
                }
            }
            val keepNewer = conflicts.toSet() - (if (conflicts.isEmpty()) emptySet() else resolve(conflicts))
            for (unit in units) {
                record(started() + unit.path)
                val copy = File(staging, "game-key-migration")
                check(!copy.exists() || copy.deleteRecursively()) { "Could not clear an earlier move" }
                try {
                    saves.export(unit, copy)
                    for (game in games) {
                        if (keepNewer.any { it.game == game && it.unit == unit.kind }) continue
                        saves.import(SaveUnit.v1(game.hashName, unit.kind), copy)
                    }
                } finally {
                    copy.deleteRecursively()
                }
                saves.delete(unit)
                record(started() - unit.path)
            }
            legacy = legacy - from
        }

        override fun isEmpty() = legacy.isEmpty()

        /** Units whose move began: `<game>/<unit>` per line. Unit and game names hold no line breaks. */
        private fun started(): Set<String> =
            if (journal.isFile) journal.readLines().filter { it.isNotEmpty() }.toSet() else emptySet()

        private fun record(started: Set<String>) {
            val pending = File(journal.path + ".part")
            pending.writeText(started.joinToString("\n"))
            Files.move(pending.toPath(), journal.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
        }
    }

    private companion object {
        const val DONE = "game_keys_migrated_v1"
    }
}
