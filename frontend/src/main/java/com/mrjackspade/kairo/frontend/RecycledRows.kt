package com.mrjackspade.kairo.frontend

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * Rows as a recycled list: views exist only for the rows on screen, so a page of thousands of
 * rows, such as a library's disks, opens at once. Each row draws its part of its group's card.
 * The list scrolls by itself; the page has no scroll view around it.
 */
internal class RecycledRows(private val context: Context, private val host: RowHost,
                            private val bind: (RowView, SettingsRow) -> Unit) : RowsBody {
    private sealed interface Item
    private class Label(val text: String?) : Item
    private class Row(val row: SettingsRow, val first: Boolean, val last: Boolean) : Item
    private class Foot(val text: String) : Item

    private var items: List<Item> = emptyList()
    private var positions: Map<String, Int> = emptyMap()
    /** The row view inside each recycled row holder. */
    private val bound = HashMap<View, RowView>()

    private val list = ListView(context).apply {
        testId("page.list")
        divider = null
        dividerHeight = 0
        selector = ColorDrawable(Color.TRANSPARENT)
        isFocusable = false
        itemsCanFocus = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        clipToPadding = false
        setPadding(dp(18), 0, dp(18), dp(24))
    }

    /** Capped at the stacked pages' 640 dp column, plus the list's side padding. */
    override val view: View = MaxWidthColumn(context, Ui.dp(context, 640 + 36)).apply {
        addView(list, LinearLayout.LayoutParams(-1, -1))
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 3
        override fun getItemViewType(position: Int) = when (items[position]) {
            is Label -> 0
            is Row -> 1
            is Foot -> 2
        }
        // Rows take touches themselves; the list never selects an item.
        override fun isEnabled(position: Int) = false
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            when (val item = items[position]) {
                is Label -> labelView(item, convertView)
                is Row -> rowView(item, convertView)
                is Foot -> footView(item, convertView)
            }
    }

    init { list.adapter = adapter }

    private fun footView(item: Foot, convertView: View?): View {
        val view = (convertView as? TextView) ?: Ui.text(context, "", Ui.SECONDARY, Ui.TEXT_MUTED)
        view.setPadding(dp(14), dp(8), dp(14), 0)
        view.text = item.text
        return view
    }

    private fun labelView(item: Label, convertView: View?): View {
        val view = (convertView as? PixelTextView) ?: Ui.sectionLabel(context, "")
        view.text = item.text ?: ""
        // A group without a label opens with a gap, as on a stacked page.
        if (item.text != null) view.setPadding(dp(14), dp(18), dp(14), dp(8)) else view.setPadding(0, dp(16), 0, 0)
        return view
    }

    private fun rowView(item: Row, convertView: View?): View {
        val holder = convertView as? LinearLayout ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToOutline = true
            addView(View(context).apply { setBackgroundColor(Ui.LINE) }, LinearLayout.LayoutParams(-1, dp(1)))
        }
        val rowView = bound.getOrPut(holder) {
            RowView(context, host, false).also { holder.addView(it.root, LinearLayout.LayoutParams(-1, -2)) }
        }
        // The divider sits between rows of a group, as the stacked card's dividers do.
        holder.getChildAt(0).visibility = if (item.first) View.GONE else View.VISIBLE
        val radius = Ui.dp(context, Ui.RADIUS_LARGE).toFloat()
        val top = if (item.first) radius else 0f
        val bottom = if (item.last) radius else 0f
        holder.background = GradientDrawable().apply {
            setColor(Ui.SURFACE)
            cornerRadii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
        }
        bind(rowView, item.row)
        TestAttributes.markItem(rowView.line, item.row.id)
        return holder
    }

    override fun render(groups: List<RowGroup>) {
        val flat = ArrayList<Item>()
        val at = HashMap<String, Int>()
        for (group in groups) {
            flat += Label(group.label)
            group.rows.forEachIndexed { index, row ->
                at[row.id] = flat.size
                flat += Row(row, index == 0, index == group.rows.lastIndex)
            }
            group.foot?.let { flat += Foot(it) }
        }
        items = flat
        positions = at
        adapter.notifyDataSetChanged()
    }

    /** Rebinds the rows on screen; the others bind when they scroll in. */
    override fun rebind(groups: List<RowGroup>) = render(groups)

    override fun rowView(id: String): RowView? =
        bound.entries.firstOrNull { (holder, view) -> view.row?.id == id && holder.parent === list }?.value

    override fun revealer(id: String): () -> Unit = { reveal(id) }

    /** Brings [id]'s row fully on screen with a margin, moving the list as little as it can. */
    private fun reveal(id: String) {
        val position = positions[id] ?: return
        list.post {
            val margin = dp(16)
            val first = list.firstVisiblePosition
            val child = list.getChildAt(position - first)
            when {
                list.height == 0 -> list.setSelectionFromTop(position, margin)
                child != null && child.top >= margin && child.bottom <= list.height - margin -> Unit
                child != null && child.top < margin -> list.scrollListBy(child.top - margin)
                child != null -> list.scrollListBy(child.bottom - (list.height - margin))
                position < first -> list.setSelectionFromTop(position, margin)
                else -> list.setSelectionFromTop(position, list.height - margin - rowHeight())
            }
        }
    }

    /** A row's height, from one on screen. */
    private fun rowHeight(): Int = (0 until list.childCount).map(list::getChildAt)
        .firstOrNull { bound.containsKey(it) }?.height ?: dp(48)

    override fun edge(direction: String): Boolean {
        if (direction == "up") list.setSelection(0)
        return true
    }

    override fun position(): Int = list.firstVisiblePosition
    override fun restore(position: Int) {
        if (items.isNotEmpty()) list.setSelection(position.coerceIn(0, items.size - 1))
    }

    private fun dp(value: Int) = Ui.dp(context, value)
}
