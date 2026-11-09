package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * New game's last page while the game is written (Kairo#184): its own page, not a notice on the
 * library. [write] runs off the UI thread, telling its progress; the page shows how far it is (a bar,
 * filled as far as the bytes written when their sizes are known, and the file being written). Back
 * does nothing while it runs: a game half written would be left behind. When it is written, [done]
 * gets what [write] returned; when it fails, the reason stays on the page and Back returns to the
 * page below, where the player can change what they chose and Create again.
 */
class CreatingPage<R>(
    private val activity: Activity,
    private val name: String,
    private val write: (progress: (CreateGameFlow.Progress) -> Unit) -> R,
    private val done: (R) -> Unit
) : Page() {
    private lateinit var bar: ProgressBar
    private lateinit var file: TextView
    private lateinit var message: TextView
    private var writing = false
    override val testName: String get() = "new-game-creating"

    /** Menu and Back stay on this page while the game is written. */
    override val capturing: Boolean get() = writing

    override fun createView(): View {
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        page.addView(header(context.getString(R.string.kairo_creating, name), null), LinearLayout.LayoutParams(-1, dp(56)))
        val column = MaxWidthColumn(context, dp(640)).apply { setPadding(dp(18), dp(16), dp(18), dp(8)) }
        bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            max = 100
            progressTintList = ColorStateList.valueOf(Ui.ACCENT)
            indeterminateTintList = ColorStateList.valueOf(Ui.ACCENT)
        }
        column.addView(bar, LinearLayout.LayoutParams(-1, -2))
        file = Ui.text(context, "", Ui.SECONDARY, Ui.TEXT_MUTED).apply {
            setPadding(dp(4), dp(8), dp(4), 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        column.addView(file, LinearLayout.LayoutParams(-1, -2))
        message = Ui.text(context, "", Ui.SECONDARY, Ui.DANGER).apply {
            setPadding(dp(4), dp(8), dp(4), 0)
            visibility = View.GONE
        }
        column.addView(message, LinearLayout.LayoutParams(-1, -2))
        page.addView(FrameLayout(context).apply {
            addView(column, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    override fun navItems(): List<NavItem> = emptyList()

    override fun onShown(first: Boolean) {
        if (!first) return
        writing = true
        Thread {
            val result = runCatching { write { progress -> activity.runOnUiThread { show(progress) } } }
            activity.runOnUiThread {
                writing = false
                result.onSuccess(done).onFailure { failure -> failed(failure) }
            }
        }.apply { name = "create-game"; start() }
    }

    private fun show(progress: CreateGameFlow.Progress) {
        if (!writing) return
        val percent = progress.percent
        bar.isIndeterminate = percent == null
        if (percent != null) bar.progress = percent
        file.text = if (percent != null) context.getString(R.string.kairo_create_progress, progress.file, percent) else progress.file
    }

    private fun failed(failure: Throwable) {
        bar.visibility = View.GONE
        file.visibility = View.GONE
        message.text = failure.message ?: context.getString(R.string.kairo_create_failed, name)
        message.visibility = View.VISIBLE
    }

    override fun back(): Boolean = writing
}
