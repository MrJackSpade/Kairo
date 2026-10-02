package com.mrjackspade.kairo.frontend

import android.app.AlertDialog
import android.view.View

/** Common session state: pausing a machine does not hide its display or keyboard. */
class SessionNavigationState {
    var libraryVisible = true
        private set
    var userPaused = false

    fun showLibrary() { libraryVisible = true }
    fun enterGame() { libraryVisible = false }

    data class Presentation(val pauseGuest: Boolean, val showGuest: Boolean)

    fun presentation(activityVisible: Boolean, menuOpen: Boolean,
                     editing: Boolean, preparing: Boolean): Presentation {
        val blocked = libraryVisible || !activityVisible || menuOpen || editing || preparing
        return Presentation(blocked || userPaused, !blocked)
    }
}

/** Owns confirmed return-to-library and presentation transitions; native teardown stays in adapters. */
class SessionNavigationCoordinator(
    private val state: SessionNavigationState,
    private val library: LibraryScreen<*>,
    private val session: () -> SessionFlow?,
    private val hasGame: () -> Boolean,
    private val externalSession: () -> Boolean,
    private val exitExternalSession: () -> Unit,
    private val resetGestures: () -> Unit,
    private val prepareLibrary: () -> Unit,
    private val prepareGame: () -> Unit,
    private val refreshLibrary: () -> Unit,
    private val applyState: () -> Unit,
    private val focusGame: () -> Unit,
    private val endSession: (() -> Unit) -> Unit
) {
    private var returnPending = false

    /** User action; internal startup/teardown calls use showLibrary directly. */
    fun requestLibrary() {
        if (returnPending) return
        if (!hasGame()) { showLibrary(); return }
        returnPending = true
        fun cancel() {
            returnPending = false
            session()?.close()
        }
        AlertDialog.Builder(library.context)
            .setTitle("End game session?")
            .setMessage("Return to Library and stop the current game. Unsaved progress will be lost.")
            .setNegativeButton("Cancel") { _, _ -> cancel() }
            .setPositiveButton("End session") { _, _ ->
                endSession {
                    returnPending = false
                    showLibrary()
                }
            }
            .setOnCancelListener { cancel() }
            .show().also(Ui::styleDialog)
    }

    fun showLibrary() {
        resetGestures()
        if (externalSession()) { exitExternalSession(); return }
        prepareLibrary()
        library.closeActions()
        library.closeDetail()
        state.showLibrary()
        library.visibility = View.VISIBLE
        session()?.close()
        refreshLibrary()
        applyState()
    }

    fun resumeGame(): Boolean {
        if (!hasGame()) return false
        enterGame()
        return true
    }

    fun enterGame(notify: Boolean = true) {
        prepareGame()
        library.dismissSystemKeyboard()
        state.enterGame()
        library.visibility = View.GONE
        focusGame()
        if (notify) applyState()
    }
}
