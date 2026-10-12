package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.view.View

/** Real game cancellation, complete teardown, relaunch, and frontend exit in both emulator hosts. */
object EndSessionFixture {
    fun verify(test: Instrumentation, uri: String, nextUri: String? = null) {
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
        val pages by lazy { field("pages") as PageStack }
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
        fun stopped() = if (dos) status() == "0" else status().startsWith("Stopped")
        fun library(): LibraryScreen<*> {
            val flow = field("libraryFlow")!!
            return flow.javaClass.getMethod("getScreen").invoke(flow) as LibraryScreen<*>
        }
        fun session() = field("sessionFlow") as SessionFlow
        fun key(code: Int) {
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        // Games start through the external launch interface; clearing the host's frontend
        // flag gives the same session the library-launched return path.
        fun treatAsLibraryLaunch() = ui {
            activity.javaClass.getDeclaredField("sessionFromFrontend").apply { isAccessible = true }
                .setBoolean(activity, false)
        }
        /** Open the game flyout and the Return row's inline confirm. */
        fun request(external: Boolean) {
            ui { session().open() }
            Thread.sleep(250)
            ui {
                val flyout = pages.top as RowsPage
                check(flyout.isFlyout) { "The game flyout did not open" }
                flyout.itemView("f-library")!!.performClick()
                check(flyout.trayRowId == "f-library") { "Return did not ask first" }
                check(flyout.trayButtons() == listOf("Cancel", "End game")) { "Return buttons: ${flyout.trayButtons()}" }
                check(flyout.trayFocused() == "Cancel")
                val message = flyout.trayMessage() ?: ""
                if (external) check(message.startsWith("This game stops and ") && message.contains(" closes.")) {
                    "Frontend confirm says \"$message\""
                } else check(message == "This game stops. Progress since your last save is lost.") {
                    "Library confirm says \"$message\""
                }
            }
        }
        fun choose(confirm: Boolean) = ui {
            if (confirm) key(KeyEvent.KEYCODE_DPAD_RIGHT)
            key(KeyEvent.KEYCODE_BUTTON_A)
        }
        val controls = field("onScreenControls") as OnScreenControls
        val enabled = OnScreenControls::class.java.getDeclaredField("enabled").apply { isAccessible = true }
        val wasEnabled = enabled.getBoolean(controls)
        val overlay = OnScreenControls::class.java.getDeclaredField("overlay").apply { isAccessible = true }
            .get(controls) as View
        fun arranging() = OnScreenControls::class.java.getDeclaredField("arrangement")
            .apply { isAccessible = true }.get(controls) != null
        fun verifyTouchControls() {
            ui {
                check(field("onScreenControls") === controls) { "Activity controls lost during session teardown" }
                enabled.setBoolean(controls, true)
                call(if (dos) "refreshControllerUi" else "applyPauseState")
                check(overlay.isShown) { "On-screen controls are not visible after launch" }
                session().open()
                (pages.top as RowsPage).itemView("f-controls")!!.performClick()
            }
            Thread.sleep(300)
            ui {
                val controlsPage = pages.top as GroupsPage
                check(controlsPage.title == "Controls")
                controlsPage.itemView("arrange")!!.performClick()
            }
            Thread.sleep(300)
            ui {
                check(arranging() && !overlay.isShown) { "Arrange layout did not open over the game" }
                pages.back()
            }
            Thread.sleep(300)
            ui {
                check(!arranging() && (pages.top as? GroupsPage)?.title == "Controls") {
                    "Returning from Arrange layout lost the Controls page"
                }
                session().close()
            }
            Thread.sleep(300)
            ui {
                call(if (dos) "refreshControllerUi" else "applyPauseState")
                check(overlay.isShown) { "Controls did not return after closing the menu" }
            }
            await("Game did not resume after control settings", ::running)
        }
        fun relaunch(target: String) = ui {
            activity.javaClass.getDeclaredMethod("onNewIntent", Intent::class.java)
                .apply { isAccessible = true }.invoke(activity, Intent(launch).setData(Uri.parse(target)))
        }
        try {
            await("Game did not start", ::running)
            verifyTouchControls()
            val identity = field(if (dos) "currentGame" else "currentDisk")
            // Cancel closes the confirm; the menu stays open until the player resumes.
            request(external = true); choose(false)
            ui {
                check(field(if (dos) "currentGame" else "currentDisk") === identity)
                check(session().isOpen && (pages.top as RowsPage).trayRowId == null) { "Cancel did not keep the menu open" }
                check(library().visibility != View.VISIBLE)
                session().close()
            }
            await("Closing the menu did not resume the game", ::running)
            ui { check(!session().isOpen) }
            repeat(if (dos) 3 else 2) { cycle ->
                treatAsLibraryLaunch()
                request(external = false); choose(true)
                await("Session did not end") { library().visibility == View.VISIBLE && stopped() }
                ui {
                    check(!activity.isFinishing)
                    check(field(if (dos) "currentGame" else "currentDisk") == null)
                    val keys = field(if (dos) "keys" else "inputRouter") as InputRouter
                    check(keys.pressedKeys().isEmpty())
                    check(field("onScreenControls") === controls) { "Activity controls discarded on return to library" }
                    check(!session().isOpen && !pages.isActive)
                    if (dos) {
                        check(field("gameThread") == null && field("audioThread") == null && field("audio") == null)
                        check(field("gameRoot") == null)
                    } else {
                        check(field("currentEntry") == null && field("currentGame") == null)
                        check(status().contains("audio off")) { status() }
                    }
                    (call("getSessionNavigation") as SessionNavigationCoordinator).returnToLibrary()
                    check(!pages.isActive) { "Idle library incorrectly asked to end a game" }
                }
                if (cycle < (if (dos) 2 else 1)) {
                    relaunch(nextUri ?: uri)
                    if (dos && cycle == 1) {
                        await("Early launch did not create session") { field("currentGame") != null }
                        test.sendStatus(0, android.os.Bundle().apply {
                            putString("stream", "Early DOS return native status=${status()}\n")
                        })
                    } else {
                        await("Game did not restart after teardown", ::running)
                        verifyTouchControls()
                    }
                }
            }
            relaunch(uri)
            await("Frontend game did not start after teardown", ::running)
            request(external = true); choose(true)
            await("Frontend session did not close the app") { activity.isFinishing }
            await("Frontend exit did not stop the machine", ::stopped)
            ui { check(library().visibility != View.VISIBLE) { "Frontend session returned to Library" } }
        } finally {
            ui { enabled.setBoolean(controls, wasEnabled); activity.finish() }
        }
    }
}
