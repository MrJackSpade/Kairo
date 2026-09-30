package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog
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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
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
    data class ActionRow(val view: LinearLayout, val detail: TextView)
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
    val SCRIM = 0xb8000000.toInt()

    // Type scale, in sp.
    const val LABEL = 12f
    const val SECONDARY = 14f
    const val BODY = 16f
    const val TITLE = 20f
    const val DISPLAY = 28f

    const val RADIUS_SMALL = 6
    const val RADIUS_LARGE = 12

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
                      activeFill: Int = SELECTED, activatedOnly: Boolean = false): Drawable {
        fun active(): Drawable = LayerDrawable(arrayOf(
            rounded(context, activeFill),
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
            addState(intArrayOf(), if (rest == Color.TRANSPARENT) ColorDrawable(rest) else rounded(context, rest))
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

    /** A 48dp touch target around a 24dp icon. */
    fun iconButton(context: Context, drawable: Int, description: String, action: () -> Unit) =
        FrameLayout(context).apply {
            contentDescription = description
            isFocusable = true
            isClickable = true
            background = rowBackground(context)
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
        addState(intArrayOf(android.R.attr.state_pressed), shape(true))
        addState(intArrayOf(), shape(false))
    }

    /** Small pixel-font heading used for section labels across the app. */
    fun sectionLabel(context: Context, value: String, color: Int = ACCENT) =
        PixelTextView(context).apply {
            text = value
            this.color = color
            setPadding(dp(context, 12), dp(context, 16), dp(context, 12), dp(context, 6))
        }

    /** Shared menu and settings row. The caller controls navigation and refresh timing. */
    fun actionRow(context: Context, title: String, description: () -> String,
                  minHeight: Int = 56, verticalPadding: Int = 9,
                  onClick: (View) -> Unit): ActionRow {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(context, minHeight)
            setPadding(dp(context, 14), dp(context, verticalPadding),
                dp(context, 12), dp(context, verticalPadding))
            isFocusable = true
            isClickable = true
            background = rowBackground(context)
            setOnClickListener(onClick)
        }
        row.addView(text(context, title, BODY))
        val value = text(context, description(), SECONDARY, TEXT_MUTED)
        row.addView(value)
        row.contentDescription = "$title. ${value.text}"
        return ActionRow(row, value)
    }

    fun styleDialog(dialog: AlertDialog) {
        DialogControllerNavigation.install(dialog)
        val context = dialog.context
        dialog.window?.setBackgroundDrawable(rounded(context, RAISED, RADIUS_LARGE))
        listOf(AlertDialog.BUTTON_POSITIVE to ACCENT, AlertDialog.BUTTON_NEGATIVE to TEXT_MUTED,
            AlertDialog.BUTTON_NEUTRAL to TEXT_MUTED).forEach { (which, color) ->
            dialog.getButton(which)?.apply {
                setTextColor(color)
                isAllCaps = false
                textSize = BODY
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        }
        dialog.window?.decorView?.let(::tintControls)
    }

    private fun tintControls(view: View) {
        if (view is CompoundButton) view.buttonTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(ACCENT, TEXT_FAINT))
        if (view is ViewGroup) for (index in 0 until view.childCount) tintControls(view.getChildAt(index))
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
        bar.animate().alpha(1f).setDuration(150).start()
        bar.postDelayed({
            bar.animate().alpha(0f).setDuration(200).withEndAction { host.removeView(bar) }.start()
        }, if (long) 3500 else 2000)
    }

    private const val MESSAGE_TAG = "kairo-message"
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
