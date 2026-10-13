package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/** Each host's real Exit confirmation: the modal the game flyout's Exit row opens. */
object ExitDialogFixture {
    fun verify(test: Instrumentation) {
        val launch = test.targetContext.packageManager.getLaunchIntentForPackage(test.targetContext.packageName)!!
        val activity = test.startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun ui(action: () -> Unit) {
            var error: Throwable? = null
            test.runOnMainSync { try { action() } catch (caught: Throwable) { error = caught } }
            test.waitForIdleSync()
            error?.let { throw it }
        }
        fun field(name: String) = activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity)
        val pages by lazy { field("pages") as PageStack }
        val secondary by lazy { activity.javaClass.declaredFields.single { it.type == SecondaryDisplayCoordinator::class.java }
            .apply { isAccessible = true }.get(activity) as SecondaryDisplayCoordinator }
        var companionInput = false
        fun flyout() = pages.top as RowsPage
        fun modal() = pages.top as? ConfirmPage
        fun focused() = modal()?.focusedButton
        fun open() {
            test.setInTouchMode(true)
            ui {
                pages.closeAll()
                (field("sessionFlow") as SessionFlow).open("f-exit", confirm = true)
            }
            Thread.sleep(250)
            ui {
                val modal = modal() ?: error("Exit confirm is not open: ${pages.top}")
                check(modal.title?.startsWith("Exit Kairo") == true) { "Exit title: ${modal.title}" }
                check(pages.page(pages.size - 2)?.isFlyout == true) { "Exit confirm is not over the flyout" }
                check(modal.buttons == listOf("Cancel", "Exit")) { "Exit buttons: ${modal.buttons}" }
                check(focused() == "Cancel") { "Exit initial focus: ${focused()}" }
            }
        }
        fun closed() = check(pages.isActive && modal() == null && flyout().isFlyout && !activity.isFinishing) {
            "The confirm did not close with the app and flyout still open"
        }
        fun key(code: Int) = ui {
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                val event = KeyEvent(action, code)
                if (companionInput) secondary.forwardKey(event) else activity.dispatchKeyEvent(event)
            }
        }
        fun hat(x: Float, y: Float) {
            val now = SystemClock.uptimeMillis()
            val props = MotionEvent.PointerProperties().apply { id = 0 }
            val coords = MotionEvent.PointerCoords().apply {
                setAxisValue(MotionEvent.AXIS_HAT_X, x)
                setAxisValue(MotionEvent.AXIS_HAT_Y, y)
            }
            val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1, arrayOf(props), arrayOf(coords),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_JOYSTICK, 0)
            ui { if (companionInput) secondary.forwardMotion(event) else activity.dispatchGenericMotionEvent(event) }
            event.recycle()
        }
        try {
            Thread.sleep(8000)
            open()
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            ui { check(focused() == "Exit") { "Right did not focus Exit: ${focused()}" } }
            key(KeyEvent.KEYCODE_DPAD_LEFT)
            ui { check(focused() == "Cancel") }
            key(KeyEvent.KEYCODE_BUTTON_A)
            ui { closed() }
            companionInput = true
            open()
            hat(1f, 0f); hat(0f, 0f)
            ui { check(focused() == "Exit") { "Hat right did not focus Exit: ${focused()}" } }
            hat(-1f, 0f); hat(0f, 0f)
            ui { check(focused() == "Cancel") }
            hat(0f, 1f); hat(0f, 0f)
            ui { check(focused() == "Exit") { "Hat down did not focus Exit: ${focused()}" } }
            hat(0f, -1f); hat(0f, 0f)
            ui { check(focused() == "Cancel") }
            key(KeyEvent.KEYCODE_BUTTON_B)
            ui { closed() }
            open()
            key(KeyEvent.KEYCODE_BACK)
            ui { closed() }
            open()
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            key(KeyEvent.KEYCODE_BUTTON_A)
            repeat(100) { if (activity.isFinishing) return; Thread.sleep(50) }
            error("Confirmed Exit did not finish the app")
        } finally {
            ui { if (!activity.isFinishing) activity.finish() }
        }
    }
}
