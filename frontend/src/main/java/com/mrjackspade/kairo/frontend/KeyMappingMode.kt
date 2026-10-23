package com.mrjackspade.kairo.frontend

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Maps controller buttons from the guest keyboard and joystick tabs. A tapped key or joystick
 * control waits for the next controller input, which then sends it; tapping the waiting one again
 * clears every button mapped to it. Keys and a joystick control are separate parts of a binding,
 * so mapping one keeps the other. Each change is saved to the running game's controller profile at once.
 */
class KeyMappingMode(
    /** The running game's effective mapping, inherited from global or catalog defaults. */
    private val load: () -> List<ControllerBinding>,
    private val save: (List<ControllerBinding>) -> Unit,
    private val physical: () -> List<PhysicalControllerBinding>,
    /** Routes controller input to the capture ahead of the game, or back to the game when null. */
    private val capture: (InputCapture?) -> Unit
) {
    /** What a tap picked: guest keys, or a joystick control by its id. */
    sealed class Target {
        data class Keys(val keys: List<Int>) : Target()
        data class Joystick(val control: String) : Target()
    }

    var active = false
        private set
    /** What the next controller input will send, or null when nothing is waiting. */
    var target: Target? = null
        private set
    /** The guest keys the next controller input will send, or null when no key is waiting. */
    val waiting: List<Int>? get() = (target as? Target.Keys)?.keys
    /** The joystick control the next controller input will send, or null when none is waiting. */
    val waitingJoystick: String? get() = (target as? Target.Joystick)?.control
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

    fun mappedJoystick(control: String): Boolean = bindings.any { it.joystick == control }

    fun tap(keys: List<Int>) = tap(Target.Keys(keys))

    fun tapJoystick(control: String) = tap(Target.Joystick(control))

    private fun tap(picked: Target) {
        if (!active) return
        if (target == picked) {
            stopWaiting()
            cleared(picked).takeIf { it != bindings }?.let(::update)
        } else {
            target = picked
            capture(Capture())
        }
        notifyChanged()
    }

    private fun control(input: String) = physical().firstOrNull { it.input == input }?.control

    /** A fresh capture per waiting target; the menu button still opens the game menu and cannot be mapped. */
    private inner class Capture : InputCapture {
        private val inputs = PhysicalInputCapture(::assign)

        override fun key(event: KeyEvent): Boolean =
            event.keyCode != KeyEvent.KEYCODE_MENU && control("button:${event.keyCode}") != "menu" &&
                inputs.key(event)

        override fun motion(event: MotionEvent): Boolean =
            (event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_GAMEPAD)) &&
                inputs.motion(event)
    }

    /** The physical input captured for the waiting target, such as button:96. */
    internal fun assign(input: String) {
        val picked = target ?: return
        val control = control(input)
        if (control == "menu") return
        // Inputs without a standard control are stored directly, as alternate inputs are.
        val source = control?.let { "virtual:$it" } ?: input
        stopWaiting()
        // Only keys and a joystick control share a binding; any other target is replaced.
        val existing = bindings.firstOrNull { it.input == source }
            ?.takeIf { it.action == null && it.mouse == null && it.cycleKeys.isEmpty() }
        val binding = when (picked) {
            is Target.Keys -> ControllerBinding(source, picked.keys, joystick = existing?.joystick)
            is Target.Joystick -> ControllerBinding(source, existing?.keys.orEmpty(), joystick = picked.control)
        }
        update(bindings.filterNot { it.input == source } + binding)
        notifyChanged()
    }

    private fun cleared(picked: Target): List<ControllerBinding> = bindings.mapNotNull { binding ->
        when (picked) {
            is Target.Keys -> {
                val keys = picked.keys
                when {
                    binding.keys == keys || keys.size == 1 && keys[0] in binding.keys ->
                        binding.joystick?.let { binding.copy(keys = emptyList()) }
                    keys.size == 1 && keys[0] in binding.cycleKeys ->
                        binding.copy(cycleKeys = binding.cycleKeys - keys[0]).takeIf { it.cycleKeys.size >= 2 }
                    else -> binding
                }
            }
            is Target.Joystick -> when {
                binding.joystick != picked.control -> binding
                binding.keys.isNotEmpty() -> binding.copy(joystick = null)
                else -> null
            }
        }
    }

    private fun update(next: List<ControllerBinding>) {
        save(next)
        bindings = next
    }

    private fun stopWaiting() {
        target = null
        capture(null)
    }

    private fun notifyChanged() = listeners.toList().forEach { it() }
}
