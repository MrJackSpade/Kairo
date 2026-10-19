package com.mrjackspade.kairo.frontend

import org.junit.Assert.*
import org.junit.Test

class KeyGridTest {
    private fun key(label: String, width: Float = 1f, rows: Int = 1) = KeyboardKey(label, label.hashCode(), width, rows = rows)

    private fun bounds(cells: List<KeyGrid.Cell>, label: String) =
        cells.single { it.key.label == label }.let { listOf(it.row.toFloat(), it.left, it.right, it.rows.toFloat()) }

    private fun assertBounds(expected: List<Float>, actual: List<Float>) =
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 0.0001f) }

    @Test fun plainRowsShareTheirWidthByWeight() {
        val cells = KeyGrid.place(listOf(listOf(key("a"), key("b", 3f)), listOf(key("c"))))
        assertBounds(listOf(0f, 0f, 0.25f, 1f), bounds(cells, "a"))
        assertBounds(listOf(0f, 0.25f, 1f, 1f), bounds(cells, "b"))
        assertBounds(listOf(1f, 0f, 1f, 1f), bounds(cells, "c"))
    }

    @Test fun keypadTallKeysLeaveTheirColumnsInTheNextRow() {
        val cells = KeyGrid.place(listOf(
            listOf(key("7"), key("8"), key("9"), key("+", rows = 2)),
            listOf(key("4"), key("5"), key("6")),
            listOf(key("1"), key("2"), key("3"), key("Enter", rows = 2)),
            listOf(key("0", 2f), key("."))))
        assertBounds(listOf(0f, 0.75f, 1f, 2f), bounds(cells, "+"))
        assertBounds(listOf(1f, 0f, 0.25f, 1f), bounds(cells, "4"))
        assertBounds(listOf(1f, 0.5f, 0.75f, 1f), bounds(cells, "6"))
        assertBounds(listOf(3f, 0f, 0.5f, 1f), bounds(cells, "0"))
        assertBounds(listOf(3f, 0.5f, 0.75f, 1f), bounds(cells, "."))
        assertEquals(13, cells.size)
    }

    @Test fun aSpanInTheMiddleIsSkipped() {
        val cells = KeyGrid.place(listOf(listOf(key("a"), key("tall", rows = 2), key("b")),
            listOf(key("c"), key("d"))))
        assertBounds(listOf(1f, 0f, 1f / 3, 1f), bounds(cells, "c"))
        assertBounds(listOf(1f, 2f / 3, 1f, 1f), bounds(cells, "d"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aKeyOverlappingASpanIsRejected() {
        KeyGrid.place(listOf(listOf(key("a"), key("tall", rows = 2), key("b")), listOf(key("wide", 3f), key("c"))))
    }
}
