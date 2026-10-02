package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/** Shared first-run presentation; each emulator supplies its own setup steps and actions. */
open class FirstRunScreen(private val activity: Activity) : FrameLayout(activity) {
    data class Action(
        val title: String,
        val subtitle: String,
        val primary: Boolean = false,
        val enabled: Boolean = true,
        val onClick: () -> Unit
    )

    data class Page(
        val product: String,
        val step: String,
        val title: String,
        val description: String,
        val actions: List<Action>,
        val status: String? = null,
        val focusAction: Int? = null,
        // The terminal action stays at the bottom right on every setup page.
        val footerAction: Int = actions.lastIndex
    )

    private val card = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var onBack: () -> Unit = {}
    val isOpen: Boolean get() = visibility == View.VISIBLE

    init {
        visibility = View.GONE
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xff142832.toInt(), Ui.BG, 0xff111a29.toInt()))
        elevation = Ui.dp(activity, 24).toFloat()
        isFocusableInTouchMode = true
        card.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 20),
            Ui.dp(activity, 20), Ui.dp(activity, 20))
        addView(card, LayoutParams(-1, -1, Gravity.CENTER))
    }

    fun show(page: Page, onBack: () -> Unit) {
        this.onBack = onBack
        visibility = View.VISIBLE
        FirstRunVisibility.forActivity(activity).setVisible(this, true)
        card.removeAllViews()
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        header.addView(PixelTextView(activity).apply {
            text = page.product
            color = Ui.ACCENT
            scale = 2
            contentDescription = page.product
        })
        header.addView(Ui.text(activity, page.title, 22f, bold = true).apply {
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = Ui.dp(activity, 20) })
        card.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = Ui.dp(activity, 12)
        })
        card.addView(View(activity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Ui.ACCENT, Ui.LINE))
        }, LinearLayout.LayoutParams(-1, Ui.dp(activity, 1)).apply {
            bottomMargin = Ui.dp(activity, 16)
        })
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        if (page.description.isNotBlank()) body.addView(
            Ui.text(activity, page.description, Ui.BODY, Ui.TEXT_MUTED),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(activity, 12) })
        // Normal 640x480 pages fit without scrolling. Keep an overflow fallback
        // for smaller windows and enlarged accessibility text, not a scrolling footer.
        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(body)
        }
        card.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        val actionRows = ArrayList<View>()
        page.actions.forEachIndexed { index, action ->
            val isFooter = index == page.footerAction
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(activity, if (isFooter) 48 else 60)
                minimumWidth = if (isFooter) Ui.dp(activity, 144) else 0
                setPadding(Ui.dp(activity, 18), Ui.dp(activity, 10),
                    Ui.dp(activity, 18), Ui.dp(activity, 10))
                background = actionBackground(action.primary, isFooter)
                isEnabled = action.enabled
                isClickable = action.enabled
                isFocusable = action.enabled
                alpha = if (action.enabled) 1f else .55f
                contentDescription = "${action.title}. ${action.subtitle}"
                setOnClickListener { action.onClick() }
            }
            val titleColor = when {
                isFooter && action.primary -> Ui.ON_ACCENT
                action.primary || isFooter -> Ui.ACCENT_SOFT
                else -> Ui.TEXT
            }
            row.addView(Ui.text(activity, if (isFooter) "${action.title}  →" else action.title,
                Ui.TITLE, titleColor, bold = action.primary).apply {
                if (isFooter) gravity = Gravity.CENTER
            })
            if (!isFooter && action.subtitle.isNotBlank())
                row.addView(Ui.text(activity, action.subtitle, Ui.SECONDARY, Ui.TEXT_MUTED))
            (if (isFooter) footer else body).addView(row,
                LinearLayout.LayoutParams(if (isFooter) -2 else -1, -2).apply {
                    if (!isFooter) bottomMargin = Ui.dp(activity, 8)
                })
            actionRows.add(row)
        }
        page.status?.let { status ->
            card.addView(Ui.text(activity, status, Ui.SECONDARY, Ui.ACCENT),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(activity, 8) })
        }
        card.addView(footer, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = Ui.dp(activity, 12)
        })
        card.post {
            val requested = page.focusAction?.let(actionRows::getOrNull)?.takeIf { it.isEnabled }
            if (isOpen && requested != null) requested.requestFocusFromTouch()
            else if (isOpen && findFocus()?.isDescendantOf(card) != true)
                actionRows.firstOrNull { it.isEnabled }?.requestFocusFromTouch()
        }
    }

    fun close() {
        visibility = View.GONE
        FirstRunVisibility.forActivity(activity).setVisible(this, false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        FirstRunVisibility.forActivity(activity).setVisible(this, isOpen)
    }

    override fun onDetachedFromWindow() {
        FirstRunVisibility.forActivity(activity).setVisible(this, false)
        super.onDetachedFromWindow()
    }
    fun back() { if (isOpen) onBack() }

    private fun actionBackground(primary: Boolean, footer: Boolean) = StateListDrawable().apply {
        fun shape(focused: Boolean) = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            when {
                footer && primary -> intArrayOf(Ui.ACCENT_SOFT, Ui.ACCENT)
                primary -> intArrayOf(0xff203e49.toInt(), Ui.SELECTED)
                else -> intArrayOf(Ui.RAISED, Ui.SURFACE)
            }
        ).apply {
            cornerRadius = Ui.dp(activity, Ui.RADIUS_SMALL).toFloat()
            setStroke(Ui.dp(activity, if (focused) 2 else 1),
                if (focused) Color.WHITE else if (primary || footer) Ui.ACCENT else Ui.LINE)
        }
        addState(intArrayOf(android.R.attr.state_focused), shape(true))
        addState(intArrayOf(android.R.attr.state_pressed), shape(true))
        addState(intArrayOf(), shape(false))
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (!isOpen) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) back()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                findFocus()?.performClick()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val controls = card.getFocusables(View.FOCUS_FORWARD).filter { it.isEnabled && it.isClickable }
                val current = controls.indexOf(card.findFocus())
                val index = if (current < 0) 0 else (current +
                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    .coerceIn(0, (controls.size - 1).coerceAtLeast(0))
                controls.getOrNull(index)?.requestFocusFromTouch()
            }
            return true
        }
        return true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        card.layoutParams = card.layoutParams.apply { this.width = minOf(Ui.dp(activity, 840), width) }
    }

    private fun View.isDescendantOf(ancestor: View): Boolean {
        var parent = parent
        while (parent is View) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }
}
