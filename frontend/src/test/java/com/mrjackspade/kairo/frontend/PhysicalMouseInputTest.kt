package com.mrjackspade.kairo.frontend

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalMouseInputTest {
    private val moves = mutableListOf<Pair<Int, Int>>()
    private val buttons = mutableListOf<Pair<Int, Boolean>>()
    private val wheel = mutableListOf<Int>()
    private val router = MouseInputRouter({ dx, dy -> moves += dx to dy }, { b, down -> buttons += b to down }, 100)
    private var speed = 1f

    private fun input(middle: Boolean = true, hasWheel: Boolean = true) =
        PhysicalMouseInput(router, middle, if (hasWheel) { n -> wheel += n } else null) { speed }

    @Test fun countsGoToTheGuestScaledWithFractionsKept() {
        val mouse = input()
        mouse.move(3f, -2f)
        speed = 0.5f
        mouse.move(1f, 1f)
        mouse.move(1f, 1f)
        assertEquals(listOf(3 to -2, 1 to 1), moves)
    }

    @Test fun eachButtonPressesAndReleasesOnce() {
        val mouse = input()
        mouse.buttons(7, MotionEvent.BUTTON_PRIMARY)
        mouse.buttons(7, MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY)
        mouse.buttons(7, MotionEvent.BUTTON_SECONDARY)
        mouse.buttons(7, MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY)
        mouse.buttons(7, 0)
        assertEquals(listOf(1 to true, 2 to true, 1 to false, 3 to true, 2 to false, 3 to false), buttons)
    }

    @Test fun aGuestMouseWithoutAMiddleButtonNeverGetsOne() {
        val mouse = input(middle = false)
        mouse.buttons(7, MotionEvent.BUTTON_TERTIARY)
        mouse.buttons(7, 0)
        assertEquals(emptyList<Pair<Int, Boolean>>(), buttons)
    }

    @Test fun twoMiceHoldingOneButtonReleaseItWithTheLast() {
        val mouse = input()
        mouse.buttons(1, MotionEvent.BUTTON_PRIMARY)
        mouse.buttons(2, MotionEvent.BUTTON_PRIMARY)
        mouse.buttons(1, 0)
        assertEquals(listOf(1 to true), buttons)
        mouse.releaseDevice(2)
        assertEquals(listOf(1 to true, 1 to false), buttons)
    }

    @Test fun releasingAllLetsGoOfHeldButtons() {
        val mouse = input()
        mouse.buttons(1, MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY)
        mouse.releaseAll()
        assertEquals(listOf(1 to true, 2 to true, 1 to false, 2 to false), buttons)
        // After the release the mouse's next press counts again.
        mouse.buttons(1, MotionEvent.BUTTON_PRIMARY)
        assertEquals(1 to true, buttons.last())
    }

    @Test fun theWheelSendsWholeNotchesTowardThePlayerAsPositive() {
        val mouse = input()
        mouse.scroll(-1f)
        mouse.scroll(1f)
        mouse.scroll(-0.5f)
        mouse.scroll(-0.5f)
        assertEquals(listOf(1, -1, 1), wheel)
    }

    @Test fun aGuestMouseWithoutAWheelGetsNoNotches() {
        input(hasWheel = false).scroll(-3f)
        assertEquals(emptyList<Int>(), wheel)
    }
}
