package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Kairo's design system: color roles, a five-step type scale, two corner radii, and the
 * shared pieces every screen is built from. Screens use these instead of their own values.
 */
object Ui {
    // Color roles. Accents come from the original digital palette, softened for a dark screen.
    val BG = 0xff0c1017.toInt()
    val SURFACE = 0xff151b25.toInt()
    val RAISED = 0xff1f2835.toInt()
    val SELECTED = 0xff2a3a50.toInt()
    val LINE = 0xff2e3a4b.toInt()
    val TEXT = 0xfff1f4f8.toInt()
    val TEXT_BODY = 0xffd3dae3.toInt()
    val TEXT_MUTED = 0xffa5b1c0.toInt()
    val TEXT_FAINT = 0xff7a8799.toInt()
    val ACCENT = 0xff5fdde4.toInt()
    val ACCENT_SOFT = 0xffa8eef1.toInt()
    val ON_ACCENT = 0xff061316.toInt()
    val PIN = 0xffffd166.toInt()
    val PIN_SURFACE = 0xff2a2618.toInt()
    val PIN_SELECTED = 0xff453a1c.toInt()
    val DANGER = 0xffff8f80.toInt()
    val ON_DANGER = 0xff2b0b07.toInt()
    val SCRIM = 0xb8000000.toInt()

    // Type scale, in sp.
    const val LABEL = 12f
    const val SECONDARY = 14f
    const val BODY = 16f
    const val TITLE = 20f
    const val DISPLAY = 28f

    const val RADIUS_SMALL = 6
    const val RADIUS_LARGE = 12

