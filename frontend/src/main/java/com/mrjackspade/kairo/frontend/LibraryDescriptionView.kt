package com.mrjackspade.kairo.frontend

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.Future

/** Bounded library summary. Full paragraph layout is expensive even with maxLines;
 * compute it off the input/render thread and draw the immutable result directly. */
internal class LibraryDescriptionView(context: Context) : View(context) {
    private data class Key(val text: String, val width: Int, val lines: Int, val rtl: Boolean)
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.TEXT_BODY
        textSize = Ui.BODY * resources.displayMetrics.scaledDensity
    }
    private val cache = LruCache<Key, StaticLayout>(8)
    private var executor: ThreadPoolExecutor? = null
    private var pending: Future<*>? = null
    private var generation = 0
    private var requested: Key? = null
    private var rendered: StaticLayout? = null

    var text: String = ""
        set(value) {
            if (field == value) return
            field = value
            contentDescription = value
            requestParagraph()
        }
    var maxLines: Int = 9
        set(value) {
            if (field == value) return
            field = value
            requestParagraph()
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestParagraph()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        requestParagraph()
    }

    private fun requestParagraph() {
        if (!isAttachedToWindow || width <= 0) return
        val key = Key(text, width, maxLines, layoutDirection == LAYOUT_DIRECTION_RTL)
        if (key == requested) return
        requested = key
        val token = ++generation
        pending?.cancel(false)
        // Never paint an old game's description under the newly selected title.
        rendered = cache.get(key)
        invalidate()
        if (rendered != null || text.isEmpty()) return
        val worker = executor ?: ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS,
            LinkedBlockingQueue<Runnable>()).also { executor = it }
        // Drop canceled queued selections; at most one running and one pending layout.
        worker.purge()
        val textPaint = TextPaint(paint)
        val spacing = 3 * resources.displayMetrics.density
        pending = worker.submit {
            val layout = StaticLayout.Builder.obtain(key.text, 0, key.text.length, textPaint, key.width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(if (key.rtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(spacing, 1f)
                .setIncludePad(true)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setMaxLines(key.lines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setEllipsizedWidth(key.width)
                .build()
            post {
                if (token == generation && isAttachedToWindow) {
                    cache.put(key, layout)
                    rendered = layout
                    invalidate()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rendered?.draw(canvas)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.TextView"
        info.text = text
    }

    override fun onDetachedFromWindow() {
        ++generation
        requested = null
        pending?.cancel(false)
        executor?.shutdownNow()
        executor = null
        super.onDetachedFromWindow()
    }
}
