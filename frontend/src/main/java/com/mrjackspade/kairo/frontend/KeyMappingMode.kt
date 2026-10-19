package com.mrjackspade.kairo.frontend

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Maps controller buttons from the guest keyboard. A tapped key waits for the next controller
 * input, which then sends that key; tapping the waiting key again clears every button mapped
 * to it. Each change is saved to the running game's controller profile at once.
 */
class KeyMappingMode(
    /** The running game's effective mapping, inherited from global or catalog defaults. */
    private val load: () -> List<ControllerBinding>,
    private val save: (List<ControllerBinding>) -> Unit,
    private val physical: () -> List<PhysicalControllerBinding>,
    /** Routes controller input to the capture ahead of the game, or back to the game when null. */
    private val capture: (InputCapture?) -> Unit
) {
    var active = false
        private set
    /** The guest keys the next controller input will send, or null when none is waiting. */
    var waiting: List<Int>? = null
        private set
    private var bindings = emptyList<ControllerBinding>()
    private val listeners = LinkedHashSet<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    fun toggle() { if (active) exit() else enter() }

    fun exit() {
        if (!active) return
        active = false
        stopWaiting()
        notifyChanged()
    }

    private fun enter() {
        bindings = load()
        active = true
        notifyChanged()
    }

    /** A plain key matches any button that sends it, alone or in a combination or cycle. */
    fun mapped(keys: List<Int>): Boolean = bindings.any { binding ->
        binding.keys == keys || keys.size == 1 && (keys[0] in binding.keys || keys[0] in binding.cycleKeys)
    }

    fun tap(keys: List<Int>) {
        if (!active) return
        if (waiting == keys) {
            stopWaiting()
            cleared(keys).takeIf { it != bindings }?.let(::update)
        } else {
            waiting = keys
            capture(Capture())
        }
        notifyChanged()
    }

    private fun control(input: String) = physical().firstOrNull { it.input == input }?.control

    /** A fresh capture per waiting key; the menu button still opens the game menu and cannot be given a key. */
    private inner class Capture : InputCapture {
        private val inputs = PhysicalInputCapture(::assign)

        override fun key(event: KeyEvent): Boolean =
            event.keyCode != KeyEvent.KEYCODE_MENU && control("button:${event.keyCode}") != "menu" &&
                inputs.key(event)

        override fun motion(event: MotionEvent): Boolean =
            (event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_GAMEPAD)) &&
                inputs.motion(event)
    }

    /** The physical input captured for the waiting key, such as button:96. */
    internal fun assign(input: String) {
        val keys = waiting ?: return
        val control = control(input)
        if (control == "menu") return
        // Inputs without a standard control are stored directly, as alternate inputs are.
        val target = control?.let { "virtual:$it" } ?: input
        stopWaiting()
        update(bindings.filterNot { it.input == target } + ControllerBinding(target, keys))
        notifyChanged()
    }

    private fun cleared(keys: List<Int>): List<ControllerBinding> = bindings.mapNotNull { binding ->
        when {
            binding.keys == keys || keys.size == 1 && keys[0] in binding.keys -> null
            keys.size == 1 && keys[0] in binding.cycleKeys ->
                binding.copy(cycleKeys = binding.cycleKeys - keys[0]).takeIf { it.cycleKeys.size >= 2 }
            else -> binding
        }
    }

    private fun update(next: List<ControllerBinding>) {
        save(next)
        bindings = next
    }

    private fun stopWaiting() {
        waiting = null
        capture(null)
    }

    private fun notifyChanged() = listeners.toList().forEach { it() }
}
