package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView

/** Exercise the real Android IME and each host's Back/input routing. */
object LibraryKeyboardFixture {
    fun verify(test: Instrumentation) {
        val launch = test.targetContext.packageManager.getLaunchIntentForPackage(test.targetContext.packageName)!!
        val activity = test.startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun ui(action: () -> Unit) {
            var error: Throwable? = null
            test.runOnMainSync { try { action() } catch (caught: Throwable) { error = caught } }
            test.waitForIdleSync()
            error?.let { throw it }
        }
        fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.get(owner)
        lateinit var screen: LibraryScreen<*>
        lateinit var search: EditText
        fun selectedId(): String? {
            val entries = field(screen, "entries") as List<*>
            return (entries.getOrNull(field(screen, "selectedIndex") as Int) as? LibraryItem)?.id
        }
        fun awaitIme(visible: Boolean) {
            repeat(80) {
                var matches = false
                ui { matches = screen.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == visible }
                if (matches) return
                Thread.sleep(50)
            }
            error("Android IME did not become visible=$visible")
        }
        fun focusSearchWindow() {
            val location = IntArray(2)
            var display = 0
            ui {
                search.getLocationOnScreen(location)
                location[0] += search.width / 2
                location[1] += search.height / 2
                display = search.display.displayId
            }
            // RGDS's companion may own window focus after launch. A real tap selects
            // the search window; requestFocus alone only changes its internal focus.
            android.os.ParcelFileDescriptor.AutoCloseInputStream(test.uiAutomation.executeShellCommand(
                "input -d $display tap ${location[0]} ${location[1]}" )).use { it.readBytes() }
        }
        fun reopen() {
            focusSearchWindow()
            ui {
                search.requestFocus()
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }
            awaitIme(true)
        }
        fun unchanged() {
            awaitIme(false)
            ui {
                check(search.text.toString() == "a")
                check(!search.hasFocus())
                check(screen.isShown && !screen.detailOpen && !screen.actionsOpen)
                check(!activity.isFinishing)
            }
        }
        fun key(code: Int) = ui {
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        try {
            Thread.sleep(8000)
            ui {
                val flow = field(activity, "libraryFlow")!!
                screen = flow.javaClass.getMethod("getScreen").invoke(flow) as LibraryScreen<*>
                search = field(screen, "search") as EditText
                screen.moveSelection(5)
            }
            var beforeOpen: String? = null
            var searchButton: View? = null
            fun findSearch(view: View): View? {
                if (view.contentDescription == "Search games") return view
                return (view as? ViewGroup)?.let { group ->
                    (0 until group.childCount).firstNotNullOfOrNull { findSearch(group.getChildAt(it)) }
                }
            }
            val buttonPosition = IntArray(2)
            var display = 0
            ui {
                beforeOpen = selectedId()
                check((field(screen, "selectedIndex") as Int) > 0)
                searchButton = findSearch(screen)!!
                searchButton!!.getLocationOnScreen(buttonPosition)
                buttonPosition[0] += searchButton!!.width / 2
                buttonPosition[1] += searchButton!!.height / 2
                display = screen.display.displayId
            }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(test.uiAutomation.executeShellCommand(
                "input -d $display tap ${buttonPosition[0]} ${buttonPosition[1]}" )).use { it.readBytes() }
            focusSearchWindow()
            awaitIme(true)
            ui {
                check(selectedId() == beforeOpen) { "Opening/tapping Search changed the library selection" }
                check(!screen.detailOpen && search.hasFocus())
                search.setText("a")
            }
            ui { search.onEditorAction(EditorInfo.IME_ACTION_SEARCH) }
            unchanged()
            for (code in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER)) {
                reopen(); key(code); unchanged()
            }
            reopen()
            ui {
                search.onKeyPreIme(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                search.onKeyPreIme(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            }
            unchanged()
            reopen()
            ui {
                val row = field(screen, "searchRow") as ViewGroup
                (0 until row.childCount).map(row::getChildAt)
                    .filterIsInstance<TextView>().single { it.text.toString() == "View results" }.performClick()
            }
            unchanged()
            // Results remain navigable after text input is dismissed.
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            ui { check(screen.isShown && !screen.detailOpen) }
            reopen()
            ui { screen.dismissSearchKeyboard() }
            unchanged()
        } finally {
            ui { activity.finish() }
        }
    }
}
