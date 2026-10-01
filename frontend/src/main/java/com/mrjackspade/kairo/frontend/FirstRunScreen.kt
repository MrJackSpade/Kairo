package com.mrjackspade.kairo.frontend

import android.app.Activity
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
        val focusAction: Int? = null
    )

    private val card = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var onBack: () -> Unit = {}
    val isOpen: Boolean get() = visibility == View.VISIBLE

    init {
        visibility = View.GONE
        setBackgroundColor(Ui.BG)
        elevation = Ui.dp(activity, 24).toFloat()
        isFocusableInTouchMode = true
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val center = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(activity, 20), Ui.dp(activity, 32),
                Ui.dp(activity, 20), Ui.dp(activity, 32))
        }
        center.addView(card, LinearLayout.LayoutParams(
            minOf(Ui.dp(activity, 520), activity.resources.displayMetrics.widthPixels - Ui.dp(activity, 40)), -2))
        scroll.addView(center)
        addView(scroll, LayoutParams(-1, -1))
    }

    fun show(page: Page, onBack: () -> Unit) {
        this.onBack = onBack
        visibility = View.VISIBLE
        card.removeAllViews()
        card.addView(Ui.text(activity, page.product, Ui.TITLE, bold = true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(activity, 26) })
        card.addView(Ui.text(activity, page.step, 13f, Ui.ACCENT),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(activity, 10) })
        card.addView(Ui.text(activity, page.title, Ui.DISPLAY, bold = true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(activity, 14) })
        card.addView(Ui.text(activity, page.description, 17f, Ui.TEXT_MUTED),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(activity, 28) })
        val actionRows = ArrayList<View>()
        page.actions.forEachIndexed { index, action ->
            if (index == page.actions.lastIndex) page.status?.let { status ->
                card.addView(Ui.text(activity, status, Ui.SECONDARY, Ui.ACCENT),
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(activity, 10) })
            }
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(activity, 18), Ui.dp(activity, 10),
                    Ui.dp(activity, 18), Ui.dp(activity, 10))
                background = Ui.focusable(activity, if (action.primary) Ui.SELECTED else Ui.RAISED, null)
                isEnabled = action.enabled
                isClickable = action.enabled
                isFocusable = action.enabled
                alpha = if (action.enabled) 1f else .55f
                contentDescription = "${action.title}. ${action.subtitle}"
                setOnClickListener { action.onClick() }
            }
            row.addView(Ui.text(activity, action.title, Ui.TITLE))
            row.addView(Ui.text(activity, action.subtitle, Ui.SECONDARY, Ui.TEXT_MUTED))
            card.addView(row, LinearLayout.LayoutParams(-1, Ui.dp(activity, 76)).apply {
                bottomMargin = Ui.dp(activity, 10)
            })
            actionRows.add(row)
        }
        card.post {
            val requested = page.focusAction?.let(actionRows::getOrNull)?.takeIf { it.isEnabled }
            if (isOpen && requested != null) requested.requestFocusFromTouch()
            else if (isOpen && findFocus()?.isDescendantOf(card) != true)
                (0 until card.childCount).map(card::getChildAt).firstOrNull { it.isFocusable }
                    ?.requestFocusFromTouch()
        }
    }

    fun close() { visibility = View.GONE }
    fun back() { if (isOpen) onBack() }

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
        val widthAvailable = (width - Ui.dp(activity, 40)).coerceAtLeast(Ui.dp(activity, 240))
        card.layoutParams = card.layoutParams.apply { this.width = minOf(Ui.dp(activity, 520), widthAvailable) }
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
