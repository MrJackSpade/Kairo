package com.mrjackspade.kairo.frontend

import org.junit.Assert.*
import org.junit.Test

class OnScreenModeTest {
    @Test fun autoHidesOnlyWhileAControllerIsConnected() {
        assertTrue(OnScreenMode.AUTO.shown(controllerConnected = false))
        assertFalse(OnScreenMode.AUTO.shown(controllerConnected = true))
        assertTrue(OnScreenMode.SHOWN.shown(controllerConnected = true))
        assertFalse(OnScreenMode.HIDDEN.shown(controllerConnected = false))
    }

    @Test fun legacySwitchMigrates() {
        assertEquals(OnScreenMode.AUTO, OnScreenMode.load(memoryPreferences()))
        assertEquals(OnScreenMode.SHOWN, OnScreenMode.load(memoryPreferences(mapOf("onscreen_enabled" to true))))
        assertEquals(OnScreenMode.HIDDEN, OnScreenMode.load(memoryPreferences(mapOf("onscreen_enabled" to false))))
    }

    @Test fun savedModeWinsOverLegacySwitch() {
        val preferences = memoryPreferences(mapOf("onscreen_enabled" to false))
        OnScreenMode.AUTO.save(preferences)
        assertEquals(OnScreenMode.AUTO, OnScreenMode.load(preferences))
    }
}
