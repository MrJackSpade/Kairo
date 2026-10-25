package com.mrjackspade.kairo.frontend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.concurrent.Executors

/**
 * A game's details: screenshot, box art, title and tags, Play and Game settings, description and
 * file. The game page over the library shows one ([GameDetailPage]); on a two-screen device the
 * lower screen shows one for the game the library has selected (LowerLibraryScreen). Artwork
 * loads from approved app assets.
 */
class GameDetailView<T : LibraryItem>(
    context: Context,
    private val catalog: LibraryCatalog,
    private val play: (T) -> Unit,
    private val viewScreenshot: (T) -> Unit,
    private val settings: (T) -> Unit,
    private val metadata: (T) -> LibraryGame,
    private val commandLaunch: ((T) -> Unit)? = null,
    /** The Library back button's action; null for a view without one. */
    back: (() -> Unit)? = null
) : FrameLayout(context) {
    private val imageExecutor = Executors.newSingleThreadExecutor()
    private var imageGeneration = 0
    private var closed = false
    var current: T? = null
        private set
    /** Called after [show], with whether the same game was shown again. */
    var shown: (refresh: Boolean) -> Unit = {}

    val scrollView = ScrollView(context).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    private val body = MaxWidthColumn(context, dp(840)).apply { setPadding(dp(20), dp(12), dp(20), dp(28)) }
    val backButton: LinearLayout? = back?.let { action ->
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            contentDescription = "Back to game library"
            setPadding(dp(4), 0, dp(14), 0)
            background = Ui.focusable(context, Color.TRANSPARENT, null)
            setOnClickListener { action() }
            addView(Ui.icon(context, R.drawable.ic_back, Ui.ACCENT_SOFT, 20))
            addView(Ui.text(context, "Library", Ui.BODY, Ui.ACCENT_SOFT).apply { setPadding(dp(4), 0, 0, 0) })
        }.testId("back")
    }
    private val hero = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    val imagePanel = FrameLayout(context).apply {
        background = Ui.focusable(context, Ui.SURFACE, null)
        setPadding(dp(3), dp(3), dp(3), dp(3))
        contentDescription = "View screenshot"
        setOnClickListener { current?.let(viewScreenshot) }
    }.testId("image")
    private val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        visibility = View.GONE
        contentDescription = "Game screenshot"
    }
    private val imageFallback = Ui.text(context, "No screenshot available", Ui.SECONDARY, Ui.TEXT_MUTED).apply {
        gravity = Gravity.CENTER
    }
    private val right = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), 0, 0, 0)
    }
    private val heading = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val cover = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = View.GONE
        contentDescription = "Box art"
        background = Ui.rounded(context, Ui.RAISED, 4)
        clipToOutline = true
    }
    private val title = Ui.text(context, "", Ui.TITLE, bold = true).apply {
        maxLines = 3
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val tags = WrapLayout(context, dp(6)).apply { alignEnd = false }
    val playButton: LinearLayout = Ui.primaryButton(context, "Play", R.drawable.ic_play) { playSelected() }.testId("play")
    val commandButton: FrameLayout = Ui.iconButton(context, R.drawable.ic_terminal, "DOS prompt or run program") {
        launchCommand()
    }.apply {
        visibility = if (commandLaunch == null) View.GONE else View.VISIBLE
        background = Ui.focusable(context, Color.TRANSPARENT, Ui.LINE)
    }.testId("command")
    val settingsButton: LinearLayout = Ui.secondaryButton(context, "Game settings", R.drawable.ic_tune) {
        current?.let(settings)
    }.testId("settings")
    private val launchRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val unavailable = Ui.text(context, "", Ui.SECONDARY, Ui.DANGER).apply {
        visibility = View.GONE
        setPadding(0, dp(16), 0, 0)
    }
    private val description = Ui.text(context, "", Ui.BODY, Ui.TEXT_BODY).apply { setLineSpacing(dp(8).toFloat(), 1f) }
    private val file = Ui.text(context, "", Ui.LABEL, Ui.TEXT_FAINT)

    /** Whether Game settings shows here; not while the other screen shows the settings. */
    var settingsShown: Boolean = true
        set(value) {
            field = value
            settingsButton.visibility = if (value) View.VISIBLE else View.GONE
        }

    init {
        setBackgroundColor(Ui.BG)
        addView(scrollView, LayoutParams(-1, -1))
        scrollView.addView(FrameLayout(context).apply {
            addView(body, LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL))
        })
        backButton?.let { body.addView(it, LinearLayout.LayoutParams(-2, dp(44))) }
        body.addView(hero, LinearLayout.LayoutParams(-1, dp(240)).apply { if (backButton != null) topMargin = dp(12) })
        hero.addView(imagePanel, LinearLayout.LayoutParams(0, -1, 1.15f))
        imagePanel.addView(image, LayoutParams(-1, -1))
        imagePanel.addView(imageFallback, LayoutParams(-1, -1))
        hero.addView(right, LinearLayout.LayoutParams(0, -1, 0.85f))
        heading.addView(cover, LinearLayout.LayoutParams(dp(60), dp(80)).apply { marginEnd = dp(12) })
        val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(title)
        titles.addView(tags, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        heading.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        right.addView(heading, LinearLayout.LayoutParams(-1, 0, 1f))
        launchRow.addView(playButton, LinearLayout.LayoutParams(0, -1, 2f))
        launchRow.addView(commandButton, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(8) })
        right.addView(launchRow, LinearLayout.LayoutParams(-1, dp(52)))
        right.addView(settingsButton, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        body.addView(unavailable)
        body.addView(description, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        body.addView(file, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        addOnLayoutChangeListener { _, left, top, rightEdge, bottom, _, _, _, _ ->
            updateHeroLayout(bottom - top > rightEdge - left)
        }
        updateHeroLayout(context.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT)
    }

    private var portrait: Boolean? = null

    private fun updateHeroLayout(portrait: Boolean) {
        if (this.portrait == portrait) return
        this.portrait = portrait
        hero.orientation = if (portrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        hero.layoutParams = (hero.layoutParams as LinearLayout.LayoutParams).apply {
            height = if (portrait) -2 else dp(240)
        }
        imagePanel.layoutParams = if (portrait) LinearLayout.LayoutParams(-1, dp(220))
            else LinearLayout.LayoutParams(0, -1, 1.15f)
        right.setPadding(if (portrait) 0 else dp(18), if (portrait) dp(18) else 0, 0, 0)
        right.layoutParams = if (portrait) LinearLayout.LayoutParams(-1, -2)
            else LinearLayout.LayoutParams(0, -1, 0.85f)
        heading.layoutParams = LinearLayout.LayoutParams(-1, if (portrait) -2 else 0,
            if (portrait) 0f else 1f)
        launchRow.layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply {
            if (portrait) topMargin = dp(18)
        }
    }

    fun show(entry: T) {
        // A library rescan or artwork refresh re-shows the open game; keep the user's place.
        val refresh = current?.id == entry.id
        current = entry
        val game = metadata(entry)
        title.text = game.title
        tags.removeAllViews()
        val labels = listOf(entry.mediaLabel) + game.tags +
            ((variantLabel(entry.path) ?: entry.zipEntry?.let(::variantLabel))?.split("  ·  ") ?: emptyList())
        labels.forEach { tags.addView(Ui.chip(context, it, Ui.TEXT_MUTED).apply {
            background = Ui.rounded(context, Color.TRANSPARENT, 4, Ui.LINE)
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }) }
        description.text = game.description ?: "No description available yet."
        file.text = "File: " + fileLabel(entry)
        commandButton.isEnabled = entry.playable
        commandButton.alpha = if (entry.playable) 1f else 0.4f
        playButton.isEnabled = entry.playable
        playButton.alpha = if (entry.playable) 1f else 0.4f
        unavailable.text = if (entry.playable) "" else
            "Can’t play: ${entry.error ?: "this file has not been checked yet"}. Fix the file, then Refresh from the library menu."
        unavailable.visibility = if (entry.playable) View.GONE else View.VISIBLE
        image.setImageDrawable(null)
        image.visibility = View.GONE
        cover.setImageDrawable(null)
        cover.visibility = View.GONE
        imageFallback.visibility = View.VISIBLE
        imageFallback.text = if (game.preview == null) "No screenshot available" else "Loading screenshot…"
        imagePanel.isEnabled = game.preview != null
        if (!refresh) scrollView.scrollTo(0, 0)
        shown(refresh)

        val generation = ++imageGeneration
        loadImage(game.boxArt, generation, 2) { bitmap ->
            cover.setImageBitmap(bitmap)
            cover.visibility = View.VISIBLE
        }
        loadImage(game.preview, generation, 1, { imageFallback.text = "Screenshot unavailable" }) { bitmap ->
            image.setImageBitmap(bitmap)
            image.visibility = View.VISIBLE
            imageFallback.visibility = View.GONE
        }
    }

    private fun loadImage(path: String?, generation: Int, sampleSize: Int,
                          failed: () -> Unit = {}, loaded: (Bitmap) -> Unit) {
        if (path == null) return
        imageExecutor.execute {
            val bitmap = try {
                catalog.openArtwork(path).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    })
                }
            } catch (_: Exception) { null }
            post {
                if (generation == imageGeneration && !closed) {
                    if (bitmap == null) failed() else loaded(bitmap)
                }
            }
        }
    }

    fun refreshArtwork() { current?.let(::show) }

    private fun playSelected() { current?.takeIf { it.playable }?.let(play) }

    private fun launchCommand() {
        val action = commandLaunch ?: return
        current?.takeIf { it.playable }?.let(action)
    }

    /** Stops loading artwork; the view is not shown again. */
    fun close() {
        closed = true
        imageGeneration++
        imageExecutor.shutdownNow()
    }

    private fun dp(value: Int) = Ui.dp(context, value)
}

