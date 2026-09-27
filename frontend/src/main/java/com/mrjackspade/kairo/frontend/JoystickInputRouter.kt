package com.mrjackspade.kairo.frontend

/** Shares guest joystick controls across independent controller sources. */
class JoystickInputRouter(
    private val send: (Int, Boolean) -> Unit,
    private val controls: List<String> = DEFAULT_CONTROLS
) {
    init { require(controls.isNotEmpty() && controls.distinct().size == controls.size) }
    private val owners = HashMap<String, Int>()
    private val counts = IntArray(controls.size)

    @Synchronized fun hold(owner: String, control: String) {
        val index = controls.indexOf(control)
        require(index >= 0)
        if (owners[owner] == index) return
        release(owner)
        owners[owner] = index
        if (counts[index]++ == 0) send(index, true)
    }

    @Synchronized fun release(owner: String) {
        val index = owners.remove(owner) ?: return
        if (--counts[index] == 0) send(index, false)
    }

    @Synchronized fun releasePrefix(prefix: String) {
        owners.keys.filter { it.startsWith(prefix) }.toList().forEach(::release)
    }

    companion object {
        val DEFAULT_CONTROLS = listOf("up", "down", "left", "right", "button1", "button2")
    }
}
