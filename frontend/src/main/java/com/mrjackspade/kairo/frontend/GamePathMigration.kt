package com.mrjackspade.kairo.frontend

import android.content.SharedPreferences
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Moves what v1.4.0 and earlier stored for each game under its [GameKey.hashName] (`k1-<hash>`) to
 * the game's own path (Kairo#57): save units to `<savePath>.<kind>`, working copies to the game's
 * path in app storage, settings and overrides to its key. It runs as the library loads, before
 * the library (and so any game) is shown, until nothing named the v1.4.0 way is left.
 *
 * A claim names a game and the v1 name its data had: its own key's, or for a game a frontend
 * opened, the name of the key v1.4.0 gave it, the URI the frontend sent (Kairo#56). Each store
 * moves a game's data as a whole. A failure leaves that data where it is, is reported for the
 * game and is tried again at the next load. Data no game claims stays for a later load.
 *
 * Saved data the game already has at its path is a conflict: save units and working copies are
 * listed for [resolve], which returns the conflicts whose earlier copy replaces the current one.
 * Settings and overrides the game already has are kept.
 *
 * [earlierPending] is true while an earlier migration ([GameKeyMigration]) may still give a game
 * data under its hash name; until it is done, neither is this one.
 */
class GamePathMigration<T : LibraryItem>(
    private val preferences: SharedPreferences,
    private val stores: List<Store<T>>,
    private val earlierPending: () -> Boolean = { false },
    private val resolve: (List<Conflict>) -> Set<Conflict>
) {
    /** A game, and the v1 name its data from v1.4.0 and earlier has. */
    class Claim<T : LibraryItem>(val item: T, val from: String = item.key.hashName) {
        val to: GameKey get() = item.key
        /** The data is from a launch by another app, under the key v1.4.0 gave its URI. */
        val external: Boolean get() = from != to.hashName
    }

    /** Saved data of [kind] ([label] for the player) that a game has both from v1.4.0 and at its path. */
    class Conflict(val game: GameKey, val kind: String, val label: String, val earlierSavedAt: Long,
                   val currentSavedAt: Long, val external: Boolean)

    interface Store<T : LibraryItem> {
        /** Reads what this store holds under v1 names; [has] and [isEmpty] answer from that reading. */
        fun refresh()
        fun has(hashName: String): Boolean
        /** The data [claim] would replace: what the game already has at its path. */
        fun conflicts(claim: Claim<T>): List<Conflict> = emptyList()
        /** Moves [claim]'s data, except where [keep] holds the game's current copy. */
        fun move(claim: Claim<T>, keep: Set<Conflict>)
        /** True when nothing named the v1.4.0 way is left. */
        fun isEmpty(): Boolean
    }

    /** True until a claim finds nothing left to move. */
    val pending: Boolean get() = !preferences.getBoolean(DONE, false)

    /** Moves everything held for [claims]. Returns an error message for each game whose data did not move. */
    @Synchronized fun claim(claims: List<Claim<T>>): Map<GameKey, String> {
        if (!pending) return emptyMap()
        val errors = LinkedHashMap<GameKey, String>()
        stores.forEach(Store<T>::refresh)
        val work = stores.flatMap { store -> claims.filter { store.has(it.from) }.map { store to it } }
        val conflicts = LinkedHashMap<Pair<Store<T>, Claim<T>>, List<Conflict>>()
        for (pair in work) try { conflicts[pair] = pair.first.conflicts(pair.second) }
            catch (error: Exception) { errors.putIfAbsent(pair.second.to, message(error)) }
        val listed = conflicts.values.flatten()
        val replace = if (listed.isEmpty()) emptySet() else try { resolve(listed) } catch (error: Exception) {
            // Nothing that conflicts moves until the player chooses; everything else still does.
            listed.forEach { errors.putIfAbsent(it.game, message(error)) }
            null
        }
        for ((pair, found) in conflicts) {
            val (store, claim) = pair
            if (found.isNotEmpty() && replace == null) continue
            try { store.move(claim, found.toSet() - replace.orEmpty()) }
            catch (error: Exception) { errors.putIfAbsent(claim.to, message(error)) }
        }
        if (errors.isEmpty() && stores.all { it.isEmpty() } && !earlierPending())
            check(preferences.edit().putBoolean(DONE, true).commit()) { "Could not record moved game data" }
        return errors
    }

    private fun message(error: Exception) =
        "Saved data from an earlier version did not move: ${error.message ?: error.javaClass.simpleName}"

    /**
     * Save units: `<v1 name>/<v1 kind>` to `<savePath>.<kind>` in [location], for each of [kinds]
     * (kind to v1 kind). A move copies each unit, stores it at its path, then deletes the earlier
     * one; [journal] lists the units whose move began, so a move interrupted after storing its
     * copy finishes the next time instead of asking about its own copy.
     */
    class SaveUnitStore<T : LibraryItem>(private val location: () -> SaveLocation,
                                         private val kinds: Map<String, String>,
                                         private val label: (String) -> String,
                                         private val staging: File, private val journal: File) : Store<T> {
        private var v1: Set<String> = emptySet()

        override fun refresh() { v1 = location().v1Games().filter { GameKey.HASH_NAME.matches(it) }.toSet() }

        override fun has(hashName: String) = hashName in v1

        private class Move(val kind: String, val from: SaveUnit, val to: SaveUnit)

        private fun moves(claim: Claim<T>, saves: SaveLocation) = kinds.map { (kind, v1Kind) ->
            Move(kind, SaveUnit.v1(claim.from, v1Kind), SaveUnit.of(claim.to, kind))
        }.filter { saves.exists(it.from) }

        override fun conflicts(claim: Claim<T>): List<Conflict> {
            val saves = location()
            val started = started()
            return moves(claim, saves).filter { move ->
                move.from.path !in started && saves.exists(move.to) && !saves.isEmpty(move.to)
            }.map { move ->
                Conflict(claim.to, move.kind, label(move.kind), saves.savedAt(move.from), saves.savedAt(move.to), claim.external)
            }
        }

        override fun move(claim: Claim<T>, keep: Set<Conflict>) {
            val saves = location()
            for (move in moves(claim, saves)) {
                record(started() + move.from.path)
                if (keep.none { it.kind == move.kind }) {
                    val copy = File(staging, "game-path-migration")
                    check(!copy.exists() || copy.deleteRecursively()) { "Could not clear an earlier move" }
                    try {
                        saves.export(move.from, copy)
                        saves.import(move.to, copy)
                    } finally {
                        copy.deleteRecursively()
                    }
                }
                saves.delete(move.from)
                record(started() - move.from.path)
            }
            // What is left is a save's unfinished scratch, never read again.
            saves.deleteV1Game(claim.from)
            v1 = v1 - claim.from
        }

        override fun isEmpty() = v1.isEmpty()

        /** Units whose move began, one path per line. Unit paths hold no line breaks. */
        private fun started(): Set<String> =
            if (journal.isFile) journal.readLines().filter { it.isNotEmpty() }.toSet() else emptySet()

        private fun record(started: Set<String>) {
            val pending = File(journal.path + ".part")
            pending.writeText(started.joinToString("\n"))
            Files.move(pending.toPath(), journal.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
        }
    }

    /**
     * Per-game preferences v1.4.0 named `<v1Prefix><v1 name>`, then `_<setting>` when [named];
     * [key] names one now, from the game and the setting (null when not [named]).
     */
    class PreferenceFamily(val v1Prefix: String, val named: Boolean, val key: (GameKey, String?) -> String)

    class PreferenceStore<T : LibraryItem>(private val preferences: SharedPreferences,
                                           private val families: List<PreferenceFamily>) : Store<T> {
        private class Stored(val family: PreferenceFamily, val name: String, val setting: String?)

        /** The v1.4.0 preferences, by v1 name. */
        private var v1: Map<String, List<Stored>> = emptyMap()

        override fun refresh() {
            v1 = preferences.all.keys.mapNotNull { name ->
                families.firstNotNullOfOrNull { family ->
                    if (!name.startsWith(family.v1Prefix)) return@firstNotNullOfOrNull null
                    val rest = name.removePrefix(family.v1Prefix)
                    val game = rest.take(V1_NAME_LENGTH)
                    when {
                        !GameKey.HASH_NAME.matches(game) -> null
                        !family.named && rest.length == V1_NAME_LENGTH -> game to Stored(family, name, null)
                        family.named && rest.length > V1_NAME_LENGTH + 1 && rest[V1_NAME_LENGTH] == '_' ->
                            game to Stored(family, name, rest.substring(V1_NAME_LENGTH + 1))
                        else -> null
                    }
                }
            }.groupBy({ it.first }, { it.second })
        }

        override fun has(hashName: String) = v1.containsKey(hashName)

        override fun move(claim: Claim<T>, keep: Set<Conflict>) {
            val values = preferences.all
            val editor = preferences.edit()
            for (stored in v1[claim.from].orEmpty()) {
                val name = stored.family.key(claim.to, stored.setting)
                // A setting the game already has at its key stays.
                if (name !in values) Preferences.put(editor, name, values[stored.name])
                editor.remove(stored.name)
            }
            check(editor.commit()) { "Could not store game settings" }
            v1 = v1 - claim.from
        }

        override fun isEmpty() = v1.isEmpty()

        private companion object {
            const val V1_NAME_LENGTH = 3 + 32
        }
    }

    /** Override records under v1 names; a record the game already has at its key stays. */
    class OverrideStore<T : LibraryItem>(private val overrides: GameMetadataOverrides,
                                         private val changed: () -> Unit) : Store<T> {
        private var v1: Set<String> = emptySet()
        override fun refresh() { v1 = overrides.v1Records() }
        override fun has(hashName: String) = hashName in v1
        override fun move(claim: Claim<T>, keep: Set<Conflict>) {
            overrides.moveV1Record(claim.from, claim.to)
            v1 = v1 - claim.from
            changed()
        }
        override fun isEmpty() = v1.isEmpty()
    }

    private companion object {
        const val DONE = "game_paths_migrated_v2"
    }
}
