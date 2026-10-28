package com.mrjackspade.kairo.frontend

import android.content.Context
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
    private val endSession: (() -> Unit) -> Unit,
    private val orientation: GameOrientation
) {
    private var returnPending = false

    /**
     * The game flyout's return row. A game launched by a frontend returns to the frontend and
     * the app closes; with no game loaded the row returns without asking.
     */
    fun returnRow(context: Context, appName: String): SettingsRow {
        val external = hasGame() && externalSession()
        return SettingsRow.action("f-library",
            context.getString(if (external) R.string.kairo_return_to_frontend else R.string.kairo_return_to_library),
            confirm = if (!hasGame()) null else Confirm(
                if (external) context.getString(R.string.kairo_return_to_frontend_confirm, appName)
                else context.getString(R.string.kairo_return_to_library_confirm), context.getString(R.string.kairo_end_game),
                title = context.getString(if (external) R.string.kairo_return_to_frontend_title else R.string.kairo_return_to_library_title),
                run = ::returnToLibrary),
            run = ::returnToLibrary)
    }

    /** Confirmed user action; internal startup and teardown call showLibrary directly. */
    fun returnToLibrary() {
        if (returnPending) return
        if (!hasGame()) { showLibrary(); return }
        returnPending = true
        // Read before endSession, which may reset what externalSession reports.
        if (externalSession()) {
            // Exiting replaces teardown and closes the app, so returnPending stays set.
            resetGestures()
            exitExternalSession()
        } else endSession {
            returnPending = false
            showLibrary()
        }
    }

    fun showLibrary() {
        resetGestures()
        if (externalSession()) { exitExternalSession(); return }
        prepareLibrary()
        state.showLibrary()
        orientation.leaveGame()
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
        orientation.enterGame()
        library.visibility = View.GONE
        session()?.reset()
        focusGame()
        if (notify) applyState()
    }
}
