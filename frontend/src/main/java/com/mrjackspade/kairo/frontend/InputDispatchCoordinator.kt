package com.mrjackspade.kairo.frontend

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/** Routes frontend input before Android focus handling and any guest input. */
class InputDispatchCoordinator(
    private val frontend: Frontend,
    private val mapper: GamepadMapper,
    private val keys: InputRouter,
    private val scanCode: (Int) -> Int?,
    private val back: () -> Unit,
    private val toggleMenu: () -> Unit,
    private val leaveTouchMode: () -> Unit,
    private val releaseTouch: () -> Unit,
    private val cancelAutomation: () -> Unit = {},
    private val automationOwners: Set<String> = emptySet()
) {
    private var motionScreen: Screen? = null
    private val menuMotion = DpadMotionNavigation { event ->
        val screen = frontend.screen()
        if (screen == motionScreen && screen != Screen.GUEST && screen != Screen.INACTIVE)
            frontend.key(screen, FrontendNavigation.control(event, mapper), event)
    }

    /**
     * MENU: the page stack, over the library or a game. NOTICE: a game with a notice that takes
     * the D-pad. BUSY: a machine is stopping or starting behind the progress strip.
     */
    enum class Screen { MENU, LIBRARY, NOTICE, BUSY, GUEST, INACTIVE }

    interface Frontend {
        fun screen(): Screen
        fun playing(): Boolean
        fun key(screen: Screen, control: String?, event: KeyEvent): Boolean
        /** Raw input while a Controller inputs tray is listening. */
        fun capturing(): Boolean
        fun captureKey(event: KeyEvent): Boolean
        fun captureMotion(event: MotionEvent): Boolean
    }

    fun dispatchKey(event: KeyEvent, android: (KeyEvent) -> Boolean): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) leaveTouchMode()
        val screen = frontend.screen()
        // Listening captures every controller button, B and Back included. Keyboard keys
        // still reach the tray's buttons below.
        if (screen == Screen.MENU && frontend.capturing() && frontend.captureKey(event)) return true
        if (screen == Screen.BUSY) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) back()
            return true
        }
        val control = FrontendNavigation.control(event, mapper)
        when (screen) {
            Screen.LIBRARY -> return frontend.key(screen, control, event) || android(event)
            Screen.INACTIVE -> return android(event)
            Screen.MENU -> if (!frontend.playing()) return frontend.key(screen, control, event) || android(event)
            else -> Unit
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_HOME ||
            (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE && !mapper.hasButton(event.keyCode))) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) toggleMenu()
            return true
        }
        if (screen == Screen.MENU) return frontend.key(screen, control, event) || android(event)
        if (screen == Screen.NOTICE && frontend.key(screen, control, event)) return true
        return mapper.key(event) || android(event)
    }

    fun dispatchMotion(event: MotionEvent, android: (MotionEvent) -> Boolean): Boolean {
        val screen = frontend.screen()
        if (screen == Screen.MENU && frontend.capturing()) {
            menuMotion.stop()
            return frontend.captureMotion(event)
        }
        return when (screen) {
            Screen.GUEST -> mapper.motion(event) || android(event)
            Screen.INACTIVE -> android(event)
            Screen.BUSY -> true
            else -> {
                if (motionScreen != screen) menuMotion.stop()
                motionScreen = screen
                menuMotion.motion(event) || android(event)
            }
        }
    }

    /** Activity fallback: focused Android views get first refusal on ordinary keys. */
    fun physicalKey(event: KeyEvent): Boolean {
        // Volume, mute and media keys belong to Android on every screen.
        if (event.isSystem) return false
        if (frontend.screen() != Screen.GUEST && frontend.screen() != Screen.NOTICE)
            return event.keyCode !in NAVIGATION_KEYS
        // Unmapped controller buttons must never masquerade as guest keyboard keys.
        if (KeyEvent.isGamepadButton(event.keyCode) || event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return true
        val scan = scanCode(event.keyCode) ?: return false
        val owner = "keyboard:${event.deviceId}:${event.keyCode}"
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) keys.hold(owner, listOf(scan))
            KeyEvent.ACTION_UP -> keys.release(owner)
        }
        return true
    }

    fun releaseInputs(preserveAutomation: Boolean = false) {
        menuMotion.stop()
        if (!preserveAutomation) cancelAutomation()
        mapper.releaseAll()
        keys.releaseAll(if (preserveAutomation) automationOwners else emptySet())
        releaseTouch()
    }

    fun releaseDevice(deviceId: Int) {
        mapper.releaseDevice(deviceId)
        keys.releasePrefix("keyboard:$deviceId:")
    }

    private companion object {
        val NAVIGATION_KEYS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_TAB)
    }
}

/** View adapters for the same pages, library, and game prompts in every app. */
class FrontendInputScreens(
    private val pages: () -> PageStack?,
    private val library: () -> LibraryScreen<*>?,
    private val overlay: () -> GameOverlay?,
    private val playing: () -> Boolean,
    private val busy: () -> Boolean,
    private val back: () -> Unit
) : InputDispatchCoordinator.Frontend {
    override fun screen() = when {
        pages()?.isActive == true -> InputDispatchCoordinator.Screen.MENU
        busy() -> InputDispatchCoordinator.Screen.BUSY
        library()?.visibility == android.view.View.VISIBLE -> InputDispatchCoordinator.Screen.LIBRARY
        playing() && overlay()?.takesInput == true -> InputDispatchCoordinator.Screen.NOTICE
        playing() -> InputDispatchCoordinator.Screen.GUEST
        else -> InputDispatchCoordinator.Screen.INACTIVE
    }

    override fun playing() = playing.invoke()

    override fun key(screen: InputDispatchCoordinator.Screen, control: String?, event: KeyEvent) = when (screen) {
        InputDispatchCoordinator.Screen.MENU -> pages()?.handleKey(control, event) == true
        InputDispatchCoordinator.Screen.LIBRARY -> library()?.let {
            FrontendNavigation.library(it, control, event, back) } == true
        InputDispatchCoordinator.Screen.NOTICE -> overlay()?.handleKey(control, event) == true
        else -> false
    }

    override fun capturing() = pages()?.capturing == true
    override fun captureKey(event: KeyEvent) = pages()?.captureKey(event) == true
    override fun captureMotion(event: MotionEvent) = pages()?.captureMotion(event) ?: true
}
