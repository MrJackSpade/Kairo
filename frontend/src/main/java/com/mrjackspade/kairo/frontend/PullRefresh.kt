package com.mrjackspade.kairo.frontend

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.AbsListView
import android.widget.FrameLayout

/**
 * Pull to refresh, as Android's lists do (Kairo#185): a drag down that starts while [list] is
 * scrolled to its top pulls a spinner disc down after it; let go past [THRESHOLD_DP] and [pulled]
 * runs, which says whether a refresh is now running ([refreshing] then keeps the disc spinning at
 * rest until it is set false), else the disc goes back up. Taps, and drags anywhere but the top,
 * reach the list as before: only a drag that moves down past the touch slop while the list cannot
 * scroll further up is taken.
 */
class PullRefresh(context: Context, private val list: AbsListView, private val pulled: () -> Boolean) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val size = Ui.dp(context, 40)
    private val threshold = Ui.dp(context, THRESHOLD_DP).toFloat()
    /** How far down the disc rests while refreshing. */
    private val rest = Ui.dp(context, 64).toFloat()
    private val spinner = SpinnerView(context, Ui.dp(context, 3)).apply { spinning = false }
    private val disc = FrameLayout(context).apply {
        background = Ui.rounded(context, Ui.SURFACE, 999, Ui.LINE)
        elevation = Ui.dp(context, 4).toFloat()
        addView(spinner, LayoutParams(Ui.dp(context, 22), Ui.dp(context, 22), Gravity.CENTER))
        visibility = View.GONE
    }
    private var start = 0f
    private var tracking = false
    private var dragging = false
    private var distance = 0f

    init {
        addView(list, LayoutParams(-1, -1))
        addView(disc, LayoutParams(size, size, Gravity.CENTER_HORIZONTAL or Gravity.TOP))
    }

    /** A refresh is running: the disc spins at rest until this is set false. */
    var refreshing = false
        set(value) {
            if (field == value) return
            field = value
            if (value) settle() else hide()
        }

    private fun atTop() = !list.canScrollVertically(-1)

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                start = event.y
                dragging = false
                tracking = !refreshing && atTop()
            }
            MotionEvent.ACTION_MOVE -> if (tracking && !dragging && event.y - start > slop && atTop()) {
                dragging = true
                start = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> pull((event.y - start) * RESISTANCE)
            MotionEvent.ACTION_UP -> {
                dragging = false
                tracking = false
                if (distance >= threshold && pulled()) refreshing = true else hide()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                tracking = false
                hide()
            }
        }
        return true
    }

    /** The disc [pull] down: it follows the finger, turning and growing opaque toward the threshold. */
    private fun pull(pull: Float) {
        distance = pull.coerceIn(0f, threshold * 1.5f)
        disc.animate().cancel()
        disc.visibility = View.VISIBLE
        disc.translationY = distance - size
        disc.alpha = (distance / threshold).coerceAtMost(1f)
        spinner.rotation = distance / threshold * 270f
    }

    private fun settle() {
        disc.visibility = View.VISIBLE
        spinner.spinning = true
        disc.animate().translationY(rest - size).alpha(1f).setDuration(ANIMATION_MS).withEndAction(null).start()
    }

    private fun hide() {
        distance = 0f
        spinner.spinning = false
        if (disc.visibility != View.VISIBLE) return
        disc.animate().translationY(-size.toFloat()).alpha(0f).setDuration(ANIMATION_MS)
            .withEndAction { if (!refreshing && !dragging) disc.visibility = View.GONE }.start()
    }

    private companion object {
        /** How far, in dp, the disc is pulled before letting go refreshes. */
        const val THRESHOLD_DP = 72
        /** The disc moves half as far as the finger, as Android's does. */
        const val RESISTANCE = 0.5f
        const val ANIMATION_MS = 180L
    }
}