/** The game page over the library. */
class GameDetailPage<T : LibraryItem>(
    private val catalog: LibraryCatalog,
    private val play: (T) -> Unit,
    private val viewScreenshot: (T) -> Unit,
    private val settings: (T) -> Unit,
    private val metadata: (T) -> LibraryGame,
    private val commandLaunch: ((T) -> Unit)? = null,
    /** Whether the other screen shows the game's settings, so this page has no Game settings. */
    private val settingsElsewhere: () -> Boolean = { false },
    private val closed: (GameDetailPage<T>) -> Unit
) : Page() {
    override val testName: String get() = "Game"
    private lateinit var details: GameDetailView<T>
    override val scroll: ScrollView get() = details.scrollView
    val current: T? get() = details.current
    val entryId: String? get() = current?.id

    override fun createView(): View {
        details = GameDetailView(context, catalog, play, viewScreenshot, settings, metadata, commandLaunch) { stack.back() }
        details.shown = { refresh -> renav(first = !refresh) }
        return details
    }

    fun show(entry: T) {
        details.settingsShown = !settingsElsewhere()
        details.show(entry)
    }

    /** The other screen started or stopped showing the game's settings. */
    fun settingsMoved() {
        details.settingsShown = !settingsElsewhere()
        renav()
    }

    fun refreshArtwork() = details.refreshArtwork()

    /** Back, screenshot, Play, command, Game settings. Play leaves the order when it can't run. */
    override fun navItems(): List<NavItem> {
        val playable = current?.playable == true
        val items = listOfNotNull(
            details.backButton?.let { it to "back" },
            details.imagePanel.takeIf { it.isEnabled }?.let { it to "image" },
            details.playButton.takeIf { playable }?.let { it to "play" },
            details.commandButton.takeIf { playable && commandLaunch != null }?.let { it to "command" },
            details.settingsButton.takeIf { details.settingsShown }?.let { it to "settings" })
        return items.mapIndexed { index, (view, id) -> NavItem(id, view, index, 0.5f, { view.performClick() }) }
    }

    override fun initialFocus(items: List<NavItem>) =
        if (current?.playable == true) "play" else items.firstOrNull { it.id == "settings" }?.id ?: "back"

    /** The page reads top to bottom, so Left and Up go back and Right and Down go forward. */
    override fun move(direction: String) = super.move(when (direction) {
        "left" -> "up"
        "right" -> "down"
        else -> direction
    })

    override fun onRemoved() {
        details.close()
        closed(this)
    }
}
