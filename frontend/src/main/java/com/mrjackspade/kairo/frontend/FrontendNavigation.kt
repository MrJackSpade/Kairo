package com.mrjackspade.kairo.frontend

import android.view.KeyEvent

/** Controller and keyboard navigation for the library screen and pages. */
object FrontendNavigation {
    /**
     * The menu meaning of a key: the user's physical mapping for A, B, Menu, and the D-pad
     * first, then D-pad and system keys from any device. Escape also means Back.
     */
    fun control(event: KeyEvent, mapper: GamepadMapper): String? {
        mapper.controlForButton(event)?.let { return it }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> "a"
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B -> "b"
            KeyEvent.KEYCODE_MENU -> "menu"
            else -> null
        }
    }

    fun library(screen: LibraryScreen<*>, control: String?, event: KeyEvent,
                back: () -> Unit): Boolean {
        if (control == null) return false
        if (screen.searchFocused) {
            if (control == "a" || control == "b") {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                    screen.dismissSearchKeyboard()
                return true
            }
            if (control == "up" || control == "down") {
                if (event.action == KeyEvent.ACTION_DOWN) screen.leaveSearch(control == "up")
                return true
            }
            // Horizontal text cursor navigation belongs to Android.
            return false
        }
        if (control !in setOf("up", "down", "left", "right", "a", "b", "menu")) return false
        if (event.action == KeyEvent.ACTION_DOWN) when (control) {
            "down" -> screen.moveSelection(1)
            "up" -> screen.moveSelection(-1)
            "left" -> screen.moveHeaderSelection(-1)
            "right" -> screen.moveHeaderSelection(1)
            "a" -> if (event.repeatCount == 0) screen.activateSelection()
            "menu" -> if (event.repeatCount == 0) screen.openMenu()
            "b" -> if (event.repeatCount == 0 && !screen.leaveHeader()) back()
        }
        return true
    }
}
