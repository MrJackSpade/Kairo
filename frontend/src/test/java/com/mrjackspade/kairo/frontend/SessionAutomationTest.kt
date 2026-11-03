package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A session's automation (Kairo#110): the player taking over stops the typed startup steps and
 * keeps the disk swaps watched (Kairo#83, shared 0e1c8cb); the watcher stops when the session
 * ends; a start at the prompt types nothing and swaps nothing (Kairo#89, shared 86b1b7d).
 */
class SessionAutomationTest {
    private val swapScreen = 0x5A5AL
    private val rule = DiskSwap("swap-1", 0, "sha256-fd-v1:aa", setOf(swapScreen), "", false)

    /** A machine that keeps what the automation asks of it; the test runs the swap watcher itself. */
    private class FakeMachine : SessionAutomation.Machine<String> {
        val typed = mutableListOf<String>()
        var takenBack = 0
        var typedCalls = 0
        val followed = mutableListOf<AtomicBoolean>()
        val watchers = mutableListOf<Pair<List<DiskSwap>, () -> Boolean>>()
        override fun type(steps: List<String>) { typed += steps }
        override fun takeBackSteps() { takenBack++ }
        override fun follow(steps: List<String>, cancelled: AtomicBoolean) { followed += cancelled }
        override fun typed() { typedCalls++ }
        override fun watchSwaps(rules: List<DiskSwap>, stopped: () -> Boolean) { watchers += rules to stopped }
    }

    @Test
    fun takingOverStopsTheStepsAndASwapStillComes() {
        val machine = FakeMachine()
        val automation = SessionAutomation(machine)
        val session = AtomicBoolean(true)
        automation.start(false, { listOf("A:", "GAME") }, listOf(rule)) { session.get() }
        assertEquals(listOf("A:", "GAME"), machine.typed)
        automation.takeOver()
        assertEquals("the typer's steps taken back", 1, machine.takenBack)
        assertTrue("the typer's follower cancelled", machine.followed.single().get())
        // The game asks for its next disk after the player opened the menu: the session's watcher,
        // run as MachineActivity runs it, swaps it in; the session then ends, and so does the watcher.
        val (rules, stopped) = machine.watchers.single()
        val swapped = mutableListOf<DiskSwap>()
        DiskSwapMatcher({ swapScreen }, { true }, stopped) { swap ->
            swapped += swap
            session.set(false)
        }.run(rules)
        assertEquals("the swap after taking over", listOf(rule), swapped)
    }

    @Test
    fun theWatcherRunsUntilTheSessionEnds() {
        val machine = FakeMachine()
        val automation = SessionAutomation(machine)
        val session = AtomicBoolean(true)
        automation.start(false, { emptyList() }, listOf(rule)) { session.get() }
        val stopped = machine.watchers.single().second
        assertFalse("watching while the session runs", stopped())
        automation.takeOver()
        assertFalse("still watching after the player took over", stopped())
        session.set(false)
        assertTrue("stopped when the session ended", stopped())
        assertEquals("nothing to take back once nothing is typed", 0, machine.takenBack)
    }

    @Test
    fun aStartAtThePromptTypesNothingAndSwapsNothing() {
        val machine = FakeMachine()
        var stepsRead = false
        SessionAutomation(machine).start(true, { stepsRead = true; listOf("A:", "GAME") }, listOf(rule)) { true }
        assertFalse("the steps are not even built", stepsRead)
        assertEquals(emptyList<String>(), machine.typed)
        assertEquals("the typer is done at once", 1, machine.typedCalls)
        assertEquals("no swap watched", 0, machine.watchers.size)
    }

    @Test
    fun aNewSessionCancelsTheLastOnesFollower() {
        val machine = FakeMachine()
        val automation = SessionAutomation(machine)
        automation.start(false, { listOf("A:") }, emptyList()) { true }
        automation.start(false, { listOf("B:") }, emptyList()) { true }
        assertTrue(machine.followed[0].get())
        assertFalse(machine.followed[1].get())
        automation.typingDone()
        automation.takeOver()
        assertEquals("the typer finished: nothing to take back", 0, machine.takenBack)
    }
}
