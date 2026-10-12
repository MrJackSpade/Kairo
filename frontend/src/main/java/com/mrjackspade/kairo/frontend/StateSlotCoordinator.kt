package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.view.SurfaceView
import java.io.File

/** Shared slot pages, busy state, capture, worker, transaction, and result presentation. */
class StateSlotCoordinator(
    private val activity: Activity,
    private val pages: PageStack,
    private val handler: Handler,
    private val currentId: () -> String?,
    private val title: () -> String?,
    private val storage: (String) -> StateSlotStore,
    private val surface: () -> SurfaceView?,
    private val nativeSave: (StateSlotStore, File) -> Int,
    private val nativeLoad: (StateSlotStore.Slot) -> Int,
    private val stateError: (Int) -> String,
    private val message: (String) -> Unit,
    private val loaded: () -> Unit,
    private val rejectedLoad: (Int) -> Unit,
    /** Why Save state and Load state are disabled, such as a game not started from the library. */
    val unavailable: String
) {
    @Volatile var isBusy = false
        private set

    val available: Boolean get() = currentId() != null

    /** The GAME rows of the game flyout. */
    fun rows() = listOf(
        SettingsRow.nav("f-save", "Save state", enabled = { available }, disabledReason = { unavailable }) { show(true) },
        SettingsRow.nav("f-load", "Load state", enabled = { available }, disabledReason = { unavailable }) { show(false) })

    fun show(saving: Boolean) {
        val id = currentId() ?: run { message(unavailable); return }
        try {
            val store = storage(id)
            pages.push(StateSlotPage(saving, title(), {
                store.slots().map { slot ->
                    StateSlotPage.Slot(slot.index, slot.savedAt,
                        slot.thumbnailFile.takeIf { slot.savedAt != null && it.isFile }
                            ?.let { BitmapFactory.decodeFile(it.path) })
                }
            }, { isBusy }, { save(id, store, it) }, { load(id, store, it) }))
        } catch (error: Exception) { message("Could not open save states: ${error.message}") }
    }

    private fun save(id: String, store: StateSlotStore, index: Int) {
        if (isBusy) return
        isBusy = true
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
                    if (!activity.isDestroyed) {
                        message(result)
                        pages.refresh()
                    }
                }
            }, "Kairo-save-state").start()
        }
    }

    private fun load(id: String, store: StateSlotStore, index: Int) {
        if (isBusy) return
        isBusy = true
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
                        message("Load failed: ${stateError(code)}")
                        pages.refresh()
                        rejectedLoad(code)
                    }
                }, { error ->
                    message("Load failed: ${error.message ?: "storage error"}")
                    pages.refresh()
                })
            }
        }, "Kairo-load-state").start()
    }
}
