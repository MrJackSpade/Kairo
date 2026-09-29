package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.view.SurfaceView
import java.io.File

/** Shared slot picker, busy state, capture, worker, transaction, and result presentation. */
class StateSlotCoordinator(
    private val activity: Activity,
    private val handler: Handler,
    private val currentId: () -> String?,
    private val title: () -> String?,
    private val storage: (String) -> StateSlotStore,
    private val surface: () -> SurfaceView?,
    private val nativeSave: (StateSlotStore, File) -> Int,
    private val nativeLoad: (StateSlotStore.Slot) -> Int,
    private val stateError: (Int) -> String,
    private val status: (String) -> Unit,
    private val message: (String) -> Unit,
    private val loaded: () -> Unit,
    private val rejectedLoad: (Int) -> Unit,
    private val unavailable: String,
    private val saveIconRes: Int = 0
) {
    @Volatile var isBusy = false
        private set

    fun show(saving: Boolean) {
        if (isBusy) return
        val id = currentId()
        if (id == null) {
            AlertDialog.Builder(activity).setTitle(if (saving) "Save state" else "Load state")
                .setMessage(unavailable).setPositiveButton("Close", null)
                .create().also { it.show(); Ui.styleDialog(it) }
            return
        }
        try {
            val store = storage(id)
            val slots = store.slots().map { slot ->
                StateSlotDialog.Slot(slot.index, slot.savedAt,
                    slot.thumbnailFile.takeIf { slot.savedAt != null && it.isFile }
                        ?.let { BitmapFactory.decodeFile(it.path) })
            }
            StateSlotDialog.show(activity, title(), saving, slots,
                { save(id, store, it) }, { load(id, store, it) }, saveIconRes)
        } catch (error: Exception) { message("Could not open save states: ${error.message}") }
    }

    private fun save(id: String, store: StateSlotStore, index: Int) {
        if (isBusy) return
        isBusy = true
        status("Saving slot $index…")
        SurfaceThumbnail.capture(surface(), handler) { thumbnail ->
            Thread({
                var scratch: File? = null
                val result = try {
                    check(currentId() == id) { "The running game changed" }
                    val candidate = store.beginSave(index)
                    scratch = candidate
                    val code = nativeSave(store, candidate)
                    if (code != 0) "Save failed: ${stateError(code)}"
                    else {
                        store.commitSave(index, candidate, thumbnail?.let { bitmap ->
                            { file -> file.outputStream().use {
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                                    "Could not encode thumbnail"
                                }
                            }; Unit }
                        })
                        "Saved to slot $index"
                    }
                } catch (error: Exception) { "Save failed: ${error.message ?: "storage error"}" }
                finally {
                    scratch?.let { runCatching { store.abandonSave(index, it) } }
                    thumbnail?.recycle()
                }
                activity.runOnUiThread {
                    isBusy = false
                    if (!activity.isDestroyed) { status(result); message(result) }
                }
            }, "Kairo-save-state").start()
        }
    }

    private fun load(id: String, store: StateSlotStore, index: Int) {
        if (isBusy) return
        isBusy = true
        status("Loading slot $index…")
        Thread({
            val result = runCatching {
                check(currentId() == id) { "The running game changed" }
                val slot = store.slot(index)
                check(slot.savedAt != null) { "This slot is empty" }
                nativeLoad(slot)
            }
            activity.runOnUiThread {
                isBusy = false
                if (!activity.isDestroyed) result.fold({ code ->
                    if (code == 0) { loaded(); message("Loaded slot $index") }
                    else {
                        val failure = "Load failed: ${stateError(code)}"
                        status(failure); message(failure); rejectedLoad(code)
                    }
                }, { error ->
                    val failure = "Load failed: ${error.message ?: "storage error"}"
                    status(failure); message(failure)
                })
            }
        }, "Kairo-load-state").start()
    }
}
