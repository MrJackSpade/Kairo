package com.mrjackspade.kairo.frontend

/** Merges every source holding a guest joystick control; each control is down while any holds it. */
class JoystickInputRouter(
    private val send: (Int, Boolean) -> Unit,
    private val controls: List<String> = DEFAULT_CONTROLS
) {
    init { require(controls.isNotEmpty() && controls.distinct().size == controls.size) }
    private val owners = HashMap<String, Int>()
    private val counts = IntArray(controls.size)
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    /** Called on the thread that changed a control, after it changed. */
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    /** The controls down now, by id. */
    @Synchronized fun pressed(): Set<String> = controls.filterIndexed { index, _ -> counts[index] > 0 }.toSet()

    fun hold(owner: String, control: String) {
        val changed = synchronized(this) {
            val index = controls.indexOf(control)
            require(index >= 0)
            if (owners[owner] == index) return
            val released = releaseLocked(owner)
            owners[owner] = index
            (counts[index]++ == 0).also { if (it) send(index, true) } || released
        }
        if (changed) notifyChanged()
    }

    fun release(owner: String) {
        if (synchronized(this) { releaseLocked(owner) }) notifyChanged()
    }

    fun releasePrefix(prefix: String) {
        val changed = synchronized(this) {
            owners.keys.filter { it.startsWith(prefix) }.toList().map(::releaseLocked).any { it }
        }
        if (changed) notifyChanged()
    }

    /** Whether a control went up. */
    private fun releaseLocked(owner: String): Boolean {
        val index = owners.remove(owner) ?: return false
        if (--counts[index] != 0) return false
        send(index, false)
        return true
    }

    private fun notifyChanged() = listeners.forEach { it() }

    companion object {
        val DEFAULT_CONTROLS = listOf("up", "down", "left", "right", "button1", "button2")
    }
}
