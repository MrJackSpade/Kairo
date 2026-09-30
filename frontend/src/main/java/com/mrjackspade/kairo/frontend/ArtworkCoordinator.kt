package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Shared viewer, local image selection, override persistence flow, and reset. */
class ArtworkCoordinator<Game : Any>(
    private val activity: Activity,
    private val store: CatalogArtworkStore,
    private val contentId: (Game) -> String?,
    private val resolve: (Game, String) -> Record,
    private val validPath: (String) -> Boolean,
    private val saveOverride: (String, String, String?) -> Unit,
    private val changed: () -> Unit,
    private val settings: (Game) -> Unit,
    private val message: (String) -> Unit,
    private val validImageUrl: (String) -> Boolean = { false }
) {
    data class Record(val path: String?, val largerUrl: String? = null)
    private data class Selection<Game>(val id: String, val kind: String, val game: Game?)
    private var selection: Selection<Game>? = null

    fun saveInstanceState(state: Bundle) {
        selection?.let {
            state.putString("artwork_selection_id", it.id)
            state.putString("artwork_selection_kind", it.kind)
        }
    }

    fun restoreInstanceState(state: Bundle?) {
        val id = state?.getString("artwork_selection_id") ?: return
        val kind = state.getString("artwork_selection_kind") ?: return
        if (kind in setOf("boxArt", "preview")) selection = Selection(id, kind, null)
    }

    fun edit(game: Game, kind: String) {
        requireKind(kind)
        if (contentId(game) == null) {
            message("This file needs a successful hash before settings can be saved")
            return
        }
        val label = label(kind)
        ArtworkOverrideEditor.show(activity, ArtworkOverrideEditor.Options(
            title = label,
            resetLabel = "Reset $label",
            onReset = { save(game, kind, null) },
            onCancel = { settings(game) },
            onChoose = {
                selection = Selection(contentId(game)!!, kind, game)
                if (!DocumentPicker.open(activity, REQUEST_IMAGE, "image/*", message)) {
                    selection = null
                    settings(game)
                }
            }))
    }

    fun handleActivityResult(request: Int, result: Int, data: Intent?): Boolean {
        if (request != REQUEST_IMAGE) return false
        val pending = selection.also { selection = null } ?: return true
        val uri = DocumentPicker.selectedUri(result, data)
        if (uri == null) {
            pending.game?.let(settings)
            return true
        }
        // Image validation and encoding stay off the UI thread.
        Thread({
            val imported = runCatching {
                activity.contentResolver.openInputStream(uri)?.use(store::importLocal)
                    ?: error("Could not open selected image")
            }
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) imported.fold(
                    { save(pending.id, pending.kind, it, pending.game) },
                    { message(it.message ?: "Could not import image"); pending.game?.let(settings) })
            }
        }, "Kairo-artwork-import").start()
        return true
    }

    private fun save(game: Game, kind: String, path: String?) {
        val id = contentId(game) ?: run { message("Hash this game first"); return }
        save(id, kind, path, game)
    }

    private fun save(id: String, kind: String, path: String?, game: Game?) {
        try {
            require(path == null || validPath(path)) { "Invalid artwork path" }
            saveOverride(id, kind, path)
            changed()
            message(if (path == null) "Artwork reset" else "Artwork saved")
        } catch (error: Exception) {
            message(error.message ?: "Could not save artwork")
        }
        game?.let(settings)
    }

    fun view(game: Game, kind: String = "preview", returnToSettings: Boolean = false) {
        requireKind(kind)
        val record = resolve(game, kind)
        val path = record.path ?: run { message("No ${label(kind).lowercase()} available"); return }
        val bitmap = runCatching { store.open(path).use { ArtworkImages.decode(ArtworkImages.read(it)) } }
            .getOrNull() ?: run { message("Image unavailable"); return }
        val view = ImageView(activity).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val url = record.largerUrl?.takeIf(validImageUrl)
        val hint = TextView(activity).apply {
            text = if (url == null) "" else "Tap image to load a larger version"
            setPadding(dp(16), 0, dp(16), dp(12))
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(view)
            addView(hint)
        }
        val dialog = AlertDialog.Builder(activity).setView(content)
            .setPositiveButton("Done") { _, _ -> if (returnToSettings) settings(game) }
            .create().also { it.show(); Ui.styleDialog(it) }
        var displayed = bitmap
        dialog.setOnDismissListener {
            view.setImageDrawable(null)
            displayed.recycle()
        }
        if (url != null) view.setOnClickListener {
            view.isEnabled = false
            hint.text = "Loading larger image…"
            Thread({
                val larger = runCatching { ArtworkImages.fetch(url, validImageUrl) }.getOrNull()
                activity.runOnUiThread {
                    if (dialog.isShowing && !activity.isDestroyed) {
                        if (larger == null) {
                            hint.text = "Could not load larger image"
                            view.isEnabled = true
                        } else {
                            view.setImageBitmap(larger)
                            displayed.recycle()
                            displayed = larger
                            hint.text = "Larger image loaded"
                        }
                    } else larger?.recycle()
                }
            }, "Kairo-artwork-preview").start()
        }
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun label(kind: String) = if (kind == "preview") "Screenshot" else "Box art"
    private fun requireKind(kind: String) = require(kind in setOf("boxArt", "preview"))

    companion object { private const val REQUEST_IMAGE = 6106 }
}
