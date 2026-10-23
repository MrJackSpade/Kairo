package com.mrjackspade.kairo.frontend

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class KeyMappingModeTest {
    private val buttonA = "button:${KeyEvent.KEYCODE_BUTTON_A}"
    private val buttonB = "button:${KeyEvent.KEYCODE_BUTTON_B}"
    private val menu = "button:${KeyEvent.KEYCODE_BUTTON_MODE}"

    private class Harness(initial: List<ControllerBinding>) {
        var stored = initial
        var saves = 0
        var capture: InputCapture? = null
        var changes = 0
        val mode = KeyMappingMode({ stored }, { stored = it; saves++ },
            { PhysicalControllerBindings.defaults() }, { capture = it }).apply { addListener { changes++ } }
    }

    @Test fun entersWithTheInheritedMapping() {
        val harness = Harness(listOf(ControllerBinding("virtual:a", listOf(30)),
            ControllerBinding("virtual:l1", cycleKeys = listOf(2, 3, 4))))
        harness.mode.toggle()
        assertTrue(harness.mode.active)
        assertTrue(harness.mode.mapped(listOf(30)))
        assertTrue(harness.mode.mapped(listOf(3)))
        assertFalse(harness.mode.mapped(listOf(31)))
        assertNull(harness.capture)
    }

    @Test fun waitingKeyTakesTheNextButtonAndReplacesItsOldTarget() {
        val harness = Harness(listOf(ControllerBinding("virtual:a", action = "pause")))
        harness.mode.toggle()
        harness.mode.tap(listOf(30))
        assertEquals(listOf(30), harness.mode.waiting)
        assertNotNull(harness.capture)
        harness.mode.assign(buttonA)
        assertNull(harness.mode.waiting)
        assertNull(harness.capture)
        assertEquals(listOf(ControllerBinding("virtual:a", listOf(30))), harness.stored)
        assertTrue(harness.mode.mapped(listOf(30)))
    }

    @Test fun inputWithoutAStandardControlIsStoredDirectly() {
        val harness = Harness(emptyList())
        harness.mode.toggle()
        harness.mode.tap(listOf(30))
        harness.mode.assign("button:200")
        assertEquals(listOf(ControllerBinding("button:200", listOf(30))), harness.stored)
    }

    @Test fun menuButtonKeepsWaiting() {
        val harness = Harness(emptyList())
        harness.mode.toggle()
        harness.mode.tap(listOf(30))
        harness.mode.assign(menu)
        assertEquals(listOf(30), harness.mode.waiting)
        assertEquals(0, harness.saves)
    }

    @Test fun tappingTheWaitingKeyClearsEveryButtonThatSendsIt() {
        val harness = Harness(listOf(ControllerBinding("virtual:a", listOf(30)),
            ControllerBinding("virtual:b", listOf(42, 30)), ControllerBinding("virtual:x", listOf(31)),
            ControllerBinding("virtual:l1", cycleKeys = listOf(30, 2, 3)),
            ControllerBinding("virtual:r1", cycleKeys = listOf(30, 4))))
        harness.mode.toggle()
        harness.mode.tap(listOf(30))
        harness.mode.tap(listOf(30))
        assertNull(harness.mode.waiting)
        assertNull(harness.capture)
        assertEquals(listOf(ControllerBinding("virtual:x", listOf(31)),
            ControllerBinding("virtual:l1", cycleKeys = listOf(2, 3))), harness.stored)
        assertFalse(harness.mode.mapped(listOf(30)))
    }

    @Test fun clearingAnUnmappedKeySavesNothing() {
        val harness = Harness(emptyList())
        harness.mode.toggle()
        harness.mode.tap(listOf(30))
        harness.mode.tap(listOf(30))
        assertEquals(0, harness.saves)
        assertNull(harness.mode.waiting)
    }

    @Test fun anotherKeyMovesTheSelectionAndExitStopsListening() {
        val harness = Harness(emptyList())
        harness.mode.toggle()
        harness.mode.tap(listOf(30))
        harness.mode.tap(listOf(42, 6))
        assertEquals(listOf(42, 6), harness.mode.waiting)
        harness.mode.assign(buttonB)
        assertEquals(listOf(ControllerBinding("virtual:b", listOf(42, 6))), harness.stored)
        harness.mode.tap(listOf(31))
        harness.mode.toggle()
        assertFalse(harness.mode.active)
        assertNull(harness.mode.waiting)
        assertNull(harness.capture)
        harness.mode.tap(listOf(31))
        assertNull(harness.mode.waiting)
    }

    @Test fun reentryReloadsTheSavedMapping() {
        val harness = Harness(emptyList())
        harness.mode.toggle()
        harness.mode.toggle()
        harness.stored = listOf(ControllerBinding("virtual:y", listOf(44)))
        harness.mode.toggle()
        assertTrue(harness.mode.mapped(listOf(44)))
    }

    @Test fun aJoystickControlIsMappedLikeAKeyAndKeepsTheButtonsKeys() {
        val harness = Harness(listOf(ControllerBinding("virtual:a", listOf(30))))
        harness.mode.toggle()
        harness.mode.tapJoystick("button1")
        assertEquals("button1", harness.mode.waitingJoystick)
        assertNull(harness.mode.waiting)
        harness.mode.assign(buttonA)
        assertEquals(listOf(ControllerBinding("virtual:a", listOf(30), joystick = "button1")), harness.stored)
        assertTrue(harness.mode.mappedJoystick("button1"))
        assertTrue(harness.mode.mapped(listOf(30)))
    }

    @Test fun mappingKeysKeepsTheButtonsJoystickControl() {
        val harness = Harness(listOf(ControllerBinding("virtual:a", joystick = "button1")))
        harness.mode.toggle()
        harness.mode.tap(listOf(30))
        harness.mode.assign(buttonA)
        assertEquals(listOf(ControllerBinding("virtual:a", listOf(30), joystick = "button1")), harness.stored)
    }

    @Test fun aJoystickControlReplacesAnAction() {
        val harness = Harness(listOf(ControllerBinding("virtual:b", action = "pause")))
        harness.mode.toggle()
        harness.mode.tapJoystick("button2")
        harness.mode.assign(buttonB)
        assertEquals(listOf(ControllerBinding("virtual:b", joystick = "button2")), harness.stored)
    }

    @Test fun clearingEachPartLeavesTheOther() {
        val harness = Harness(listOf(ControllerBinding("virtual:up", listOf(58), joystick = "up"),
            ControllerBinding("virtual:down", joystick = "down")))
        harness.mode.toggle()
        harness.mode.tapJoystick("up")
        harness.mode.tapJoystick("up")
        assertEquals(listOf(ControllerBinding("virtual:up", listOf(58)),
            ControllerBinding("virtual:down", joystick = "down")), harness.stored)
        harness.stored = listOf(ControllerBinding("virtual:up", listOf(58), joystick = "up"))
        harness.mode.toggle()
        harness.mode.toggle()
        harness.mode.tap(listOf(58))
        harness.mode.tap(listOf(58))
        assertEquals(listOf(ControllerBinding("virtual:up", joystick = "up")), harness.stored)
        harness.mode.tapJoystick("down")
        harness.mode.tapJoystick("down")
        assertEquals(listOf(ControllerBinding("virtual:up", joystick = "up")), harness.stored)
    }
}
