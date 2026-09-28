package com.mrjackspade.kairo.frontend

import android.view.InputDevice
import android.view.KeyEvent

/** Controller and keyboard navigation for the common library and session screens. */
object FrontendNavigation {
    fun control(event: KeyEvent, mapper: GamepadMapper): String? {
        mapper.controlForButton(event)?.let { return it }
        // D-pad and system keys retain their menu meaning across keyboard and gamepad devices.
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> return "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> return "down"
            KeyEvent.KEYCODE_DPAD_LEFT -> return "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> return "right"
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> return "a"
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> return "b"
            KeyEvent.KEYCODE_MENU -> return "menu"
        }
        if (KeyEvent.isGamepadButton(event.keyCode) ||
            event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return null
        return null
    }

    fun library(screen: LibraryScreen<*>, control: String?, event: KeyEvent,
                back: () -> Unit): Boolean {
        if (control == null) return false
        if (screen.detailOpen) {
            if (control !in setOf("a", "b", "menu")) return false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) when (control) {
                "a" -> screen.activateDetail()
                "menu" -> screen.detailsSelection()
                else -> screen.closeDetail()
            }
            return true
        }
        if (screen.actionsOpen) {
            if (event.action == KeyEvent.ACTION_DOWN) when (control) {
                "down" -> screen.moveActionSelection(1)
                "up" -> screen.moveActionSelection(-1)
                "a" -> if (event.repeatCount == 0) screen.activateAction()
                "b", "menu" -> screen.closeActions()
            }
            return true
        }
        if (control !in setOf("up", "down", "left", "right", "a", "b", "menu")) return false
        if (event.action == KeyEvent.ACTION_DOWN) when (control) {
            "down" -> screen.moveSelection(1)
            "up" -> screen.moveSelection(-1)
            "a" -> if (event.repeatCount == 0) screen.activateSelection()
            "menu" -> if (event.repeatCount == 0) screen.openActions()
            "b" -> if (event.repeatCount == 0) back()
        }
        return true
    }

    fun session(drawer: SessionDrawer, control: String?, event: KeyEvent,
                close: () -> Unit): Boolean {
        if (control == null) return false
        if (control !in setOf("up", "down", "left", "right", "a", "b", "menu")) return false
        if (event.action == KeyEvent.ACTION_DOWN) when (control) {
            "up", "left" -> drawer.focus(drawer.selectedIndex - 1)
            "down", "right" -> drawer.focus(drawer.selectedIndex + 1)
            "a" -> if (event.repeatCount == 0) drawer.activateSelected()
            "b", "menu" -> if (event.repeatCount == 0) close()
        }
        return true
    }
}
