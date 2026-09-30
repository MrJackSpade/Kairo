package com.mrjackspade.kairo.frontend

import kotlin.math.max
import kotlin.math.sqrt

/** Combines controller and touch owners before sending guest mouse input. */
class MouseInputRouter(private val move: (Int, Int) -> Unit,
                       private val button: (Int, Boolean) -> Unit) {
    @Synchronized fun moveBy(dx: Int, dy: Int) {
        if (dx != 0 || dy != 0) move(dx, dy)
    }
    private data class Held(val target: String, val strength: Float, val speed: Float)
    private val owners = HashMap<String, Held>()
    private val buttonCounts = IntArray(2)
    private var remainderX = 0f
    private var remainderY = 0f

    @Synchronized fun hold(owner: String, target: String, strength: Float = 1f, speed: Float = 1f) {
        require(target in TARGETS)
        val value = strength.coerceIn(0f, 1f)
        require(speed.isFinite() && speed in MIN_SPEED..MAX_SPEED)
        val prior = owners[owner]
        if (prior?.target == target) {
            owners[owner] = Held(target, value, speed)
            return
        }
        release(owner)
        owners[owner] = Held(target, value, speed)
        buttonIndex(target)?.let { index ->
            if (buttonCounts[index]++ == 0) button(index + 1, true)
        }
    }

    @Synchronized fun release(owner: String) {
        val held = owners.remove(owner) ?: return
        buttonIndex(held.target)?.let { index ->
            if (--buttonCounts[index] == 0) button(index + 1, false)
        }
        if (!hasMovement()) {
            remainderX = 0f
            remainderY = 0f
        }
    }

    @Synchronized fun releasePrefix(prefix: String) {
        owners.keys.filter { it.startsWith(prefix) }.toList().forEach(::release)
    }

    @Synchronized fun hasMovement(): Boolean = owners.values.any {
        it.target.startsWith("move") && it.strength > 0f
    }

    /** Call regularly while movement is held; elapsed time keeps speed stable across frame rates. */
    @Synchronized fun tick(elapsedMs: Long) {
        if (elapsedMs <= 0 || !hasMovement()) return
        fun strength(target: String) = owners.values.filter { it.target == target }
            .maxOfOrNull { it.strength * it.speed } ?: 0f
        var x = strength("moveRight") - strength("moveLeft")
        var y = strength("moveDown") - strength("moveUp")
        val speed = owners.values.filter { it.target.startsWith("move") && it.strength > 0f }
            .maxOf { it.speed }
        val length = max(1f, sqrt(x * x + y * y) / speed)
        x /= length
        y /= length
        val seconds = elapsedMs.coerceAtMost(50) / 1000f
        remainderX += x * COUNTS_PER_SECOND * seconds
        remainderY += y * COUNTS_PER_SECOND * seconds
        val dx = remainderX.toInt()
        val dy = remainderY.toInt()
        remainderX -= dx
        remainderY -= dy
        if (dx != 0 || dy != 0) move(dx, dy)
    }

    private fun buttonIndex(target: String): Int? = when (target) {
        "leftButton" -> 0
        "rightButton" -> 1
        else -> null
    }

    companion object {
        val TARGETS = listOf("moveUp", "moveDown", "moveLeft", "moveRight",
            "leftButton", "rightButton")
        private const val COUNTS_PER_SECOND = 480f
        const val MIN_SPEED = 0.1f
        const val MAX_SPEED = 20f
    }
}
