package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.Presentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.roundToInt

/** Places the guest keyboard on another Android display when one is available. */
class SecondaryDisplayCoordinator(
    private val activity: Activity,
    private val inputs: GuestPanelInputs,
    private var keyboardLayout: GuestKeyboardLayout,
    private val logTag: String,
    private val onAvailabilityChanged: (Boolean) -> Unit,
    private val onGameSurface: (Surface?, Int, Int) -> Unit,
    private val onGameTouch: (MotionEvent, Int, Int) -> Boolean,
    private val onSwapChanged: (Boolean) -> Unit,
    private val gameAspect: () -> Double = { 1.6 }
) : DisplayManager.DisplayListener {
    private val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var started = false
    private var setupVisible = false
    private var stopObservingSetup: (() -> Unit)? = null
    private var keyboardVisible = false
    private var backgroundColor = Color.BLACK
    private var touchpadMode = false
    private var presentation: KeyboardPresentation? = null
    private var companion: SecondaryDisplayActivity? = null
    private var companionStarting = false
    private var companionStopped = false
    var swapped = false
        private set

    val isShowing: Boolean get() = presentation?.isShowing == true ||
        companion?.isFinishing == false
    val isKeyboardVisible: Boolean get() = isShowing && visibleKeyboard
    val isCompanionActive: Boolean get() = companionStarting || companion != null

    /**
     * The game screen stopped under an activity it started itself (a file picker), so the
     * screen pair stays up. Any other stop means the user left, and the keyboard leaves too.
     * A finishing activity may already have lost its task.
     */
    val holdsStoppedPrimary: Boolean get() =
        isCompanionActive && !activity.isFinishing && primaryCoveredByOwnLaunch()
    val activeGameSurface: SurfaceView?
        get() = presentation?.content?.activeGameSurface ?: companion?.activeGameSurface

    fun start(handler: Handler) {
        if (started) return
        started = true
        stopObservingSetup = FirstRunVisibility.forActivity(activity).observe {
            setupVisible = it
            updateContent()
        }
        displayManager.registerDisplayListener(this, handler)
        // onResume can run before the decor view is attached to its display.
        // Wait for its actual display before choosing where the companion belongs.
        activity.window.decorView.post { if (started) refresh() }
    }

    fun stop() {
        if (!started) return
        started = false
        stopObservingSetup?.invoke()
        stopObservingSetup = null
        displayManager.unregisterDisplayListener(this)
        if (swapped) leaveSwap()
        dismiss()
    }

    fun toggleSwap() {
        if (!isShowing || setupVisible) return
        if (swapped) {
            leaveSwap()
            return
        }
        swapped = true
        updateContent()
        onSwapChanged(true)
    }

    /*
     * The game lets go of the lower screen's surface before it is hidden: hiding it while
     * not swapped sends nothing on (forwardGameSurface), so the guest kept drawing into a
     * surface Android had taken away and the presenter stopped the app.
     */
    private fun leaveSwap() {
        onGameSurface(null, 0, 0)
        swapped = false
        updateContent()
        onSwapChanged(false)
    }

    /** Builds what the lower screen shows while the library is open (LowerLibraryController). */
    var lowerLibrary: ((Context) -> LowerLibraryView)? = null
    private var libraryShown = false
    private val visibleKeyboard get() = keyboardVisible && !setupVisible
    private val visibleBackground get() = if (setupVisible) Ui.BG else backgroundColor
    private val visibleLibrary get() = libraryShown && !setupVisible

    private fun updateContent() {
        presentation?.content?.setAppearance(visibleKeyboard, visibleBackground,
            swapped && !setupVisible, visibleLibrary)
        companion?.setAppearance(visibleKeyboard, visibleBackground,
            swapped && !setupVisible, visibleLibrary)
    }

    /** Whether the lower screen shows the library (a game is selected and the library is open). */
    fun showLibrary(shown: Boolean) {
        if (libraryShown == shown) return
        libraryShown = shown
        updateContent()
    }

    fun setAppearance(showKeyboard: Boolean, color: Int) {
        keyboardVisible = showKeyboard
        backgroundColor = color
        refresh()
        updateContent()
    }

    fun setInitialMode(touchpad: Boolean) {
        touchpadMode = touchpad
        presentation?.content?.setInitialMode(touchpad)
        companion?.setInitialMode(touchpad)
    }

    override fun onDisplayAdded(displayId: Int) = refresh()
    override fun onDisplayRemoved(displayId: Int) = refresh()
    override fun onDisplayChanged(displayId: Int) = refresh()

    private fun refresh() {
        if (!started || activity.isFinishing) {
            Log.i(logTag, "Second screen: not shown (started $started, finishing ${activity.isFinishing})")
            dismiss()
            return
        }
        val primaryId = activity.window.decorView.display?.displayId
        if (primaryId == null) {
            Log.i(logTag, "Second screen: the game screen has no display yet")
            return
        }
        // The keyboard and game screens trade places only through the swap button.
        if (RgDsDisplayRouter.returnToUpper(activity)) {
            Log.i(logTag, "Second screen: the game screen opened on display $primaryId; moving it back up")
            return
        }
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val target = (presentationDisplays.asList() + displayManager.displays.asList())
            .firstOrNull { it.displayId != primaryId && it.isValid &&
                it.state != Display.STATE_OFF }
        if (target == null) {
            if (swapped) leaveSwap()
            dismiss()
            return
        }
        if (target.displayId == Display.DEFAULT_DISPLAY) {
            dismissPresentation()
            if (companion != null || companionStarting) {
                Log.i(logTag, "Second screen: keyboard already ${if (companion != null) "open" else "opening"} on display 0")
                return
            }
            Log.i(logTag, "Second screen: opening the keyboard on display 0 beside display $primaryId")
            launchCompanion(target)
            return
        }
        dismissCompanion()
        if (presentation?.display?.displayId == target.displayId && isShowing) return
        dismissPresentation()
        val next = KeyboardPresentation(activity, target)
        try {
            next.show()
            presentation = next
            updateContent()
            next.content.setInitialMode(touchpadMode)
            next.setOnDismissListener {
                if (presentation === next) {
                    presentation = null
                    onAvailabilityChanged(false)
                }
            }
            onAvailabilityChanged(true)
        } catch (error: WindowManager.InvalidDisplayException) {
            Log.w(logTag, "Secondary keyboard rejected display ${target.displayId}; game display $primaryId", error)
            next.dismiss()
        } catch (error: SecurityException) {
            Log.w(logTag, "Secondary keyboard cannot use display ${target.displayId}", error)
            next.dismiss()
        }
    }

    private fun launchCompanion(target: Display) {
        companionStarting = true
        val launch = ++nextLaunch
        pending = launch to this
        try {
            val intent = Intent(activity, SecondaryDisplayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                putExtra(LAUNCH_ID, launch)
            }
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(target.displayId)
            activity.startActivity(intent, options.toBundle())
        } catch (error: RuntimeException) {
            companionStarting = false
            clearPending()
            Log.w(logTag, "Could not open keyboard activity on display ${target.displayId}", error)
        }
    }

    /*
     * Android sends keys and controller sticks to the display that last took focus.
     * Forward them to the game, then return focus after a completed key press. Returning
     * focus as soon as the second display gains it cancels touches on its mode tabs.
     */
    fun forwardKey(event: KeyEvent): Boolean {
        val handled = activity.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP) reclaimFocus()
        return handled
    }

    /** System Back on the companion must follow the same route as a physical key. */
    fun forwardBack() {
        forwardKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        forwardKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
    }

    fun forwardMotion(event: MotionEvent): Boolean {
        return activity.dispatchGenericMotionEvent(event)
    }

    /**
     * Library search on the lower screen, where its keyboard does not cover the results: true
     * when the companion screen took it. Each change goes to [changed]; [done] runs when the
     * player finishes (the keyboard's Search key or Back), after focus returns to the game screen.
     */
    fun openSearch(query: String, changed: (String) -> Unit, done: () -> Unit): Boolean {
        val screen = companion?.takeIf { !it.isFinishing } ?: return false
        screen.openSearch(query, changed) {
            screen.closeSearch()
            reclaimFocus()
            done()
        }
        return true
    }

    /** Ends lower-screen search, as when the library closes its search row. */
    fun closeSearch() {
        val screen = companion ?: return
        if (screen.searching) {
            screen.closeSearch()
            reclaimFocus()
        }
    }

    fun reclaimFocus() {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .moveTaskToFront(activity.taskId, 0)
        } catch (error: SecurityException) {
            Log.w(logTag, "Could not return input focus to the game screen", error)
        }
    }

    fun attachCompanion(value: SecondaryDisplayActivity): GuestKeyboardPanel {
        companionStarting = false
        companion = value
        companionStopped = false
        onAvailabilityChanged(true)
        return GuestKeyboardPanel(value, inputs, keyboardLayout, {},
            showClose = false, onSwap = ::toggleSwap,
            ).also { it.setInitialMode(touchpadMode); panels.add(it) }
    }

    /** The keyboards this coordinator made, which a session's layout reaches ([setLayout]). */
    private val panels = java.util.Collections.newSetFromMap(java.util.WeakHashMap<GuestKeyboardPanel, Boolean>())

    /** The running machine's keyboard, on the second screen's keyboards now and later. */
    fun setLayout(value: GuestKeyboardLayout) {
        keyboardLayout = value
        panels.forEach { it.setLayout(value) }
    }

    fun updateCompanion(value: SecondaryDisplayActivity) {
        if (companion === value) updateContent()
    }

    /** The companion closed without being dismissed here: the user closed the app's lower half. */
    fun detachCompanion(value: SecondaryDisplayActivity) {
        if (companion !== value) return
        forgetCompanion()
        if (!activity.isFinishing) {
            Log.i(logTag, "Keyboard screen closed; closing the game screen with it")
            activity.finishAndRemoveTask()
        }
    }

    /** The companion opened on the wrong display and closes itself; the app stays open. */
    fun releaseCompanion(value: SecondaryDisplayActivity) {
        if (companion === value) forgetCompanion()
    }

    private fun forgetCompanion() {
        companion = null
        companionStopped = false
        clearPending()
        onAvailabilityChanged(false)
    }

    fun companionStarted(value: SecondaryDisplayActivity) {
        if (companion === value) companionStopped = false
    }

    /*
     * Another app or the home screen covered the keyboard. The two screens share one
     * lifecycle, so the game screen goes to the background with it; its own onStop then
     * suspends the guest and dismisses the companion, and the next resume reopens both.
     * Screen-off and a lower display switched off also stop the companion; neither is the
     * user leaving.
     */
    fun companionStopped(value: SecondaryDisplayActivity) {
        if (companion !== value) return
        companionStopped = true
        activity.window.decorView.post {
            if (!started || companion !== value || !companionStopped || activity.isFinishing) return@post
            val power = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
            val lower = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            if (!power.isInteractive || lower == null || lower.state == Display.STATE_OFF) return@post
            Log.i(logTag, "Keyboard screen covered; moving the game screen to the background with it")
            activity.moveTaskToBack(true)
        }
    }

    private fun primaryCoveredByOwnLaunch(): Boolean {
        // Companions exist only on the RGDS (Android 14); older releases lack TaskInfo.taskId.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val task = (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).appTasks
            .map { it.taskInfo }.first { it.taskId == activity.taskId }
        // The task names its activities by class; the game may run under a launcher alias
        // (an app's .Launch), which componentName would name instead.
        return task.topActivity != ComponentName(activity, activity.javaClass)
    }

    private fun dismiss() {
        dismissPresentation()
        dismissCompanion()
    }

    private fun dismissPresentation() {
        val previous = presentation ?: return
        presentation = null
        previous.dismiss()
        onAvailabilityChanged(false)
    }

    private fun dismissCompanion() {
        val previous = companion
        companion = null
        companionStarting = false
        companionStopped = false
        clearPending()
        if (previous != null) {
            previous.finishAndRemoveTask()
            onAvailabilityChanged(false)
        }
    }

    private fun clearPending() {
        if (pending?.second === this) pending = null
    }

    companion object {
        private const val LAUNCH_ID = "com.mrjackspade.kairo.frontend.companionLaunch"
        private var nextLaunch = 0
        private var pending: Pair<Int, SecondaryDisplayCoordinator>? = null

        /*
         * The coordinator waiting for this companion launch. A launch can still arrive after
         * a pause dismissed it and a resume started another; that superseded companion and a
         * recreated one find no owner and close.
         */
        internal fun claim(intent: Intent): SecondaryDisplayCoordinator? {
            val (launch, owner) = pending ?: return null
            if (intent.getIntExtra(LAUNCH_ID, 0) != launch) return null
            pending = null
            return owner
        }
    }

    private inner class KeyboardPresentation(
        activity: Activity,
        display: Display
    ) : Presentation(activity, display) {
        lateinit var content: SecondaryDisplayContent
            private set

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            @Suppress("DEPRECATION")
            window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            content = SecondaryDisplayContent(context,
                GuestKeyboardPanel(context, inputs, keyboardLayout, {}, showClose = false,
                    onSwap = ::toggleSwap).also { panels.add(it) }, ::forwardGameSurface, onGameTouch,
                gameAspect, lowerLibrary?.invoke(context))
            setContentView(content)
        }

        override fun onStop() {
            if (::content.isInitialized) content.close()
            super.onStop()
        }

        override fun dispatchKeyEvent(event: KeyEvent) = forwardKey(event)

        override fun dispatchGenericMotionEvent(event: MotionEvent) = forwardMotion(event)

    }

    fun forwardGameSurface(surface: Surface?, width: Int, height: Int) {
        if (swapped) onGameSurface(surface, width, height)
    }

    fun forwardGameTouch(event: MotionEvent, width: Int, height: Int): Boolean =
        swapped && onGameTouch(event, width, height)

    fun currentGameAspect(): Double = gameAspect()
}

