package com.mrjackspade.kairo.frontend

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The same in-game menu structure and controls for every Kairo emulator. */
data class SessionAction(val label: String, val icon: Int, val action: () -> Unit)

class SessionDrawer(
    context: Context,
    root: FrameLayout,
    productName: String,
    private val closeRequested: () -> Unit,
    statusClicked: () -> Unit,
    actions: List<SessionAction>,
    sessionEntries: List<SettingsEntry>,
    settings: List<SettingsEntry>
) {
    val backdrop = View(context).apply {
        setBackgroundColor(Ui.SCRIM)
        visibility = View.GONE
        alpha = 0f
        setOnClickListener { closeRequested() }
    }
    val drawer = ScrollView(context).apply {
        visibility = View.GONE
        elevation = dp(context, 16).toFloat()
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_NEVER
        setBackgroundColor(Ui.SURFACE)
    }
    val mediaLabel = Ui.text(context, "", Ui.TITLE, bold = true).apply {
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(dp(context, 12), 0, dp(context, 12), dp(context, 2))
    }
    val status = Ui.text(context, "", Ui.SECONDARY, Ui.TEXT_MUTED).apply {
        maxLines = 4
        setPadding(dp(context, 12), 0, dp(context, 12), dp(context, 14))
        contentDescription = "Machine status. Tap for details."
        setOnClickListener { statusClicked() }
    }
    private val items = ArrayList<View>()
    private val values = ArrayList<Pair<TextView, () -> String>>()
    var selectedIndex = 0
        private set
    var isOpen = false
        private set

    init {
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        val width = minOf(dp(context, 320), context.resources.displayMetrics.widthPixels - dp(context, 40))
        root.addView(drawer, FrameLayout.LayoutParams(width, -1, Gravity.START))
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), dp(context, 20), dp(context, 12), dp(context, 24))
        }
        drawer.addView(content)
        content.addView(PixelTextView(context).apply {
            text = productName
            scale = 2
            setPadding(dp(context, 12), 0, dp(context, 12), dp(context, 14))
        })
        content.addView(mediaLabel)
        content.addView(status)
        val actionRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(actionRow, LinearLayout.LayoutParams(-1, dp(context, 66)).apply {
            bottomMargin = dp(context, 8)
        })
        actions.forEach { action ->
            val button = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                contentDescription = action.label
                isFocusable = true
                isClickable = true
                background = Ui.rowBackground(context)
                setOnClickListener { clicked ->
                    focus(items.indexOf(clicked))
                    action.action()
                }
            }
            button.addView(Ui.icon(context, action.icon,
                if (action.label == "Resume") Ui.ACCENT else Ui.TEXT))
            button.addView(Ui.text(context, action.label, Ui.LABEL, Ui.TEXT_MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 4), 0, 0)
            })
            actionRow.addView(button, LinearLayout.LayoutParams(0, -1, 1f))
            items.add(button)
        }
        content.addView(Ui.sectionLabel(context, "SESSION"))
        sessionEntries.forEach { addEntry(content, it) }
        content.addView(Ui.sectionLabel(context, "SETTINGS"))
        settings.forEach { addEntry(content, it) }
    }

    private fun addEntry(content: LinearLayout, entry: SettingsEntry) {
        val row = Ui.actionRow(drawer.context, entry.title, entry.value) { clicked ->
            focus(items.indexOf(clicked))
            entry.action()
        }
        content.addView(row.view, LinearLayout.LayoutParams(-1, -2))
        items.add(row.view)
        values.add(row.detail to entry.value)
    }

    fun refreshValues() { values.forEach { (label, value) -> label.text = value() } }

    fun focus(index: Int) {
        if (items.isEmpty()) return
        items[selectedIndex].isSelected = false
        selectedIndex = index.coerceIn(0, items.lastIndex)
        val item = items[selectedIndex]
        item.isSelected = true
        item.requestFocus()
        val margin = dp(drawer.context, 16)
        if (drawer.height > 0 && item.bottom > drawer.scrollY + drawer.height)
            drawer.smoothScrollTo(0, item.bottom - drawer.height + margin)
        else if (item.top < drawer.scrollY)
            drawer.smoothScrollTo(0, (item.top - margin).coerceAtLeast(0))
    }

    fun activateSelected() { items.getOrNull(selectedIndex)?.performClick() }

    fun open(title: String, statusText: String) {
        if (isOpen) return
        isOpen = true
        drawer.scrollTo(0, 0)
        focus(0)
        refreshValues()
        mediaLabel.text = title
        status.text = statusText
        backdrop.visibility = View.VISIBLE
        backdrop.alpha = 0f
        drawer.visibility = View.VISIBLE
        drawer.translationX = -drawer.layoutParams.width.toFloat()
        backdrop.animate().alpha(1f).setDuration(180).start()
        drawer.animate().translationX(0f).setDuration(180).start()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        backdrop.animate().alpha(0f).setDuration(160).withEndAction {
            if (!isOpen) backdrop.visibility = View.GONE
        }.start()
        drawer.animate().translationX(-drawer.layoutParams.width.toFloat())
            .setDuration(160).withEndAction {
                if (!isOpen) drawer.visibility = View.GONE
            }.start()
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
