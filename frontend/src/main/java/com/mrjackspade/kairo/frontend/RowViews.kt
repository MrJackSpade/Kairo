package com.mrjackspade.kairo.frontend

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** What a row's surface does when the row is touched or changed in place. */
internal interface RowHost {
    fun activate(row: SettingsRow)
    fun side(row: SettingsRow, direction: Int)
    fun slide(row: SettingsRow, value: Int)
}

internal fun toneColor(tone: ValueTone) = when (tone) {
    ValueTone.CURRENT -> Ui.ACCENT_SOFT
    ValueTone.SUMMARY -> Ui.TEXT_MUTED
    ValueTone.EMPTY -> Ui.TEXT_FAINT
    ValueTone.CUSTOM -> Ui.ACCENT
}

/**
 * Draws one [SettingsRow]: label on the left, value right-aligned, and a 24 dp trailing column
 * that holds a chevron or nothing, so values line up. Rebinding updates it in place.
 */
internal class RowView(private val context: Context, private val host: RowHost, private val flat: Boolean) {
    val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    var row: SettingsRow? = null
        private set
    /** The selectable part, which the page scrolls into view and outlines. */
    lateinit var line: View
        private set
    private var label: TextView? = null
    private var description: TextView? = null
    private var tag: TextView? = null
    private var spinner: View? = null
    private var cycle: TextView? = null
    private var value: TextView? = null
    private var switch: SwitchView? = null
    private var radio: RadioView? = null
    private var chevron: ImageView? = null
    private var slider: SliderBar? = null
    private val trayHolder = FrameLayout(context)
    private var builtKind: SettingsRow.Kind? = null
    private var builtDescription = false
    private var flashUntil = 0L
    private var shownColor = 0

    fun bind(next: SettingsRow, focused: Boolean, trayOpen: Boolean) {
        if (builtKind != next.kind || builtDescription != (next.description != null)) build(next)
        row = next
        // The row by its id, its parts by their role; switches and options say whether they are on.
        TestAttributes.mark(line, next.id, if (next.kind == SettingsRow.Kind.SWITCH || next.kind == SettingsRow.Kind.OPTION)
            next.checked().toString() else null)
        val enabled = next.enabled()
        line.alpha = if (enabled || next.kind == SettingsRow.Kind.INFO) 1f else 0.45f
        line.isSelected = focused || trayOpen
        label?.text = next.label
        label?.setTextColor(if (next.danger) Ui.DANGER else Ui.TEXT)
        description?.text = next.description
        val shown = next.value()
        value?.let {
            it.text = shown
            it.visibility = if (shown.isEmpty()) View.GONE else View.VISIBLE
            val color = if (System.currentTimeMillis() < flashUntil) Ui.ACCENT else toneColor(next.tone())
            if (color != shownColor) { it.setTextColor(color); shownColor = color }
        }
        tag?.let { chip ->
            val source = next.tag()
            chip.visibility = if (source == null) View.GONE else View.VISIBLE
            chip.text = source
            val color = if (source == "Custom") Ui.ACCENT else Ui.TEXT_FAINT
            chip.setTextColor(color)
            chip.background = Ui.rounded(context, Color.TRANSPARENT, 4, color)
        }
        spinner?.visibility = if (next.busy()) View.VISIBLE else View.GONE
        // A cycling choice shows ‹ value › whether or not it is selected; the ‹ keeps its space when
        // disabled, so the value never moves.
        cycle?.visibility = when {
            next.choice?.cycles != true -> View.GONE
            enabled -> View.VISIBLE
            else -> View.INVISIBLE
        }
        switch?.setChecked(next.checked(), animate = builtKind == next.kind)
        radio?.checked = next.checked()
        slider?.let { bar -> next.slider?.let { bar.setRange(it.min, it.max, it.get()) } }
        line.contentDescription = listOfNotNull(next.label, next.tag(), shown.ifEmpty { null },
            if (next.kind == SettingsRow.Kind.SWITCH) if (next.checked()) "On" else "Off" else null,
            if (!enabled) next.disabledReason() else null).joinToString(", ")
    }

