package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView

/** Traverse each host's real menu, including held hat input and touch-mode entry. */
object LibraryMenuFixture {
    fun verify(test: Instrumentation) {
        val launch = test.targetContext.packageManager.getLaunchIntentForPackage(test.targetContext.packageName)!!
        val activity = test.startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun ui(action: () -> Unit) {
            var failure: Throwable? = null
            test.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
            test.waitForIdleSync()
            failure?.let { throw it }
        }
        fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.get(owner)
        lateinit var screen: LibraryScreen<*>
        lateinit var scroll: ScrollView
        lateinit var items: List<View>
        fun selected() = items.indexOfFirst { it.isSelected }
        fun visible() {
            check(items.count { it.isSelected } == 1)
            val item = items[selected()]
            val row = IntArray(2); val viewport = IntArray(2)
            item.getLocationOnScreen(row); scroll.getLocationOnScreen(viewport)
            check(row[1] >= viewport[1] && row[1] + item.height <= viewport[1] + scroll.height) {
                "Menu row ${selected()} clipped: ${row[1]}..${row[1] + item.height}, viewport=${viewport[1]}..${viewport[1] + scroll.height}"
            }
        }
        fun key(code: Int) = ui {
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        fun axis(y: Float) {
            val now = SystemClock.uptimeMillis()
            val p = MotionEvent.PointerProperties().apply { id = 0 }
            val c = MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_HAT_Y, y) }
            val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1, arrayOf(p), arrayOf(c),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_JOYSTICK, 0)
            try { ui { activity.dispatchGenericMotionEvent(event) } } finally { event.recycle() }
        }
        try {
            Thread.sleep(5000)
            ui {
                val flow = field(activity, "libraryFlow")!!
                screen = flow.javaClass.getMethod("getScreen").invoke(flow) as LibraryScreen<*>
                scroll = field(screen, "actionsScroll") as ScrollView
                @Suppress("UNCHECKED_CAST")
                items = field(screen, "actionItems") as List<View>
                check(items.size > 6)
                test.setInTouchMode(true)
                screen.openActions()
            }
            Thread.sleep(250)
            ui { visible(); check(selected() == 0) }
            repeat(items.lastIndex) { key(KeyEvent.KEYCODE_DPAD_DOWN); ui { visible() } }
            ui { check(selected() == items.lastIndex) }
            repeat(items.lastIndex) { key(KeyEvent.KEYCODE_DPAD_UP); ui { visible() } }
            ui { check(selected() == 0) }
            for (direction in listOf(1f, -1f)) {
                // Physical hats emit MotionEvents and do not leave Android touch mode.
                ui { test.setInTouchMode(true) }
                axis(direction)
                repeat(items.size + 5) { Thread.sleep(150); ui { visible() } }
                axis(0f)
                ui { check(selected() == if (direction > 0) items.lastIndex else 0) }
            }
            // Reopening from a scrolled bottom must reveal the new first selection.
            ui { repeat(items.size) { screen.moveActionSelection(1) }; screen.closeActions() }
            Thread.sleep(200)
            ui { screen.openActions() }
            Thread.sleep(250)
            ui { visible(); check(selected() == 0) }
        } finally { ui { activity.finish() } }
    }
}
