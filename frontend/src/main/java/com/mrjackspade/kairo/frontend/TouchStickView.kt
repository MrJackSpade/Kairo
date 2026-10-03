package com.mrjackspade.kairo.frontend

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/** A pointer-owned circle pad. Its thumb center can reach, but never cross, the rim. */
class TouchStickView(context: Context, private val moved: (Float, Float) -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val circle = Path()
    private var pointer = -1
    private var stickX = 0f
    private var stickY = 0f
    private val radius get() = (min(width, height) / 2f - Ui.dp(context, 2)).coerceAtLeast(1f)

    fun reset() {
        pointer = -1
        stickX = 0f
        stickY = 0f
        moved(0f, 0f)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointer = event.getPointerId(event.actionIndex)
                parent?.requestDisallowInterceptTouchEvent(true)
                update(event, event.actionIndex)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (index >= 0) update(event, index) else reset()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                if (event.getPointerId(event.actionIndex) == pointer) reset()
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }

    private fun update(event: MotionEvent, index: Int) {
        val x = (event.getX(index) - width / 2f) / radius
        val y = (event.getY(index) - height / 2f) / radius
        val scale = hypot(x, y).coerceAtLeast(1f)
        stickX = x / scale
        stickY = y / scale
        moved(stickX, stickY)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = radius
        paint.style = Paint.Style.FILL
        paint.color = 0x55253344
        canvas.drawCircle(cx, cy, r, paint)
        circle.reset()
        circle.addCircle(cx, cy, r, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(circle)
        paint.color = 0xffd8e8ef.toInt()
        canvas.drawCircle(cx + stickX * r, cy + stickY * r, r * .36f, paint)
        canvas.restoreToCount(save)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = Ui.dp(context, 2).toFloat()
        paint.color = 0xccffffff.toInt()
        canvas.drawCircle(cx, cy, r, paint)
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }
}
