package com.mrjackspade.kairo.uitest

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mrjackspade.kairo.frontend.GameProbe
import com.mrjackspade.kairo.frontend.KairoActivity

/**
 * Drives the app as a player would, Selenium-style: it finds elements by selector in every window
 * of the app (both screens of a dual-screen device), touches them where they are at that moment,
 * and sends controller and keyboard input through the system's input path. Every action is
 * logged. Elements are looked up again for every action, so none can go stale.
 */
class Driver(val instrumentation: Instrumentation, val log: TestLog) {
    val expect = Expect(log)

    init {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "The UI tests need Android 10 or later (window inspection)" }
    }

    /** Runs [block] on the main thread and returns its result; its failure fails the caller. */
    fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    /** The root view of every window the app shows, on every display, bottom to top. */
    fun roots(): List<View> = onMain { WindowInspector.getGlobalWindowViews().filter { it.isAttachedToWindow } }

    /** An element the selector finds anywhere in the app. */
    fun find(by: By) = Element(this, listOf(Element.Step(by)))

    /** An element inside the page named [name], such as "Machine" or "flyout". */
    fun page(name: String) = Element(this, listOf(Element.Step(By.page(name))))

    /**
     * The views [path] selects: each selector searches below the views the previous one found.
     * Only present views count (Views.present); they are returned in drawing order, so the topmost comes last.
     * Main thread only.
     */
    internal fun resolve(path: List<By>): List<View> = resolveSteps(path.map { Element.Step(it) })

    /** [resolve] for steps that may each keep one match by index. Main thread only. */
    internal fun resolveSteps(path: List<Element.Step>): List<View> {
        var scopes = WindowInspector.getGlobalWindowViews().filter { it.isAttachedToWindow }
        for ((depth, step) in path.withIndex()) {
            val matches = scopes.flatMap { scope ->
                // A scope does not match itself again; the first selector may match a window's root.
                Views.descendants(scope).drop(if (depth == 0) 0 else 1).filter { Views.present(it) && step.by.matches(it) }.toList()
            }.distinct()
            scopes = step.index?.let { listOfNotNull(matches.getOrNull(it)) } ?: matches
            if (scopes.isEmpty()) return emptyList()
        }
        return scopes
    }

    /**
     * Waits for [what] to hold, as [Patience] allows. [condition] runs on the test thread, so it may
     * use elements and [onMain]; it must not touch views itself.
     */
    fun waitUntil(what: String, condition: () -> Boolean) {
        log.step("wait", what)
        Patience.until(log, what, condition)
    }

    /** Every text the app shows now, in every window, as the player could read it. */
    fun visibleTexts(): List<String> = onMain {
        WindowInspector.getGlobalWindowViews().filter { it.isAttachedToWindow }.flatMap(Views::texts)
    }

    /** Waits until the main thread has nothing left to do, as after an action. */
    fun idle() = instrumentation.waitForIdleSync()

    // Touch -----------------------------------------------------------------------------------

    /**
     * Touches [view] at its centre as it is now: a down and an up, dispatched to its window as
     * the touch screen would. The position is read in the same main-thread turn that sends the
     * events, so it cannot be out of date. Main thread only.
     */
    internal fun tapNow(view: View) {
        val bounds = Views.boundsInWindow(view)
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        val root = view.rootView
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, time, action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { root.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }

    // Keys ------------------------------------------------------------------------------------

    /** The device's own controller, so key events come from a real gamepad; null on a phone. */
    private val gamepad: InputDevice? by lazy {
        InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)
            .firstOrNull { !it.isVirtual && it.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD }
    }

    /**
     * Presses and releases [keyCode] through the system's input path, to whichever window has
     * focus, as the device's controller (or keyboard, for [fromKeyboard]) would.
     */
    fun press(keyCode: Int, holdMillis: Long = 80, fromKeyboard: Boolean = false) {
        log.step("key", KeyEvent.keyCodeToString(keyCode), mapOf("holdMillis" to holdMillis))
        val source = if (fromKeyboard) InputDevice.SOURCE_KEYBOARD else InputDevice.SOURCE_GAMEPAD
        val device = if (fromKeyboard) KeyEvent.KEYCODE_UNKNOWN else gamepad?.id ?: KeyEvent.KEYCODE_UNKNOWN
        val down = SystemClock.uptimeMillis()
        inject(KeyEvent(down, down, KeyEvent.ACTION_DOWN, keyCode, 0, 0, device, 0, 0, source))
        if (holdMillis > 0) Thread.sleep(holdMillis)
        inject(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0, 0, device, 0, 0, source))
        idle()
    }

    private fun inject(event: KeyEvent) {
        check(instrumentation.uiAutomation.injectInputEvent(event, true)) { "The system refused $event" }
    }

    /** Types [text] into the focused field through the system's input path. */
    fun type(text: String) {
        log.step("type", text)
        instrumentation.sendStringSync(text)
        idle()
    }

    // The app ---------------------------------------------------------------------------------

    /** The app's activity in front, on whichever screen. */
    fun activity(): KairoActivity<*> = onMain(::activityNow)

    private fun activityNow(): KairoActivity<*> {
        val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        return resumed.filterIsInstance<KairoActivity<*>>().firstOrNull()
            ?: ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.PAUSED)
                .filterIsInstance<KairoActivity<*>>().firstOrNull()
            ?: error("No Kairo activity is open (resumed: ${resumed.map(Activity::getLocalClassName)})")
    }

    /** Whether any activity of the app is still open. Main thread only. */
    internal fun anyActivityOpen(): Boolean {
        val registry = ActivityLifecycleMonitorRegistry.getInstance()
        return listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.STARTED, Stage.CREATED)
            .any { registry.getActivitiesInStage(it).isNotEmpty() }
    }

    /** [probe] without logging, for polling. */
    fun probeNow(): GameProbe = onMain { activityNow().gameProbe() }

    /** What the running game is doing (KairoActivity.gameProbe). */
    fun probe(): GameProbe = probeNow().also { log.step("probe", it.toString()) }

    /** Waits until the game's session reaches [state], such as "Running". */
    fun waitForGame(state: String = "Running") = waitUntil("the game to be $state") {
        val probe = probeNow()
        check(probe.state != "Error" && probe.state != "Failed") { "The game failed: $probe" }
        probe.state == state
    }

    /** Waits until the running game has drawn [frames] more frames, so time has passed in it. */
    fun waitFrames(frames: Long) {
        val start = probe().details.getValue("frames").toLong()
        waitUntil("$frames more frames") { probeNow().details["frames"]?.toLongOrNull()?.let { it >= start + frames } == true }
    }

    // Guest input --------------------------------------------------------------------------------

    /** The newest guest input so far, to read what arrives after it (GuestInputRecord). */
    fun inputMark(): Long = onMain { activityNow().guestInput.latest }

    /** Every input the guest received after [mark], logged. */
    fun inputSince(mark: Long): List<com.mrjackspade.kairo.frontend.GuestInputRecord.Event> =
        onMain { activityNow().guestInput.since(mark) }.also { events ->
            log.step("input", "${events.size} events since #$mark", mapOf("events" to events.take(40).joinToString()))
        }

    /** Waits until the input the guest received after [mark] satisfies [holds]. */
    fun waitForInput(what: String, mark: Long, holds: (List<com.mrjackspade.kairo.frontend.GuestInputRecord.Event>) -> Boolean) =
        waitUntil("the guest to receive $what") { holds(onMain { activityNow().guestInput.since(mark) }) }

    // Launches from outside --------------------------------------------------------------------

    /**
     * Starts [game] the way a frontend does: a VIEW intent for the file's document, sent to the
     * app's launcher activity. Returns once the activity has handled it.
     */
    fun launchExternally(game: android.net.Uri) {
        val target = instrumentation.targetContext
        val launcher = checkNotNull(target.packageManager.getLaunchIntentForPackage(target.packageName)).component
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).setComponent(launcher).setData(game)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        log.step("launch", "external VIEW of $game")
        target.startActivity(intent)
        idle()
    }

    val pictures = Pictures(this)
}
