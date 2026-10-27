package com.mrjackspade.kairo.frontend

import android.content.SharedPreferences

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
        /**
         * Reads what this store holds under hash names: every name when [every] (a library load,
         * whose [claims] are every game), else only [claims]' own names, looked up directly
         * (a file a frontend opened, Kairo#74). [has] and [isEmpty] answer from that reading.
         */
        fun refresh(claims: List<Claim<T>>, every: Boolean)
        fun has(hashName: String): Boolean
        /** The data [claim] would replace: what the game already has at its path. */
        fun conflicts(claim: Claim<T>): List<Conflict> = emptyList()
        /** Moves [claim]'s data, except where [keep] holds the game's current copy. */
        fun move(claim: Claim<T>, keep: Set<Conflict>)
        /** True when nothing named the v1.4.0 way is left, as last read with every name. */
        fun isEmpty(): Boolean
    }

    /** True until a library load finds nothing left to move. */
    val pending: Boolean get() = !preferences.getBoolean(DONE, false)

    /**
     * Moves everything held for [claims]. Returns an error message for each game whose data did
     * not move. A library load claims [every] game while [pending]; a game a frontend opened
     * claims only its own earlier names, every time, as nothing else knows them.
     */
    @Synchronized fun claim(claims: List<Claim<T>>, every: Boolean): Map<GameKey, String> {
        val errors = LinkedHashMap<GameKey, String>()
        stores.forEach { it.refresh(claims, every) }
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
        if (every && errors.isEmpty() && stores.all { it.isEmpty() } && !earlierPending())
            check(preferences.edit().putBoolean(DONE, true).commit()) { "Could not record moved game data" }
        return errors
    }

    private fun message(error: Exception) =
        "Saved data from an earlier version did not move: ${error.message ?: error.javaClass.simpleName}"

    /**
     * Save units: `<hash name>/<v1 kind>` to `<savePath>.<kind>` in [location], for each of
     * [kinds] (kind to v1 kind). Each unit moves within the location ([SaveLocation.move]), so it
     * keeps its files and their times and nothing is copied.
     */
    class SaveUnitStore<T : LibraryItem>(private val location: () -> SaveLocation,
                                         private val kinds: Map<String, String>,
                                         private val label: (String) -> String) : Store<T> {
        private var v1: Set<String> = emptySet()

        override fun refresh(claims: List<Claim<T>>, every: Boolean) {
            val saves = location()
            v1 = if (every) saves.v1Games().filter { GameKey.HASH_NAME.matches(it) }.toSet()
                else claims.map { it.from }.filter { saves.hasV1Game(it) }.toSet()
        }

        override fun has(hashName: String) = hashName in v1

        private class Move(val kind: String, val from: SaveUnit, val to: SaveUnit)

        private fun moves(claim: Claim<T>, saves: SaveLocation) = kinds.map { (kind, v1Kind) ->
            Move(kind, SaveUnit.v1(claim.from, v1Kind), SaveUnit.of(claim.to, kind))
        }.filter { saves.exists(it.from) || saves.movePending(it.from, it.to) }

        override fun conflicts(claim: Claim<T>): List<Conflict> {
            val saves = location()
            return moves(claim, saves).filter { move ->
                saves.exists(move.from) && saves.exists(move.to) && !saves.isEmpty(move.to)
            }.map { move ->
                Conflict(claim.to, move.kind, label(move.kind), saves.savedAt(move.from), saves.savedAt(move.to), claim.external)
            }
        }

        override fun move(claim: Claim<T>, keep: Set<Conflict>) {
            val saves = location()
            for (move in moves(claim, saves)) {
                if (keep.any { it.kind == move.kind }) saves.delete(move.from)
                else {
                    // An empty unit, or the current copy the player chose to replace.
                    if (saves.exists(move.to)) saves.delete(move.to)
                    saves.move(move.from, move.to)
                }
            }
            // What is left is a save's unfinished scratch, never read again.
            saves.deleteV1Game(claim.from)
            v1 = v1 - claim.from
        }

        override fun isEmpty() = v1.isEmpty()
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

        override fun refresh(claims: List<Claim<T>>, every: Boolean) {
            val wanted = if (every) null else claims.map { it.from }.toSet()
            v1 = preferences.all.keys.mapNotNull { name ->
                families.firstNotNullOfOrNull { family ->
                    if (!name.startsWith(family.v1Prefix)) return@firstNotNullOfOrNull null
                    val rest = name.removePrefix(family.v1Prefix)
                    val game = rest.take(V1_NAME_LENGTH)
                    when {
                        !GameKey.HASH_NAME.matches(game) || wanted?.contains(game) == false -> null
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
        override fun refresh(claims: List<Claim<T>>, every: Boolean) {
            v1 = if (every) overrides.v1Records() else claims.map { it.from }.filter(overrides::hasV1Record).toSet()
        }
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
