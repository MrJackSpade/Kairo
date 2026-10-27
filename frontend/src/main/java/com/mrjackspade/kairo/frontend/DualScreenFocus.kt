package com.mrjackspade.kairo.frontend

import android.view.KeyEvent

/** A screen's D-pad focus on a two-screen device, which it hands to the other screen at an edge. */
interface FocusRegion {
    /** True while the other screen holds the focus: nothing here shows as focused. */
    var focusAway: Boolean
    /** Runs when a move finds nothing further in its direction ("up" or "down"); true when another screen took the focus. */
    var crossEdge: (String) -> Boolean
    /** Takes the focus at the edge facing the screen it came from: moving "down" lands on the first element, "up" on the last. */
    fun enter(direction: String)
}

/** A region on the screen whose keys reach the frontend only through [DualScreenFocus]. */
interface ForwardedFocusRegion : FocusRegion {
    /** Whether it is on screen with something to focus. */
    val canFocus: Boolean
    fun key(control: String?, event: KeyEvent): Boolean
}

/**
 * The D-pad focus across a two-screen device's screens (Kairo#53). Android's focus never crosses
 * windows, and every key from either screen reaches the upper screen's activity, so the frontend
 * moves the focus itself: down past the upper screen's last element lands on the lower screen's
 * first, and up past the lower screen's first returns to the upper screen's last. While the lower
 * screen holds it, the D-pad, A and B go there and the upper screen shows nothing focused.
 */
class DualScreenFocus(
    /** Every region of the upper screen, which all show no focus while the lower screen holds it. */
    private val upperRegions: List<FocusRegion>,
    /** The upper screen's region that takes the D-pad now, or null when none does. */
    private val upper: () -> FocusRegion?,
    /** The lower screen's region, or null without one. */
    private val lower: () -> ForwardedFocusRegion?
) {
    private var held: ForwardedFocusRegion? = null

    init {
        upperRegions.forEach { it.crossEdge = ::cross }
    }

    /** Whether the lower screen holds the focus. */
    val onLower: Boolean get() = held != null

    fun cross(direction: String): Boolean {
        if (direction == "down" && held == null) {
            val target = lower()?.takeIf { it.canFocus } ?: return false
            upperRegions.forEach { it.focusAway = true }
            held = target
            target.crossEdge = ::cross
            target.focusAway = false
            target.enter("down")
            return true
        }
        if (direction == "up" && held != null) {
            giveBack()
            upper()?.enter("up")
            return true
        }
        return false
    }

    /**
     * A menu control while the lower screen may hold the focus: true when the lower screen took
     * it. Menu, or a lower screen that no longer shows anything to focus, returns the focus to
     * the upper screen where it was, and the key then goes on to the upper screen.
     */
    fun key(control: String?, event: KeyEvent): Boolean {
        val region = held ?: return false
        if (region !== lower() || !region.canFocus || control !in LOWER_CONTROLS) {
            giveBack()
            return false
        }
        return region.key(control, event)
    }

    /** Returns the focus to the upper screen where it was. */
    fun release() { if (held != null) giveBack() }

    private fun giveBack() {
        held?.focusAway = true
        held = null
        upperRegions.forEach { it.focusAway = false }
    }

    private companion object {
        val LOWER_CONTROLS = setOf("up", "down", "left", "right", "a", "b")
    }
}
