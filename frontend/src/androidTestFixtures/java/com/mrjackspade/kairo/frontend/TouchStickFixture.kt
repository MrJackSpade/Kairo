package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.FrameLayout
import java.io.File
import kotlin.math.hypot

object TouchStickFixture {
    fun verify(test: Instrumentation) {
        val activity = test.startActivitySync(test.targetContext.packageManager
            .getLaunchIntentForPackage(test.targetContext.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val preferences = activity.getSharedPreferences("touch-stick-fixture", 0)
        fun ui(action: () -> Unit) {
            var failure: Throwable? = null
            test.runOnMainSync { try { action() } catch (e: Throwable) { failure = e } }
            test.waitForIdleSync()
            failure?.let { throw it }
        }
        fun descendants(view: View): List<View> = listOf(view) + ((view as? ViewGroup)?.let { group ->
            (0 until group.childCount).flatMap { descendants(group.getChildAt(it)) }
        } ?: emptyList())
        try {
            ui {
                preferences.edit().clear().putBoolean("onscreen_enabled", true).commit()
                var profile = ControllerLayout.WITH_STICKS
                val keys = InputRouter({ _, _ -> })
                var mouseX = 0
                val mouse = MouseInputRouter({ x, _ -> mouseX += x }, { _, _ -> })
                val mapper = GamepadMapper(keys, JoystickInputRouter({ _, _ -> }), mouse, {}, {})
                mapper.bindings = listOf(ControllerBinding("virtual:lsright", keys = listOf(10)),
                    ControllerBinding("virtual:lsup", keys = listOf(11)),
                    ControllerBinding("virtual:rsleft", keys = listOf(12)),
                    ControllerBinding("virtual:right", keys = listOf(13)))
                val root = FrameLayout(activity)
                val controls = OnScreenControls(activity, root, mapper, preferences, {}, { profile })
                fun layout(w: Int, h: Int) {
                    controls.refreshVisibility(true)
                    repeat(3) {
                        controls.refreshVisibility(true)
                        descendants(root).forEach { it.forceLayout() }
                        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, w, h)
                    }
                    controls.refreshVisibility(true)
                }
                fun view(id: String) = descendants(root).single { it.tag == id }
                fun axis(stick: TouchStickView, name: String) = TouchStickView::class.java.getDeclaredField(name)
                    .apply { isAccessible = true }.getFloat(stick)
                fun event(stick: View, action: Int, x: Float, y: Float) {
                    val e = MotionEvent.obtain(0, 0, action, x, y, 0)
                    try { stick.dispatchTouchEvent(e) } finally { e.recycle() }
                }
                for ((w, h) in listOf(1280 to 720, 720 to 1280)) {
                    layout(w, h)
                    val left = view("ls") as TouchStickView
                    val right = view("rs") as TouchStickView
                    check(left.visibility == View.VISIBLE && right.visibility == View.VISIBLE)
                    check(left.top > view("down").bottom && right.top > view("a").bottom) { "Sticks overlap upper controls at ${w}x$h: L=${left.top}, down=${view("down").bottom}, R=${right.top}, A=${view("a").bottom}" }
                    check(descendants(root).none { it.tag in listOf("rsup", "rsdown", "rsleft", "rsright") })
                    event(left, MotionEvent.ACTION_DOWN, left.width / 2f, left.height / 2f)
                    check(keys.pressedKeys().isEmpty())
                    event(left, MotionEvent.ACTION_MOVE, left.width * 3f, -left.height * 2f)
                    check(hypot(axis(left, "stickX"), axis(left, "stickY")) in .999f..1.001f)
                    check(keys.pressedKeys().containsAll(listOf(10, 11)))
                    event(right, MotionEvent.ACTION_DOWN, 0f, right.height / 2f)
                    check(keys.pressedKeys().containsAll(listOf(10, 11, 12)))
                    event(left, MotionEvent.ACTION_UP, left.width * 3f, -left.height * 2f)
                    check(keys.pressedKeys() == setOf(12))
                    check(axis(left, "stickX") == 0f && axis(left, "stickY") == 0f)
                    event(right, MotionEvent.ACTION_CANCEL, 0f, 0f)
                    check(keys.pressedKeys().isEmpty() && axis(right, "stickX") == 0f)
                    // Preserve a rendered view of the default layout and rim-clipped thumb.
                    event(left, MotionEvent.ACTION_DOWN, left.width.toFloat(), left.height / 2f)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).also { it.drawColor(0xff18212a.toInt()); root.draw(it) }
                    File(activity.cacheDir, "touch-sticks-$w.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    controls.refreshVisibility(false)
                    check(keys.pressedKeys().isEmpty() && axis(left, "stickX") == 0f)
                    profile = ControllerLayout.WITHOUT_STICKS
                    controls.refreshVisibility(true); layout(w, h)
                    check(left.visibility == View.GONE && right.visibility == View.GONE)
                    profile = ControllerLayout.WITH_STICKS
                    controls.refreshVisibility(true); layout(w, h)
                    check(left.visibility == View.VISIBLE && right.visibility == View.VISIBLE)
                }
                mapper.bindings = listOf(ControllerBinding("virtual:right", keys = listOf(13)))
                mapper.moveVirtualStick("ls", 1f, 0f)
                check(keys.pressedKeys() == setOf(13)) { "Left stick lacks D-pad fallback" }
                mapper.moveVirtualStick("ls", 0f, 0f)
                mapper.bindings = listOf(ControllerBinding("virtual:rsright", mouse = "moveRight"))
                mapper.moveVirtualStick("rs", .5f, 0f); mouse.tick(100); val slow = mouseX
                mapper.moveVirtualStick("rs", 0f, 0f); mouseX = 0
                mapper.moveVirtualStick("rs", 1f, 0f); mouse.tick(100)
                check(slow > 0 && mouseX > slow) { "Stick mouse movement is not proportional" }
                mapper.releaseAll()
                controls.refreshVisibility(false)
            }
            test.setInTouchMode(true)
            var selected = false
            ui {
                ControllerConfiguration(preferences) { ControllerCapabilities.Detection(null) }
                    .show(activity, required = true, product = "TEST") { selected = true }
            }
            fun screen() = WindowInspector.getGlobalWindowViews().flatMap(::descendants)
                .filterIsInstance<FirstRunScreen>().single { it.isOpen }
            fun rows(screen: FirstRunScreen) = listOf("With Sticks.", "Without Sticks.", "Continue.").map { title ->
                descendants(screen).single { it.contentDescription?.toString()?.startsWith(title) == true }
            }
            ui {
                val screen = screen()
                val rows = rows(screen).filterIsInstance<View>()
                check(rows.none { it.hasFocus() || it.isSelected || it.isActivated }) { "Touch setup pre-highlights a choice" }
                check(!rows.last().isEnabled)
                screen.handleKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
                check(rows.first().hasFocus()) { "D-pad cannot enter unfocused setup" }
                screen.handleKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A))
            }
            ui {
                val rows = rows(screen()).filterIsInstance<View>()
                check(rows.last().isEnabled)
                rows.last().performClick()
                check(selected)
            }
        } finally {
            ui { preferences.edit().clear().commit(); activity.finish() }
        }
    }
}
