package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
            ui { screen.closeActions() }
            Thread.sleep(200)
            fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
                (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            lateinit var menu: View
            ui { menu = descendants(screen).first { it.contentDescription == "Library menu" } }
            fun cleanClose() = ui {
                check(!screen.actionsOpen)
                check(!menu.hasFocus() && !menu.isSelected && !menu.isPressed && !menu.isActivated) {
                    "Stale Menu highlight: focused=${menu.hasFocus()} selected=${menu.isSelected} pressed=${menu.isPressed} activated=${menu.isActivated}"
                }
                check(items.none { it.isSelected || it.isPressed || it.hasFocus() }) { "Hidden menu retains selection/focus" }
            }
            for (closeWithBack in listOf(true, false)) {
                ui {
                    test.setInTouchMode(true)
                    val now = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event = MotionEvent.obtain(now, now, action, menu.width / 2f, menu.height / 2f, 0)
                        try { menu.dispatchTouchEvent(event) } finally { event.recycle() }
                    }
                }
                Thread.sleep(250)
                ui { check(screen.actionsOpen); visible() }
                if (closeWithBack) key(KeyEvent.KEYCODE_BACK)
                else ui { (field(screen, "scrim") as View).performClick() }
                Thread.sleep(250)
                cleanClose()
            }
            // Controller entry and mixed touch/hat navigation retain one intentional cursor.
            key(KeyEvent.KEYCODE_MENU)
            Thread.sleep(250)
            axis(1f); axis(0f)
            ui { visible(); check(selected() == 1) }
            key(KeyEvent.KEYCODE_BACK)
            Thread.sleep(250)
            cleanClose()
            var before = 0
            ui { before = field(screen, "selectedIndex") as Int }
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            ui {
                val count = (field(screen, "entries") as List<*>).size
                check(field(screen, "selectedIndex") == (before + 1).coerceAtMost(count - 1))
                check(!menu.hasFocus())
            }
            ui { screen.openActions() }
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
