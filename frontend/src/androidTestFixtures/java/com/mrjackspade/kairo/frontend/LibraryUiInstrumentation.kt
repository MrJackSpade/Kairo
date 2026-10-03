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
    private var endSessionUri: String? = null
    private var touchSticks = false
    private var nextSessionUri: String? = null
    private var catalogProgress = false
    private var catalogInstall = false
    private var catalogPackageFile: String? = null
    private var snapshotActivation = false
    private var keyCycle = false
    private var directoryPicker = false
    private var startup = false
    private var traceStartup = false
    private var exitDialog = false
    private var traceSearch = false
    private var keyboard = false
    private var touchKeyboardUri: String? = null
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        touchKeyboardUri = arguments?.getString("touchKeyboardUri")
        artwork = arguments?.getString("libraryArtwork") == "true"
        menu = arguments?.getString("libraryMenu") == "true"
        deadZone = arguments?.getString("deadZone") == "true"
        search = arguments?.getString("librarySearch") == "true"
        traceSearch = arguments?.getString("traceSearch") == "true"
        keyboard = arguments?.getString("libraryKeyboard") == "true"
        exitDialog = arguments?.getString("exitDialog") == "true"
        endSessionUri = arguments?.getString("endSessionUri")
        touchSticks = arguments?.getString("touchSticks") == "true"
        nextSessionUri = arguments?.getString("nextSessionUri")
        catalogPackageFile = arguments?.getString("catalogPackageFile")
        catalogInstall = arguments?.getString("catalogInstall") == "true"
        catalogProgress = arguments?.getString("catalogProgress") == "true"
        snapshotActivation = arguments?.getString("snapshotActivation") == "true"
        keyCycle = arguments?.getString("keyCycle") == "true"
        directoryPicker = arguments?.getString("directoryPicker") == "true"
        startup = arguments?.getString("startup") == "true"
        traceStartup = arguments?.getString("traceStartup") == "true"
        start()
    }
    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
        val before = android.os.SystemClock.elapsedRealtime()
        super.callActivityOnCreate(activity, icicle)
        com.mrjackspade.kairo.frontend.StartupFixture.created(activity, before)
    }
    override fun onStart() {
        val result = Bundle()
        if (touchSticks) {
            try {
                com.mrjackspade.kairo.frontend.TouchStickFixture.verify(this)
                result.putString("stream", "Circle pads, profile layouts and touch setup focus: OK\n")
                finish(Activity.RESULT_OK, result)
            } catch (failure: Throwable) {
                result.putString("stream", failure.stackTraceToString())
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (catalogInstall) {
            try {
                result.putString("stream", com.mrjackspade.kairo.frontend.CatalogInstallFixture.verify(this, catalogPackageFile))
                finish(Activity.RESULT_OK, result)
            } catch (failure: Throwable) {
                result.putString("stream", failure.stackTraceToString())
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        touchKeyboardUri?.let { uri ->
            try {
                result.putString("stream", com.mrjackspade.kairo.frontend.TouchKeyboardFixture.verify(this, uri))
                finish(Activity.RESULT_OK, result)
            } catch (failure: Throwable) {
                result.putString("stream", failure.stackTraceToString())
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (directoryPicker) {
            try {
                DirectoryPickerFixture.verify(this)
                result.putString("stream", "Shared directory picker: async load, retry, controller navigation/selection and cancellation: OK\n")
                finish(Activity.RESULT_OK, result)
            } catch (failure: Throwable) {
                result.putString("stream", failure.stackTraceToString())
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (keyCycle) {
            try {
                com.mrjackspade.kairo.frontend.KeyCycleFixture.verify(this)
                result.putString("stream", "D-pad/shoulder cycles, independent sequences, wrap, hats, release, codec and editor save: OK\n")
                finish(Activity.RESULT_OK, result)
            } catch (failure: Throwable) {
                result.putString("stream", failure.stackTraceToString())
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (startup) {
            try {
                result.putString("stream", com.mrjackspade.kairo.frontend.StartupFixture.measure(this, traceStartup))
                com.mrjackspade.kairo.frontend.StartupFixture.verifyPc98Fallback(this)
                finish(Activity.RESULT_OK, result)
            } catch (failure: Throwable) {
                result.putString("stream", failure.stackTraceToString())
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (snapshotActivation) {
            try {
                com.mrjackspade.kairo.frontend.SnapshotActivationFixture.verify(this)
                result.putString("stream", "Snapshot APK reactivation, offline reuse, repair, compatibility, rollback and host defaults: OK\n")
                finish(Activity.RESULT_OK, result)
            } catch (failure: Throwable) {
                result.putString("stream", failure.stackTraceToString())
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (catalogProgress) {
            try {
                com.mrjackspade.kairo.frontend.CatalogProgressFixture.verify(this)
                result.putString("stream", "Catalog progress, completion, failure, cancellation and metadata-only check: OK\n")
                finish(Activity.RESULT_OK, result)
            } catch (failure: Throwable) {
                result.putString("stream", failure.stackTraceToString())
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        endSessionUri?.let { uri ->
            try {
                com.mrjackspade.kairo.frontend.EndSessionFixture.verify(this, uri, nextSessionUri)
                result.putString("stream", "Library cancellation, teardown and relaunch: OK\n")
                finish(Activity.RESULT_OK, result)
            } catch (failure: Throwable) {
                result.putString("stream", failure.stackTraceToString())
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
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
