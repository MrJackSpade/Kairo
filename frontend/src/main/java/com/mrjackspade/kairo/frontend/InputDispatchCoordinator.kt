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
    enum class Screen { FIRST_RUN, TOUCH_EDITOR, CONTROLLER_EDITOR, LIBRARY, SESSION, GUEST, INACTIVE }

    interface Frontend {
        fun screen(): Screen
        fun key(screen: Screen, control: String?, event: KeyEvent): Boolean
        fun motion(screen: Screen, event: MotionEvent): Boolean
    }

    fun dispatchKey(event: KeyEvent, android: (KeyEvent) -> Boolean): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) leaveTouchMode()
        val screen = frontend.screen()
        when (screen) {
            Screen.FIRST_RUN -> return frontend.key(screen, null, event)
            Screen.TOUCH_EDITOR, Screen.CONTROLLER_EDITOR ->
                return frontend.key(screen, null, event) || android(event)
            else -> Unit
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) back()
            return true
        }
        if (screen == Screen.LIBRARY)
            return frontend.key(screen, FrontendNavigation.control(event, mapper), event) || android(event)
        if (screen == Screen.INACTIVE) return android(event)
        if (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_HOME ||
            (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE && !mapper.hasButton(event.keyCode))) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) toggleMenu()
            return true
        }
        if (screen == Screen.SESSION) {
            if (!frontend.key(screen, FrontendNavigation.control(event, mapper), event)) android(event)
            return true
        }
        return mapper.key(event) || android(event)
    }

    fun dispatchMotion(event: MotionEvent, android: (MotionEvent) -> Boolean): Boolean {
        return when (val screen = frontend.screen()) {
            Screen.CONTROLLER_EDITOR -> frontend.motion(screen, event)
            Screen.GUEST -> mapper.motion(event) || android(event)
            Screen.INACTIVE -> android(event)
            else -> {
                if (motionScreen != screen) menuMotion.stop()
                motionScreen = screen
                menuMotion.motion(event) || android(event)
            }
        }
    }

    /** Activity fallback: focused Android views get first refusal on ordinary keys. */
    fun physicalKey(event: KeyEvent): Boolean {
        if (frontend.screen() != Screen.GUEST) return event.keyCode !in NAVIGATION_KEYS
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

/** View adapters for the same first-run, editors, library and session UI in every app. */
class FrontendInputScreens(
    private val firstRun: () -> FirstRunScreen?,
    private val controls: () -> OnScreenControls?,
    private val editor: () -> ControllerEditor<*>?,
    private val library: () -> LibraryScreen<*>?,
    private val drawer: () -> SessionDrawer?,
    private val playing: () -> Boolean,
    private val back: () -> Unit,
    private val closeMenu: () -> Unit
) : InputDispatchCoordinator.Frontend {
    override fun screen() = when {
        firstRun()?.isOpen == true -> InputDispatchCoordinator.Screen.FIRST_RUN
        controls()?.isOpen == true -> InputDispatchCoordinator.Screen.TOUCH_EDITOR
        editor()?.isOpen == true -> InputDispatchCoordinator.Screen.CONTROLLER_EDITOR
        library()?.visibility == android.view.View.VISIBLE -> InputDispatchCoordinator.Screen.LIBRARY
        drawer()?.isOpen == true -> InputDispatchCoordinator.Screen.SESSION
        playing() -> InputDispatchCoordinator.Screen.GUEST
        else -> InputDispatchCoordinator.Screen.INACTIVE
    }

    override fun key(screen: InputDispatchCoordinator.Screen, control: String?, event: KeyEvent) = when (screen) {
        InputDispatchCoordinator.Screen.FIRST_RUN -> firstRun()?.handleKey(event) == true
        InputDispatchCoordinator.Screen.TOUCH_EDITOR -> controls()?.handleKey(event) == true
        InputDispatchCoordinator.Screen.CONTROLLER_EDITOR -> editor()?.handleKey(event) == true
        InputDispatchCoordinator.Screen.LIBRARY -> library()?.let {
            FrontendNavigation.library(it, control, event, back) } == true
        InputDispatchCoordinator.Screen.SESSION -> drawer()?.let {
            FrontendNavigation.session(it, control, event, closeMenu) } == true
        else -> false
    }

    override fun motion(screen: InputDispatchCoordinator.Screen, event: MotionEvent) =
        screen == InputDispatchCoordinator.Screen.CONTROLLER_EDITOR && editor()?.captureMotion(event) == true
}
