package com.mrjackspade.kairo.frontend

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

/** The game page over the library. Artwork loads from approved app assets. */
class GameDetailPage<T : LibraryItem>(
    private val catalog: LibraryCatalog,
    private val play: (T) -> Unit,
    private val viewScreenshot: (T) -> Unit,
    private val settings: (T) -> Unit,
    private val metadata: (T) -> LibraryGame,
    private val commandLaunch: ((T) -> Unit)? = null,
    private val closed: (GameDetailPage<T>) -> Unit
) : Page() {
    override val testName: String get() = "Game"
    private lateinit var imagePanel: FrameLayout
    private lateinit var image: ImageView
    private lateinit var imageFallback: android.widget.TextView
    private lateinit var backButton: LinearLayout
    private lateinit var cover: ImageView
    private lateinit var title: android.widget.TextView
    private lateinit var tags: WrapLayout
    private lateinit var description: android.widget.TextView
    private lateinit var playButton: LinearLayout
    private lateinit var launchRow: LinearLayout
    private lateinit var commandButton: FrameLayout
    private lateinit var settingsButton: LinearLayout
    private lateinit var unavailable: android.widget.TextView
    private lateinit var file: android.widget.TextView
    private lateinit var scrollView: ScrollView
    private lateinit var hero: LinearLayout
    private lateinit var right: LinearLayout
    private lateinit var heading: LinearLayout
    private val imageExecutor = Executors.newSingleThreadExecutor()
    var current: T? = null
        private set
    private var imageGeneration = 0
    override val scroll: ScrollView get() = scrollView

    val entryId: String? get() = current?.id

    override fun createView(): View {
        val root = FrameLayout(context).apply { setBackgroundColor(Ui.BG) }
        scrollView = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        root.addView(scrollView, FrameLayout.LayoutParams(-1, -1))
        val body = MaxWidthColumn(context, dp(840)).apply { setPadding(dp(20), dp(12), dp(20), dp(28)) }
        scrollView.addView(FrameLayout(context).apply {
            addView(body, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL))
        })

        backButton = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            contentDescription = "Back to game library"
            setPadding(dp(4), 0, dp(14), 0)
            background = Ui.focusable(context, Color.TRANSPARENT, null)
            setOnClickListener { stack.back() }
            addView(Ui.icon(context, R.drawable.ic_back, Ui.ACCENT_SOFT, 20))
            addView(Ui.text(context, "Library", Ui.BODY, Ui.ACCENT_SOFT).apply { setPadding(dp(4), 0, 0, 0) })
        }
        body.addView(backButton, LinearLayout.LayoutParams(-2, dp(44)))

        hero = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(hero, LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(12) })

        imagePanel = FrameLayout(context).apply {
            background = Ui.focusable(context, Ui.SURFACE, null)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            contentDescription = "View screenshot"
            setOnClickListener { current?.let(viewScreenshot) }
        }
        hero.addView(imagePanel, LinearLayout.LayoutParams(0, -1, 1.15f))
        image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            contentDescription = "Game screenshot"
        }
        imagePanel.addView(image, FrameLayout.LayoutParams(-1, -1))
        imageFallback = Ui.text(context, "No screenshot available", Ui.SECONDARY, Ui.TEXT_MUTED).apply {
            gravity = Gravity.CENTER
        }
        imagePanel.addView(imageFallback, FrameLayout.LayoutParams(-1, -1))

        right = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, 0, 0)
        }
        hero.addView(right, LinearLayout.LayoutParams(0, -1, 0.85f))
        heading = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        cover = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            contentDescription = "Box art"
            background = Ui.rounded(context, Ui.RAISED, 4)
            clipToOutline = true
        }
        heading.addView(cover, LinearLayout.LayoutParams(dp(60), dp(80)).apply { marginEnd = dp(12) })
        val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        title = Ui.text(context, "", Ui.TITLE, bold = true).apply {
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titles.addView(title)
        tags = WrapLayout(context, dp(6)).apply { alignEnd = false }
        titles.addView(tags, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        heading.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        right.addView(heading, LinearLayout.LayoutParams(-1, 0, 1f))
        playButton = Ui.primaryButton(context, "Play", R.drawable.ic_play) { playSelected() }
        commandButton = Ui.iconButton(context, R.drawable.ic_terminal, "DOS prompt or run program") {
            launchCommand()
        }.apply {
            visibility = if (commandLaunch == null) View.GONE else View.VISIBLE
            background = Ui.focusable(context, Color.TRANSPARENT, Ui.LINE)
        }
        settingsButton = Ui.secondaryButton(context, "Game settings", R.drawable.ic_tune) {
            current?.let(settings)
        }
        launchRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        launchRow.addView(playButton, LinearLayout.LayoutParams(0, -1, 2f))
        launchRow.addView(commandButton, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(8) })
        right.addView(launchRow, LinearLayout.LayoutParams(-1, dp(52)))
        right.addView(settingsButton, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        unavailable = Ui.text(context, "", Ui.SECONDARY, Ui.DANGER).apply {
            visibility = View.GONE
            setPadding(0, dp(16), 0, 0)
        }
        body.addView(unavailable)

        description = Ui.text(context, "", Ui.BODY, Ui.TEXT_BODY).apply { setLineSpacing(dp(8).toFloat(), 1f) }
        body.addView(description, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        file = Ui.text(context, "", Ui.LABEL, Ui.TEXT_FAINT)
        body.addView(file, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        root.addOnLayoutChangeListener { _, left, top, rightEdge, bottom, _, _, _, _ ->
            updateHeroLayout(bottom - top > rightEdge - left)
        }
        updateHeroLayout(context.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT)
        return root
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
        renav(first = !refresh)

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
            view.post {
                if (generation == imageGeneration && inStack) {
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

    /** Back, screenshot, Play, command, Game settings. Play leaves the order when it can't run. */
    override fun navItems(): List<NavItem> {
        val playable = current?.playable == true
        val items = listOfNotNull(
            backButton to "back",
            imagePanel.takeIf { it.isEnabled }?.let { it to "image" },
            playButton.takeIf { playable }?.let { it to "play" },
            commandButton.takeIf { playable && commandLaunch != null }?.let { it to "command" },
            settingsButton to "settings")
        return items.mapIndexed { index, (view, id) -> NavItem(id, view, index, 0.5f, { view.performClick() }) }
    }

    override fun initialFocus(items: List<NavItem>) =
        if (current?.playable == true) "play" else "settings"

    /** The page reads top to bottom, so Left and Up go back and Right and Down go forward. */
    override fun move(direction: String) = super.move(when (direction) {
        "left" -> "up"
        "right" -> "down"
        else -> direction
    })

    override fun onRemoved() {
        imageGeneration++
        imageExecutor.shutdownNow()
        closed(this)
    }
}
