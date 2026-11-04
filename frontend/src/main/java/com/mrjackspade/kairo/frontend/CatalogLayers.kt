package com.mrjackspade.kairo.frontend

import org.json.JSONArray
import org.json.JSONObject

/**
 * How [LayeredGameCatalog] merges a game's record from its layers, apart from where the layers
 * are stored (Kairo#107): the shipped catalog (base, shards, name index and additional parts), the
 * downloaded update, installed catalogs, the user's catalog file, and the user's overrides, lowest
 * first. [sources] reads them: the app's files on Android, documents in memory in unit tests.
 * Not thread-safe: [LayeredGameCatalog] calls it under its own lock.
 */
class CatalogLayers(private val schema: LayeredGameCatalog.Schema, private val sources: Sources,
                    /** The language this device shows descriptions in besides English, or null for English alone. */
                    private val language: String?) {
    /** Where the layers' documents are read. */
    interface Sources {
        /**
         * The shipped catalog's document [path] (such as "base-v1.json"): from every part, from the
         * parts every build ships, or from the others alone (adult). An empty object when no part has it.
         */
        fun shipped(path: String): JSONObject
        fun shippedMain(path: String): JSONObject
        fun shippedAdditional(path: String): JSONObject
        /** The downloaded update's document [path]; null without an update or without that document. */
        fun updated(path: String): JSONObject?
        /** The downloaded update of this device's descriptions' document [path]; null as for [updated]. */
        fun updatedLanguage(path: String): JSONObject?
        /** The catalogs the player installed, lowest layer first. */
        fun installed(): List<InstalledCatalogs.Catalog>
        /** The user's catalog file. */
        fun additions(): JSONObject
        /** The player's own record for [game], or null. */
        fun override(game: GameKey): JSONObject?
    }

    /** The object fields: each layer's object merges with lower layers' subfield by subfield. */
    val objectFields: Map<String, Set<String>> = LayeredGameCatalog.COMMON_OBJECT_FIELDS +
        ("input" to (if (schema.directTouch) LayeredGameCatalog.INPUT_FIELDS else LayeredGameCatalog.INPUT_FIELDS - "touch")) +
        schema.fields.mapNotNull { (field, subfields) -> subfields?.let { field to it } }

    private val base = sources.shipped("base-v1.json")
    private val nameIndex by lazy { sources.shipped("name-index-v1.json") }
    private val shardNames = readShardNames()
    // Weighted by count: shards are small beside the whole-library documents held below.
    private val shardCache = object : LinkedHashMap<String, JSONObject>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JSONObject>?) = size > SHARD_CACHE
    }
    // Every resolve reads these whole-library documents: each layer's name index and description
    // document in this device's language. They are held for the catalog's life, as the shipped name
    // index is; the caches below them are sized for shards, and one bigger than its cache, or two
    // that did not fit it together, were parsed again for nearly every game of a library walk (Kairo#135).
    private val languageDocument = language?.let { "descriptions/$it.json" }
    private val shippedLanguage by lazy { languageDocument?.let(sources::shippedMain) }
    private val additionalNames by lazy { sources.shippedAdditional("name-index-v1.json") }
    private val additionalLanguage by lazy { languageDocument?.let(sources::shippedAdditional) }
    private var updatedNames: Lazy<JSONObject?> = lazy { sources.updated("name-index-v1.json") }
    private var updatedLanguage: Lazy<JSONObject?> = lazy { languageDocument?.let(sources::updatedLanguage) }

    // A game's merged record and resolved metadata are reused until a catalog, override or
    // artwork change; settings pages read them for every row and every focus move.
    /** [owner] is the game whose overrides apply; null for its catalog record alone. */
    private data class MemoKey(val contentId: String, val owner: GameKey?, val member: String?, val fileName: String? = null)
    private val layeredMemo = HashMap<MemoKey, CatalogFieldLayers.Result>()
    private val resolvedMemo = HashMap<MemoKey, CatalogGame>()

    /** Any layer changed. */
    fun invalidate() {
        layeredMemo.clear()
        resolvedMemo.clear()
    }

    /** Only this game's overrides changed. */
    fun invalidate(owner: GameKey) {
        layeredMemo.keys.removeAll { it.owner == owner }
        resolvedMemo.keys.removeAll { it.owner == owner }
    }

    /** A new update was downloaded: its documents are read again. */
    fun updateReplaced() {
        updatedNames = lazy { sources.updated("name-index-v1.json") }
        updatedLanguage = lazy { languageDocument?.let(sources::updatedLanguage) }
        invalidate()
    }

    /** [member] is the collection member the player picked, whose record sits above its collection's. */
    fun layered(contentId: String, owner: GameKey?, member: String?): CatalogFieldLayers.Result {
        val key = MemoKey(contentId, owner, member)
        return layeredMemo.getOrPut(key) { layeredUncached(key) }
    }

    private fun layeredUncached(key: MemoKey): CatalogFieldLayers.Result {
        val contentId = key.contentId
        val member = key.member
        val baseRecord = base.optJSONObject("games")?.optJSONObject(contentId)
        val shardRecord = shardFor(contentId)?.optJSONObject("games")?.optJSONObject(contentId)
        val updateRecord = updatedRecord(contentId)
        val userRecord = sources.additions().optJSONObject("games")?.optJSONObject(contentId)
        val overrideRecord = key.owner?.let(sources::override)
        val installed = sources.installed()
        // A disk record names its game's shared record. Each layer adds its own copy of that
        // record beneath its disk records, so the disk's own fields override it in every layer.
        // A collection member's game comes first from any layer that has the member: a layer
        // that names only the collection's game (an update from before the collection) does not
        // hide which game the player picked.
        val records = listOf(overrideRecord, userRecord) +
            installed.asReversed().map { it.data.getJSONObject("games").optJSONObject(contentId) } +
            listOf(updateRecord, shardRecord, baseRecord)
        fun gameOf(record: JSONObject?) = record?.optString("game")?.takeIf { it.isNotEmpty() }
        val game = (records.map { LayeredGameCatalog.member(it, member) } + records).firstNotNullOfOrNull(::gameOf)
        // A collection's own game, whose shared fields (such as its controls) sit beneath its member's.
        val collectionGame = records.firstNotNullOfOrNull(::gameOf)?.takeIf { member != null && it != game }
        fun shared(games: JSONObject?) = game?.let { games?.optJSONObject("games")?.optJSONObject(it) }
        fun collectionShared(games: JSONObject?) = collectionGame?.let { games?.optJSONObject("games")?.optJSONObject(it) }
        // The game's description in this device's language, from a layer's language document.
        fun translated(document: JSONObject?) = language?.let { language ->
            game?.let { document?.optJSONObject("games")?.optString(it) }?.takeIf { it.isNotBlank() }
                ?.let { JSONObject().put("descriptions", JSONObject().put(language, it)) }
        }
        val updatedNames = updatedNames.value
        return CatalogFieldLayers.merge(LayeredGameCatalog.withMember(listOf(
            CatalogFieldLayers.Source(SHIPPED, collectionShared(nameIndex)),
            CatalogFieldLayers.Source(SHIPPED, shared(nameIndex)),
            CatalogFieldLayers.Source(SHIPPED, translated(shippedLanguage)),
            CatalogFieldLayers.Source(SHIPPED, baseRecord),
            CatalogFieldLayers.Source(SHIPPED, shardRecord),
            CatalogFieldLayers.Source(UPDATED, collectionShared(updatedNames)),
            CatalogFieldLayers.Source(UPDATED, shared(updatedNames)),
            CatalogFieldLayers.Source(UPDATED, translated(updatedLanguage.value)),
            CatalogFieldLayers.Source(UPDATED, updateRecord),
            CatalogFieldLayers.Source(SHIPPED, collectionShared(additionalNames)),
            CatalogFieldLayers.Source(SHIPPED, shared(additionalNames)),
            CatalogFieldLayers.Source(SHIPPED, translated(additionalLanguage)),
            CatalogFieldLayers.Source(SHIPPED, bundledAdditional(contentId)),
            *installedSources(installed, contentId, ::collectionShared, ::shared, ::translated).toTypedArray(),
            CatalogFieldLayers.Source("User catalog", userRecord),
            CatalogFieldLayers.Source("User override", overrideRecord)
        ), member)
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
    fun documents(name: String): List<JSONObject> =
        listOfNotNull(sources.shipped(name).takeIf { it.length() > 0 }, sources.updated(name)) +
            sources.installed().mapNotNull { it.data.optJSONObject("documents")?.optJSONObject(name) }

    fun resolve(contentId: String, owner: GameKey?, member: String?, fileName: String): CatalogGame =
        resolvedMemo.getOrPut(MemoKey(contentId, owner, member, fileName)) {
            resolveUncached(contentId, owner, member, fileName)
        }

    // An unedited game needs no lookup, so startup reads no shards for it.
    fun matchId(item: LibraryItem): String? {
        val current = item.contentId ?: return item.catalogId
        val first = item.catalogId ?: return current
        return if (current == first || known(current)) current else first
    }

    /** Whether any catalog has a record for [contentId]. */
    fun known(contentId: String): Boolean {
        fun has(source: JSONObject?) = source?.optJSONObject("games")?.has(contentId) == true
        return has(base) || has(shardFor(contentId)) || updatedRecord(contentId) != null || has(sources.additions()) ||
            has(sources.shippedAdditional(shardPath(contentId))) ||
            sources.installed().any { it.data.optJSONObject("games")?.has(contentId) == true }
    }

    /** Read for every library entry, so it takes the field from the highest layer instead of merging records. */
    fun setDisks(item: LibraryItem): List<String> = setDisks(item, withOverride = true)

    /** The set the catalogs give [item], without the player's own. */
    fun catalogSetDisks(item: LibraryItem): List<String> = setDisks(item, withOverride = false)

    private fun setDisks(item: LibraryItem, withOverride: Boolean): List<String> {
        val contentId = matchId(item) ?: return emptyList()
        fun disks(record: JSONObject?) = record?.optJSONArray("setDisks")
        fun game(source: JSONObject?) = source?.optJSONObject("games")?.optJSONObject(contentId)
        val array = (if (withOverride) disks(sources.override(item.key)) else null) ?: disks(game(sources.additions()))
            ?: sources.installed().asReversed().firstNotNullOfOrNull { disks(game(it.data)) }
            ?: disks(game(sources.shippedAdditional(shardPath(contentId))))
            ?: disks(updatedRecord(contentId)) ?: disks(game(shardFor(contentId))) ?: disks(game(base))
            ?: return emptyList()
        return (0 until array.length()).map(array::getString)
    }

    private fun resolveUncached(contentId: String, owner: GameKey?, member: String?, fileName: String): CatalogGame {
        val layers = layered(contentId, owner, member)
        val merged = layers.record
        val user = owner?.let(sources::override)
        val catalogTitle = merged.optString("title").takeIf { it.isNotBlank() }
        // The game's own title in the device's language, where the catalog knows one (its Japanese
        // title, Kairo#61), over a catalog's title but never over one the player or a catalog they
        // installed gave it.
        val localTitle = merged.optString("game").takeIf(String::isNotEmpty)
            ?.let { LayeredGameCatalog.localTitle(languageDocuments(), it) }
            ?.takeIf { LayeredGameCatalog.localTitleShows(layers.sourceOf("title")) }
        val title = localTitle ?: catalogTitle
            ?: fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val artwork = merged.optJSONObject("artwork")
        val controller = merged.optJSONObject("controller")
        val input = merged.optJSONObject("input")
        // Paths and URLs are checked where an image is opened or downloaded, for that game alone.
        return CatalogGame(
            contentId, schema.title(title.ifBlank { fileName }, fileName),
            LayeredGameCatalog.description(merged, layers),
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
            input?.optString("mode")?.takeIf { it in LayeredGameCatalog.INPUT_MODES },
            input?.optString("touch")?.takeIf { it in LayeredGameCatalog.INPUT_TOUCH },
            input?.optString("secondary")?.takeIf { it in LayeredGameCatalog.INPUT_SECONDARY },
            user?.keys()?.asSequence()?.toSet() ?: emptySet(),
            merged, layers, tagText(), catalogTitle.takeIf { localTitle != null })
    }

    /** This device's language documents, the downloaded update's before the shipped one's. */
    private fun languageDocuments(): List<JSONObject> = listOfNotNull(updatedLanguage.value, shippedLanguage)

    /**
     * Tags in this device's language: each catalog language document names its tags' words
     * ("tags"), the downloaded update's over the shipped ones (KairoDos#141).
     */
    private fun tagText(): (String) -> String = LayeredGameCatalog.tagText(languageDocuments())

    /**
     * The system's controller mapping from the catalog's system definition
     * ([LayeredGameCatalog.SYSTEM_DOCUMENT]): the downloaded update's when it has one, so a
     * corrected mapping reaches players without a new build (Kairo#55), else the shipped one.
     */
    fun systemController(): JSONObject =
        (sources.updated(LayeredGameCatalog.SYSTEM_DOCUMENT) ?: sources.shipped(LayeredGameCatalog.SYSTEM_DOCUMENT))
            .getJSONObject("controller")

    fun systemControllerBindings(): JSONArray = systemController().getJSONArray("bindings")

    private fun bundledAdditional(contentId: String): JSONObject? =
        sources.shippedAdditional(shardPath(contentId)).optJSONObject("games")?.optJSONObject(contentId)

    private fun installedSources(catalogs: List<InstalledCatalogs.Catalog>, contentId: String,
                                 collectionShared: (JSONObject?) -> JSONObject?,
                                 shared: (JSONObject?) -> JSONObject?, translated: (JSONObject?) -> JSONObject?
    ): List<CatalogFieldLayers.Source> =
        catalogs.flatMap { catalog ->
            val names = catalog.data.getJSONObject("nameIndex")
            listOf(CatalogFieldLayers.Source(catalog.name, collectionShared(names)),
                CatalogFieldLayers.Source(catalog.name, shared(names)),
                CatalogFieldLayers.Source(catalog.name, translated(languageDocument?.let {
                    catalog.data.optJSONObject("documents")?.optJSONObject(it) })),
                CatalogFieldLayers.Source(catalog.name, catalog.data.getJSONObject("games").optJSONObject(contentId)))
        }

    private fun readShardNames(): Set<String> {
        val manifest = sources.shipped("manifest-v1.json")
        require(manifest.optInt("schemaVersion") == 1) { "Unsupported catalog manifest" }
        val names = manifest.getJSONArray("shards")
        return (0 until names.length()).map { names.getString(it) }.filter { it.matches(SHARD_NAME) }.toSet()
    }

    private fun shardFor(contentId: String): JSONObject? {
        val name = contentId.substringAfter(':').take(2)
        if (name !in shardNames) return null
        return shardCache.getOrPut(name) { sources.shipped("shards/$name.json") }
    }

    /** A content ID's record in the downloaded update: in its shard, or the base records. */
    private fun updatedRecord(contentId: String): JSONObject? =
        sources.updated(shardPath(contentId))?.optJSONObject("games")?.optJSONObject(contentId)
            ?: sources.updated("base-v1.json")?.optJSONObject("games")?.optJSONObject(contentId)

    companion object {
        const val SHIPPED = "Shipped catalog"
        const val UPDATED = "Updated catalog"
        private val SHARD_NAME = Regex("[0-9a-f]{2}")
        private const val SHARD_CACHE = 8
        /** The shard document that holds [contentId]'s record. */
        fun shardPath(contentId: String) = "shards/${contentId.substringAfter(':').take(2)}.json"
    }
}
