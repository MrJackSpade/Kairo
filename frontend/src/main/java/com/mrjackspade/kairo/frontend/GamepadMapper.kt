package com.mrjackspade.kairo.frontend


import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper

/** Converts controller buttons, hats and axis directions into owned virtual input. */
class GamepadMapper(private val router: InputRouter,
                    private val joystick: JoystickInputRouter,
                    private val mouse: MouseInputRouter,
                    private val runAction: (String) -> Unit,
                    private val releaseAction: (String) -> Unit,
                    defaultBindings: List<ControllerBinding> = emptyList()) {
    var physicalBindings: List<PhysicalControllerBinding> = PhysicalControllerBindings.defaults()
        set(value) {
            releaseAll()
            field = value
            updateMotionInputs()
        }
    var bindings: List<ControllerBinding> = defaultBindings
        set(value) {
            releaseAll()
            cycleIndex.clear()
            field = value
            updateMotionInputs()
        }
    var deadZone = 0.35f
        set(value) { field = value.coerceIn(0.10f, 0.90f) }

    private val active = HashSet<String>()
    private val heldActions = HashMap<String, String>()
    private val cycleIndex = HashMap<String, Int>()
    private var motionInputs = emptyList<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var lastMouseTick = 0L
    private val mouseTick = object : Runnable {
        override fun run() {
            if (!mouse.hasMovement()) { lastMouseTick = 0L; return }
            val now = android.os.SystemClock.uptimeMillis()
            mouse.tick(now - lastMouseTick)
            lastMouseTick = now
            handler.postDelayed(this, 16)
        }
    }

    init { updateMotionInputs() }

    private fun updateMotionInputs() {
        motionInputs = (physicalBindings.map { it.input } + bindings.map { it.input })
            .filter { it.startsWith("axis:") || it.startsWith("hat:") }.distinct()
    }

    fun hasButton(keyCode: Int) = resolve("button:$keyCode") != null

    fun pressVirtual(control: String, owner: String) {
        if (control !in PhysicalControllerBindings.controls || !owner.startsWith("onscreen:")) return
        val binding = bindings.firstOrNull { it.input == "virtual:$control" }
            ?: if (control == "menu") ControllerBinding("virtual:menu", action = "menu") else null
        if (binding != null) activate(owner, binding)
    }

    fun releaseVirtual(owner: String) {
        if (owner.startsWith("onscreen:")) deactivate(owner)
    }

    fun releaseOnScreen() {
        releaseActions("onscreen:")
        active.removeAll { it.startsWith("onscreen:") }
        router.releasePrefix("onscreen:")
        joystick.releasePrefix("onscreen:")
        mouse.releasePrefix("onscreen:")
        stopMouseTickIfIdle()
    }

    fun controlForButton(event: KeyEvent): String? {
        if (!KeyEvent.isGamepadButton(event.keyCode) &&
            !event.isFromSource(InputDevice.SOURCE_GAMEPAD) &&
            !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return null
        return physicalBindings.firstOrNull { it.input == "button:${event.keyCode}" }?.control
    }

    fun key(event: KeyEvent): Boolean {
        if (!KeyEvent.isGamepadButton(event.keyCode) &&
            !event.isFromSource(InputDevice.SOURCE_GAMEPAD) &&
            !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        val input = "button:${event.keyCode}"
        val binding = resolve(input) ?: return false
        val owner = "gamepad:${event.deviceId}:$input"
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) activate(owner, binding)
            KeyEvent.ACTION_UP -> deactivate(owner)
        }
        return true
    }

    fun motion(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) return false
        var handled = false
        for (input in motionInputs) {
            val binding = resolve(input) ?: continue
            val parts = input.split(':')
            if (parts.size != 3 || parts[0] !in setOf("axis", "hat")) continue
            val axis = parts[1].toIntOrNull() ?: continue
            val value = event.getAxisValue(axis)
            val signed = if (parts[2] == "+") value else -value
            val owner = "gamepad:${event.deviceId}:$input"
            if (binding.mouse?.startsWith("move") == true) {
                if (signed > deadZone) {
                    val strength = ((signed - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
                    activate(owner, binding, strength)
                } else deactivate(owner)
            } else if (signed >= deadZone) activate(owner, binding)
            else if (signed <= deadZone * 0.5f) deactivate(owner)
            handled = true
        }
        return handled
    }

    fun releaseDevice(deviceId: Int) {
        val prefix = "gamepad:$deviceId:"
        releaseActions(prefix)
        active.removeAll { it.startsWith(prefix) }
        router.releasePrefix(prefix)
        joystick.releasePrefix(prefix)
        mouse.releasePrefix(prefix)
        stopMouseTickIfIdle()
    }

    fun releaseAll() {
        releaseOnScreen()
        releaseActions("gamepad:")
        active.clear()
        router.releasePrefix("gamepad:")
        joystick.releasePrefix("gamepad:")
        mouse.releasePrefix("gamepad:")
        stopMouseTickIfIdle()
    }

    private fun resolve(input: String): ControllerBinding? {
        val control = physicalBindings.firstOrNull { it.input == input }?.control
        if (control != null) bindings.firstOrNull { it.input == "virtual:$control" }?.let { return it }
        // Older profiles use the D-pad directions for both the D-pad and left stick.
        // A game can now override the left stick without changing the D-pad.
        val dpadControl = when (control) {
            "lsup" -> "up"
            "lsdown" -> "down"
            "lsleft" -> "left"
            "lsright" -> "right"
            else -> null
        }
        if (dpadControl != null)
            bindings.firstOrNull { it.input == "virtual:$dpadControl" }?.let { return it }
        if (control == "menu") return ControllerBinding("virtual:menu", action = "menu")
        // Old saved profiles contain direct Android-to-guest bindings.
        return bindings.firstOrNull { it.input == input }
    }

    private fun activate(owner: String, binding: ControllerBinding, strength: Float = 1f) {
        if (!active.add(owner)) {
            if (binding.mouse?.startsWith("move") == true)
                mouse.hold(owner, binding.mouse, strength)
            return
        }
        when {
            binding.mouse != null -> {
                mouse.hold(owner, binding.mouse, strength)
                startMouseTick()
            }
            binding.joystick != null -> joystick.hold(owner, binding.joystick)
            binding.action != null -> {
                heldActions[owner] = binding.action
                runAction(binding.action)
            }
            binding.cycleKeys.isNotEmpty() -> {
                val pair = if (binding.input == "virtual:l1" || binding.input == "virtual:r1") 1 else 2
                val sequence = binding.cycleKeys
                val group = "$pair:${sequence.joinToString(",")}"
                val previous = cycleIndex[group] ?: -1
                val backwards = binding.input == "virtual:l1" || binding.input == "virtual:l2"
                val next = if (backwards) {
                    if (previous < 0) sequence.lastIndex else (previous + sequence.size - 1) % sequence.size
                } else (previous + 1) % sequence.size
                cycleIndex[group] = next
                router.hold(owner, listOf(sequence[next]))
            }
            else -> router.hold(owner, binding.keys)
        }
    }

    private fun deactivate(owner: String) {
        active.remove(owner)
        heldActions.remove(owner)?.let(::endAction)
        router.release(owner)
        joystick.release(owner)
        mouse.release(owner)
        stopMouseTickIfIdle()
    }

    /** Reports an action's release once no other input still holds it. */
    private fun endAction(action: String) {
        if (action !in heldActions.values) releaseAction(action)
    }

    private fun releaseActions(prefix: String) {
        val owners = heldActions.keys.filter { it.startsWith(prefix) }
        owners.mapNotNull(heldActions::remove).distinct().forEach(::endAction)
    }

    private fun startMouseTick() {
        if (lastMouseTick != 0L || !mouse.hasMovement()) return
        lastMouseTick = android.os.SystemClock.uptimeMillis()
        handler.postDelayed(mouseTick, 16)
    }

    private fun stopMouseTickIfIdle() {
        if (mouse.hasMovement()) return
        handler.removeCallbacks(mouseTick)
        lastMouseTick = 0L
    }
}
