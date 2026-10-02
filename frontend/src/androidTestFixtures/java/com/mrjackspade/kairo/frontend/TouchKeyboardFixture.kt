package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View

/** Actual host-window dispatch, including letterboxes and the real control overlay. */
object TouchKeyboardFixture {
    fun verify(test: Instrumentation, uri: String): String {
        val context = test.targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = test.startActivitySync(intent)
        fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
        fun value(owner: Any, name: String) = field(owner, name).get(owner)
        fun ui(action: () -> Unit) {
            var error: Throwable? = null
            test.runOnMainSync { try { action() } catch (e: Throwable) { error = e } }
            test.waitForIdleSync()
            error?.let { throw it }
        }
        Thread.sleep(10000)
        val dos = context.packageName.endsWith("kairodos")
        val panel = value(activity, if (dos) "keyboard" else "keyboardPanel") as GuestKeyboardPanel
        val surface = value(activity, if (dos) "surface" else "screen") as View
        val background = value(activity, if (dos) "videoFrame" else "root") as View
        val hostRoot = value(activity, if (dos) "appRoot" else "root") as View
        val oldWidth = hostRoot.layoutParams.width
        val controls = value(activity, "onScreenControls") as OnScreenControls
        val decider = value(activity, "inputModeDecider") as InputModeDecider
        val secondary = value(activity, if (dos) "secondaryDisplay" else "secondaryKeyboard") as SecondaryDisplayCoordinator
        val game = value(activity, "currentGame")!!
        val prefs = context.getSharedPreferences(if (dos) "kairodos" else "kairo98", 0)
        val id = if (dos) game.javaClass.getMethod("getContentId").invoke(game) as String else ""
        val touchKey = "game_setting_v1_${id}_touch_mode"
        val oldTouch = prefs.all[touchKey] as? Int
        val oldInput = if (!dos) value(game, "inputMode") else null
        val enabled = field(controls, "enabled")
        val oldEnabled = enabled.getBoolean(controls)
        val report = StringBuilder()
        fun rect(view: View): Rect {
            val point = IntArray(2); view.getLocationInWindow(point)
            return Rect(point[0], point[1], point[0] + view.width, point[1] + view.height)
        }
        fun dispatch(x: Float, y: Float, action: Int, time: Long) {
            val event = MotionEvent.obtain(time, time + if (action == MotionEvent.ACTION_DOWN) 0 else 20,
                action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { activity.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        fun point(video: Boolean): Pair<Float, Float> {
            val bounds = rect(background); val picture = rect(surface)
            for (y in bounds.top + 8 until bounds.bottom - 8 step 8)
                for (x in bounds.left + 40 until bounds.right - 40 step 8)
                    if (picture.contains(x,y) == video && !controls.hitTest(x.toFloat(),y.toFloat()))
                        return x.toFloat() to y.toFloat()
            error("No ${if(video) "video" else "letterbox"} test point: $bounds / $picture")
        }
        try {
            ui {
                secondary.stop(); panel.close()
                // A portrait-shaped app viewport on the RGDS reproduces phone
                // letterboxes without changing device display settings.
                hostRoot.layoutParams = hostRoot.layoutParams.apply { width = 320 }
            }
            Thread.sleep(500)
            for (mode in listOf(InputModeDecider.Mode.KEYBOARD, InputModeDecider.Mode.AUTO)) {
                ui {
                    if (dos) prefs.edit().putInt(touchKey, if(mode == InputModeDecider.Mode.KEYBOARD) 1 else 2).commit()
                    else field(game,"inputMode").set(game, InputModeDecider.storageValue(mode))
                }
                for (shown in listOf(false,true)) {
                    ui { enabled.setBoolean(controls,shown); panel.close(); controls.refreshVisibility(true) }
                    for (video in listOf(true,false)) {
                        ui {
                            decider.reset()
                            val (x,y) = point(video); val time=SystemClock.uptimeMillis()
                            dispatch(x,y,MotionEvent.ACTION_DOWN,time)
                            dispatch(x,y,MotionEvent.ACTION_UP,time)
                        }
                        Thread.sleep(1000)
                        ui {
                            check(panel.visibility == View.VISIBLE) { "$mode controls=$shown video=$video: guest keyboard did not open" }
                            panel.close(); controls.refreshVisibility(true)
                        }
                        report.append("$mode controls=$shown video=$video: OK\n")
                    }
                    if (shown) ui {
                        val button = (value(controls,"buttons") as Map<*,*>)["a"] as View
                        val r=rect(button); val t=SystemClock.uptimeMillis()
                        dispatch(r.exactCenterX(),r.exactCenterY(),MotionEvent.ACTION_DOWN,t)
                        dispatch(r.exactCenterX(),r.exactCenterY(),MotionEvent.ACTION_UP,t)
                        check(panel.visibility != View.VISIBLE) { "Control button opened keyboard" }
                        report.append("$mode control button: OK\n")
                    }
                }
            }
        } finally {
            ui {
                panel.close()
                hostRoot.layoutParams = hostRoot.layoutParams.apply { width = oldWidth }
                if(dos) prefs.edit().apply { if(oldTouch == null) remove(touchKey) else putInt(touchKey,oldTouch) }.commit()
                else field(game,"inputMode").set(game,oldInput)
                enabled.setBoolean(controls,oldEnabled)
                controls.refreshVisibility(true)
                activity.finish()
            }
        }
        return report.toString()
    }
}
