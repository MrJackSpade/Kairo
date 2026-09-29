package com.mrjackspade.kairo.frontend

import android.view.InputDevice
import android.view.MotionEvent
import kotlin.math.abs

/** Shared edge navigation for the session menu and guest keyboard. */
class EdgeSwipeNavigation(private val density: Float) {
    enum class Result { PASS, CONSUME, OPEN_MENU, CLOSE_MENU, OPEN_KEYBOARD, REPLAY_GUEST }

    private var startX: Float? = null
    private var startY = 0f
    private var direction = 0 // +1 from the left, -1 from the right, 0 for menu close.
    private var trackingMenu = false
    private var consumed = false
    private var replayStart: Pair<Float, Float>? = null

    fun handle(event: MotionEvent, width: Int, menuOpen: Boolean,
               canOpenMenu: Boolean, canOpenKeyboard: Boolean,
               controlsHit: Boolean): Result {
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) return Result.PASS
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                consumed = false
                startX = null
                replayStart = null
                trackingMenu = menuOpen
                if (menuOpen) {
                    startX = event.x
                    startY = event.y
                    return Result.PASS
                }
                if (controlsHit) return Result.PASS
                direction = when {
                    canOpenMenu && event.x <= 28f * density -> 1
                    canOpenKeyboard && event.x >= width - 28f * density -> -1
                    else -> 0
                }
                if (direction != 0) {
                    startX = event.x
                    startY = event.y
                    return Result.CONSUME
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (consumed) return Result.CONSUME
                val start = startX ?: return Result.PASS
                val horizontal = (event.x - start) * if (trackingMenu) -1 else direction
                if (horizontal >= 72f * density && horizontal >
                    abs(event.y - startY) * 1.3f) {
                    startX = null
                    consumed = true
                    return when {
                        trackingMenu -> Result.CLOSE_MENU
                        direction > 0 -> Result.OPEN_MENU
                        else -> Result.OPEN_KEYBOARD
                    }
                }
                if (!trackingMenu) {
                    val vertical = abs(event.y - startY)
                    if (horizontal <= -24f * density ||
                        (vertical >= 24f * density && vertical * 1.3f >= abs(horizontal))) {
                        replayStart = start to startY
                        startX = null
                        return Result.REPLAY_GUEST
                    }
                    return Result.CONSUME
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (startX != null && !trackingMenu) {
                    replayStart = startX!! to startY
                    startX = null
                    return Result.REPLAY_GUEST
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasTrackingEdge = startX != null && !trackingMenu
                if (wasTrackingEdge && event.actionMasked == MotionEvent.ACTION_UP)
                    replayStart = startX!! to startY
                startX = null
                val wasConsumed = consumed
                consumed = false
                trackingMenu = false
                return when {
                    wasTrackingEdge && event.actionMasked == MotionEvent.ACTION_UP ->
                        Result.REPLAY_GUEST
                    wasTrackingEdge || wasConsumed -> Result.CONSUME
                    else -> Result.PASS
                }
            }
        }
        return Result.PASS
    }

    /** Restore a tap or diagonal drag after withholding its edge ACTION_DOWN. */
    fun replay(event: MotionEvent, dispatch: (MotionEvent) -> Boolean): Boolean {
        val (x, y) = replayStart ?: return dispatch(event)
        replayStart = null
        val down = MotionEvent.obtain(event.downTime, event.downTime,
            MotionEvent.ACTION_DOWN, x, y, 0).apply { source = event.source }
        val accepted = try { dispatch(down) } finally { down.recycle() }
        return dispatch(event) || accepted
    }

    fun reset() {
        startX = null
        trackingMenu = false
        consumed = false
        replayStart = null
    }
}