    /** The value turns Accent for 900 ms after it changes in place or from a choice page. */
    fun flash() {
        flashUntil = System.currentTimeMillis() + 900
        value?.let { it.setTextColor(Ui.ACCENT); shownColor = Ui.ACCENT }
        root.postDelayed({
            val current = row ?: return@postDelayed
            if (System.currentTimeMillis() < flashUntil) return@postDelayed
            value?.let { view ->
                val target = toneColor(current.tone())
                ValueAnimator.ofArgb(Ui.ACCENT, target).apply {
                    duration = 300
                    addUpdateListener { view.setTextColor(it.animatedValue as Int) }
                    start()
                }
                shownColor = target
            }
        }, 900)
    }

    fun showTray(tray: View?) {
        trayHolder.removeAllViews()
        tray?.let { trayHolder.addView(it, FrameLayout.LayoutParams(-1, -2)) }
        trayHolder.visibility = if (tray == null) View.GONE else View.VISIBLE
    }

    private fun build(next: SettingsRow) {
        root.removeAllViews()
        label = null; description = null; tag = null; spinner = null; cycle = null
        value = null; switch = null; radio = null; chevron = null; slider = null
        shownColor = 0
        builtKind = next.kind
        builtDescription = next.description != null
        line = when (next.kind) {
            SettingsRow.Kind.INFO -> buildInfo()
            SettingsRow.Kind.SLIDER -> buildSlider()
            else -> buildLine(next)
        }
        root.addView(line, LinearLayout.LayoutParams(-1, -2))
        trayHolder.visibility = View.GONE
        root.addView(trayHolder, LinearLayout.LayoutParams(-1, -2))
    }

    private fun background() = Ui.rowBackground(context, radius = if (flat) Ui.RADIUS_SMALL else 0)

    private fun buildLine(next: SettingsRow): View {
        val valueView = Ui.text(context, "", Ui.SECONDARY).apply {
            gravity = Gravity.END
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }.testId("value")
        val line = object : LinearLayout(context) {
            override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                valueView.maxWidth = (MeasureSpec.getSize(widthSpec) * 0.6f).toInt()
                super.onMeasure(widthSpec, heightSpec)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(if (next.description != null) 64 else 48)
            setPadding(dp(14), dp(6), dp(4), dp(6))
            background = background()
            isClickable = true
            setOnClickListener { row?.let(host::activate) }
        }
        val text = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        label = Ui.text(context, "", Ui.BODY).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }.testId("label")
        text.addView(label)
        if (next.description != null) description = Ui.text(context, "", Ui.SECONDARY, Ui.TEXT_MUTED).testId("description")
            .also { text.addView(it, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) }) }
        line.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        if (next.kind != SettingsRow.Kind.OPTION) {
            tag = Ui.chip(context, "").testId("tag").also { line.addView(it, gap()) }
            spinner = Ui.spinner(context).also {
                it.visibility = View.GONE
                line.addView(it, gap(16))
            }
        }
        when (next.kind) {
            SettingsRow.Kind.SWITCH -> switch = SwitchView(context).also { line.addView(it, gap(40, 22)) }
            SettingsRow.Kind.OPTION -> radio = RadioView(context).also { line.addView(it, gap(20, 20)) }
            else -> {
                if (next.kind == SettingsRow.Kind.CHOICE) cycle = Ui.text(context, "‹", 18f, Ui.TEXT_FAINT).testId("cycle").apply {
                    visibility = View.GONE
                    includeFontPadding = false
                }.also { line.addView(it, gap().apply { marginEnd = -dp(4) }) }
                value = valueView
                line.addView(valueView, gap())
            }
        }
        val trailing = FrameLayout(context)
        if (next.kind == SettingsRow.Kind.NAV || next.kind == SettingsRow.Kind.CHOICE)
            chevron = Ui.icon(context, R.drawable.ic_chevron, Ui.TEXT_FAINT, 16).also {
                trailing.addView(it, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
            }
        line.addView(trailing, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(10) })
        return line
    }

    private fun buildInfo(): View {
        val line = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(13), dp(28), dp(13))
        }
        label = Ui.text(context, "", Ui.BODY).testId("label")
        line.addView(label, LinearLayout.LayoutParams(-2, -2))
        value = Ui.text(context, "", Ui.SECONDARY, Ui.TEXT_MUTED).testId("value").apply {
            gravity = Gravity.END
            setPadding(0, dp(1), 0, 0)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
        }
        line.addView(value, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
        return line
    }

    private fun buildSlider(): View {
        val line = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(28), dp(6))
            background = background()
            isClickable = true
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(22)
        }
        label = Ui.text(context, "", Ui.BODY).testId("label")
        top.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        value = Ui.text(context, "", Ui.SECONDARY).testId("value")
        top.addView(value, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        line.addView(top)
        slider = SliderBar(context) { picked -> row?.let { host.slide(it, picked) } }
        line.addView(slider, LinearLayout.LayoutParams(-1, dp(24)).apply { topMargin = dp(4) })
        return line
    }

    private fun gap(width: Int? = null, height: Int? = null) = LinearLayout.LayoutParams(
        width?.let(::dp) ?: -2, height?.let(::dp) ?: -2).apply { marginStart = dp(10) }

    private fun dp(value: Int) = Ui.dp(context, value)
}

