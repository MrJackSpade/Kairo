package com.mrjackspade.kairo.frontend

import android.view.MotionEvent

/**
 * What a Bluetooth or USB mouse's captured reports send the guest (Kairo#175): its counts scaled
 * by [speed], its buttons as each mouse presses and releases them, and its wheel's whole notches.
 * [PhysicalMouse] takes the reports from Android; this part has none of Android in it.
 */
class PhysicalMouseInput(private val mouse: MouseInputRouter,
                         /** Whether the guest's mouse has a middle button for the mouse's own. */
                         middleButton: Boolean,
                         /** Wheel notches to the guest, positive toward the player; null when its mouse has no wheel. */
                         private val wheel: ((Int) -> Unit)?,
                         /** Counts sent to the guest per count the mouse reports. */
                         private val speed: () -> Float) {
    private val buttonTargets = BUTTONS +
        (if (middleButton) mapOf(MotionEvent.BUTTON_TERTIARY to MouseInputRouter.MIDDLE_BUTTON) else emptyMap())
    private val buttonBits = buttonTargets.keys.fold(0) { all, bit -> all or bit }
    /** The buttons each mouse holds, as MotionEvent button bits. */
    private val held = HashMap<Int, Int>()
    private var remainderX = 0f
    private var remainderY = 0f
    private var remainderWheel = 0f

    /** Relative counts a report carries, which may be fractions. */
    fun move(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        val scale = speed()
        remainderX += dx * scale
        remainderY += dy * scale
        val x = remainderX.toInt()
        val y = remainderY.toInt()
        remainderX -= x
        remainderY -= y
        mouse.moveBy(x, y)
    }

    /** Android's vertical scroll: notches positive away from the player, fractions from a fine wheel. */
    fun scroll(vertical: Float) {
        val send = wheel ?: return
        remainderWheel -= vertical
        val notches = remainderWheel.toInt()
        remainderWheel -= notches
        if (notches != 0) send(notches)
    }

    /** [state]: the MotionEvent button bits [device] holds now. */
    fun buttons(device: Int, state: Int) {
        val now = state and buttonBits
        val before = held[device] ?: 0
        if (now == before) return
        for ((bit, target) in buttonTargets) {
            val owner = owner(device, target)
            if (now and bit != 0 && before and bit == 0) mouse.hold(owner, target)
            else if (now and bit == 0 && before and bit != 0) mouse.release(owner)
        }
        if (now == 0) held.remove(device) else held[device] = now
    }

    /** A mouse was disconnected: whatever it held is let go. */
    fun releaseDevice(device: Int) {
        held.remove(device) ?: return
        mouse.releasePrefix(owner(device, ""))
    }

    /** Lets go of every button, as when the guest loses the mouse. */
    fun releaseAll() {
        held.clear()
        mouse.releasePrefix(OWNER)
        remainderX = 0f
        remainderY = 0f
        remainderWheel = 0f
    }

    private fun owner(device: Int, target: String) = "$OWNER$device:$target"

    private companion object {
        const val OWNER = "mouse:"
        val BUTTONS = mapOf(MotionEvent.BUTTON_PRIMARY to "leftButton",
            MotionEvent.BUTTON_SECONDARY to "rightButton")
    }
}
