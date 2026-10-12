package com.mrjackspade.kairo.frontend

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Sits under the library header while a background task runs and names the actual task, with
 * Stop where the task can be cancelled. Results go to the message bar instead.
 */
class TaskStrip(context: Context) : LinearLayout(context) {
    private class Task(val text: String, val priority: Int, val stop: (() -> Unit)?)
    private val tasks = HashMap<String, Task>()
    private val label = Ui.text(context, "", Ui.SECONDARY).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    val stopButton: TextView = Ui.text(context, "Stop", Ui.SECONDARY, Ui.ACCENT, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), 0)
        background = Ui.focusable(context, Color.TRANSPARENT, null)
        isClickable = true
        setOnClickListener { shown?.stop?.invoke() }
    }
    private var shown: Task? = null
    val canStop: Boolean get() = visibility == View.VISIBLE && shown?.stop != null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Ui.dp(context, 44)
        setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 6), 0)
        background = Ui.rounded(context, Ui.RAISED)
        visibility = View.GONE
        addView(Ui.spinner(context), LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16)).apply {
            marginEnd = Ui.dp(context, 10)
        })
        addView(label, LayoutParams(0, -2, 1f))
        addView(stopButton, LayoutParams(-2, Ui.dp(context, 36)))
    }

    /** Higher [priority] wins while several tasks run. */
    fun show(key: String, text: String, priority: Int = 0, stop: (() -> Unit)? = null) {
        tasks[key] = Task(text, priority, stop)
        update()
    }

    fun hide(key: String) {
        if (tasks.remove(key) != null) update()
    }

    private fun update() {
        val task = tasks.values.maxByOrNull { it.priority }
        shown = task
        visibility = if (task == null) View.GONE else View.VISIBLE
        label.text = task?.text ?: ""
        stopButton.visibility = if (task?.stop == null) View.GONE else View.VISIBLE
        if (task?.stop == null) stopButton.isSelected = false
    }
}

/** A prompt while a game runs. The game keeps running behind it. */
class GameNotice(
    val title: String,
    val message: String,
    val buttons: List<TrayButton> = emptyList(),
    /** Dismissing the notice, with B or by closing it. */
    val dismiss: () -> Unit = {}
)

/**
 * Layers over the game view: a centred progress strip while a machine stops or starts, a
 * notice docked at the top, and a Paused badge while the player keeps the game paused.
 */
class GameOverlay(context: Context) : FrameLayout(context) {
    private val progress = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        visibility = View.GONE
        isClickable = true
    }
    private val progressText = Ui.text(context, "", Ui.SECONDARY)
    private val notice = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        isClickable = true
        background = Ui.rounded(context, 0xf71f2835.toInt(), Ui.RADIUS_LARGE, Ui.LINE)
        elevation = Ui.dp(context, 8).toFloat()
        setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 14), Ui.dp(context, 12))
    }
    private val noticeTitle = Ui.text(context, "", Ui.BODY, bold = true)
    private val noticeMessage = Ui.text(context, "", Ui.SECONDARY, Ui.TEXT_BODY)
    private val noticeButtons = WrapLayout(context, Ui.dp(context, 8))
    private val paused = PixelTextView(context).apply {
        text = "PAUSED"
        color = Ui.PIN
        visibility = View.GONE
        background = Ui.rounded(context, 0xd90c1017.toInt())
        setPadding(Ui.dp(context, 8), Ui.dp(context, 6), Ui.dp(context, 8), Ui.dp(context, 6))
    }
    private var current: GameNotice? = null
    private var noticeFocus = 0
    private val buttonViews = ArrayList<View>()

    /** A notice with buttons takes the D-pad and A or B while the menu is closed. */
    val takesInput: Boolean get() = notice.visibility == View.VISIBLE && buttonViews.isNotEmpty()
    val progressShown: Boolean get() = progress.visibility == View.VISIBLE

    init {
        isClickable = false
        val strip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(context, 44)
            background = Ui.rounded(context, Ui.RAISED)
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
            addView(Ui.spinner(context), LinearLayout.LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16)).apply {
                marginEnd = Ui.dp(context, 10)
            })
            addView(progressText)
        }
        progress.addView(strip, LayoutParams(-2, -2, Gravity.CENTER))
        addView(progress, LayoutParams(-1, -1))
        notice.addView(noticeTitle)
        notice.addView(noticeMessage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 10) })
        notice.addView(noticeButtons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 10) })
        addView(notice, LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), 0)
        })
        addView(paused, LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            setMargins(0, Ui.dp(context, 14), Ui.dp(context, 14), 0)
        })
    }

    /** The game area fades in over the library or game in 240 ms, then shows progress. */
    fun showProgress(text: String) {
        progressText.text = text
        if (progress.visibility == View.VISIBLE) return
        progress.visibility = View.VISIBLE
        progress.alpha = 0f
        progress.animate().alpha(1f).setDuration(240).setInterpolator(android.view.animation.LinearInterpolator()).start()
    }

    fun hideProgress() {
        progress.animate().cancel()
        progress.visibility = View.GONE
    }

    fun setPaused(value: Boolean) { paused.visibility = if (value) View.VISIBLE else View.GONE }

    fun showNotice(value: GameNotice) {
        current = value
        noticeTitle.text = value.title
        noticeMessage.text = value.message
        noticeMessage.visibility = if (value.message.isEmpty()) View.GONE else View.VISIBLE
        noticeButtons.removeAllViews()
        buttonViews.clear()
        value.buttons.forEach { button ->
            val view = Ui.toneButton(context, button.label, button.tone) { press(button) }
            buttonViews += view
            noticeButtons.addView(view, LayoutParams(-2, Ui.dp(context, 44)))
        }
        noticeButtons.visibility = if (buttonViews.isEmpty()) View.GONE else View.VISIBLE
        noticeFocus = buttonViews.lastIndex
        select()
        notice.visibility = View.VISIBLE
    }

    fun hideNotice() {
        current = null
        notice.visibility = View.GONE
    }

    /** True while [notice] is the one shown, for updates from the task that posted it. */
    fun showing(value: GameNotice) = current === value && notice.visibility == View.VISIBLE

    fun handleKey(control: String?, event: KeyEvent): Boolean {
        if (!takesInput) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0
        when (control) {
            "left", "up" -> if (down) { noticeFocus = (noticeFocus - 1).coerceAtLeast(0); select() }
            "right", "down" -> if (down) { noticeFocus = (noticeFocus + 1).coerceAtMost(buttonViews.lastIndex); select() }
            "a" -> if (first) buttonViews.getOrNull(noticeFocus)?.performClick()
            "b" -> if (first) current?.let { hideNotice(); it.dismiss() }
            else -> return false
        }
        return true
    }

    private fun press(button: TrayButton) {
        hideNotice()
        button.run("")
    }

    private fun select() = buttonViews.forEachIndexed { index, view -> view.isSelected = index == noticeFocus }
}
