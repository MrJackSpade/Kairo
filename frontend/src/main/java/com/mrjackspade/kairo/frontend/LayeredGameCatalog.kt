package com.mrjackspade.kairo.frontend

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.URI
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
    private val layers: CatalogFieldLayers.Result,
    /** A tag in the device's language (KairoDos#141); a tag no catalog translates, such as a year, as it is. */
    private val tagText: (String) -> String = { it },
    /** The catalog's own title when [title] is the game's title in the device's language (Kairo#61). */
    val catalogTitle: String? = null
) : LibraryGame {
    /** Search finds the game by the title it shows and by the catalog's own. */
    override val searchTitles: List<String> get() = listOfNotNull(title, catalogTitle)

    /** The record's tags (year, genre, players), and the heart marker. */
    override val tags: List<String> get() = (record.optJSONArray("tags")?.let { array ->
        (0 until array.length()).mapNotNull { (array.opt(it) as? String)?.takeIf(String::isNotBlank)?.let(tagText) }
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
        /**
         * Subfields of [fields] that say what a game is or where its files are rather than how it
         * runs, as `field.subfield`: no game setting changes them (KairoDos's launch folder).
         */
        val describes: Set<String> get() = emptySet()
        /** Whether a game can choose touch that moves the guest mouse to the touched point (`input.touch`). */
        val directTouch: Boolean get() = false
        /**
         * The guest keyboard's Esc keys and its Enter (Return) keys, keypad ones included, as catalog
         * bindings write them; empty for a guest without one. No shipped mapping gives START Esc and
         * SELECT Enter (ShippedControllerMappingTest).
         */
        val escapeKeys: Set<Int>
        val enterKeys: Set<Int>
        /** Whether [contentId] can fill the media [role]. */
        fun validMedia(role: String, contentId: String): Boolean
        fun validField(field: String, value: Any): Boolean
        fun validBindings(bindings: JSONArray): Boolean
        /** Subfields a layer's object replaces in lower layers when it sets one of them. */
        fun replacedSubfields(path: List<String>, record: JSONObject): Set<String> = emptySet()
        /** The title a library entry shows for the catalog's [title] and its [fileName]. */
        fun title(title: String, fileName: String): String = title
        /** A record's `artwork` references as image paths and download URLs: LaunchBox references, by default. */
        fun expandArtwork(art: JSONObject): JSONObject = LaunchBoxArtworkReferences.expand(art)
    }

    class ArtworkSource(val path: String, val url: String)

    val artworkStore = CatalogArtworkStore(context, ::validImageUrl)

    override val installedCatalogs: InstalledCatalogs by lazy {
        InstalledCatalogs(context, schema.platform, ::requireUpdateFormat) {
            synchronized(this) { invalidate() }
        }
    }
    private val parts = CatalogParts(context)
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

    private var additions = readLocal(additionsFile)
    private val overrides = GameMetadataOverrides(overridesFile, MAX_LOCAL_JSON.toInt(),
        schemaEnvelope = true)
    private val layers = CatalogLayers(schema, object : CatalogLayers.Sources {
        override fun shipped(path: String) = parts.read(path)
        override fun shippedMain(path: String) = parts.main(path)
        override fun shippedAdditional(path: String) = parts.additional(path)
        override fun updated(path: String) = synchronized(this@LayeredGameCatalog) { update.value?.document(path) }
        override fun updatedLanguage(path: String) = synchronized(this@LayeredGameCatalog) { languageUpdate.value?.document(path) }
        override fun installed(): List<InstalledCatalogs.Catalog> = installedCatalogs.catalogs()
        override fun additions() = additions
        override fun override(game: GameKey) = overrides.record(game)
    }, language)
    private fun invalidate() = layers.invalidate()
    private fun invalidate(owner: GameKey) = layers.invalidate(owner)
    private val objectFields: Map<String, Set<String>> = layers.objectFields
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
            layers.updateReplaced()
        }
        return true
    }

    @Synchronized fun sourceOf(item: LibraryItem, field: String, subfield: String? = null): String {
        val path = if (subfield == null) arrayOf(field) else arrayOf(field, subfield)
        return layered(matchId(item) ?: "", item.key, item.member).sourceOf(*path)
            ?: if (field == "title") "Filename" else "App default"
    }

    /** Where a setting would come from without the user's own change, for its Default option. */
    @Synchronized fun defaultSourceOf(item: LibraryItem, field: String, subfield: String? = null): String {
        val path = if (subfield == null) arrayOf(field) else arrayOf(field, subfield)
        return layered(matchId(item) ?: "", null, item.member).sourceOf(*path)
            ?: if (field == "title") "Filename" else "App default"
    }

    /** The game without the user's own changes, so each Default option can show its value. */
    @Synchronized fun resolveDefault(item: LibraryItem): CatalogGame =
        resolve(matchId(item) ?: "", null, item.member, item.displayName)

    /**
     * The games of [item]'s collection, in the order the player sees them: one medium holding
     * several games, which the player picks between when it starts. Empty for any other game, and
     * for a member already picked.
     */
    @Synchronized override fun collection(item: LibraryItem): List<CollectionMember> {
        if (item.member != null) return emptyList()
        val contentId = matchId(item) ?: return emptyList()
        val members = layered(contentId, item.key, null).record.optJSONObject("collection") ?: return emptyList()
        return members.keys().asSequence().sortedBy { it.toInt() }.map { member ->
            CollectionMember(member, resolve(contentId, item.key.member(member), member, item.displayName).title)
        }.toList()
    }

    /**
     * A product's own catalog document [name], such as KairoDos's launch folders, from the shipped
     * catalog, the downloaded update and each installed catalog, lowest layer first. The update
     * holds them at their own paths; installed catalogs under "documents".
     */
    @Synchronized fun documents(name: String): List<JSONObject> = layers.documents(name)

    @Synchronized override fun resolve(item: LibraryItem): CatalogGame =
        resolve(matchId(item) ?: "", item.key, item.member, item.displayName)

    /** A catalog record alone, as for a disk a game names that is not a library entry. */
    @Synchronized fun resolve(contentId: String, fileName: String): CatalogGame = resolve(contentId, null, null, fileName)

    private fun resolve(contentId: String, owner: GameKey?, member: String?, fileName: String): CatalogGame =
        layers.resolve(contentId, owner, member, fileName)

    private fun layered(contentId: String, owner: GameKey?, member: String?) = layers.layered(contentId, owner, member)

    @Synchronized override fun matchId(item: LibraryItem): String? = layers.matchId(item)

    @Synchronized override fun knows(item: LibraryItem): Boolean = matchId(item)?.let(layers::known) == true

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
    @Synchronized override fun setDisks(item: LibraryItem): List<String> = layers.setDisks(item)

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
        "setDisks" -> value is JSONArray && value.length() in 1..MAX_SET_DISKS &&
            (0 until value.length()).map { value.opt(it) }.let { ids ->
                ids.all { it is String && validId(it) } && ids.toSet().size == ids.size
            }
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

    /**
     * The system's controller mapping from the catalog's system definition (system-v2.json, which
     * binds the C and Z buttons; system-v1.json beside it is the same without them, for apps from
     * before them, whose mapping reader rejects a control it lacks): the
     * downloaded update's when it has one, so a corrected mapping reaches players without a new
     * build (Kairo#55), else the shipped one. Games' own mappings layer over it as their records do.
     */
    @Synchronized fun systemControllerBindings(): org.json.JSONArray = layers.systemControllerBindings()

    /**
     * A layer of the system's definition that a game's machine settings choose over the system's
     * mapping (MachineActivity.machineControllerLayers), such as KairoTowns's "sixButtonPad": what the
     * 6-button pad's games play with. Its `controller.layers` holds it by name.
     */
    @Synchronized fun systemControllerLayer(name: String): org.json.JSONArray =
        requireNotNull(layers.systemController().optJSONObject("layers")?.optJSONArray(name)) {
            "The system definition has no controller layer $name"
        }

    private fun readLocal(file: File): JSONObject = LocalCatalogFile.read(file, MAX_LOCAL_JSON.toInt()) ?: empty()

    /** An installed catalog's format (InstalledCatalogs). */
    private fun requireUpdateFormat(root: JSONObject) {
        require(root.optInt("schemaVersion") == 1)
        root.getJSONObject("games")
        val names = root.getJSONObject("nameIndex")
        require(names.optInt("schemaVersion") == 1)
        names.getJSONObject("games")
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
        const val SYSTEM_DOCUMENT = "system-v2.json"
        private const val MAX_LOCAL_JSON = 8L * 1024 * 1024
        /** The downloaded update's format (CatalogUpdateArchive), and how large a download may be. */
        private const val UPDATE_VERSION = CatalogUpdateArchive.UPDATE_VERSION
        private const val MAX_UPDATE_BYTES = 64L * 1024 * 1024
        internal val INPUT_MODES = setOf("auto", "keyboard", "mouse")
        internal val INPUT_TOUCH = setOf("touchpad", "direct")
        internal val INPUT_SECONDARY = setOf("keyboard", "touchpad")
        private val ART_PATH_FIELDS = setOf("boxArt", "preview")
        private val ART_URL_FIELDS = setOf("boxArtUrl", "previewUrl")
        private val ART_FIELDS = ART_PATH_FIELDS + ART_URL_FIELDS
        internal val INPUT_FIELDS = setOf("mode", "touch", "secondary")
        private val DISPLAY_FIELDS = setOf("landscapeAnchor", "portraitAnchor")
        /** The [member] of [record]'s collection, the game the player picked from it. */
        fun member(record: JSONObject?, member: String?): JSONObject? =
            member?.let { record?.optJSONObject("collection")?.optJSONObject(it) }

        /** Each layer followed by its record's collection [member]: a member sits above its collection. */
        fun withMember(sources: List<CatalogFieldLayers.Source>, member: String?): List<CatalogFieldLayers.Source> =
            sources.flatMap { source ->
                listOfNotNull(source, member(source.record, member)?.let { source.copy(record = it) })
            }

        /** The catalog language [locale] reads descriptions in besides English, by its tag or its language alone. */
        /** A tag from the first of [documents] whose "tags" names it; a tag none names (a year) as it is. */
        fun tagText(documents: List<JSONObject>): (String) -> String {
            val names = documents.mapNotNull { it.optJSONObject("tags") }
            return { tag -> names.firstNotNullOfOrNull { it.optString(tag).takeIf(String::isNotEmpty) } ?: tag }
        }

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
        internal val COMMON_OBJECT_FIELDS = mapOf(
            "artwork" to ART_FIELDS,
            // "layer" is the player's own mapping layer (ControllerDefaults.cascade); catalogs never hold one.
            "controller" to setOf("profile", "bindings", "defaults", "layer"),
            "input" to INPUT_FIELDS,
            "display" to DISPLAY_FIELDS,
            // A description per language beside the English one, by BCP 47 tag; each layer replaces a language on its own.
            "descriptions" to DESCRIPTION_LANGUAGES)
        /**
         * Whether a game's title in the device's language shows over the title from [titleSource]: over
         * the catalogs Kairo ships, never over a title the player or a catalog they installed gave it.
         */
        fun localTitleShows(titleSource: String?) = titleSource == "Shipped catalog" || titleSource == "Updated catalog"

        /** [game]'s title from the first of [documents] (language documents, newest first) whose "titles" names it. */
        fun localTitle(documents: List<JSONObject>, game: String): String? = documents.firstNotNullOfOrNull {
            it.optJSONObject("titles")?.optString(game)?.takeIf(String::isNotBlank)
        }

        // A player's own set is "setDisks" on the disk the game starts from (Kairo#134).
        private val COMMON_FIELDS = setOf("title", "description", "aliases", "tags", "heart", "hidden", "media", "setDisks") +
            COMMON_OBJECT_FIELDS.keys
        /** The most disks a game's set holds besides its own (MAX_IDS in shared tools/app_catalog.py). */
        const val MAX_SET_DISKS = 64
        private fun validTitle(value: String) = value.isNotBlank() && value.length <= 256
        private fun validDescription(value: String) = value.isNotBlank() && value.length <= 8000
        private fun empty() = JSONObject().put("schemaVersion", 1).put("games", JSONObject())
    }
}
