package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.TextView

/** Real game cancellation, complete teardown, and relaunch in both emulator hosts. */
object EndSessionFixture {
    fun verify(test: Instrumentation, uri: String) {
        val dos = test.targetContext.packageName.endsWith("kairodos")
        val launch = test.targetContext.packageManager.getLaunchIntentForPackage(test.targetContext.packageName)!!
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse(uri))
            .putExtra("kairo98.skipStartupChoices", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = test.startActivitySync(launch)
        fun ui(action: () -> Unit) {
            var error: Throwable? = null
            test.runOnMainSync { try { action() } catch (caught: Throwable) { error = caught } }
            test.waitForIdleSync()
            error?.let { throw it }
        }
        fun field(name: String) = activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity)
        fun call(name: String) = activity.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
        fun status() = call("nativeStatus").toString()
        fun descendants(view: View): List<View> = listOf(view) +
            ((view as? ViewGroup)?.let { group -> (0 until group.childCount).flatMap { descendants(group.getChildAt(it)) } }
                ?: emptyList())
        fun prompt(): View? = WindowInspector.getGlobalWindowViews().firstOrNull { root ->
            descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "End game session?" }
        }
        fun await(message: String, condition: () -> Boolean) {
            repeat(600) {
                var ready = false
                ui { ready = condition() }
                if (ready) return
                Thread.sleep(100)
            }
            error("$message; native=${status()}")
        }
        fun running() = (if (dos) field("currentGame") else field("currentDisk")) != null &&
            (if (dos) status() == "2" else status().startsWith("Running"))
        fun library(): LibraryScreen<*> {
            val flow = field("libraryFlow")!!
            return flow.javaClass.getMethod("getScreen").invoke(flow) as LibraryScreen<*>
        }
        fun request() = ui {
            call("openMenu")
            val flow = field("sessionFlow") as SessionFlow
            descendants(flow.drawer.drawer).single { it.contentDescription == "Library" }.performClick()
            check(prompt() != null) { "Library action did not prompt" }
        }
        fun choose(confirm: Boolean) = ui {
            val decor = prompt()!!
            if (confirm) {
                decor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                decor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
            }
            decor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A))
            decor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A))
        }
        try {
            await("Game did not start", ::running)
            val identity = field(if (dos) "currentGame" else "currentDisk")
            request(); choose(false)
            await("Cancel did not resume game", ::running)
            ui {
                check(field(if (dos) "currentGame" else "currentDisk") === identity)
                check(library().visibility != View.VISIBLE)
                check(!(field("sessionFlow") as SessionFlow).isOpen)
            }
            repeat(if (dos) 3 else 2) { cycle ->
                request(); choose(true)
                await("Session did not end") {
                    library().visibility == View.VISIBLE &&
                        (if (dos) status() == "0" else status().startsWith("Stopped"))
                }
                ui {
                    check(!activity.isFinishing)
                    check(field(if (dos) "currentGame" else "currentDisk") == null)
                    val keys = field(if (dos) "keys" else "inputRouter") as InputRouter
                    check(keys.pressedKeys().isEmpty())
                    if (dos) {
                        check(field("gameThread") == null && field("audioThread") == null && field("audio") == null)
                        check(field("gameRoot") == null && field("sessionFlow") == null)
                    } else {
                        check(field("currentEntry") == null && field("currentGame") == null)
                        check(status().contains("audio off")) { status() }
                        check(!(field("sessionFlow") as SessionFlow).isOpen)
                    }
                    (call("getSessionNavigation") as SessionNavigationCoordinator).requestLibrary()
                    check(prompt() == null) { "Idle library incorrectly prompted" }
                }
                if (cycle < (if (dos) 2 else 1)) {
                    ui { activity.javaClass.getDeclaredMethod("onNewIntent", Intent::class.java)
                        .apply { isAccessible = true }.invoke(activity, launch) }
                    if (dos && cycle == 1) {
                        await("Early launch did not create session") { field("currentGame") != null }
                        test.sendStatus(0, android.os.Bundle().apply {
                            putString("stream", "Early DOS return native status=${status()}\n")
                        })
                    } else await("Game did not restart after teardown", ::running)
                }
            }
        } finally {
            ui { activity.finish() }
        }
    }
}
