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
        val pages by lazy { activity.javaClass.getDeclaredField("pages").apply { isAccessible = true }.get(activity) as PageStack }
        fun page() = pages.top as DirectoryPicker
        fun text(value: String) = descendants(page().view).filterIsInstance<TextView>().firstOrNull { it.text.toString() == value }
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
        fun inParent() = page().itemIds().firstOrNull() == "entry0:child"
        var selected: DirectoryPicker.Entry? = null
        var cancelled = 0
        val loads = AtomicInteger()
        try {
            Thread.sleep(3000)
            ui {
                picker = DirectoryPicker.show(pages, "Fixture files", DirectoryPicker.Location("root", "Files"), {
                    check(Looper.myLooper() != Looper.getMainLooper())
                    if (loads.incrementAndGet() == 1) error("Fixture read failure")
                    if (it == "child") listOf(DirectoryPicker.Entry("child-file", "child.bat", false))
                    else listOf(DirectoryPicker.Entry("child", "Folder", true)) + (0..39).map { number ->
                        DirectoryPicker.Entry("file$number", "file%02d.exe".format(number), false)
                    }
                }, { selected = it }, { cancelled++ })
            }
            await("Directory error was not displayed") { text("Fixture read failure") != null }
            ui { check(page().focusedItemId == "retry"); page().itemView("retry")!!.performClick() }
            await("Directory retry did not load") { inParent() }
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            ui { check(page().focusedItemId == "entry1:file0") { "D-pad did not move: ${page().focusedItemId}" } }
            key(KeyEvent.KEYCODE_DPAD_UP)
            key(KeyEvent.KEYCODE_BUTTON_A)
            await("Controller did not enter directory") { page().itemIds() == listOf("entry0:child-file") }
            key(KeyEvent.KEYCODE_BACK)
            await("Back did not return to parent") { inParent() }
            companionInput = true
            test.setInTouchMode(true)
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            await("Companion D-pad did not move the selection") { page().focusedItemId == "entry1:file0" }
            key(KeyEvent.KEYCODE_DPAD_UP)
            key(KeyEvent.KEYCODE_BUTTON_A)
            await("Bottom-display confirm did not enter folder") { page().itemIds() == listOf("entry0:child-file") }
            ui { secondary.forwardBack() }
            await("Bottom-display Back did not return to parent") { inParent() }
            repeat(40) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
            Thread.sleep(600)
            ui {
                check(page().focusedItemId == "entry40:file39") { "Off-screen selection: ${page().focusedItemId}" }
                val row = page().itemView("entry40:file39")!!
                val visible = android.graphics.Rect()
                check(row.getGlobalVisibleRect(visible) && visible.height() == row.height) { "Selected file is off screen" }
            }
            key(KeyEvent.KEYCODE_BUTTON_A)
            await("Controller did not select file") { selected?.id == "file39" }
            check(cancelled == 0)
            companionInput = false
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            ui {
                picker = DirectoryPicker.show(pages, "Pending files", DirectoryPicker.Location("root", "Files"), {
                    started.countDown()
                    release.await(30, TimeUnit.SECONDS)
                    emptyList()
                }, { error("Cancelled picker selected a file") }, { cancelled++ })
            }
            check(started.await(5, TimeUnit.SECONDS))
            key(KeyEvent.KEYCODE_BACK)
            release.countDown()
            await("Pending picker did not close") { cancelled == 1 && picker?.inStack != true }
            Thread.sleep(150)
            ui { check(picker?.inStack != true && cancelled == 1) }
        } finally {
            ui { picker?.close(); pages.closeAll(); activity.finish() }
        }
    }
}
