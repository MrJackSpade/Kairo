package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle

/** Shared UI tests run against the target app, including its actual window/display setup. */
class LibraryUiInstrumentation : Instrumentation() {
    private var artwork = false
    private var menu = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        artwork = arguments?.getString("libraryArtwork") == "true"
        menu = arguments?.getString("libraryMenu") == "true"
        start()
    }
    override fun onStart() {
        val result = Bundle()
        try {
            if (menu) LibraryMenuFixture.verify(this)
            else if (artwork) LibraryArtworkFixture.verify(this) else LibraryScrollFixture.verify(this)
            result.putString("stream", "${targetContext.packageName}: ${if (menu) "library menu key/hat scrolling" else if (artwork) "library artwork cache, ordering, failures and lifecycle" else "library selection avoids row rebinding and preserves activation"}: OK\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", failure.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
