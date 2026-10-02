package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.ActivityOptions
import android.app.Instrumentation
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/** Uses each host's actual guest key set, with isolated bindings and no saved user changes. */
object KeyCycleFixture {
    fun verify(test: Instrumentation) {
        val launch = test.targetContext.packageManager.getLaunchIntentForPackage(test.targetContext.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val display = (test.targetContext.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager).displays
            .firstOrNull { it.displayId != 0 && it.isValid }
        val options = ActivityOptions.makeBasic().apply { if (display != null) launchDisplayId = display.displayId }.toBundle()
        val activity = test.startActivitySync(launch, options)
        var failure: Throwable? = null
        try {
            test.runOnMainSync {
                try {
                    val hostEditor = activity.javaClass.getDeclaredField("controllerEditor")
                        .apply { isAccessible = true }.get(activity)
                    val guest = hostEditor.javaClass.getDeclaredField("guest").apply { isAccessible = true }
                        .get(hostEditor) as ControllerGuestSpec
                    val sequence = listOf("A", "B", "C").map { label -> guest.keyCodes.first { guest.keyLabel(it).equals(label, true) } }
                    val codec = ControllerBindingsCodec({ emptyList() }, { it in guest.keyCodes },
                        guest.joystickControls.map { it.first }, guest.actions.map { it.first })
                    val emitted = ArrayList<Pair<Int, Boolean>>()
                    val router = InputRouter({ key, down -> emitted.add(key to down) })
                    val mapper = GamepadMapper(router, JoystickInputRouter({ _, _ -> }),
                        MouseInputRouter({ _, _ -> }, { _, _ -> }), {}, {})
                    fun cycle(input: String, keys: List<Int> = sequence) = ControllerBinding(input, cycleKeys = keys)
                    fun tap(control: String, key: Int) {
                        emitted.clear()
                        mapper.pressVirtual(control, "onscreen:test")
                        mapper.pressVirtual(control, "onscreen:test") // Held/repeated event does not advance.
                        mapper.releaseVirtual("onscreen:test")
                        check(emitted == listOf(key to true, key to false)) { "$control: $emitted expected $key" }
                    }
                    for ((left, right) in ControllerKeyCycles.pairs) {
                        val bindings = listOf(cycle(left), cycle(right))
                        check(codec.parse(codec.toJson(bindings).toString()) == bindings)
                        mapper.bindings = bindings
                        tap(left.removePrefix("virtual:"), sequence[2])
                        tap(right.removePrefix("virtual:"), sequence[0])
                        tap(right.removePrefix("virtual:"), sequence[1])
                        tap(right.removePrefix("virtual:"), sequence[2])
                        tap(right.removePrefix("virtual:"), sequence[0])
                        tap(left.removePrefix("virtual:"), sequence[2])
                        mapper.bindings = listOf(cycle(left))
                        tap(left.removePrefix("virtual:"), sequence[2])
                        tap(left.removePrefix("virtual:"), sequence[1])
                        mapper.bindings = listOf(cycle(left), cycle(right, sequence.reversed()))
                        tap(left.removePrefix("virtual:"), sequence[2])
                        tap(right.removePrefix("virtual:"), sequence[2]) // Different sequences are independent.
                    }
                    mapper.bindings = ControllerKeyCycles.inputs.map { cycle(it) }
                    for (control in listOf("right", "r1", "r2")) tap(control, sequence[0])
                    mapper.bindings = listOf(cycle("virtual:left"), cycle("virtual:right"))
                    fun hat(value: Float) {
                        val p = MotionEvent.PointerProperties().apply { id = 0 }
                        val c = MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_HAT_X, value) }
                        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 1, arrayOf(p), arrayOf(c),
                            0, 0, 1f, 1f, 42, 0, InputDevice.SOURCE_JOYSTICK, 0)
                        try { check(mapper.motion(event)) } finally { event.recycle() }
                    }
                    emitted.clear(); hat(-1f); hat(-1f); hat(0f); hat(1f); hat(0f)
                    check(emitted == listOf(sequence[2] to true, sequence[2] to false, sequence[0] to true, sequence[0] to false))
                    mapper.pressVirtual("right", "onscreen:test")
                    mapper.bindings = emptyList()
                    check(router.pressedKeys().isEmpty())
                    for (input in listOf("virtual:a", "virtual:up", "virtual:rsleft")) {
                        val invalid = org.json.JSONArray().put(org.json.JSONObject().put("input", input)
                            .put("cycleKeys", org.json.JSONArray(sequence)))
                        check(!codec.valid(invalid))
                    }
                    mapper.releaseAll()

                    var saved = listOf(cycle("virtual:left"), cycle("virtual:l1"), cycle("virtual:r1"))
                    val originalShoulders = saved.filter { it.input != "virtual:left" }
                    val root = FrameLayout(activity)
                    val content = activity.findViewById<ViewGroup>(android.R.id.content)
                    content.addView(root, ViewGroup.LayoutParams(-1, -1))
                    val editor = ControllerEditor<String>(activity, root, { saved }, { _, value -> saved = value }, {},
                        { PhysicalControllerBindings.defaults() }, {}, {}, { .1f }, {}, {}, {}, { false }, {},
                        { it }, guest, { codec.toJson(it) })
                    try {
                        editor.show(null)
                        val type = editor.javaClass
                        type.getDeclaredField("selectedInput").apply { isAccessible = true }.set(editor, "virtual:left")
                        val stage = type.getDeclaredField("stage").apply { isAccessible = true }
                        stage.set(editor, stage.type.enumConstants.first { it.toString() == "TARGET" })
                        type.getDeclaredMethod("render").apply { isAccessible = true }.invoke(editor)
                        fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
                            (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                        val choice = descendants(root).filterIsInstance<TextView>().first { it.text.toString() == "Key cycle" }
                        (choice.parent as View).performClick()
                        val save = descendants(root).filterIsInstance<TextView>().first { it.text.toString() == "Save key cycle" }
                        save.isFocusableInTouchMode = true
                        check(save.requestFocusFromTouch())
                        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP))
                            check(editor.handleKey(KeyEvent(action, KeyEvent.KEYCODE_BUTTON_A)))
                        check(saved.filter { it.input in listOf("virtual:l1", "virtual:r1") } == originalShoulders)
                        check(saved.single { it.input == "virtual:left" }.cycleKeys == sequence)
                        check(saved.single { it.input == "virtual:right" }.cycleKeys == sequence)
                    } finally { editor.close(); content.removeView(root) }
                } catch (caught: Throwable) { failure = caught }
            }
            failure?.let { throw it }
        } finally { test.runOnMainSync { activity.finish() } }
    }
}
