package com.mrjackspade.kairo.frontend

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Where an integer-scaled picture sits on the screen (Kairo#54): the picture's matching corner,
 * edge midpoint or center lies on the screen's, whether it is smaller than the screen (the gap
 * goes to the other side) or larger (the overflow is cut from the other side).
 */
enum class Anchor(val id: String, val label: String, val column: Int, val row: Int) {
    TOP_LEFT("top-left", "Top Left", 0, 0),
    TOP_CENTER("top-center", "Top Center", 1, 0),
    TOP_RIGHT("top-right", "Top Right", 2, 0),
    CENTER_LEFT("center-left", "Center Left", 0, 1),
    CENTER("center", "Center", 1, 1),
    CENTER_RIGHT("center-right", "Center Right", 2, 1),
    BOTTOM_LEFT("bottom-left", "Bottom Left", 0, 2),
    BOTTOM_CENTER("bottom-center", "Bottom Center", 1, 2),
    BOTTOM_RIGHT("bottom-right", "Bottom Right", 2, 2);

    /** The offset that places a [size] picture in [space] along the columns (or rows, with [row]). */
    fun offset(space: Int, size: Int, row: Boolean): Int {
        val free = space - size
        return when (if (row) this.row else column) {
            0 -> 0
            1 -> free / 2
            else -> free
        }
    }

    companion object {
        val IDS: Set<String> = entries.map { it.id }.toSet()
        fun of(id: String): Anchor = requireNotNull(entries.firstOrNull { it.id == id }) { "Unknown anchor $id" }
        /** Landscape centers the picture; portrait keeps it at the top, under the notch padding. */
        fun default(orientation: String): Anchor = if (orientation == "portrait") TOP_CENTER else CENTER
    }
}

/**
 * Picks an [Anchor] from a 3x3 grid of squares, as an image editor's canvas anchor does. [selected]
 * is the chosen anchor's ID, or null for a game that uses its default; [default] names that
 * default for a game's page, which then offers it below the grid, and is null for all games.
 */
class AnchorPage(
    title: String,
    scope: () -> String,
    private val selected: () -> String?,
    /** The anchor in effect: [selected], else the default. */
    private val effective: () -> String,
    private val default: (() -> String)?,
    private val select: (String?) -> Unit,
    /** The row that opened the page, flashed once the pick returns to it. */
    private val rowId: String
) : GroupsPage(title, scope, { emptyList() }) {
    private val squares = LinkedHashMap<Anchor, TextView>()

    override fun groups(): List<RowGroup> = default?.let { label ->
        listOf(RowGroup(null, listOf(SettingsRow.option("o:default", "Default", label(),
            { selected() == null }) { pick(null) })))
    } ?: emptyList()

    override fun createView(): View {
        val page = super.createView() as LinearLayout
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(12), 0, dp(12))
        }
        for (row in 0..2) {
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (column in 0..2) {
                val anchor = Anchor.entries.first { it.row == row && it.column == column }
                val square = TextView(context).apply {
                    gravity = Gravity.CENTER
                    textSize = 22f
                    contentDescription = anchor.label
                    isClickable = true
                    setOnClickListener { pick(anchor.id) }
                }.testId("anchor", anchor.id)
                squares[anchor] = square
                line.addView(square, LinearLayout.LayoutParams(dp(64), dp(64)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            grid.addView(line)
        }
        page.addView(FrameLayout(context).apply { addView(grid, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL)) }, 1,
            LinearLayout.LayoutParams(-1, -2))
        paint()
        return page
    }

    /**
     * The anchor in effect is marked, filled when chosen here and hollow when it is the default;
     * the other squares point away from it, as the picture grows away from its anchor.
     */
    private fun paint() {
        val current = Anchor.of(effective())
        val chosen = selected() != null
        for ((anchor, square) in squares) {
            val isCurrent = anchor == current
            square.background = Ui.focusable(context, if (isCurrent && chosen) Ui.ACCENT else Ui.RAISED, Ui.LINE)
            square.setTextColor(if (isCurrent && chosen) Ui.ON_ACCENT else Ui.TEXT_MUTED)
            square.text = if (isCurrent) (if (chosen) "●" else "○")
                else ARROWS[(anchor.row - current.row).coerceIn(-1, 1) + 1][(anchor.column - current.column).coerceIn(-1, 1) + 1]
        }
    }

    override fun navItems(): List<NavItem> =
        squares.map { (anchor, square) -> NavItem("a:${anchor.id}", square, anchor.row, (anchor.column + 0.5f) / 3f, { pick(anchor.id) }) } +
            rowNav(3)

    override fun initialFocus(items: List<NavItem>): String =
        selected()?.let { "a:$it" } ?: if (default != null) "o:default" else "a:${Anchor.CENTER.id}"

    override fun refresh() {
        super.refresh()
        if (squares.isNotEmpty()) paint()
    }

    private fun pick(id: String?) {
        select(id)
        refresh()
        view.postDelayed({
            if (stack.top === this) {
                stack.back()
                stack.flash(rowId)
            }
        }, 150)
    }

    private companion object {
        val ARROWS = arrayOf(arrayOf("↖", "↑", "↗"), arrayOf("←", "", "→"), arrayOf("↙", "↓", "↘"))
    }
}
