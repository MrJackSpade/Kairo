package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
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
    private var keyboardVisible = false
    private var backgroundColor = Color.BLACK
    private var touchpadMode = false
    private var presentation: KeyboardPresentation? = null
    private var companion: SecondaryDisplayActivity? = null
    private var companionStarting = false
    var swapped = false
        private set

    val isShowing: Boolean get() = presentation?.isShowing == true ||
        companion?.isFinishing == false
    val isKeyboardVisible: Boolean get() = isShowing && keyboardVisible
    val isCompanionActive: Boolean get() = companionStarting || companion != null
    val activeGameSurface: SurfaceView?
        get() = presentation?.content?.activeGameSurface ?: companion?.activeGameSurface

    fun start(handler: Handler) {
        if (started) return
        started = true
        displayManager.registerDisplayListener(this, handler)
        // onResume can run before the decor view is attached to its display.
        // Wait for its actual display before choosing where the companion belongs.
        activity.window.decorView.post { if (started) refresh() }
    }

    fun stop() {
        if (!started) return
        started = false
        displayManager.unregisterDisplayListener(this)
        if (swapped) {
            swapped = false
            onSwapChanged(false)
        }
        dismiss()
    }

    fun toggleSwap() {
        if (!isShowing) return
        swapped = !swapped
        presentation?.content?.setAppearance(keyboardVisible, backgroundColor, swapped, libraryInfo)
        companion?.setAppearance(keyboardVisible, backgroundColor, swapped, libraryInfo)
        onSwapChanged(swapped)
    }

    /** What the second screen shows about the selected game while the library is open. */
    data class LibraryInfo(val title: String, val tags: List<String>, val description: String,
                           val art: android.graphics.Bitmap?)

    private var libraryInfo: LibraryInfo? = null

    fun setLibraryInfo(info: LibraryInfo?) {
        libraryInfo = info
        presentation?.content?.setAppearance(keyboardVisible, backgroundColor, swapped, libraryInfo)
        companion?.setAppearance(keyboardVisible, backgroundColor, swapped, libraryInfo)
    }

    fun setAppearance(showKeyboard: Boolean, color: Int) {
        keyboardVisible = showKeyboard
        backgroundColor = color
        refresh()
        presentation?.content?.setAppearance(keyboardVisible, backgroundColor, swapped, libraryInfo)
        companion?.setAppearance(keyboardVisible, backgroundColor, swapped, libraryInfo)
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
        if (!started) {
            dismiss()
            return
        }
        val primaryId = activity.window.decorView.display?.displayId ?: return
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
            next.content.setAppearance(keyboardVisible, backgroundColor, swapped, libraryInfo)
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
        pendingCompanion = this
        try {
            val intent = Intent(activity, SecondaryDisplayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(target.displayId)
            activity.startActivity(intent, options.toBundle())
        } catch (error: RuntimeException) {
            companionStarting = false
            if (pendingCompanion === this) pendingCompanion = null
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

    fun forwardMotion(event: MotionEvent): Boolean = activity.dispatchGenericMotionEvent(event)

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
        onAvailabilityChanged(true)
        return GuestKeyboardPanel(value, input, keyboardLayout, {},
            showClose = false, onSwap = ::toggleSwap,
            mouse = mouse).also { it.setInitialMode(touchpadMode) }
    }

    fun updateCompanion(value: SecondaryDisplayActivity) {
        if (companion === value) value.setAppearance(keyboardVisible, backgroundColor, swapped, libraryInfo)
    }

    fun detachCompanion(value: SecondaryDisplayActivity) {
        if (companion !== value) return
        companion = null
        if (pendingCompanion === this) pendingCompanion = null
        onAvailabilityChanged(false)
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
        if (pendingCompanion === this) pendingCompanion = null
        if (previous != null) {
            previous.finish()
            onAvailabilityChanged(false)
        }
    }

    companion object {
        internal var pendingCompanion: SecondaryDisplayCoordinator? = null
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
        libraryArt.layoutParams = (libraryArt.layoutParams as LinearLayout.LayoutParams).apply {
            this.width = minOf(dp(300), ((width - dp(48)).coerceAtLeast(0) * 0.46f).roundToInt())
        }
        val aspect = gameAspect().takeIf { it in 0.5..3.0 } ?: 1.6
        val sourceHeight = 640.0 / aspect
        val scale = minOf(width / 640f, height / sourceHeight.toFloat())
        val gameWidth = (640 * scale).roundToInt().coerceAtLeast(1)
        val gameHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        gameSurface.layoutParams = LayoutParams(gameWidth, gameHeight,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = (height - gameHeight) / 2
        }
    }

    private val libraryPanel = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = View.GONE
        setPadding(dp(24), dp(24), dp(24), dp(24))
    }
    private val libraryArt = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_START
        adjustViewBounds = true
    }
    private val libraryTitle = Ui.text(context, "", Ui.TITLE, bold = true).apply { maxLines = 3 }
    private val libraryTags = Ui.text(context, "", Ui.SECONDARY, Ui.TEXT_MUTED).apply { maxLines = 5 }
    private val libraryDescription = Ui.text(context, "", Ui.BODY, Ui.TEXT_BODY).apply {
        setLineSpacing(dp(3).toFloat(), 1f)
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    init {
        libraryPanel.addView(libraryArt, LinearLayout.LayoutParams(dp(180), -2))
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
            libraryTitle.text = info.title
            libraryTags.text = info.tags.joinToString("\n")
            libraryDescription.text = info.description
            libraryArt.setImageBitmap(info.art)
            libraryArt.visibility = if (info.art == null) View.GONE else View.VISIBLE
            libraryDescription.maxLines = if (info.art == null) 12 else 9
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
