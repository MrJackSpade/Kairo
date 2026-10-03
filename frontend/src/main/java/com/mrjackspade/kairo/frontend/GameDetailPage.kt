package com.mrjackspade.kairo.frontend

import com.mrjackspade.kairo.frontend.Ui

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

/** Full library detail page. Artwork loads from approved app assets. */
class GameDetailPage<T : LibraryItem>(
    context: Context,
    private val catalog: LibraryCatalog,
    private val play: (T) -> Unit,
    private val viewScreenshot: (T) -> Unit,
    private val settings: (T) -> Unit,
    private val back: () -> Unit,
    private val metadata: (T) -> LibraryGame
) : FrameLayout(context) {
    private val imagePanel = FrameLayout(context)
    private val image = ImageView(context)
    private val imageFallback = Ui.text(context, "No screenshot available", Ui.SECONDARY, Ui.TEXT_MUTED)
    private val backButton = LinearLayout(context)
    private val cover = ImageView(context)
    private val title = Ui.text(context, "", Ui.TITLE, bold = true)
    private val tags = LinearLayout(context)
    private val description = Ui.text(context, "", Ui.BODY, Ui.TEXT_BODY)
    private val playButton = Ui.primaryButton(context, "Play", R.drawable.ic_play) { playSelected() }
    private val settingsButton = Ui.secondaryButton(context, "Game settings", R.drawable.ic_tune) {
        currentEntry?.let(settings)
    }
    private val unavailable = Ui.text(context, "", Ui.SECONDARY, Ui.DANGER)
    private val file = Ui.text(context, "", Ui.LABEL, Ui.TEXT_FAINT)
    private val scroll = ScrollView(context)
    private val hero = LinearLayout(context)
    private val right = LinearLayout(context)
    private val heading = LinearLayout(context)
    private val imageExecutor = Executors.newSingleThreadExecutor()
    private var currentEntry: T? = null
    private var imageGeneration = 0

    val isOpen: Boolean get() = visibility == View.VISIBLE

    init {
        visibility = View.GONE
        setBackgroundColor(Ui.BG)
        isClickable = true

        scroll.isFillViewport = true
        addView(scroll, LayoutParams(-1, -1))
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(28))
        }
        scroll.addView(body)

        backButton.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = "Back to game library"
            setPadding(dp(4), 0, dp(14), 0)
            background = Ui.focusable(context, Color.TRANSPARENT, null)
            setOnClickListener { back() }
            addView(Ui.icon(context, R.drawable.ic_back, Ui.ACCENT_SOFT, 20))
            addView(Ui.text(context, "Library", Ui.BODY, Ui.ACCENT_SOFT).apply { setPadding(dp(4), 0, 0, 0) })
        }
        body.addView(backButton, LinearLayout.LayoutParams(-2, dp(44)))

        hero.orientation = LinearLayout.HORIZONTAL
        body.addView(hero, LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(12) })

        imagePanel.apply {
            background = Ui.focusable(context, Ui.SURFACE, null)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            contentDescription = "View screenshot"
            setOnClickListener { currentEntry?.let(viewScreenshot) }
        }
        hero.addView(imagePanel, LinearLayout.LayoutParams(0, -1, 1.15f))
        image.apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            contentDescription = "Game screenshot"
        }
        imagePanel.addView(image, LayoutParams(-1, -1))
        imageFallback.gravity = Gravity.CENTER
        imagePanel.addView(imageFallback, LayoutParams(-1, -1))

        right.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, 0, 0)
        }
        hero.addView(right, LinearLayout.LayoutParams(0, -1, 0.85f))
        heading.orientation = LinearLayout.HORIZONTAL
        cover.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            contentDescription = "Box art"
            background = Ui.rounded(context, Ui.RAISED, 4)
            clipToOutline = true
        }
        heading.addView(cover, LinearLayout.LayoutParams(dp(60), dp(80)).apply { marginEnd = dp(12) })
        val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        title.maxLines = 3
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        titles.addView(title)
        tags.orientation = LinearLayout.VERTICAL
        titles.addView(tags, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        heading.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        right.addView(heading, LinearLayout.LayoutParams(-1, 0, 1f))
        right.addView(playButton, LinearLayout.LayoutParams(-1, dp(52)))
        right.addView(settingsButton, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        unavailable.visibility = View.GONE
        unavailable.setPadding(0, dp(8), 0, 0)
        right.addView(unavailable)

        description.setLineSpacing(dp(4).toFloat(), 1f)
        body.addView(description, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        body.addView(file, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        updateHeroLayout(resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT)
    }

    private fun tag(label: String) = Ui.text(context, label, Ui.LABEL, Ui.TEXT_MUTED).apply {
        background = Ui.rounded(context, Color.TRANSPARENT, 4, Ui.LINE)
        setPadding(dp(6), dp(2), dp(6), dp(2))
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun updateHeroLayout(portrait: Boolean) {
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
        playButton.layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply {
            if (portrait) topMargin = dp(18)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateHeroLayout(newConfig.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT)
    }

    fun show(entry: T) {
        // A library rescan or artwork refresh re-shows the open game; keep the user's place.
        val refresh = isOpen && currentEntry?.id == entry.id
        currentEntry = entry
        val game = metadata(entry)
        title.text = game.title
        tags.removeAllViews()
        tags.addView(tag(entry.mediaLabel),
            LinearLayout.LayoutParams(-2, -2))
        game.tags.forEach { label ->
            tags.addView(tag(label),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        }
        (variantLabel(entry.path) ?: entry.zipEntry?.let(::variantLabel))?.split("  ·  ")?.forEach {
            tags.addView(tag(it), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        }
        description.text = game.description ?: "No description available yet."
        file.text = "File: " + fileLabel(entry)
        playButton.isEnabled = entry.playable
        playButton.alpha = if (entry.playable) 1f else 0.4f
        unavailable.text = if (entry.playable) "" else
            "Can't play: ${entry.error ?: "this file has not been checked yet"}. Fix the file, then Refresh from the library menu."
        unavailable.visibility = if (entry.playable) View.GONE else View.VISIBLE
        image.setImageDrawable(null)
        image.visibility = View.GONE
        cover.setImageDrawable(null)
        cover.visibility = View.GONE
        imageFallback.visibility = View.VISIBLE
        imageFallback.text = if (game.preview == null) "No screenshot available" else "Loading screenshot…"
        imagePanel.isEnabled = game.preview != null
        imagePanel.isFocusable = game.preview != null
        visibility = View.VISIBLE
        if (!refresh) {
            scroll.scrollTo(0, 0)
            if (entry.playable) playButton.requestFocus()
            else settingsButton.requestFocus()
        } else if (!playButton.isEnabled && playButton.hasFocus()) settingsButton.requestFocus()

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
                if (generation == imageGeneration && isOpen) {
                    if (bitmap == null) failed() else loaded(bitmap)
                }
            }
        }
    }

    fun close(): Boolean {
        if (!isOpen) return false
        imageGeneration++
        currentEntry = null
        visibility = View.GONE
        return true
    }

    fun refreshArtwork() { currentEntry?.let(::show) }

    fun playSelected() { currentEntry?.takeIf { it.playable }?.let(play) }

    fun activateFocused() {
        when {
            backButton.hasFocus() -> back()
            imagePanel.hasFocus() -> currentEntry?.let(viewScreenshot)
            settingsButton.hasFocus() -> currentEntry?.let(settings)
            else -> playSelected()
        }
    }

    override fun onDetachedFromWindow() {
        imageExecutor.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
