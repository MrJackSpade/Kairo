package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout

/** What a two-screen device's lower screen shows while the library is open, built for that screen. */
interface LowerLibraryView {
    val view: View
    /** Where the D-pad focus goes on this screen (DualScreenFocus). */
    val focus: ForwardedFocusRegion
    fun close()
}

/**
 * The library on a two-screen device's lower screen (Kairo#43). While the player browses, it shows
 * the game page of the selected game, as the upper screen shows it once a game is opened: Play
 * starts the game, and Game settings opens it. Once the game is open on the upper screen, the
 * lower screen shows its Game settings, and the upper page leaves its Game settings button out.
 * Back on those settings closes the game on the upper screen too.
 */
class LowerLibraryController<T : LibraryItem>(
    private val activity: Activity,
    private val catalog: LibraryCatalog,
    private val metadata: (T) -> LibraryGame,
    private val play: (T) -> Unit,
    private val viewScreenshot: (T) -> Unit,
    private val commandLaunch: ((T) -> Unit)?,
    /** Opens [T]'s game page on the upper screen. */
    private val open: (T) -> Unit,
    /** Closes the game page on the upper screen. */
    private val closeOpened: () -> Unit,
    private val settingsPage: (T) -> Page,
    /** The lower screen started or stopped showing settings, which the upper game page follows. */
    private val settingsMoved: () -> Unit
) {
    private var browsing: T? = null
    private var opened: T? = null
    private val screens = ArrayList<Screen>()

    /** Whether a lower screen shows the open game's settings. */
    val showsSettings: Boolean get() = opened != null && screens.isNotEmpty()

    /** A view for a lower screen that opened, built with its [context]. */
    fun create(context: Context): LowerLibraryView = Screen(context).also {
        screens += it
        it.render()
        settingsMoved()
    }

    /** The library's selected game, or null. */
    fun browse(entry: T?) {
        browsing = entry
        screens.forEach { it.render() }
    }

    /** The game open on the upper screen, or null once it closed. */
    fun opened(entry: T?) {
        if (opened?.id == entry?.id && opened != null) { opened = entry; return }
        opened = entry
        screens.forEach { it.render() }
        settingsMoved()
    }

    /** The newest lower screen's focus region, or null without a lower screen. */
    val focus: ForwardedFocusRegion? get() = screens.lastOrNull()?.focus

    /** A setting changed somewhere: the lower settings show its new value. */
    fun refresh() = screens.forEach { it.refresh() }

    private inner class Screen(context: Context) : LowerLibraryView {
        override val view = FrameLayout(context).testId("secondary.library").apply { setBackgroundColor(Ui.BG) }
        /** The selected game's page while browsing, else the open game's settings. */
        private val pages = PageStack(activity, view, context).apply { focusAway = true }
        override val focus: ForwardedFocusRegion get() = pages
        private var detail: GameDetailPage<T>? = null
        private var settingsFor: String? = null
        /** The stack closes because this screen changed what it shows, not by the player's Back. */
        private var replacing = false

        init {
            pages.menuKey = {}
            pages.onActiveChanged { active ->
                if (!active && !replacing && opened != null) closeOpened()
            }
        }

        private fun clear() {
            replacing = true
            pages.closeAll()
            replacing = false
            detail = null
            settingsFor = null
        }

        fun render() {
            val game = opened
            if (game != null) {
                if (settingsFor != game.id) {
                    clear()
                    settingsFor = game.id
                    pages.push(settingsPage(game))
                }
                return
            }
            val entry = browsing
            if (entry == null) { clear(); return }
            val page = detail ?: GameDetailPage(catalog, play, viewScreenshot, { open(it) }, metadata, commandLaunch,
                libraryButton = false, backElsewhere = { pages.crossEdge("up") }) {}.also {
                clear()
                detail = it
                pages.push(it)
            }
            page.show(entry)
        }

        fun refresh() {
            pages.refresh()
            browsing?.takeIf { opened == null }?.let { entry -> detail?.takeIf { it.entryId == entry.id }?.show(entry) }
        }

        override fun close() {
            screens.remove(this)
            clear()
            settingsMoved()
        }
    }
}