/** Opens under its row in the selected fill: a message, an optional field, and buttons. */
internal class TrayView(context: Context, val spec: TraySpec, flat: Boolean,
                        private val press: (TrayButton, String) -> Unit) : LinearLayout(context) {
    val buttons = ArrayList<View>()
    val entry: EditText? = spec.input?.let { input ->
        EditText(context).testId("tray.input").apply {
            setText(input.initial)
            setSelection(text.length)
            hint = input.hint
            textSize = Ui.BODY
            isSingleLine = true
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.TEXT_FAINT)
            inputType = if (input.numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = Ui.rounded(context, Ui.BG, Ui.RADIUS_SMALL, Ui.ACCENT)
            setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), 0)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) { pressPrimary(); true } else false
            }
        }
    }
    val text: String get() = entry?.text?.toString()?.trim() ?: ""

    init {
        orientation = VERTICAL
        background = if (flat) Ui.rounded(context, Ui.SELECTED) else GradientDrawable().apply { setColor(Ui.SELECTED) }
        setPadding(Ui.dp(context, 14), Ui.dp(context, 2), Ui.dp(context, 18), Ui.dp(context, 12))
        isClickable = true
        spec.message?.let { message ->
            val line = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            if (spec.listening) line.addView(PulseDot(context),
                LayoutParams(Ui.dp(context, 8), Ui.dp(context, 8)).apply { marginEnd = Ui.dp(context, 8) })
            line.addView(Ui.text(context, message, Ui.SECONDARY, Ui.TEXT_BODY), LayoutParams(0, -2, 1f))
            addView(line, LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(context, 10) })
        }
        entry?.let { addView(it, LayoutParams(-1, Ui.dp(context, 44)).apply { bottomMargin = Ui.dp(context, 10) }) }
        val row = WrapLayout(context, Ui.dp(context, 8))
        spec.buttons.forEach { button ->
            val view = Ui.choiceButton(context, button.label, button.tone) { press(button, text) }
                .testId("tray.button", button.label)
            buttons += view
            row.addView(view, ViewGroup.LayoutParams(-2, Ui.dp(context, 44)))
        }
        addView(row, LayoutParams(-1, -2))
    }

    fun pressPrimary() {
        spec.buttons.lastOrNull { it.tone != ButtonTone.SECONDARY }?.let { press(it, text) }
    }

    /** -1 selects the field; buttons are 0 and up. */
    fun select(index: Int) {
        buttons.forEachIndexed { position, view -> view.isSelected = position == index }
    }
}

/** Lays children out left to right, wrapping, with each line aligned to the end or start. */
internal class WrapLayout(context: Context, private val gap: Int) : ViewGroup(context) {
    var alignEnd = true
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        var x = 0
        var y = 0
        var line = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue
            measureChild(child, MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), heightSpec)
            if (x > 0 && x + child.measuredWidth > width) { y += line + gap; x = 0; line = 0 }
            x += child.measuredWidth + gap
            line = maxOf(line, child.measuredHeight)
        }
        setMeasuredDimension(width, y + line)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val lines = ArrayList<MutableList<View>>()
        var x = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue
            if (lines.isEmpty() || (x > 0 && x + child.measuredWidth > width)) { lines += ArrayList<View>(); x = 0 }
            lines.last() += child
            x += child.measuredWidth + gap
        }
        var y = 0
        for (line in lines) {
            val used = line.sumOf { it.measuredWidth } + gap * (line.size - 1)
            var cursor = if (alignEnd) width - used else 0
            val height = line.maxOf { it.measuredHeight }
            for (child in line) {
                child.layout(cursor, y, cursor + child.measuredWidth, y + child.measuredHeight)
                cursor += child.measuredWidth + gap
            }
            y += height + gap
        }
    }
}

