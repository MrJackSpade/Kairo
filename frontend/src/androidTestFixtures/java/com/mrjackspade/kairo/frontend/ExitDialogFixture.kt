package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.TextView

/** Each host's real Exit confirmation, through its own dialog window dispatch. */
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
        fun descendants(view: View): List<View> = listOf(view) +
            ((view as? ViewGroup)?.let { group -> (0 until group.childCount).flatMap { descendants(group.getChildAt(it)) } }
                ?: emptyList())
        lateinit var decor: View
        val secondary by lazy { activity.javaClass.declaredFields.single { it.type == SecondaryDisplayCoordinator::class.java }
            .apply { isAccessible = true }.get(activity) as SecondaryDisplayCoordinator }
        var companionInput = false
        fun focused() = (decor.findFocus() as? Button)?.text?.toString()
        fun open() {
            test.setInTouchMode(true)
            ui { activity.javaClass.getDeclaredMethod("confirmExit").apply { isAccessible = true }.invoke(activity) }
            ui {
                decor = WindowInspector.getGlobalWindowViews().single { root ->
                    descendants(root).filterIsInstance<TextView>().any { it.text.toString().startsWith("Exit Kairo") }
                }
                check(focused() == "Cancel") { "Exit initial focus: ${focused()} (${decor.findFocus()?.javaClass?.simpleName})" }
            }
        }
        fun key(code: Int) = ui {
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                val event = KeyEvent(action, code)
                if (companionInput) secondary.forwardKey(event) else decor.dispatchKeyEvent(event)
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
            ui { if (companionInput) secondary.forwardMotion(event) else decor.dispatchGenericMotionEvent(event) }
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
            ui { check(!decor.isAttachedToWindow && !activity.isFinishing) { "Cancel did not keep app open" } }
            companionInput = true
            open()
            hat(1f, 0f); hat(0f, 0f)
            ui { check(focused() == "Exit") { "Hat right did not focus Exit: ${focused()}" } }
            hat(-1f, 0f); hat(0f, 0f)
            ui { check(focused() == "Cancel") }
            hat(0f, 1f); hat(0f, 0f)
            ui { check(focused() == "Exit") }
            hat(0f, -1f); hat(0f, 0f)
            ui { check(focused() == "Cancel") }
            key(KeyEvent.KEYCODE_BUTTON_B)
            ui { check(!decor.isAttachedToWindow && !activity.isFinishing) }
            open()
            key(KeyEvent.KEYCODE_BACK)
            ui { check(!decor.isAttachedToWindow && !activity.isFinishing) }
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
