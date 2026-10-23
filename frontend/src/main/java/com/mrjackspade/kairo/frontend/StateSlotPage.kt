package com.mrjackspade.kairo.frontend

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.DateFormat
import java.util.Date

/**
 * Save state and Load state: four slot cards. Saving to an empty slot runs at once; replacing
 * or loading asks on the card itself, with Cancel focused.
 */
class StateSlotPage(
    private val saving: Boolean,
    private val title: String?,
    private val load: () -> List<Slot>,
    private val busy: () -> Boolean,
    private val onSave: (Int) -> Unit,
    private val onLoad: (Int) -> Unit
) : Page() {
    class Slot(val index: Int, val savedAt: Long?, val thumbnail: Bitmap?)

    override val testName: String get() = if (saving) "Save state" else "Load state"

    private class Card(val slot: Slot, val view: FrameLayout, val body: LinearLayout,
                       val spinner: View)

    private val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    private lateinit var scrollView: ScrollView
    private lateinit var grid: LinearLayout
    private val cards = ArrayList<Card>()
    private var slots: List<Slot> = emptyList()
    private var confirm: Pair<Int, View>? = null
    private var confirmFocus = 0
    private var confirmButtons: List<View> = emptyList()
    private var confirmAction: (() -> Unit)? = null
    private var running: Int? = null
    override val scroll: ScrollView get() = scrollView

    override fun createView(): View {
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        page.addView(header(if (saving) "Save state" else "Load state", title), LinearLayout.LayoutParams(-1, dp(56)))
        grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        scrollView = PageParts.scroll(this, grid, intArrayOf(dp(18), 0, dp(18), dp(24)))
        page.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        render()
        return page
    }

    private fun render() {
        confirm = null
        grid.removeAllViews()
        cards.clear()
        recycle()
        slots = load()
        slots.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { column, slot ->
                val card = card(slot)
                cards += card
                row.addView(card.view, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (column > 0) marginStart = dp(12)
                })
            }
            if (pair.size == 1) row.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = dp(12) })
            grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (rowIndex > 0) topMargin = dp(12) })
        }
    }

    private fun card(slot: Slot): Card {
        val frame = FrameLayout(context)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = Ui.focusable(context, Ui.SURFACE, null)
            isClickable = true
            contentDescription = "Slot ${slot.index}" + (slot.savedAt?.let { ", ${format.format(Date(it))}" } ?: ", empty")
            setOnClickListener { activate(slot) }
        }
        val thumbnail = object : FrameLayout(context) {
            override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                val width = MeasureSpec.getSize(widthSpec)
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY))
            }
        }.apply {
            background = GradientDrawable().apply {
                setColor(Ui.BG)
                cornerRadius = dp(Ui.RADIUS_SMALL).toFloat()
                if (slot.thumbnail == null) setStroke(dp(1), Ui.LINE, dp(4).toFloat(), dp(3).toFloat())
            }
            clipToOutline = true
        }
        if (slot.thumbnail != null) thumbnail.addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(slot.thumbnail)
        }, FrameLayout.LayoutParams(-1, -1))
        else thumbnail.addView(Ui.text(context, "Empty", Ui.SECONDARY, Ui.TEXT_FAINT),
            FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        val spinner = Ui.spinner(context, 24, 3).apply { visibility = View.GONE }
        thumbnail.addView(spinner, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        body.addView(thumbnail, LinearLayout.LayoutParams(-1, -2))
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labels.addView(Ui.text(context, "Slot ${slot.index}", Ui.BODY))
        labels.addView(Ui.text(context, slot.savedAt?.let { format.format(Date(it)) } ?: "", Ui.SECONDARY, Ui.TEXT_MUTED).apply {
            gravity = Gravity.END
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        body.addView(labels, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        frame.addView(body, FrameLayout.LayoutParams(-1, -2))
        body.alpha = if (!saving && slot.savedAt == null) 0.45f else 1f
        return Card(slot, frame, body, spinner)
    }

    /** Cards use the selected fill as well as the outline, like a focused row. */
    override fun navItems(): List<NavItem> = cards.mapIndexed { index, card ->
        NavItem("slot${card.slot.index}", card.body, index / 2, if (index % 2 == 0) 0.25f else 0.75f,
            { activate(card.slot) }) { selected ->
            card.body.isSelected = selected
            card.body.background = Ui.focusable(context, if (selected) Ui.SELECTED else Ui.SURFACE, null)
        }
    }

    private fun activate(slot: Slot) {
        if (confirm != null) closeConfirm()
        focus("slot${slot.index}", reveal = false)
        if (busy()) return
        val savedAt = slot.savedAt
        when {
            !saving && savedAt == null -> Ui.message(stack.activity, "This slot is empty")
            saving && savedAt == null -> start(slot.index) { onSave(slot.index) }
            saving -> openConfirm(slot, "Replace the save from ${format.format(Date(savedAt!!))}?", "Overwrite") {
                start(slot.index) { onSave(slot.index) }
            }
            else -> openConfirm(slot, "Progress since your last save is lost.", "Load") {
                start(slot.index) { onLoad(slot.index) }
            }
        }
    }

    private fun start(index: Int, action: () -> Unit) {
        running = index
        cards.forEach { card ->
            card.spinner.visibility = if (card.slot.index == index) View.VISIBLE else View.GONE
            card.body.alpha = if (card.slot.index == index) 1f else 0.45f
        }
        action()
    }

    /** The save or load finished; show the new thumbnail and date. */
    override fun refresh() {
        if (busy()) return
        running = null
        render()
        renav()
    }

    private fun openConfirm(slot: Slot, message: String, verb: String, run: () -> Unit) {
        val card = cards.firstOrNull { it.slot == slot } ?: return
        val overlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(0xf2151b25.toInt())
                cornerRadius = dp(Ui.RADIUS_LARGE).toFloat()
                setStroke(dp(2), Ui.ACCENT)
            }
        }
        overlay.addView(Ui.text(context, message, Ui.SECONDARY, Ui.TEXT_BODY).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2))
        val buttons = WrapLayout(context, dp(8)).apply { alignEnd = false }
        val cancel = Ui.toneButton(context, "Cancel", ButtonTone.SECONDARY, minWidth = 96) { closeConfirm() }
        val primary = Ui.toneButton(context, verb, ButtonTone.PRIMARY, minWidth = 96) {
            closeConfirm()
            run()
        }
        buttons.addView(cancel, LinearLayout.LayoutParams(-2, dp(44)))
        buttons.addView(primary, LinearLayout.LayoutParams(-2, dp(44)))
        val center = FrameLayout(context).apply {
            addView(buttons, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))
        }
        overlay.addView(center, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        card.view.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        confirm = slot.index to overlay
        confirmButtons = listOf(cancel, primary)
        confirmAction = run
        confirmFocus = 0
        selectConfirm()
    }

    private fun selectConfirm() = confirmButtons.forEachIndexed { index, view -> view.isSelected = index == confirmFocus }

    private fun closeConfirm() {
        val (index, overlay) = confirm ?: return
        cards.firstOrNull { it.slot.index == index }?.view?.removeView(overlay)
        confirm = null
        confirmButtons = emptyList()
        confirmAction = null
    }

    override fun back(): Boolean {
        if (confirm == null) return false
        closeConfirm()
        return true
    }

    override fun handleKey(control: String?, event: KeyEvent): Boolean {
        if (confirm == null) return super.handleKey(control, event)
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0
        when (control) {
            "left", "right" -> if (down) {
                confirmFocus = (confirmFocus + if (control == "right") 1 else -1).coerceIn(0, confirmButtons.lastIndex)
                selectConfirm()
            }
            "a" -> if (first) confirmButtons.getOrNull(confirmFocus)?.performClick()
            "b", "up", "down" -> if (first || (down && control != "b")) closeConfirm()
            "menu" -> if (first) stack.menuKey()
            else -> return false
        }
        return true
    }

    private fun recycle() {
        slots.forEach { it.thumbnail?.recycle() }
        slots = emptyList()
    }

    override fun onRemoved() {
        cards.forEach { card -> (card.body.getChildAt(0) as? FrameLayout)?.let { thumb ->
            (thumb.getChildAt(0) as? ImageView)?.setImageDrawable(null)
        } }
        recycle()
    }
}