    /** Entering motion. Fades are linear; nothing bounces. */
    fun ease() = PathInterpolator(0.2f, 0f, 0f, 1f)

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    fun rounded(context: Context, color: Int, radius: Int = RADIUS_SMALL, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radius).toFloat()
            stroke?.let { setStroke(dp(context, 1), it) }
        }

    /**
     * A selectable row: transparent at rest, and when focused, selected, or pressed a raised fill
     * with a bar in [accent] on its leading edge, so the current row reads from across the room.
     */
    fun rowBackground(context: Context, rest: Int = Color.TRANSPARENT, accent: Int = ACCENT,
                      activeFill: Int = SELECTED, activatedOnly: Boolean = false,
                      radius: Int = RADIUS_SMALL): Drawable {
        fun active(): Drawable = LayerDrawable(arrayOf(
            rounded(context, activeFill, radius),
            rounded(context, accent, 2))).apply {
            setLayerWidth(1, dp(context, 3))
            setLayerInset(1, 0, dp(context, 8), 0, dp(context, 8))
            setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL)
        }
        return StateListDrawable().apply {
            // List rows follow only the app's own selection, never a list's touch or focus state.
            if (!activatedOnly) {
                addState(intArrayOf(android.R.attr.state_focused), active())
                addState(intArrayOf(android.R.attr.state_selected), active())
            }
            addState(intArrayOf(android.R.attr.state_activated), active())
            addState(intArrayOf(android.R.attr.state_pressed), active())
            addState(intArrayOf(), if (rest == Color.TRANSPARENT) ColorDrawable(rest) else rounded(context, rest, radius))
        }
    }

    fun text(context: Context, value: CharSequence, size: Float, color: Int = TEXT,
             bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun icon(context: Context, drawable: Int, tint: Int = TEXT, size: Int = 24) = ImageView(context).apply {
        setImageResource(drawable)
        imageTintList = ColorStateList.valueOf(tint)
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = LinearLayout.LayoutParams(dp(context, size), dp(context, size))
    }

    /** A 48dp touch target around a 24dp icon, outlined like a button when selected. */
    fun iconButton(context: Context, drawable: Int, description: String, action: () -> Unit) =
        FrameLayout(context).apply {
            contentDescription = description
            isFocusable = true
            isClickable = true
            background = StateListDrawable().apply {
                fun shape(fill: Int, ring: Boolean) = GradientDrawable().apply {
                    setColor(fill)
                    cornerRadius = dp(context, RADIUS_SMALL).toFloat()
                    if (ring) setStroke(dp(context, 2), Color.WHITE)
                }
                addState(intArrayOf(android.R.attr.state_selected), shape(SELECTED, true))
                addState(intArrayOf(android.R.attr.state_focused), shape(SELECTED, true))
                addState(intArrayOf(android.R.attr.state_pressed), shape(SELECTED, false))
                addState(intArrayOf(), shape(Color.TRANSPARENT, false))
            }
            setOnClickListener { action() }
            addView(icon(context, drawable), FrameLayout.LayoutParams(dp(context, 24), dp(context, 24), Gravity.CENTER))
        }

    fun primaryButton(context: Context, label: String, drawable: Int? = null, action: () -> Unit) =
        button(context, label, drawable, ON_ACCENT, focusable(context, ACCENT, null), action)

    fun secondaryButton(context: Context, label: String, drawable: Int? = null, action: () -> Unit) =
        button(context, label, drawable, ACCENT_SOFT, focusable(context, Color.TRANSPARENT, LINE), action)

    private fun button(context: Context, label: String, drawable: Int?, color: Int,
                       background: Drawable, action: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        contentDescription = label
        this.background = background
        setOnClickListener { action() }
        drawable?.let { addView(icon(context, it, color, 20).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(context, 8)
        }) }
        addView(text(context, label, BODY, color, bold = true))
    }

    /** Fill with a white outline while focused or pressed. */
    fun focusable(context: Context, fill: Int, stroke: Int?) = StateListDrawable().apply {
        fun shape(focused: Boolean) = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(context, RADIUS_SMALL).toFloat()
            if (focused) setStroke(dp(context, 2), Color.WHITE)
            else stroke?.let { setStroke(dp(context, 1), it) }
        }
        addState(intArrayOf(android.R.attr.state_focused), shape(true))
        addState(intArrayOf(android.R.attr.state_selected), shape(true))
        addState(intArrayOf(android.R.attr.state_pressed), shape(true))
        addState(intArrayOf(), shape(false))
    }

    /**
     * A text button in one of the three tones. Pages select it with isSelected, which draws
     * the same white outline as Android focus.
     */
    fun toneButton(context: Context, label: String, tone: ButtonTone, drawable: Int? = null,
                   minWidth: Int = 88, action: () -> Unit): LinearLayout {
        val fill = when (tone) {
            ButtonTone.PRIMARY -> ACCENT
            ButtonTone.DANGER -> DANGER
            ButtonTone.SECONDARY -> Color.TRANSPARENT
        }
        val color = when (tone) {
            ButtonTone.PRIMARY -> ON_ACCENT
            ButtonTone.DANGER -> ON_DANGER
            ButtonTone.SECONDARY -> ACCENT_SOFT
        }
        return button(context, label, drawable, color,
            focusable(context, fill, if (tone == ButtonTone.SECONDARY) LINE else null), action).apply {
            minimumWidth = dp(context, minWidth)
            setPadding(dp(context, 14), 0, dp(context, 14), 0)
        }
    }

    /**
     * A tray or confirmation button. Only the selected one is filled, so the fill always
     * marks what A presses; the others are outlined in their tone.
     */
    fun choiceButton(context: Context, label: String, tone: ButtonTone, action: () -> Unit): LinearLayout {
        val (fill, onFill, rest) = when (tone) {
            ButtonTone.PRIMARY -> Triple(ACCENT, ON_ACCENT, ACCENT)
            ButtonTone.DANGER -> Triple(DANGER, ON_DANGER, DANGER)
            ButtonTone.SECONDARY -> Triple(ACCENT_SOFT, ON_ACCENT, ACCENT_SOFT)
        }
        val chosen = intArrayOf(android.R.attr.state_selected)
        val background = StateListDrawable().apply {
            for (state in listOf(android.R.attr.state_selected, android.R.attr.state_focused, android.R.attr.state_pressed))
                addState(intArrayOf(state), rounded(context, fill))
            addState(intArrayOf(), rounded(context, Color.TRANSPARENT, stroke = if (tone == ButtonTone.SECONDARY) LINE else rest))
        }
        val text = ColorStateList(arrayOf(chosen, intArrayOf(android.R.attr.state_focused),
            intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(onFill, onFill, onFill, rest))
        return button(context, label, null, rest, background, action).apply {
            minimumWidth = dp(context, 88)
            setPadding(dp(context, 14), 0, dp(context, 14), 0)
            (getChildAt(0) as TextView).apply {
                isDuplicateParentStateEnabled = true
                setTextColor(text)
            }
        }
    }

    /** The busy indicator: a Line ring with an Accent segment, one turn every 0.9 s. */
    fun spinner(context: Context, size: Int = 16, stroke: Int = 2) = SpinnerView(context, dp(context, stroke))
        .apply { layoutParams = LinearLayout.LayoutParams(dp(context, size), dp(context, size)) }

    /** A small outlined label, such as a value's source or a game's media type. */
    fun chip(context: Context, value: String, color: Int = TEXT_FAINT) =
        text(context, value, LABEL, color).apply {
            background = rounded(context, Color.TRANSPARENT, 4, color)
            setPadding(dp(context, 6), dp(context, 1), dp(context, 6), dp(context, 1))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

    /** Small pixel-font heading used for section labels across the app. */
    fun sectionLabel(context: Context, value: String, color: Int = ACCENT) =
        PixelTextView(context).apply {
            text = value
            this.color = color
            setPadding(dp(context, 12), dp(context, 16), dp(context, 12), dp(context, 6))
        }

    /** In-app message bar in place of the system toast, styled like the rest of the app. */
    fun message(activity: Activity, value: String, long: Boolean = false) {
        val host = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        host.findViewWithTag<View>(MESSAGE_TAG)?.let(host::removeView)
        val bar = TextView(activity).apply {
            tag = MESSAGE_TAG
            text = value
            textSize = SECONDARY
            setTextColor(TEXT)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            background = rounded(activity, RAISED, RADIUS_SMALL, LINE)
            setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 10))
            elevation = dp(activity, 24).toFloat()
            alpha = 0f
        }
        host.addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(activity, 20)
            leftMargin = dp(activity, 20)
            rightMargin = dp(activity, 20)
        })
        bar.translationY = dp(activity, 8).toFloat()
        bar.animate().alpha(1f).translationY(0f).setInterpolator(ease()).setDuration(150).start()
        bar.postDelayed({
            bar.animate().alpha(0f).setInterpolator(LinearInterpolator()).setDuration(200)
                .withEndAction { host.removeView(bar) }.start()
        }, if (long || value.length > 60) 3500 else 2000)
    }

    /** Remove [value] if it is the message showing, as when what it reported has changed. */
    fun dismissMessage(activity: Activity, value: String) {
        val host = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val bar = host.findViewWithTag<View>(MESSAGE_TAG) as? TextView ?: return
        if (bar.text.toString() == value) host.removeView(bar)
    }

    private const val MESSAGE_TAG = "kairo-message"
}

