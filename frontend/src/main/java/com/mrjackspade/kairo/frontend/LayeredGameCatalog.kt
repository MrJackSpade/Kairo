package com.mrjackspade.kairo.frontend

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.URI
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A game's metadata after every catalog layer and the user's overrides are merged. The common
 * fields are read here; a product reads its own sections from [record], as with [section].
 */
class CatalogGame(
    val contentId: String,
    override val title: String,
    override val description: String?,
    val heart: Boolean?,
    override val boxArt: String?,
    override val preview: String?,
    val boxArtUrl: String?,
    val previewUrl: String?,
    val controllerProfile: String?,
    val controllerBindings: String?,
    /** The catalog's bindings by controller layout: "withoutSticks", and "withSticks" when it has its own. */
    val controllerDefaultBindings: String?,
    val inputMode: String?,
    val inputTouch: String?,
    val inputSecondary: String?,
    /** Top-level fields the user changed for this game. */
    val overriddenFields: Set<String>,
    /** The merged record; product sections are read as they are used, so bad data fails that use alone. */
    val record: JSONObject,
    private val layers: CatalogFieldLayers.Result
) : LibraryGame {
    /** The record's tags (year, genre, players), and the heart marker. */
    override val tags: List<String> get() = (record.optJSONArray("tags")?.let { array ->
        (0 until array.length()).mapNotNull { (array.opt(it) as? String)?.takeIf(String::isNotBlank) }
    } ?: emptyList()) + if (heart == true) listOf("♥") else emptyList()

    fun section(name: String): JSONObject? = record.optJSONObject(name)

    /** The priority of the layer a field came from, higher above lower; null when no layer has it. */
    fun sourceIndexOf(vararg path: String): Int? = layers.sourceIndexOf(*path)

    /** The content ID the game names for a media [role], such as a second floppy. */
    fun mediaId(role: String): String? = record.optJSONArray("media")?.let { items ->
        (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            .firstOrNull { it.optString("role") == role }
            ?.optString("contentId")?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Versioned, data-only metadata in layers: the shipped catalog (base, shards, name index and
 * additional parts), the downloaded update, installed catalogs, the user's catalog file, and the
 * user's overrides. Catalog records are named by content ID; overrides belong to a game's key.
 * Nothing in a catalog is executed.
 */
class LayeredGameCatalog(private val context: Context, val schema: Schema) : LibraryCatalog {
    /** What a product's catalog holds beyond the common fields, and where its updates come from. */
    interface Schema {
        /** The installed-catalog platform, such as "pc88". */
        val platform: String
        /**
         * Where the catalog's update is published, a folder URL ending in "/": core-v4.zip with
         * core-v4.meta.json, and each language's descriptions in core-v4-<language>.zip.
         */
        val catalogUrl: String
        /** Artwork served from the product's own repository: a raw.githubusercontent.com path prefix. */
        val selfHostedArt: String
        /** Content IDs, such as sha256-fd-v1:<hex>. */
        val contentId: Regex
        /** Media file extensions, removed from file names before name matching. */
        val mediaExtensions: Regex
        /** Product fields; a field with subfields maps to them, a plain field to null. */
        val fields: Map<String, Set<String>?>
        /**
         * The settings a record can give a game, each a row of the game's settings (AGENTS.md: a
         * catalog setting is a per-game setting). Its drives are the media roles a game names, once each.
         */
        val settings: List<CatalogSetting>
        /** Whether a game can choose touch that moves the guest mouse to the touched point (`input.touch`). */
        val directTouch: Boolean get() = false
        /** Whether [contentId] can fill the media [role]. */
        fun validMedia(role: String, contentId: String): Boolean
        fun validField(field: String, value: Any): Boolean
        fun validBindings(bindings: JSONArray): Boolean
        /** Subfields a layer's object replaces in lower layers when it sets one of them. */
        fun replacedSubfields(path: List<String>, record: JSONObject): Set<String> = emptySet()
        /**
         * The key a record's `variants` names a library entry's [fileName] by: one medium that
         * holds several games, each picked by its file's name.
         */
        fun variantName(fileName: String): String =
            fileName.substringAfterLast('/').substringAfterLast('\\').replace(mediaExtensions, "").lowercase()
        /** The title a library entry shows for the catalog's [title] and its [fileName]. */
        fun title(title: String, fileName: String): String = title
        /** A record's `artwork` references as image paths and download URLs: LaunchBox references, by default. */
        fun expandArtwork(art: JSONObject): JSONObject = LaunchBoxArtworkReferences.expand(art)
    }

    class ArtworkSource(val path: String, val url: String)

    val artworkStore = CatalogArtworkStore(context, ::validImageUrl)

    override val installedCatalogs by lazy {
        InstalledCatalogs(context, schema.platform, ::requireUpdateFormat) {
            synchronized(this) { fallbackRecords.clear(); updatedFallbackIds.clear(); invalidate() }
        }
    }
    private val parts = CatalogParts(context)
    private val base = readAsset("catalog/base-v1.json")
    // Hash matches and downloaded name matches do not need the bundled fallback index.
    private val nameIndex by lazy { readAsset("catalog/name-index-v1.json") }
    private val shardNames = readShardNames()
    private val shardCache = object : android.util.LruCache<String, JSONObject>(8) {}
    private val fallbackRecords = HashMap<String, JSONObject>()
    // A game's merged record and resolved metadata are reused until a catalog, override or
    // artwork change; settings pages read them for every row and every focus move.
    /** [owner] is the game whose overrides apply; null for its catalog record alone. */
    private data class MemoKey(val contentId: String, val owner: GameKey?, val fileName: String?)
    private val layeredMemo = HashMap<MemoKey, CatalogFieldLayers.Result>()
    private val resolvedMemo = HashMap<MemoKey, CatalogGame>()
    private fun invalidate() {
        layeredMemo.clear()
        resolvedMemo.clear()
    }

    /** Only this game's overrides changed. */
    private fun invalidate(owner: GameKey) {
        layeredMemo.keys.removeAll { it.owner == owner }
        resolvedMemo.keys.removeAll { it.owner == owner }
    }
    private val knownFileNames = HashMap<String, String>()
    private val updatedFallbackIds = HashSet<String>()
    private val additionsFile = File(context.filesDir, "user-catalog-v1.json")
    private val overridesFile = File(context.filesDir, "overrides-v1.json")
    /** The language this device shows descriptions in besides English, or null for English alone. */
    private val language = descriptionLanguage(java.util.Locale.getDefault())
    private val snapshot = updateStore("core-v$UPDATE_VERSION", "name-index-v1.json")
    private val languageSnapshot = language?.let { updateStore("core-v$UPDATE_VERSION-$it", "descriptions/$it.json") }
    // The downloaded update is opened when a game first needs it, on whichever thread asks (the
    // library's workers at a start), not while the activity is created (Kairo#52); its documents
    // are parsed as they are read.
    private var update: Lazy<CatalogUpdateArchive?> = lazy { snapshot.activeFile()?.let(::CatalogUpdateArchive) }
    private var languageUpdate: Lazy<CatalogUpdateArchive?> = lazy { languageSnapshot?.activeFile()?.let(::CatalogUpdateArchive) }

    init {
        // Before the v4 update an app kept the update as one JSON file.
        for (name in listOf("core-update-v3.json", "core-update-v3.json.apk")) File(context.filesDir, name).delete()
    }

    private fun updateStore(name: String, vararg required: String) = CatalogSnapshotStore(context,
        name.replaceFirst("core-", "core-update-") + ".zip", schema.catalogUrl + "$name.zip",
        schema.catalogUrl + "$name.meta.json", MAX_UPDATE_BYTES) { file -> CatalogUpdateArchive.validate(file, *required) }

    /** A document of the downloaded update, or null without an update or without that document. */
    private fun updated(path: String): JSONObject? = synchronized(this) { update.value?.document(path) }
    private var additions = readLocal(additionsFile)
    private val overrides = GameMetadataOverrides(overridesFile, MAX_LOCAL_JSON.toInt(),
        schemaEnvelope = true)
    private val objectFields: Map<String, Set<String>> = COMMON_OBJECT_FIELDS +
        ("input" to (if (schema.directTouch) INPUT_FIELDS else INPUT_FIELDS - "touch")) +
        schema.fields.mapNotNull { (field, subfields) -> subfields?.let { field to it } }
    private val allFields: Set<String> = COMMON_FIELDS + schema.fields.keys
    private val mediaRoles = CatalogSetting.roles(schema.settings)

    @Synchronized fun reloadAdditions() {
        additions = readLocal(additionsFile)
        invalidate()
    }

    /** Call from a worker thread. An invalid or interrupted download leaves the current catalog intact. */
    fun downloadUpdate(task: CatalogUpdateTask = CatalogUpdateTask()): Boolean {
        // Both downloads run: the device language's descriptions update on their own.
        val changed = snapshot.download(task) or (languageSnapshot?.download(task) ?: false)
        if (!changed) return false
        synchronized(this) {
            if (update.isInitialized()) update.value?.close()
            if (languageUpdate.isInitialized()) languageUpdate.value?.close()
            update = lazy { snapshot.activeFile()?.let(::CatalogUpdateArchive) }
            languageUpdate = lazy { languageSnapshot?.activeFile()?.let(::CatalogUpdateArchive) }
            fallbackRecords.clear()
            updatedFallbackIds.clear()
            invalidate()
        }
        return true
    }

    @Synchronized fun sourceOf(item: LibraryItem, field: String, subfield: String? = null): String {
        val path = if (subfield == null) arrayOf(field) else arrayOf(field, subfield)
        return layered(matchId(item) ?: "", item.key, item.displayName).sourceOf(*path)
            ?: if (field == "title") "Filename" else "App default"
    }

    /** Where a setting would come from without the user's own change, for its Default option. */
    @Synchronized fun defaultSourceOf(item: LibraryItem, field: String, subfield: String? = null): String {
        val path = if (subfield == null) arrayOf(field) else arrayOf(field, subfield)
        return layered(matchId(item) ?: "", null, item.displayName).sourceOf(*path)
            ?: if (field == "title") "Filename" else "App default"
    }

    /** The game without the user's own changes, so each Default option can show its value. */
    @Synchronized fun resolveDefault(item: LibraryItem): CatalogGame = resolve(matchId(item) ?: "", null, item.displayName)

    private fun layered(contentId: String, owner: GameKey?, fileName: String? = null): CatalogFieldLayers.Result {
        if (fileName != null && contentId.isNotEmpty()) knownFileNames[contentId] = fileName
        val key = MemoKey(contentId, owner, fileName ?: knownFileNames[contentId])
        return layeredMemo.getOrPut(key) { layeredUncached(key) }
    }

    private fun layeredUncached(key: MemoKey): CatalogFieldLayers.Result {
        val contentId = key.contentId
        val fileName = key.fileName
        val baseRecord = base.optJSONObject("games")?.optJSONObject(contentId)
        val shardRecord = shardFor(contentId)?.optJSONObject("games")?.optJSONObject(contentId)
        val updateRecord = updatedRecord(contentId)
        var fallback = fallbackRecords[contentId]
        if (baseRecord == null && shardRecord == null && updateRecord == null && fileName != null) {
            lookupByName(fileName)?.let { (record, fromUpdate) ->
                fallback = record
                if (contentId.isNotEmpty()) {
                    fallbackRecords[contentId] = record
                    if (fromUpdate) updatedFallbackIds.add(contentId) else updatedFallbackIds.remove(contentId)
                }
            }
        } else if (baseRecord != null || shardRecord != null || updateRecord != null) fallback = null
        val userRecord = additions.optJSONObject("games")?.optJSONObject(contentId)
        val overrideRecord = key.owner?.let(overrides::record)
        val installed = installedCatalogs.catalogs()
        val variantName = fileName?.let(schema::variantName)
        // A disk record names its game's shared record. Each layer adds its own copy of that
        // record beneath its disk records, so the disk's own fields override it in every layer.
        val game = (listOf(overrideRecord, userRecord) +
            installed.asReversed().map { it.data.getJSONObject("games").optJSONObject(contentId) } +
            listOf(updateRecord, shardRecord, baseRecord))
            .flatMap { listOf(variant(it, variantName), it) }
            .firstNotNullOfOrNull { record -> record?.optString("game")?.takeIf { it.isNotEmpty() } }
        fun shared(games: JSONObject?) = game?.let { games?.optJSONObject("games")?.optJSONObject(it) }
        // The game's description in this device's language, from a layer's language document.
        fun translated(document: JSONObject?) = language?.let { language ->
            game?.let { document?.optJSONObject("games")?.optString(it) }?.takeIf { it.isNotBlank() }
                ?.let { JSONObject().put("descriptions", JSONObject().put(language, it)) }
        }
        val languageDocument = language?.let { "descriptions/$it.json" }
        return CatalogFieldLayers.merge(listOf(
            CatalogFieldLayers.Source("Shipped catalog", shared(nameIndex)),
            CatalogFieldLayers.Source("Shipped catalog", translated(languageDocument?.let(parts::main))),
            CatalogFieldLayers.Source("Shipped catalog", baseRecord),
            CatalogFieldLayers.Source("Shipped catalog", shardRecord),
            CatalogFieldLayers.Source(if (contentId in updatedFallbackIds) "Updated catalog"
                else "Shipped catalog", fallback),
            CatalogFieldLayers.Source("Updated catalog", shared(updated("name-index-v1.json"))),
            CatalogFieldLayers.Source("Updated catalog", translated(languageDocument?.let { path ->
                synchronized(this) { languageUpdate.value?.document(path) } })),
            CatalogFieldLayers.Source("Updated catalog", updateRecord),
            CatalogFieldLayers.Source("Shipped catalog", shared(parts.additional("name-index-v1.json"))),
            CatalogFieldLayers.Source("Shipped catalog", translated(languageDocument?.let(parts::additional))),
            CatalogFieldLayers.Source("Shipped catalog", bundledAdditional(contentId, fileName)),
            *installedSources(installed, contentId, fileName, ::shared, ::translated, languageDocument).toTypedArray(),
            CatalogFieldLayers.Source("User catalog", userRecord),
            CatalogFieldLayers.Source("User override", overrideRecord)
        ).let { withVariants(it, variantName) }
            .map { source -> source.copy(record = source.record?.let { record ->
                record.optJSONObject("artwork")?.let { JsonCopy.shallow(record).put("artwork", schema.expandArtwork(it)) } ?: record
            }) },
            { path -> path.size == 1 && path[0] in objectFields },
            schema::replacedSubfields)
    }

    /**
     * A product's own catalog document [name], such as KairoDos's launch folders, from the shipped
     * catalog, the downloaded update and each installed catalog, lowest layer first. The update
     * holds them at their own paths; installed catalogs under "documents".
     */
    @Synchronized fun documents(name: String): List<JSONObject> =
        listOfNotNull(readAsset("catalog/$name").takeIf { it.length() > 0 }, updated(name)) +
            installedCatalogs.catalogs().mapNotNull { it.data.optJSONObject("documents")?.optJSONObject(name) }

    @Synchronized override fun resolve(item: LibraryItem): CatalogGame =
        resolve(matchId(item) ?: "", item.key, item.displayName)

    /** A catalog record alone, as for a disk a game names that is not a library entry. */
    @Synchronized fun resolve(contentId: String, fileName: String): CatalogGame = resolve(contentId, null, fileName)

    private fun resolve(contentId: String, owner: GameKey?, fileName: String): CatalogGame =
        resolvedMemo.getOrPut(MemoKey(contentId, owner, fileName)) {
            resolveUncached(contentId, owner, fileName)
        }

    // An unedited game needs no lookup, so startup reads no shards for it.
    @Synchronized override fun matchId(item: LibraryItem): String? {
        val current = item.contentId ?: return item.catalogId
        val first = item.catalogId ?: return current
        return if (current == first || known(current)) current else first
    }

    @Synchronized override fun knows(item: LibraryItem): Boolean = matchId(item)?.let(::known) == true

    /** Whether any catalog has a record for [contentId]. */
    private fun known(contentId: String): Boolean {
        fun has(source: JSONObject?) = source?.optJSONObject("games")?.has(contentId) == true
        return has(base) || has(shardFor(contentId)) || updatedRecord(contentId) != null || has(additions) ||
            has(parts.additional("shards/${contentId.substringAfter(':').take(2)}.json")) ||
            installedCatalogs.sources(contentId).any { it.record != null }
    }

    private fun resolveUncached(contentId: String, owner: GameKey?, fileName: String): CatalogGame {
        val layers = layered(contentId, owner, fileName)
        val merged = layers.record
        val user = owner?.let(overrides::record)
        val title = merged.optString("title").takeIf { it.isNotBlank() }
            ?: fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val artwork = merged.optJSONObject("artwork")
        val controller = merged.optJSONObject("controller")
        val input = merged.optJSONObject("input")
        // Paths and URLs are checked where an image is opened or downloaded, for that game alone.
        return CatalogGame(
            contentId, schema.title(title.ifBlank { fileName }, fileName),
            description(merged, layers),
            merged.opt("heart") as? Boolean,
            artwork?.optString("boxArt")?.takeIf { it.isNotEmpty() },
            artwork?.optString("preview")?.takeIf { it.isNotEmpty() },
            artwork?.optString("boxArtUrl")?.takeIf { it.isNotEmpty() },
            artwork?.optString("previewUrl")?.takeIf { it.isNotEmpty() },
            controller?.optString("profile")?.takeIf { it.length in 1..128 },
            controller?.optJSONArray("bindings")?.toString(),
            (controller?.optJSONObject("defaults")
                ?: controller?.optJSONArray("bindings")?.takeIf {
                    it.length() > 0 || controller.optString("profile") == "custom-v1"
                }?.let { JSONObject().put("withoutSticks", it) })?.toString(),
            input?.optString("mode")?.takeIf { it in INPUT_MODES },
            input?.optString("touch")?.takeIf { it in INPUT_TOUCH },
            input?.optString("secondary")?.takeIf { it in INPUT_SECONDARY },
            user?.keys()?.asSequence()?.toSet() ?: emptySet(),
            merged, layers)
    }

    override fun openArtwork(path: String): InputStream = installedCatalogs.openArtwork(path) ?: artworkStore.open(path)

    fun missingArtworkFor(entries: List<LibraryItem>): List<ArtworkSource> {
        return entries.asSequence().filter { it.playable }
            .flatMap { entry ->
                val game = resolve(entry)
                listOfNotNull(
                    game.boxArt?.takeIf { game.boxArtUrl != null &&
                        sourceOf(entry, "artwork", "boxArt") ==
                            sourceOf(entry, "artwork", "boxArtUrl") }
                        ?.let { ArtworkSource(it, game.boxArtUrl!!) },
                    game.preview?.takeIf { game.previewUrl != null &&
                        sourceOf(entry, "artwork", "preview") ==
                            sourceOf(entry, "artwork", "previewUrl") }
                        ?.let { ArtworkSource(it, game.previewUrl!!) }).asSequence()
            }
            .distinctBy { it.path }
            .filter { installedCatalogs.artwork(it.path) == null && artworkStore.availablePath(it.path) == null }
            .toList()
    }

    fun downloadArtwork(source: ArtworkSource, cancelled: AtomicBoolean) =
        artworkStore.download(source.path, source.url, cancelled)

    @Synchronized fun setOverride(game: GameKey, field: String, value: Any) {
        require(field in allFields) { "Unsupported field" }
        require(field !in objectFields) { "Use subfield updates for nested settings" }
        require(validField(field, value)) { "Invalid $field" }
        overrides.set(game, field, value)
        invalidate(game)
    }

    /** Overrides from before games had keys, for [GameKeyMigration]. */
    fun legacyOverrides() = GameKeyMigration.OverrideStore(overrides) { synchronized(this) { invalidate() } }
    /** Overrides v1.4.0 and earlier kept under games' v1 names, for [GamePathMigration]. */
    fun <E : LibraryItem> v1Overrides() = GamePathMigration.OverrideStore<E>(overrides) { synchronized(this) { invalidate() } }

    /**
     * Hide a known support disk without removing it from media lookup or disk swaps. Read from the
     * resolved record, so a disk is hidden by its game's shared record too (a name-indexed
     * catalog's disk records only name their game), with the usual layer precedence.
     */
    @Synchronized override fun hiddenFromLibrary(item: LibraryItem): Boolean {
        if (matchId(item) == null) return false
        return CatalogFieldLayers.hidden(resolve(item).record.opt("hidden"))
    }

    /** Read for every library entry, so it takes the field from the highest layer instead of merging records. */
    @Synchronized override fun setDisks(item: LibraryItem): List<String> {
        val contentId = matchId(item) ?: return emptyList()
        fun disks(record: JSONObject?) = record?.optJSONArray("setDisks")
        fun game(source: JSONObject?) = source?.optJSONObject("games")?.optJSONObject(contentId)
        val array = disks(overrides.record(item.key)) ?: disks(game(additions))
            ?: installedCatalogs.sources(contentId).asReversed().firstNotNullOfOrNull { disks(it.record) }
            ?: disks(game(parts.additional("shards/${contentId.substringAfter(':').take(2)}.json")))
            ?: disks(updatedRecord(contentId)) ?: disks(game(shardFor(contentId))) ?: disks(game(base))
            ?: return emptyList()
        return (0 until array.length()).map(array::getString)
    }

    @Synchronized fun updateOverrideSubfields(game: GameKey, field: String, changes: Map<String, Any?>) {
        require(field in objectFields) { "Unsupported setting" }
        if (changes.isEmpty()) return
        for ((subfield, value) in changes) {
            require(subfield in objectFields.getValue(field)) { "Unsupported setting" }
            require(value == null || validField(field, JSONObject().put(subfield, value))) {
                "Invalid $field.$subfield"
            }
        }
        overrides.updateSubfields(game, field, changes)
        invalidate(game)
    }

    /** The player's own value of [field] for [game], or null when they have not changed it. */
    @Synchronized fun overrideOf(game: GameKey, field: String): JSONObject? = overrides.record(game)?.optJSONObject(field)

    fun setOverrideSubfield(game: GameKey, field: String, subfield: String, value: Any) =
        updateOverrideSubfields(game, field, mapOf(subfield to value))

    @Synchronized fun resetOverride(game: GameKey, field: String? = null) {
        require(field == null || field in allFields)
        overrides.clear(game, field)
        invalidate(game)
    }

    /** The player's changes to [from] become [to]'s, as an installer's do for the game it installs. */
    @Synchronized fun moveOverrides(from: GameKey, to: GameKey) {
        overrides.move(from, to)
        invalidate(from)
        invalidate(to)
    }

    fun resetOverrideSubfield(game: GameKey, field: String, subfield: String) =
        updateOverrideSubfields(game, field, mapOf(subfield to null))

    fun setArtworkOverride(game: GameKey, kind: String, path: String) {
        require(kind in ART_PATH_FIELDS && validArtPath(path))
        setOverrideSubfield(game, "artwork", kind, path)
    }

    fun resetArtworkOverride(game: GameKey, kind: String) {
        require(kind in ART_PATH_FIELDS)
        resetOverrideSubfield(game, "artwork", kind)
    }

    fun validId(value: String) = schema.contentId.matches(value)

    private fun validField(field: String, value: Any): Boolean = when (field) {
        "title" -> value is String && validTitle(value)
        "heart" -> value is Boolean
        "hidden" -> value is Boolean
        "description" -> value is String && validDescription(value)
        "descriptions" -> value is JSONObject && value.keys().asSequence().all { language ->
            language in DESCRIPTION_LANGUAGES && (value.opt(language) as? String)?.let(::validDescription) == true
        }
        "tags" -> value is JSONArray && value.length() <= 32 &&
            (0 until value.length()).all { index -> (value.opt(index) as? String)?.let { it.isNotBlank() && it.length <= 128 } == true }
        "aliases" -> value is JSONArray && value.length() <= 64 &&
            (0 until value.length()).all { index ->
                (value.opt(index) as? String)?.let(::validTitle) == true
            }
        "artwork" -> value is JSONObject && runCatching {
            val art = schema.expandArtwork(value)
            art.keys().asSequence().all { it in ART_FIELDS } && ART_PATH_FIELDS.all {
                !art.has(it) || validArtPath(art.optString(it))
            } && ART_URL_FIELDS.all { !art.has(it) || validImageUrl(art.optString(it)) }
        }.getOrDefault(false)
        "controller" -> value is JSONObject &&
            value.keys().asSequence().all { it in objectFields.getValue("controller") } &&
            (!value.has("profile") ||
                (value.opt("profile") is String && value.optString("profile").length in 1..128)) &&
            (!value.has("bindings") || value.optJSONArray("bindings")?.let(schema::validBindings) == true) &&
            (!value.has("layer") || value.optJSONArray("layer")?.let(schema::validBindings) == true) &&
            (!value.has("defaults") || value.optJSONObject("defaults")?.let { defaults ->
                defaults.has("withoutSticks") && defaults.keys().asSequence().all {
                    it in setOf("withoutSticks", "withSticks") &&
                        defaults.optJSONArray(it)?.let(schema::validBindings) == true
                }
            } == true)
        "input" -> value is JSONObject && value.length() > 0 &&
            value.keys().asSequence().all { it in objectFields.getValue("input") } &&
            (!value.has("mode") || value.optString("mode") in INPUT_MODES) &&
            (!value.has("touch") || schema.directTouch && value.optString("touch") in INPUT_TOUCH) &&
            (!value.has("secondary") || value.optString("secondary") in INPUT_SECONDARY)
        // Where the picture sits in each orientation (Kairo#54).
        "display" -> value is JSONObject && value.length() > 0 &&
            value.keys().asSequence().all { it in DISPLAY_FIELDS && value.optString(it) in Anchor.IDS }
        "media" -> value is JSONArray && value.length() <= 16 &&
            (0 until value.length()).all { index ->
                value.optJSONObject(index)?.let { item ->
                    item.optString("role") in mediaRoles && validId(item.optString("contentId")) &&
                        schema.validMedia(item.optString("role"), item.optString("contentId"))
                } == true
            } && mediaRoles.all { role ->
                (0 until value.length()).count { value.optJSONObject(it)?.optString("role") == role } <= 1
            }
        else -> field in schema.fields && schema.validField(field, value)
    }

    private fun readAsset(path: String): JSONObject = parts.read(path.removePrefix("catalog/"))

    /**
     * The system's controller mapping from the catalog's system definition (system-v1.json): the
     * downloaded update's when it has one, so a corrected mapping reaches players without a new
     * build (Kairo#55), else the shipped one. Games' own mappings layer over it as their records do.
     */
    @Synchronized fun systemControllerBindings(): org.json.JSONArray =
        (updated(SYSTEM_DOCUMENT) ?: readAsset("catalog/$SYSTEM_DOCUMENT")).getJSONObject("controller").getJSONArray("bindings")

    private fun normalizedName(fileName: String): String {
        val simple = fileName.substringAfterLast('/').substringAfterLast('\\')
            .replace(BRACKETED, "")
            .replace(DISK_NUMBER, "")
            .replace(schema.mediaExtensions, "")
        return Normalizer.normalize(simple, Normalizer.Form.NFKC).lowercase().filter(Char::isLetterOrDigit)
    }

    private fun bundledAdditional(contentId: String, fileName: String?): JSONObject? {
        parts.additional("shards/${contentId.substringAfter(':').take(2)}.json")
            .optJSONObject("games")?.optJSONObject(contentId)?.let { return it }
        val key = fileName?.let(::normalizedName) ?: return null
        val nameId = nameIndex.optJSONObject("names")?.optString(key) ?: return null
        return parts.additional("name-index-v1.json").optJSONObject("games")?.optJSONObject(nameId)
    }

    private fun installedSources(catalogs: List<InstalledCatalogs.Catalog>, contentId: String, fileName: String?,
                                 shared: (JSONObject?) -> JSONObject?, translated: (JSONObject?) -> JSONObject?,
                                 languageDocument: String?): List<CatalogFieldLayers.Source> =
        catalogs.flatMap { catalog ->
            val names = catalog.data.getJSONObject("nameIndex")
            val key = fileName?.let(::normalizedName)
            val nameId = key?.let { names.getJSONObject("names").optString(it) }
            listOf(CatalogFieldLayers.Source(catalog.name, nameId?.let { names.getJSONObject("games").optJSONObject(it) }),
                CatalogFieldLayers.Source(catalog.name, shared(names)),
                CatalogFieldLayers.Source(catalog.name, translated(languageDocument?.let {
                    catalog.data.optJSONObject("documents")?.optJSONObject(it) })),
                CatalogFieldLayers.Source(catalog.name, catalog.data.getJSONObject("games").optJSONObject(contentId)))
        }

    private fun lookupByName(fileName: String): Pair<JSONObject, Boolean>? {
        val key = normalizedName(fileName)
        if (key.length < 4) return null
        val updatedNames = updated("name-index-v1.json")
        val updatedId = updatedNames?.optJSONObject("names")?.optString(key)
        updatedId?.let { updatedNames?.optJSONObject("games")?.optJSONObject(it) }
            ?.let { return it to true }
        val id = nameIndex.optJSONObject("names")?.optString(key) ?: return null
        return nameIndex.optJSONObject("games")?.optJSONObject(id)?.let { it to false }
    }

    private fun readShardNames(): Set<String> {
        val manifest = readAsset("catalog/manifest-v1.json")
        require(manifest.optInt("schemaVersion") == 1) { "Unsupported catalog manifest" }
        val names = manifest.getJSONArray("shards")
        return (0 until names.length()).map { names.getString(it) }.filter { it.matches(SHARD_NAME) }.toSet()
    }

    private fun shardFor(contentId: String): JSONObject? {
        val name = contentId.substringAfter(':').take(2)
        if (name !in shardNames) return null
        shardCache.get(name)?.let { return it }
        return readAsset("catalog/shards/$name.json").also { shardCache.put(name, it) }
    }

    private fun readLocal(file: File): JSONObject = LocalCatalogFile.read(file, MAX_LOCAL_JSON.toInt()) ?: empty()

    /** A content ID's record in the downloaded update: in its shard, or the base records. */
    private fun updatedRecord(contentId: String): JSONObject? =
        updated("shards/${contentId.substringAfter(':').take(2)}.json")?.optJSONObject("games")?.optJSONObject(contentId)
            ?: updated("base-v1.json")?.optJSONObject("games")?.optJSONObject(contentId)

    /** An installed catalog's format (InstalledCatalogs). */
    private fun requireUpdateFormat(root: JSONObject) {
        require(root.optInt("schemaVersion") == 1)
        root.getJSONObject("games")
        val names = root.getJSONObject("nameIndex")
        require(names.optInt("schemaVersion") == 1)
        names.getJSONObject("games")
        names.getJSONObject("names")
        // Publishing audits all records. Resolution handles fields only as used.
    }

    private fun validArtPath(value: String) = ArtworkOverridePath.valid(value, "art/")

    /** Artwork from LaunchBox, or from the product's own repository for sources without an image server. */
    fun validImageUrl(value: String): Boolean = try {
        if (value.length !in 1..512) false else URI(value).let { uri ->
            uri.scheme == "https" && (uri.host in setOf("images.launchbox-app.com", "gamesdb-images.launchbox.gg") ||
                uri.host == "raw.githubusercontent.com" && uri.rawPath.startsWith(schema.selfHostedArt) &&
                !uri.rawPath.contains("..")) &&
                uri.port == -1 && uri.userInfo == null && uri.rawPath.isNotEmpty() &&
                uri.rawQuery == null && uri.rawFragment == null
        }
    } catch (_: java.net.URISyntaxException) { false }

    companion object {
        /** The catalog's system definition: the system's controller mapping, below every game's. */
        const val SYSTEM_DOCUMENT = "system-v1.json"
        private val BRACKETED = Regex("\\[[^]]*]")
        private val DISK_NUMBER = Regex("\\((?:disk|disc|fd)\\s*\\d+[^)]*\\)", RegexOption.IGNORE_CASE)
        private val SHARD_NAME = Regex("[0-9a-f]{2}")
        private const val MAX_LOCAL_JSON = 8L * 1024 * 1024
        /** The downloaded update's format (CatalogUpdateArchive), and how large a download may be. */
        private const val UPDATE_VERSION = CatalogUpdateArchive.UPDATE_VERSION
        private const val MAX_UPDATE_BYTES = 64L * 1024 * 1024
        private val INPUT_MODES = setOf("auto", "keyboard", "mouse")
        private val INPUT_TOUCH = setOf("touchpad", "direct")
        private val INPUT_SECONDARY = setOf("keyboard", "touchpad")
        private val ART_PATH_FIELDS = setOf("boxArt", "preview")
        private val ART_URL_FIELDS = setOf("boxArtUrl", "previewUrl")
        private val ART_FIELDS = ART_PATH_FIELDS + ART_URL_FIELDS
        private val INPUT_FIELDS = setOf("mode", "touch", "secondary")
        private val DISPLAY_FIELDS = setOf("landscapeAnchor", "portraitAnchor")
        /** The variant of [record] that [variantName] (Schema.variantName of a file's name) picks. */
        fun variant(record: JSONObject?, variantName: String?): JSONObject? =
            variantName?.let { record?.optJSONObject("variants")?.optJSONObject(it) }

        /** Each layer followed by its record's variant for [variantName]: a variant sits above its record. */
        fun withVariants(sources: List<CatalogFieldLayers.Source>, variantName: String?): List<CatalogFieldLayers.Source> =
            sources.flatMap { source ->
                listOfNotNull(source, variant(source.record, variantName)?.let { source.copy(record = it) })
            }

        /** The catalog language [locale] reads descriptions in besides English, by its tag or its language alone. */
        fun descriptionLanguage(locale: java.util.Locale): String? = locale.toLanguageTag().let { tag ->
            listOf(tag, tag.substringBefore('-')).firstOrNull { it in DESCRIPTION_LANGUAGES }
        }

        /** The languages a catalog describes games in besides English (tools/translations.py names the same). */
        val DESCRIPTION_LANGUAGES = setOf("es", "pt-BR", "de", "ru", "ja", "ko", "ar", "id")

        /**
         * The description in the device's language, else the English one. A layer that replaced the
         * English text above the translation, as the adult part does, is shown instead.
         */
        fun description(record: JSONObject, layers: CatalogFieldLayers.Result,
                        locale: java.util.Locale = java.util.Locale.getDefault()): String? {
            val english = record.optString("description").takeIf { it.isNotBlank() }
            val translations = record.optJSONObject("descriptions") ?: return english
            val language = descriptionLanguage(locale)?.takeIf { translations.has(it) } ?: return english
            val translated = translations.optString(language).takeIf { it.isNotBlank() } ?: return english
            val englishLayer = layers.sourceIndexOf("description") ?: -1
            return if ((layers.sourceIndexOf("descriptions", language) ?: -1) >= englishLayer) translated else english
        }
        private val COMMON_OBJECT_FIELDS = mapOf(
            "artwork" to ART_FIELDS,
            // "layer" is the player's own mapping layer (ControllerDefaults.cascade); catalogs never hold one.
            "controller" to setOf("profile", "bindings", "defaults", "layer"),
            "input" to INPUT_FIELDS,
            "display" to DISPLAY_FIELDS,
            // A description per language beside the English one, by BCP 47 tag; each layer replaces a language on its own.
            "descriptions" to DESCRIPTION_LANGUAGES)
        private val COMMON_FIELDS = setOf("title", "description", "aliases", "tags", "heart", "hidden", "media") +
            COMMON_OBJECT_FIELDS.keys
        private fun validTitle(value: String) = value.isNotBlank() && value.length <= 256
        private fun validDescription(value: String) = value.isNotBlank() && value.length <= 8000
        private fun empty() = JSONObject().put("schemaVersion", 1).put("games", JSONObject())
    }
}