/** Track and knob, 150 ms. Left turns it off; Right turns it on. */
internal class SwitchView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private var position = 0f
    private var checked = false
    private var animator: ValueAnimator? = null

    fun setChecked(value: Boolean, animate: Boolean) {
        if (value == checked && animator == null && position == (if (value) 1f else 0f)) return
        checked = value
        animator?.cancel()
        if (!animate || !isAttachedToWindow) { position = if (value) 1f else 0f; invalidate(); return }
        animator = ValueAnimator.ofFloat(position, if (value) 1f else 0f).apply {
            duration = 150
            interpolator = Ui.ease()
            addUpdateListener { position = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { animator = null }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        track.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = blend(Ui.LINE, Ui.ACCENT, position)
        canvas.drawRoundRect(track, radius, radius, paint)
        val knob = Ui.dp(context, 8).toFloat()
        val inset = Ui.dp(context, 3).toFloat() + knob
        val travel = width - inset * 2
        paint.color = blend(Ui.TEXT_MUTED, Ui.ON_ACCENT, position)
        canvas.drawCircle(inset + travel * position, height / 2f, knob, paint)
    }

    private fun blend(from: Int, to: Int, amount: Float) =
        android.animation.ArgbEvaluator().evaluate(amount, from, to) as Int
}

/** A 20 dp radio for choice-page options. */
internal class RadioView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var checked = false
        set(value) { if (field != value) { field = value; invalidate() } }

    override fun onDraw(canvas: Canvas) {
        val stroke = Ui.dp(context, 2).toFloat()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = if (checked) Ui.ACCENT else Ui.TEXT_FAINT
        canvas.drawCircle(width / 2f, height / 2f, width / 2f - stroke / 2f, paint)
        if (checked) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(width / 2f, height / 2f, Ui.dp(context, 5).toFloat(), paint)
        }
    }
}

/** The listening indicator, fading between 30% and full over 1.2 s. */
internal class PulseDot(context: Context) : View(context) {
    private val pulse = ObjectAnimator.ofFloat(this, View.ALPHA, 0.3f, 1f).apply {
        duration = 600
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
    }

    init { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.ACCENT) } }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); pulse.start() }
    override fun onDetachedFromWindow() { pulse.cancel(); super.onDetachedFromWindow() }
}

/** A 4 dp track with an Accent fill and an 18 dp thumb. Dragging picks a value. */
internal class SliderBar(context: Context, private val picked: (Int) -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private var min = 0
    private var max = 100
    private var value = 0

    fun setRange(min: Int, max: Int, value: Int) {
        this.min = min
        this.max = maxOf(max, min + 1)
        this.value = value.coerceIn(min, this.max)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val thumb = Ui.dp(context, 9).toFloat()
        val center = height / 2f
        val half = Ui.dp(context, 2).toFloat()
        val fraction = (value - min).toFloat() / (max - min)
        val end = thumb + (width - thumb * 2) * fraction
        paint.color = Ui.LINE
        track.set(0f, center - half, width.toFloat(), center + half)
        canvas.drawRoundRect(track, half, half, paint)
        paint.color = Ui.ACCENT
        track.set(0f, center - half, end, center + half)
        canvas.drawRoundRect(track, half, half, paint)
        canvas.drawCircle(end, center, thumb, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP)
                val thumb = Ui.dp(context, 9).toFloat()
                val fraction = ((event.x - thumb) / (width - thumb * 2)).coerceIn(0f, 1f)
                val next = (min + fraction * (max - min)).roundToInt()
                if (next != value) { value = next; invalidate(); picked(next) }
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}
