package com.mrjackspade.kairo.frontend

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The optional round 8-way D-pad. It only draws; the consuming screen owns touch handling.
 */
class DpadView(context: Context) : View(context) {
    var held: Set<String> = emptySet()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xb5253344.toInt() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = 0xccffffff.toInt()
    }
    private val active = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xaa5fdde4.toInt() }
    private val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = density
        color = 0x40ffffff
    }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xeeffffff.toInt() }
    private val bounds = RectF()
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - density
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawOval(bounds, fill)
        // Wedges run clockwise from right, 45 degrees each, matching the touch sectors.
        SECTORS.forEachIndexed { index, directions ->
            if (directions.isNotEmpty() && held.containsAll(directions) && held.size == directions.size)
                canvas.drawArc(bounds, index * 45f - 22.5f, 45f, true, active)
        }
        for (index in 0 until 8) {
            val angle = Math.toRadians(index * 45.0 + 22.5)
            canvas.drawLine(cx + cos(angle).toFloat() * radius * 0.28f, cy + sin(angle).toFloat() * radius * 0.28f,
                cx + cos(angle).toFloat() * radius, cy + sin(angle).toFloat() * radius, divider)
        }
        canvas.drawOval(bounds, ring)
        for (index in 0 until 8) {
            val angle = Math.toRadians(index * 45.0)
            val distance = radius * 0.68f
            val size = radius * if (index % 2 == 0) 0.16f else 0.1f
            drawArrow(canvas, cx + cos(angle).toFloat() * distance, cy + sin(angle).toFloat() * distance,
                angle, size)
        }
    }

    private fun drawArrow(canvas: Canvas, x: Float, y: Float, angle: Double, size: Float) {
        val tip = size
        val side = size * 0.9f
        path.reset()
        path.moveTo(x + cos(angle).toFloat() * tip, y + sin(angle).toFloat() * tip)
        path.lineTo(x + cos(angle + 2.3).toFloat() * side, y + sin(angle + 2.3).toFloat() * side)
        path.lineTo(x + cos(angle - 2.3).toFloat() * side, y + sin(angle - 2.3).toFloat() * side)
        path.close()
        canvas.drawPath(path, arrow)
    }

    companion object {
        /** Directions for each 45-degree sector, clockwise from right (screen y points down). */
        val SECTORS = listOf(
            setOf("right"), setOf("down", "right"), setOf("down"), setOf("down", "left"),
            setOf("left"), setOf("up", "left"), setOf("up"), setOf("up", "right"))
    }
}
