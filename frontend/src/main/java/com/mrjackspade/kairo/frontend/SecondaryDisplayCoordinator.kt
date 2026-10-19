package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.Presentation
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
    private val input: InputRouter,
    private val mouse: MouseInputRouter,
    private val keyboardLayout: GuestKeyboardLayout,
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
        libraryArtwork.resume()
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
        libraryArtwork.suspend()
        if (!started) return
        started = false
        stopObservingSetup?.invoke()
        stopObservingSetup = null
        displayManager.unregisterDisplayListener(this)
        if (swapped) {
            swapped = false
            onSwapChanged(false)
        }
        dismiss()
    }

    fun toggleSwap() {
        if (!isShowing || setupVisible) return
        swapped = !swapped
        updateContent()
        onSwapChanged(swapped)
    }

    /** What the second screen shows about the selected game while the library is open. */
    data class LibraryInfo(val title: String, val tags: List<String>, val description: String,
                           val art: android.graphics.Bitmap?, val hasArtwork: Boolean = art != null)

    private var libraryInfo: LibraryInfo? = null
    private val libraryArtwork = LibraryArtworkLoader {
        libraryInfo = it
        updateContent()
    }
    private val visibleKeyboard get() = keyboardVisible && !setupVisible
    private val visibleBackground get() = if (setupVisible) Ui.BG else backgroundColor
    private val visibleLibraryInfo get() = libraryInfo.takeUnless { setupVisible }

    private fun updateContent() {
        presentation?.content?.setAppearance(visibleKeyboard, visibleBackground,
            swapped && !setupVisible, visibleLibraryInfo)
        companion?.setAppearance(visibleKeyboard, visibleBackground,
            swapped && !setupVisible, visibleLibraryInfo)
    }

    fun setLibraryInfo(info: LibraryInfo?, artworkPath: String? = null,
                       openArtwork: ((String) -> java.io.InputStream)? = null) {
        libraryArtwork.show(info, artworkPath, openArtwork)
    }

    /** The library's selected game: title, file, media, revision tags, catalog tags and preview. */
    fun showLibrarySelection(entry: LibraryItem?, game: LibraryGame?, catalog: LibraryCatalog) {
        if (entry == null || game == null) {
            setLibraryInfo(null)
            return
        }
        val variants = variantTags(entry.path).ifEmpty { entry.zipEntry?.let(::variantTags).orEmpty() }
        // A file name's (year) repeats the catalog's year tag; show each tag once.
        val tags = (listOf(fileLabel(entry), entry.mediaLabel) + variants + game.tags).distinct()
        setLibraryInfo(LibraryInfo(game.title, tags,
            game.description ?: "No description available yet.", null, hasArtwork = game.preview != null),
            game.preview, catalog::openArtwork)
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
            dismiss()
            return
        }
        val primaryId = activity.window.decorView.display?.displayId ?: return
        // The keyboard and game screens trade places only through the swap button.
        if (RgDsDisplayRouter.returnToUpper(activity)) return
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val target = (presentationDisplays.asList() + displayManager.displays.asList())
            .firstOrNull { it.displayId != primaryId && it.isValid &&
                it.state != Display.STATE_OFF }
        if (target == null) {
            if (swapped) {
                swapped = false
                onSwapChanged(false)
            }
            dismiss()
            return
        }
        if (target.displayId == Display.DEFAULT_DISPLAY) {
            dismissPresentation()
            if (companion != null || companionStarting) return
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
        return GuestKeyboardPanel(value, input, keyboardLayout, {},
            showClose = false, onSwap = ::toggleSwap,
            mouse = mouse).also { it.setInitialMode(touchpadMode) }
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
        return task.topActivity != activity.componentName
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
                GuestKeyboardPanel(context, input, keyboardLayout, {}, showClose = false,
                    onSwap = ::toggleSwap, mouse = mouse), ::forwardGameSurface, onGameTouch,
                gameAspect)
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
    private val gameAspect: () -> Double
) : FrameLayout(context), SurfaceHolder.Callback {
    fun setInitialMode(touchpad: Boolean) = keyboard.setInitialMode(touchpad)
    private val gameSurface = SurfaceView(context).apply {
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
        val artWidth = minOf(dp(300), ((width - dp(48)).coerceAtLeast(0) * 0.46f).roundToInt())
        if (libraryArt.layoutParams.width != artWidth) {
            libraryArt.layoutParams = libraryArt.layoutParams.apply { this.width = artWidth }
        }
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

    private val libraryPanel = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = View.GONE
        setPadding(dp(24), dp(24), dp(24), dp(24))
    }
    private val libraryArt = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_START
    }
    private val libraryTitle = Ui.text(context, "", Ui.TITLE, bold = true).apply { maxLines = 3 }
    private val libraryTags = Ui.text(context, "", Ui.SECONDARY, Ui.TEXT_MUTED).apply { maxLines = 5 }
    private val libraryDescription = LibraryDescriptionView(context)

    init {
        libraryPanel.addView(libraryArt, LinearLayout.LayoutParams(dp(180), -1))
        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, 0, 0)
        }
        text.addView(Ui.sectionLabel(context, "SELECTED").apply { setPadding(0, 0, 0, dp(8)) })
        text.addView(libraryTitle)
        text.addView(libraryTags, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        text.addView(libraryDescription, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(14) })
        text.addView(Ui.text(context, "Press A or tap the game above to open it", Ui.LABEL, Ui.TEXT_FAINT))
        libraryPanel.addView(text, LinearLayout.LayoutParams(0, -1, 1f))
        addView(libraryPanel, LayoutParams(-1, -1))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    fun setAppearance(showKeyboard: Boolean, color: Int, swapped: Boolean,
                      info: SecondaryDisplayCoordinator.LibraryInfo?) {
        layoutGameSurface(width, height)
        setBackgroundColor(if (!showKeyboard && info != null) Ui.BG else color)
        libraryPanel.visibility = if (!showKeyboard && info != null) View.VISIBLE else View.GONE
        if (info != null) {
            // Artwork arrives separately. Reassigning unchanged text forces another
            // text layout on that update, including the full catalog description.
            val tags = info.tags.joinToString("\n")
            if (libraryTitle.text.toString() != info.title) libraryTitle.text = info.title
            if (libraryTags.text.toString() != tags) libraryTags.text = tags
            if (libraryDescription.text.toString() != info.description) libraryDescription.text = info.description
            libraryArt.setImageBitmap(info.art)
            // Reserve catalog artwork space before decoding. Image arrival must not
            // change the text width or lay out the entire description a second time.
            libraryArt.visibility = when {
                info.art != null -> View.VISIBLE
                info.hasArtwork -> View.INVISIBLE
                else -> View.GONE
            }
            libraryDescription.maxLines = if (info.hasArtwork || info.art != null) 9 else 12
        }
        gameActive = showKeyboard && swapped
        gameSurface.visibility = if (gameActive) View.VISIBLE else View.GONE
        if (showKeyboard && !swapped) keyboard.visibility = View.VISIBLE
        else if (keyboard.visibility == View.VISIBLE) keyboard.close()
        if (!gameActive) onGameSurface(null, 0, 0)
    }

    fun close() = keyboard.close()

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
