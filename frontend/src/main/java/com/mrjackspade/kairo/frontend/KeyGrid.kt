package com.mrjackspade.kairo.frontend

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

/**
 * Lays out keyboard rows where a key may span rows, as a keypad's tall + and Enter do. Each
 * row shares its width among its own keys by [KeyboardKey.width], around the columns that
 * taller keys from the rows above still occupy.
 */
class KeyGrid(context: Context, private val gap: Int) : ViewGroup(context) {
    /** A key's place: its row, the left and right edges as fractions of the width, and rows spanned. */
    data class Cell(val key: KeyboardKey, val row: Int, val left: Float, val right: Float, val rows: Int)

    private class Params(val cell: Cell) : LayoutParams(0, 0)

    private var rowCount = 1

    fun add(view: View, cell: Cell) {
        rowCount = maxOf(rowCount, cell.row + cell.rows)
        addView(view, Params(cell))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val rowHeight = height.toFloat() / rowCount
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val cell = (child.layoutParams as Params).cell
            val childWidth = ((cell.right - cell.left) * width).roundToInt() - gap * 2
            val childHeight = (rowHeight * cell.rows).roundToInt() - gap * 2
            child.measure(MeasureSpec.makeMeasureSpec(childWidth.coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childHeight.coerceAtLeast(0), MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val rowHeight = (b - t).toFloat() / rowCount
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val cell = (child.layoutParams as Params).cell
            val left = (cell.left * width).roundToInt() + gap
            val top = (cell.row * rowHeight).roundToInt() + gap
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
        }
    }

    companion object {
        fun place(rows: List<List<KeyboardKey>>): List<Cell> {
            val cells = ArrayList<Cell>()
            /** Columns still covered in a later row, by the index of the last row they cover. */
            val covered = ArrayList<Pair<ClosedFloatingPointRange<Float>, Int>>()
            for ((row, keys) in rows.withIndex()) {
                covered.removeAll { it.second < row }
                val taken = covered.map { it.first }.sortedBy { it.start }
                val free = 1f - taken.sumOf { (it.endInclusive - it.start).toDouble() }.toFloat()
                val total = keys.sumOf { it.width.toDouble() }.toFloat()
                var cursor = 0f
                for (key in keys) {
                    while (true) cursor = taken.firstOrNull {
                        cursor >= it.start - EPSILON && cursor < it.endInclusive - EPSILON
                    }?.endInclusive ?: break
                    val right = cursor + key.width / total * free
                    require(taken.none { right > it.start + EPSILON && cursor < it.endInclusive - EPSILON }) {
                        "Key ${key.label} in row $row overlaps a key spanning from an earlier row"
                    }
                    cells.add(Cell(key, row, cursor, right, key.rows))
                    if (key.rows > 1) covered.add(cursor..right to row + key.rows - 1)
                    cursor = right
                }
            }
            return cells
        }

        private const val EPSILON = 0.0001f
    }
}
