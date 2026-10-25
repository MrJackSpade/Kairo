package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout

/** What a two-screen device's lower screen shows while the library is open, built for that screen. */
interface LowerLibraryView {
    val view: View
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

    /** A setting changed somewhere: the lower settings show its new value. */
    fun refresh() = screens.forEach { it.refresh() }

    private inner class Screen(context: Context) : LowerLibraryView {
        override val view = FrameLayout(context).testId("secondary.library").apply { setBackgroundColor(Ui.BG) }
        private val details = GameDetailView(context, catalog, play, viewScreenshot, { entry -> open(entry) }, metadata, commandLaunch)
        private val settingsHost = FrameLayout(context)
        private val settings = PageStack(activity, settingsHost, context)
        private var settingsFor: String? = null
        /** The stack closes because this screen changed what it shows, not by the player's Back. */
        private var replacing = false

        init {
            view.addView(details, FrameLayout.LayoutParams(-1, -1))
            view.addView(settingsHost, FrameLayout.LayoutParams(-1, -1))
            settings.menuKey = {}
            settings.onActiveChanged { active ->
                if (!active && !replacing && opened != null) closeOpened()
            }
        }

        fun render() {
            val game = opened
            if (game != null) {
                details.visibility = View.GONE
                settingsHost.visibility = View.VISIBLE
                if (settingsFor != game.id) {
                    replacing = true
                    settings.closeAll()
                    replacing = false
                    settingsFor = game.id
                    settings.push(settingsPage(game))
                }
                return
            }
            if (settingsFor != null) {
                replacing = true
                settings.closeAll()
                replacing = false
                settingsFor = null
            }
            settingsHost.visibility = View.GONE
            val entry = browsing
            details.visibility = if (entry == null) View.INVISIBLE else View.VISIBLE
            if (entry != null) details.show(entry)
        }

        fun refresh() {
            settings.refresh()
            browsing?.takeIf { opened == null }?.let { if (details.current?.id == it.id) details.show(it) }
        }

        override fun close() {
            screens.remove(this)
            replacing = true
            settings.closeAll()
            details.close()
            settingsMoved()
        }
    }
}
