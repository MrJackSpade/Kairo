package com.mrjackspade.kairo.frontend

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** Relative mouse surface for the keyboard display, independent of game-screen taps. */
class SecondaryTouchpadView(
    context: Context,
    private val mouse: MouseInputRouter,
    private val referenceSize: () -> Pair<Int, Int> = { 640 to 400 }
) : View(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val owner = "secondary-touchpad:${hashCode()}"
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var fractionX = 0f
    private var fractionY = 0f
    private var moved = false
    private var dragging = false
    private var hold: Runnable? = null
    private var release: Runnable? = null
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.TEXT_MUTED
        textAlign = Paint.Align.CENTER
        textSize = 15f * resources.displayMetrics.scaledDensity
    }

    init {
        setBackgroundColor(Ui.RAISED)
        contentDescription = "Guest mouse touchpad. Drag to move, tap to click, hold to drag."
        isClickable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPending()
                mouse.release(owner)
                startX = event.x
                startY = event.y
                lastX = event.x
                lastY = event.y
                fractionX = 0f
                fractionY = 0f
                moved = false
                dragging = false
                hold = Runnable {
                    if (!moved) {
                        dragging = true
                        mouse.hold(owner, "leftButton")
                    }
                    hold = null
                }.also { handler.postDelayed(it, 500) }
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - startX) > 12 * resources.displayMetrics.density ||
                    abs(event.y - startY) > 12 * resources.displayMetrics.density) {
                    moved = true
                    hold?.let(handler::removeCallbacks)
                    hold = null
                }
                move(event)
            }
            MotionEvent.ACTION_UP -> {
                hold?.let(handler::removeCallbacks)
                hold = null
                move(event)
                if (dragging) mouse.release(owner)
                else if (!moved) {
                    mouse.hold(owner, "leftButton")
                    release = Runnable {
                        mouse.release(owner)
                        release = null
                    }.also { handler.postDelayed(it, 700) }
                }
                dragging = false
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> close()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val center = height / 2f
        canvas.drawText("Drag to move · Tap to click", width / 2f, center - hint.textSize / 2f, hint)
        canvas.drawText("Hold to drag", width / 2f, center + hint.textSize, hint)
    }

    private fun move(event: MotionEvent) {
        if (width <= 0 || height <= 0) return
        val (referenceWidth, referenceHeight) = referenceSize()
        fractionX += (event.x - lastX) * referenceWidth / width
        fractionY += (event.y - lastY) * referenceHeight / height
        lastX = event.x
        lastY = event.y
        val dx = fractionX.toInt().coerceIn(-referenceWidth, referenceWidth)
        val dy = fractionY.toInt().coerceIn(-referenceHeight, referenceHeight)
        fractionX -= dx
        fractionY -= dy
        mouse.moveBy(dx, dy)
    }

    private fun cancelPending() {
        hold?.let(handler::removeCallbacks)
        release?.let(handler::removeCallbacks)
        hold = null
        release = null
    }

    fun close() {
        cancelPending()
        mouse.release(owner)
        dragging = false
    }

    override fun onDetachedFromWindow() {
        close()
        super.onDetachedFromWindow()
    }
}
