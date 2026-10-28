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
    private val currentId: () -> GameKey?,
    private val title: () -> String?,
    private val storage: (GameKey) -> StateSlots,
    private val surface: () -> SurfaceView?,
    private val nativeSave: (File) -> Int,
    private val nativeLoad: (File) -> Int,
    private val stateError: (Int) -> String,
    private val message: (String) -> Unit,
    private val loaded: () -> Unit,
    private val rejectedLoad: (Int) -> Unit,
    /** Why Save state and Load state are disabled, such as a game not started from the library. */
    val unavailable: String
) {
    @Volatile var isBusy = false
        private set

    /** The open page's slots. Listing can read a user-chosen folder, so it runs on a worker. */
    @Volatile private var summaries: List<StateSlots.Summary> = emptyList()

    val available: Boolean get() = currentId() != null

    /** The GAME rows of the game flyout. */
    fun rows() = listOf(
        SettingsRow.nav("f-save", activity.getString(R.string.kairo_state_save), enabled = { available },
            disabledReason = { unavailable }) { show(true) },
        SettingsRow.nav("f-load", activity.getString(R.string.kairo_state_load), enabled = { available },
            disabledReason = { unavailable }) { show(false) })

    fun show(saving: Boolean) {
        if (isBusy) return
        val id = currentId() ?: run { message(unavailable); return }
        isBusy = true
        Thread({
            val result = runCatching {
                val store = storage(id)
                store to store.summaries()
            }
            activity.runOnUiThread {
                isBusy = false
                if (!activity.isDestroyed) result.fold({ (store, listed) ->
                    summaries = listed
                    pages.push(StateSlotPage(saving, title(), ::pageSlots, { isBusy },
                        { save(id, store, it) }, { load(id, store, it) }))
                }, { error ->
                    message(activity.getString(R.string.kairo_state_open_failed, reason(error)))
                })
            }
        }, "Kairo-save-slots").start()
    }

    private fun pageSlots() = summaries.map { slot ->
        StateSlotPage.Slot(slot.index, slot.savedAt, slot.thumbnail?.let { bytes ->
            checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
                "Slot ${slot.index} thumbnail is unreadable"
            }
        })
    }

    private fun save(id: GameKey, store: StateSlots, index: Int) {
        if (isBusy) return
        isBusy = true
        android.util.Log.i("Kairo", "save slot $index: capturing the thumbnail")
        SurfaceThumbnail.capture(surface(), handler) { thumbnail ->
            android.util.Log.i("Kairo", "save slot $index: thumbnail ${if (thumbnail == null) "none" else "captured"}")
            Thread({
                var scratch: File? = null
                var saved = false
                val result = try {
                    check(currentId() == id) { "The running game changed" }
                    val candidate = store.beginSave(index)
                    scratch = candidate
                    val code = nativeSave(candidate)
                    if (code != 0) activity.getString(R.string.kairo_state_save_failed, stateError(code))
                    else {
                        thumbnail?.let { bitmap ->
                            store.thumbnailFileIn(candidate).outputStream().use {
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                                    "Could not encode thumbnail"
                                }
                            }
                        }
                        store.commitSave(index, candidate)
                        saved = true
                        activity.getString(R.string.kairo_state_saved, index)
                    }
                } catch (error: Exception) { activity.getString(R.string.kairo_state_save_failed, reason(error)) }
                finally {
                    scratch?.let { store.abandonSave(index, it) }
                    thumbnail?.recycle()
                }
                val listed = if (saved) store.summaries() else null
                activity.runOnUiThread {
                    isBusy = false
                    if (!activity.isDestroyed) {
                        listed?.let { summaries = it }
                        message(result)
                        pages.refresh()
                    }
                }
            }, "Kairo-save-state").start()
        }
    }

    private fun load(id: GameKey, store: StateSlots, index: Int) {
        if (isBusy) return
        isBusy = true
        Thread({
            val result = runCatching {
                check(currentId() == id) { "The running game changed" }
                store.withSlot(index, nativeLoad)
            }
            activity.runOnUiThread {
                isBusy = false
                if (!activity.isDestroyed) result.fold({ code ->
                    if (code == 0) { loaded(); message(activity.getString(R.string.kairo_state_loaded, index)) }
                    else {
                        message(activity.getString(R.string.kairo_state_load_failed, stateError(code)))
                        pages.refresh()
                        rejectedLoad(code)
                    }
                }, { error ->
                    message(activity.getString(R.string.kairo_state_load_failed, reason(error)))
                    pages.refresh()
                })
            }
        }, "Kairo-load-state").start()
    }

    private fun reason(error: Throwable) = error.message ?: activity.getString(R.string.kairo_saves_storage_error)
}
