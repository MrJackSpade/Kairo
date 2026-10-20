package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Shared viewer, local image selection, override persistence, and reset for box art and screenshots. */
class ArtworkCoordinator<Game : Any>(
    private val activity: Activity,
    private val pages: PageStack,
    private val store: CatalogArtworkStore,
    private val key: (Game) -> GameKey,
    private val resolve: (Game, String) -> Record,
    private val validPath: (String) -> Boolean,
    private val saveOverride: (GameKey, String, String?) -> Unit,
    /** A game's artwork override changed. */
    private val changed: (GameKey) -> Unit,
    private val title: (Game) -> String,
    private val message: (String) -> Unit,
    private val validImageUrl: (String) -> Boolean = { false }
) {
    /** [source] names the catalog layer the image comes from; "User override" means Custom. */
    data class Record(val path: String?, val largerUrl: String? = null, val source: String? = null)
    private data class Selection(val game: GameKey, val kind: String)
    private var selection: Selection? = null

    fun saveInstanceState(state: Bundle) {
        selection?.let {
            state.putString("artwork_selection_key", it.game.path)
            state.putString("artwork_selection_kind", it.kind)
        }
    }

    fun restoreInstanceState(state: Bundle?) {
        val game = state?.getString("artwork_selection_key") ?: return
        val kind = state.getString("artwork_selection_kind") ?: return
        if (kind in setOf("boxArt", "preview")) selection = Selection(GameKey(game), kind)
    }

    /**
     * Box art or Screenshot on Game settings. The tray offers View when there is an image,
     * Reset when it is the user's own, and Choose image… or Replace….
     */
    fun row(game: Game, kind: String, enabled: () -> Boolean): SettingsRow {
        requireKind(kind)
        fun record() = resolve(game, kind)
        fun custom() = record().source == "User override"
        return SettingsRow.action(kind, label(kind),
            value = { when {
                custom() -> GameSource.CUSTOM
                record().path != null -> GameSource.label(record().source ?: GameSource.BUILT_IN)
                else -> "None"
            } },
            tone = { when {
                custom() -> ValueTone.CUSTOM
                record().path != null -> ValueTone.CURRENT
                else -> ValueTone.EMPTY
            } },
            enabled = enabled, disabledReason = { GameSettingsRows.NO_HASH },
            tray = {
                val available = record().path != null
                TraySpec(buttons = listOfNotNull(
                    if (available) TrayButton("View") { view(game, kind) } else null,
                    if (custom()) TrayButton("Reset") { save(game, kind, null) } else null,
                    TrayButton(if (available) "Replace…" else "Choose image…", ButtonTone.PRIMARY) {
                        choose(game, kind)
                    }))
            })
    }

    private fun choose(game: Game, kind: String) {
        selection = Selection(key(game), kind)
        if (!DocumentPicker.open(activity, REQUEST_IMAGE, "image/*", message)) selection = null
    }

    fun handleActivityResult(request: Int, result: Int, data: Intent?): Boolean {
        if (request != REQUEST_IMAGE) return false
        val pending = selection.also { selection = null } ?: return true
        val uri = DocumentPicker.selectedUri(result, data) ?: return true
        // Image validation and encoding stay off the UI thread.
        Thread({
            val imported = runCatching {
                activity.contentResolver.openInputStream(uri)?.use(store::importLocal)
                    ?: error("Could not open selected image")
            }
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) imported.fold(
                    { save(pending.game, pending.kind, it) },
                    { message(it.message ?: "Could not import image") })
            }
        }, "Kairo-artwork-import").start()
        return true
    }

    private fun save(game: Game, kind: String, path: String?) = save(key(game), kind, path)

    private fun save(game: GameKey, kind: String, path: String?) {
        try {
            require(path == null || validPath(path)) { "Invalid artwork path" }
            saveOverride(game, kind, path)
            changed(game)
            message(if (path == null) "Artwork reset" else "Artwork saved")
        } catch (error: Exception) {
            message(error.message ?: "Could not save artwork")
        }
        pages.refresh()
    }

    /** The full-size image page. A tap loads a larger version when the catalog has one. */
    fun view(game: Game, kind: String = "preview") {
        requireKind(kind)
        val record = resolve(game, kind)
        val path = record.path ?: run { message("No ${label(kind).lowercase()} available"); return }
        val bitmap = runCatching { store.open(path).use { ArtworkImages.decode(ArtworkImages.read(it)) } }
            .getOrNull() ?: run { message("Image unavailable"); return }
        val url = record.largerUrl?.takeIf(validImageUrl)
        var loading = false
        pages.push(ImagePage(label(kind), title(game), bitmap,
            if (url == null) null else "Tap image to load a larger version",
            url?.let { address -> { page: ImagePage ->
                if (!loading) {
                    loading = true
                    page.setHint("Loading larger image…")
                    Thread({
                        val larger = runCatching { ArtworkImages.fetch(address, validImageUrl) }.getOrNull()
                        activity.runOnUiThread {
                            if (page.inStack && !activity.isDestroyed) {
                                if (larger == null) {
                                    page.setHint("Could not load larger image")
                                    loading = false
                                } else {
                                    page.setImage(larger)
                                    page.setHint("Larger image loaded")
                                }
                            } else larger?.recycle()
                        }
                    }, "Kairo-artwork-preview").start()
                }
            } }))
    }

    private fun label(kind: String) = if (kind == "preview") "Screenshot" else "Box art"
    private fun requireKind(kind: String) = require(kind in setOf("boxArt", "preview"))

    companion object { private const val REQUEST_IMAGE = 6106 }
}
