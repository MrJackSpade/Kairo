package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.ActivityOptions
import android.app.Instrumentation
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.ListView
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Run in both hosts: directory workers, controller scrolling/back, error retry,
 * selection, and dismissal while a provider is still working. */
object DirectoryPickerFixture {
    fun verify(test: Instrumentation) {
        val intent = test.targetContext.packageManager.getLaunchIntentForPackage(test.targetContext.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val display = (test.targetContext.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager).displays
            .firstOrNull { it.displayId != 0 && it.isValid }
        val options = ActivityOptions.makeBasic().apply { if (display != null) launchDisplayId = display.displayId }.toBundle()
        val activity = test.startActivitySync(intent, options)
        var picker: DirectoryPicker? = null
        fun ui(action: () -> Unit) {
            var error: Throwable? = null
            test.runOnMainSync { try { action() } catch (failure: Throwable) { error = failure } }
            test.waitForIdleSync()
            error?.let { throw it }
        }
        fun descendants(view: View): List<View> = listOf(view) + ((view as? ViewGroup)?.let { group ->
            (0 until group.childCount).flatMap { descendants(group.getChildAt(it)) }
        } ?: emptyList())
        fun views() = WindowInspector.getGlobalWindowViews().flatMap(::descendants)
        fun text(value: String) = views().filterIsInstance<TextView>().firstOrNull { it.text.toString() == value }
        fun list() = views().filterIsInstance<ListView>().single { view ->
            view.adapter?.count?.let { it > 0 } == true && view.getItemAtPosition(0) in setOf("Folder/", "child.bat")
        }
        fun await(message: String, condition: () -> Boolean) {
            repeat(200) {
                var ready = false
                ui { ready = condition() }
                if (ready) return
                Thread.sleep(50)
            }
            error(message)
        }
        val secondary by lazy { activity.javaClass.declaredFields.single { it.type == SecondaryDisplayCoordinator::class.java }
            .apply { isAccessible = true }.get(activity) as SecondaryDisplayCoordinator }
        var companionInput = false
        fun key(code: Int) {
            if (companionInput) ui {
                secondary.forwardKey(KeyEvent(KeyEvent.ACTION_DOWN, code))
                secondary.forwardKey(KeyEvent(KeyEvent.ACTION_UP, code))
            } else { test.sendKeyDownUpSync(code); test.waitForIdleSync() }
        }
        var selected: DirectoryPicker.Entry? = null
        var cancelled = 0
        val loads = AtomicInteger()
        try {
            ui {
                picker = DirectoryPicker.show(activity, "Fixture files", DirectoryPicker.Location("root", "Files"), {
                    check(Looper.myLooper() != Looper.getMainLooper())
                    if (loads.incrementAndGet() == 1) error("Fixture read failure")
                    if (it == "child") listOf(DirectoryPicker.Entry("child-file", "child.bat", false))
                    else listOf(DirectoryPicker.Entry("child", "Folder", true)) + (0..39).map { number ->
                        DirectoryPicker.Entry("file$number", "file%02d.exe".format(number), false)
                    }
                }, { selected = it }, { cancelled++ })
            }
            await("Directory error was not displayed") { text("Fixture read failure") != null }
            ui { check(text("Retry")!!.performClick()) }
            await("Directory retry did not load") { text("Folder/") != null }
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            ui { list().setSelection(0); list().requestFocus() }
            key(KeyEvent.KEYCODE_BUTTON_A)
            await("Controller did not enter directory") { text("child.bat") != null }
            key(KeyEvent.KEYCODE_BACK)
            await("Back did not return to parent") { text("Folder/") != null }
            companionInput = true
            test.setInTouchMode(true)
            await("Picker did not enter touch mode") { list().isInTouchMode }
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            await("Companion D-pad did not restore list selection") {
                !list().isInTouchMode && list().selectedItem != null
            }
            ui { list().setSelection(0) }
            key(KeyEvent.KEYCODE_BUTTON_A)
            await("Bottom-display confirm did not enter folder") { text("child.bat") != null }
            ui { secondary.forwardBack() }
            await("Bottom-display Back did not return to parent") { text("Folder/") != null }
            ui { list().requestFocus(); list().setSelection(0) }
            repeat(40) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
            ui {
                check(list().selectedItem == "file39.exe") { "Off-screen selection: ${list().selectedItem}" }
                check(list().selectedView?.isShown == true)
            }
            key(KeyEvent.KEYCODE_BUTTON_A)
            await("Controller did not select file") { selected?.id == "file39" }
            check(cancelled == 0)
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            ui {
                picker = DirectoryPicker.show(activity, "Pending files", DirectoryPicker.Location("root", "Files"), {
                    started.countDown()
                    release.await(30, TimeUnit.SECONDS)
                    emptyList()
                }, { error("Cancelled picker selected a file") }, { cancelled++ })
            }
            check(started.await(5, TimeUnit.SECONDS))
            key(KeyEvent.KEYCODE_BACK)
            release.countDown()
            await("Pending picker did not close") { cancelled == 1 && text("Pending files") == null }
            Thread.sleep(150)
            ui { check(text("Pending files") == null && cancelled == 1) }
        } finally {
            ui { picker?.close(); activity.finish() }
        }
    }
}