internal class SecondaryDisplayContent(
    context: Context,
    private val keyboard: GuestKeyboardPanel,
    private val onGameSurface: (Surface?, Int, Int) -> Unit,
    private val onGameTouch: (MotionEvent, Int, Int) -> Boolean,
    private val gameAspect: () -> Double,
    /** The library's view on this screen, or null for an app without one. */
    private val library: LowerLibraryView?
) : FrameLayout(context), SurfaceHolder.Callback {
    fun setInitialMode(touchpad: Boolean) = keyboard.setInitialMode(touchpad)
    private val gameSurface = SurfaceView(context).apply {
        testId("secondary.surface")
        visibility = View.GONE
        holder.addCallback(this@SecondaryDisplayContent)
        setOnTouchListener { view, event -> onGameTouch(event, view.width, view.height) }
    }
    private var gameActive = false

    init {
        setBackgroundColor(Color.BLACK)
        addView(gameSurface, LayoutParams(640, 400, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        addView(keyboard, LayoutParams(-1, -1))
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        layoutGameSurface(width, height)
    }

    private fun layoutGameSurface(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val aspect = gameAspect().takeIf { it in 0.5..3.0 } ?: 1.6
        val sourceHeight = 640.0 / aspect
        val scale = minOf(width / 640f, height / sourceHeight.toFloat())
        val gameWidth = (640 * scale).roundToInt().coerceAtLeast(1)
        val gameHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        val margin = (height - gameHeight) / 2
        val current = gameSurface.layoutParams as LayoutParams
        if (current.width != gameWidth || current.height != gameHeight || current.topMargin != margin) {
            gameSurface.layoutParams = LayoutParams(gameWidth, gameHeight,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = margin }
        }
    }

    init {
        library?.view?.let { view ->
            view.visibility = View.GONE
            addView(view, LayoutParams(-1, -1))
        }
    }

    /** The library search typed on this screen, which filters the library on the other. */
    private var onSearchChanged: ((String) -> Unit)? = null
    private var onSearchDone: (() -> Unit)? = null
    private val searchField = android.widget.EditText(context).testId("secondary.search.field").apply {
        visibility = View.GONE
        hint = context.getString(R.string.kairo_search_hint)
        textSize = Ui.BODY
        isSingleLine = true
        setTextColor(Ui.TEXT)
        setHintTextColor(Ui.TEXT_FAINT)
        background = Ui.rounded(context, Ui.SURFACE, Ui.RADIUS_SMALL, Ui.LINE)
        setPadding(dp(14), 0, dp(14), 0)
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        setOnEditorActionListener { _, _, _ -> onSearchDone?.invoke(); true }
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(text: android.text.Editable?) { onSearchChanged?.invoke(text.toString()) }
        })
    }
    val searching: Boolean get() = searchField.visibility == View.VISIBLE

    init {
        addView(searchField, LayoutParams(-1, dp(52), Gravity.TOP).apply { setMargins(dp(24), dp(16), dp(24), 0) })
    }

    /** Shows the search field with [query], focused with the keyboard open on this screen. */
    fun openSearch(query: String, changed: (String) -> Unit, done: () -> Unit) {
        onSearchChanged = null
        searchField.setText(query)
        searchField.setSelection(query.length)
        onSearchChanged = changed
        onSearchDone = done
        searchField.visibility = View.VISIBLE
        searchField.requestFocus()
        searchField.post {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(searchField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** Hides the search field and its keyboard; the query stays on the library. */
    fun closeSearch() {
        if (!searching) return
        onSearchChanged = null
        onSearchDone = null
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(searchField.windowToken, 0)
        searchField.clearFocus()
        searchField.visibility = View.GONE
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    fun setAppearance(showKeyboard: Boolean, color: Int, swapped: Boolean, showLibrary: Boolean) {
        layoutGameSurface(width, height)
        val libraryVisible = !showKeyboard && showLibrary && library != null
        setBackgroundColor(if (libraryVisible) Ui.BG else color)
        library?.view?.visibility = if (libraryVisible) View.VISIBLE else View.GONE
        gameActive = showKeyboard && swapped
        gameSurface.visibility = if (gameActive) View.VISIBLE else View.GONE
        if (showKeyboard && !swapped) keyboard.visibility = View.VISIBLE
        else if (keyboard.visibility == View.VISIBLE) keyboard.close()
        if (!gameActive) onGameSurface(null, 0, 0)
    }

    fun close() {
        keyboard.close()
        library?.close()
    }

    /** The surface showing the guest while the screens are swapped, else null. */
    val activeGameSurface: SurfaceView? get() = gameSurface.takeIf { gameActive }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (gameActive) onGameSurface(holder.surface, gameSurface.width, gameSurface.height)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (gameActive) onGameSurface(holder.surface, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (gameActive) onGameSurface(null, 0, 0)
    }
}
