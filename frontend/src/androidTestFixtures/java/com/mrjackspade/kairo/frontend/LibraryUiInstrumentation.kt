package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle

/** Shared UI tests run against the target app, including its actual window/display setup. */
class LibraryUiInstrumentation : Instrumentation() {
    private var artwork = false
    private var menu = false
    private var deadZone = false
    private var search = false
    private var exitDialog = false
    private var traceSearch = false
    private var keyboard = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        artwork = arguments?.getString("libraryArtwork") == "true"
        menu = arguments?.getString("libraryMenu") == "true"
        deadZone = arguments?.getString("deadZone") == "true"
        search = arguments?.getString("librarySearch") == "true"
        traceSearch = arguments?.getString("traceSearch") == "true"
        keyboard = arguments?.getString("libraryKeyboard") == "true"
        exitDialog = arguments?.getString("exitDialog") == "true"
        start()
    }
    override fun onStart() {
        val result = Bundle()
        if (exitDialog) {
            try {
                com.mrjackspade.kairo.frontend.ExitDialogFixture.verify(this)
                result.putString("stream", "Exit dialog keys, hats, cancel and exit: OK\n")
                finish(Activity.RESULT_OK, result)
            } catch (failure: Throwable) {
                result.putString("stream", failure.stackTraceToString())
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (search) {
            try {
                result.putString("stream", LibrarySearchFixture.measure(this, traceSearch))
                finish(Activity.RESULT_OK, result)
            } catch (failure: Throwable) {
                result.putString("stream", failure.stackTraceToString())
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        try {
            if (keyboard) LibraryKeyboardFixture.verify(this)
            else if (deadZone) DeadZoneFixture.verify(this)
            else if (menu) LibraryMenuFixture.verify(this)
            else if (artwork) LibraryArtworkFixture.verify(this) else LibraryScrollFixture.verify(this)
            result.putString("stream", "${targetContext.packageName}: ${if (keyboard) "Android search keyboard dismissal and reopening" else if (deadZone) "default/custom/reset deadzone and axis activation" else if (menu) "library menu key/hat scrolling" else if (artwork) "library artwork cache, ordering, failures and lifecycle" else "library selection avoids row rebinding and preserves activation"}: OK\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", failure.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