/** See [Ui.spinner]. It turns only while shown. */
class SpinnerView(context: Context, private val stroke: Int) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke.toFloat()
        color = Ui.LINE
    }
    private val segment = Paint(ring).apply { color = Ui.ACCENT }
    private val bounds = RectF()
    private val spin = ObjectAnimator.ofFloat(this, View.ROTATION, 0f, 360f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = stroke / 2f
        bounds.set(inset, inset, width - inset, height - inset)
        canvas.drawOval(bounds, ring)
        canvas.drawArc(bounds, -135f, 90f, false, segment)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && isAttachedToWindow) { if (!spin.isStarted) spin.start() } else spin.cancel()
    }

    override fun onDetachedFromWindow() {
        spin.cancel()
        super.onDetachedFromWindow()
    }
}

/**
 * Draws ASCII text in the pinned Spleen 8x16 bitmap font at a whole-pixel scale.
 */
class PixelTextView(context: Context) : View(context) {
    var text: String = ""
        set(value) {
            // The font covers printable ASCII; spell out the few symbols labels use.
            field = value.replace("→", "->").replace("·", "-").replace("…", "...")
            requestLayout()
            invalidate()
        }
    var color: Int = Ui.TEXT
        set(value) { field = value; paint.color = value; invalidate() }
    /** Screen pixels per font pixel, before density; 1 draws 8x16 glyphs at 1dp per pixel. */
    var scale: Int = 1
        set(value) { field = value; requestLayout(); invalidate() }

    private val paint = Paint().apply { color = Ui.TEXT }
    private val pixel: Int get() = max(1, (scale * resources.displayMetrics.density).roundToInt())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = paddingLeft + paddingRight + text.length * GLYPH_WIDTH * pixel
        val height = paddingTop + paddingBottom + GLYPH_HEIGHT * pixel
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val glyphs = glyphs(context) ?: return
        val size = pixel.toFloat()
        text.forEachIndexed { index, character ->
            val code = character.code.takeIf { it in 32..126 } ?: '?'.code
            val originX = paddingLeft + index * GLYPH_WIDTH * size
            for (row in 0 until GLYPH_HEIGHT) {
                val bits = glyphs[(code - 32) * GLYPH_HEIGHT + row].toInt() and 0xff
                if (bits == 0) continue
                val top = paddingTop + row * size
                for (column in 0 until GLYPH_WIDTH) {
                    if (bits and (0x80 shr column) != 0)
                        canvas.drawRect(originX + column * size, top, originX + (column + 1) * size, top + size, paint)
                }
            }
        }
    }

    companion object {
        private const val GLYPH_WIDTH = 8
        private const val GLYPH_HEIGHT = 16
        @Volatile private var table: ByteArray? = null

        private fun glyphs(context: Context): ByteArray? = table ?: try {
            context.assets.open("ui/spleen-8x16-ascii.bin").use { it.readBytes() }
                .takeIf { it.size == 95 * GLYPH_HEIGHT }?.also { table = it }
        } catch (_: Exception) { null }
    }
}
