package com.mrjackspade.kairo.frontend

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.text.TextUtils
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.abs

/**
 * Everything above the library or the running game: a flyout at the bottom, then full-screen
 * pages. Back pops one page; the host decides what Menu does. Pages slide in from +48 dp over
 * 220 ms; the page below moves to -48 dp and fades. The flyout slides 320 dp from the left.
 */
class PageStack(
    val activity: Activity,
    private val host: FrameLayout,
    /**
     * What pages build their views with: the activity, or another screen's context for a stack
     * shown there (a two-screen device's lower screen), whose pages still act on [activity].
     */
    val viewContext: Context = activity
) : FrameLayout(viewContext) {
    private val scrim = View(activity).apply {
        setBackgroundColor(Ui.SCRIM)
        alpha = 0f
        visibility = View.GONE
        setOnClickListener { if (this@PageStack.top?.isFlyout == true) closeAll() }
    }
    private val pages = ArrayList<Page>()
    private val running = HashMap<View, Animator>()

    /** Tells the UI tests whether a page is still sliding (id pages, key moving or settled), so they touch only settled pages. */
    private fun markMotion() = TestAttributes.mark(this, "pages", if (running.isEmpty()) "settled" else "moving")
    /** The library view moved aside by the first page, restored when the last page closes. */
    private var covered: View? = null

    private val activeListeners = ArrayList<(Boolean) -> Unit>()
    /** What Menu does while pages are open. The game closes everything and resumes. */
    var menuKey: () -> Unit = { closeAll() }
    /** The library view slides back behind a page pushed directly over it. */
    var base: () -> View? = { null }

    val isActive: Boolean get() = pages.isNotEmpty()
    val top: Page? get() = pages.lastOrNull()
    val flyoutOpen: Boolean get() = pages.firstOrNull()?.isFlyout == true
    val capturing: Boolean get() = top?.capturing == true
    val size: Int get() = pages.size
    fun page(index: Int): Page? = pages.getOrNull(index)

    /** Called when the stack gains its first page or loses its last. */
    fun onActiveChanged(listener: (Boolean) -> Unit) { activeListeners += listener }
    inline fun <reified P : Page> find(): P? = (0 until size).mapNotNull { page(it) as? P }.lastOrNull()

    init {
        visibility = View.GONE
        isFocusableInTouchMode = true
        isClickable = false
        addView(scrim, LayoutParams(-1, -1))
        host.addView(this, LayoutParams(-1, -1))
    }

    fun push(page: Page) {
        val previous = top
        if (previous != null && previous.isFlyout && page.isFlyout) return
        page.attach(this)
        pages += page
        addView(page.view, page.layoutParams())
        layer(page)
        if (previous == null) becomeActive()
        if (page.isModal) {
            previous?.hidden()
            page.view.alpha = 0f
            slide(page.view, 0f, 1f, 150)
            page.shown(first = true)
            return
        }
        previous?.let { hide(it, forward = true) }
        if (page.isFlyout) {
            scrim.visibility = View.VISIBLE
            fade(scrim, 1f, 180)
            page.view.translationX = -page.width().toFloat()
            slide(page.view, 0f, 1f, 200)
        } else {
            if (previous == null) base()?.let {
                covered = it
                slide(it, -dp(48f), 0f, 220, alphaDuration = 180)
            }
            page.view.translationX = dp(48f)
            page.view.alpha = 0f
            slide(page.view, 0f, 1f, 220, alphaDuration = 180)
        }
        page.shown(first = true)
    }

    /** Replace the top page without returning to it, such as the splash or a setup step. */
    fun replace(page: Page) {
        val old = pages.removeLastOrNull()
        old?.let {
            it.hidden()
            slide(it.view, 0f, 0f, 180) { removeView(it.view); it.removed() }
        }
        page.attach(this)
        pages += page
        addView(page.view, page.layoutParams())
        layer(page)
        if (old == null) becomeActive()
        page.view.alpha = 0f
        slide(page.view, 0f, 1f, 220, alphaDuration = 180)
        page.shown(first = true)
    }

    fun pop() {
        val leaving = pages.removeLastOrNull() ?: return
        leaving.hidden()
        if (leaving.isModal) slide(leaving.view, 0f, 0f, 120) { removeView(leaving.view); leaving.removed() }
        else if (leaving.isFlyout) {
            fade(scrim, 0f, 160) { if (!flyoutOpen) scrim.visibility = View.GONE }
            slide(leaving.view, -leaving.width().toFloat(), 1f, 160) { removeView(leaving.view); leaving.removed() }
        } else slide(leaving.view, dp(48f), 0f, 220, alphaDuration = 180) {
            removeView(leaving.view)
            leaving.removed()
        }
        val next = top
        if (next == null) {
            uncover()
            becomeInactive()
            return
        }
        // The page below a modal never moved.
        if (!leaving.isModal) show(next)
        next.shown(first = false)
    }

    /** Pop pages above [page], which becomes the top page. */
    fun popTo(page: Page) {
        if (page !in pages) return
        while (pages.size > pages.indexOf(page) + 2) pages.removeAt(pages.size - 2).let {
            it.hidden(); removeView(it.view); it.removed()
        }
        if (top !== page) pop()
    }

    /** Pop [page] and every page above it in one motion, as a pick in a nested choice page does. */
    fun popThrough(page: Page) {
        val index = pages.indexOf(page)
        if (index < 0) return
        while (pages.size > index + 1) pages.removeAt(index).let {
            it.hidden(); removeView(it.view); it.removed()
        }
        pop()
    }

    /** Close the flyout and every page, as Menu does in a game or a tap outside the flyout. */
    fun closeAll() {
        if (pages.isEmpty()) return
        val all = pages.toList()
        pages.clear()
        all.forEach { it.hidden() }
        // The top page and every page showing behind its modals leave with motion.
        var visible = all.size - 1
        while (visible > 0 && all[visible].isModal) visible--
        all.take(visible).forEach { removeView(it.view); it.removed() }
        all.drop(visible).forEach { shown ->
            if (shown.isFlyout)
                slide(shown.view, -shown.width().toFloat(), 1f, 160) { removeView(shown.view); shown.removed() }
            else slide(shown.view, 0f, 0f, 160) { removeView(shown.view); shown.removed() }
        }
        fade(scrim, 0f, 160) { if (!flyoutOpen) scrim.visibility = View.GONE }
        uncover()
        becomeInactive()
    }

    /**
     * Android draws a higher-elevation sibling on top whatever the child order, so a page with
     * no elevation, such as a modal, would sit under the flyout's 16 dp shadow elevation. Lift
     * each page just above every view already in the stack, including one still animating
     * out; its own elevation still shapes its shadow.
     */
    private fun layer(page: Page) {
        val below = (0 until childCount).map(::getChildAt).filter { it !== page.view }.maxOfOrNull { it.z } ?: 0f
        page.view.translationZ = maxOf(0f, below + 1f - page.view.elevation)
    }

    private fun uncover() {
        covered?.let { slide(it, 0f, 1f, 220, alphaDuration = 180) }
        covered = null
    }

    /** Back closes the tray, then the page, then the flyout. */
    fun back(): Boolean {
        val page = top ?: return false
        if (page.back()) return true
        if (page.isFlyout) closeAll() else pop()
        return true
    }

    fun refresh() { top?.refresh() }

    /** Four times a second while open, for live status and diagnostics. */
    fun tick() { top?.tick() }

    fun handleKey(control: String?, event: KeyEvent): Boolean = top?.handleKey(control, event) ?: false
    fun captureKey(event: KeyEvent): Boolean = top?.captureKey(event) ?: false
    fun captureMotion(event: MotionEvent): Boolean = top?.captureMotion(event) ?: false

    fun closeTray() { (top as? RowsPage)?.closeTray() }
    fun replaceTray(spec: TraySpec) { (top as? RowsPage)?.replaceTray(spec) }

    /** Flash a row's value on whichever page shows it, after a choice page returns. */
    fun flash(rowId: String) { (top as? RowsPage)?.flash(rowId) }

    private fun becomeActive() {
        visibility = View.VISIBLE
        bringToFront()
        requestFocus()
        activeListeners.toList().forEach { it(true) }
    }

    private fun becomeInactive() {
        hideKeyboard()
        clearFocus()
        postDelayed({ if (pages.isEmpty()) visibility = View.GONE }, 240)
        activeListeners.toList().forEach { it(false) }
    }

    private fun hide(page: Page, forward: Boolean) {
        page.hidden()
        if (page.isFlyout) slide(page.view, -page.width().toFloat(), 1f, 200)
        else slide(page.view, if (forward) -dp(48f) else dp(48f), 0f, 220, alphaDuration = 180) {
            if (top !== page) page.view.visibility = View.INVISIBLE
        }
    }

    private fun show(page: Page) {
        page.view.visibility = View.VISIBLE
        if (page.isFlyout) slide(page.view, 0f, 1f, 200)
        else {
            if (running[page.view] == null) page.view.translationX = -dp(48f)
            slide(page.view, 0f, 1f, 220, alphaDuration = 180)
        }
    }

    internal fun hideKeyboard() {
        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(windowToken, 0)
    }

    private fun slide(view: View, x: Float, alpha: Float, duration: Long, alphaDuration: Long = duration,
                      end: (() -> Unit)? = null) {
        running.remove(view)?.cancel()
        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(view, View.TRANSLATION_X, x).apply {
                this.duration = duration
                interpolator = Ui.ease()
            },
            ObjectAnimator.ofFloat(view, View.ALPHA, alpha).apply {
                this.duration = alphaDuration
                interpolator = LinearInterpolator()
            })
        set.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) {
                if (running[view] === set) running.remove(view)
                markMotion()
                if (!cancelled) end?.invoke()
            }
        })
        running[view] = set
        markMotion()
        set.start()
    }

    private fun fade(view: View, alpha: Float, duration: Long, end: (() -> Unit)? = null) {
        view.animate().cancel()
        view.animate().alpha(alpha).setDuration(duration).setInterpolator(LinearInterpolator())
            .withEndAction { end?.invoke() }.start()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}

/**
 * A focusable item: rows, buttons, tabs, and keys. Up and Down move between [r] rows. An item of a
 * recycled list (RecycledRows) has a view only while it is on screen, and [reveal] brings it there.
 */
class NavItem(
    val id: String,
    private val viewOf: () -> View?,
    val r: Int,
    val x: Float = 0.5f,
    val activate: () -> Unit,
    /** Left and Right when no neighbour sits in that direction. */
    val side: ((Int) -> Unit)? = null,
    val show: (Boolean) -> Unit = { viewOf()?.isSelected = it },
    /** Scrolls the item into view; null for an item whose view always exists. */
    val reveal: (() -> Unit)? = null
) {
    constructor(id: String, view: View, r: Int, x: Float = 0.5f, activate: () -> Unit,
                side: ((Int) -> Unit)? = null, show: (Boolean) -> Unit = { view.isSelected = it }) :
        this(id, { view }, r, x, activate, side, show)

    /** The item's view, or null while a recycled list has none for it. */
    val view: View? get() = viewOf()
}

/** One layer of the stack. Subclasses build the view and its navigation. */
abstract class Page {
    lateinit var stack: PageStack
        private set
    lateinit var view: View
        private set
    val context: Context get() = stack.viewContext
    /** Whether the page is still in its stack, for work that finishes after the page closed. */
    val inStack: Boolean get() = ::stack.isInitialized && (0 until stack.size).any { stack.page(it) === this }
    open val isFlyout: Boolean get() = false
    /** A modal fades in over the page below, which stays in place behind its scrim. */
    open val isModal: Boolean get() = false
    /** Menu and Back go to [back] and nothing else, as on setup pages. */
    open val capturing: Boolean get() = false
    protected open val scroll: ScrollView? get() = null
    private var nav: List<NavItem> = emptyList()
    protected var focusId: String? = null
    private var visible = false

    internal fun attach(owner: PageStack) {
        if (::view.isInitialized) return
        stack = owner
        view = createView()
        view.isClickable = true
        TestAttributes.mark(view, TestAttributes.PAGE, testName)
    }

    /** What the UI tests call this page (TestAttributes): its title, or its kind. */
    open val testName: String get() = javaClass.simpleName.ifEmpty { javaClass.name.substringAfterLast('.') }

    internal open fun layoutParams() = FrameLayout.LayoutParams(-1, -1)
    internal open fun width() = stack.width

    protected abstract fun createView(): View
    /** Focusable items in reading order, rebuilt after every render. */
    protected abstract fun navItems(): List<NavItem>
    /** Where focus starts the first time the page is shown; null starts with nothing focused. */
    protected open fun initialFocus(items: List<NavItem>): String? = items.firstOrNull()?.id

    internal fun shown(first: Boolean) {
        visible = true
        if (!first) refresh()
        renav(first)
        onShown(first)
    }

    internal fun hidden() { visible = false; onHidden() }
    internal fun removed() { onRemoved() }
    protected open fun onShown(first: Boolean) {}
    protected open fun onHidden() {}
    protected open fun onRemoved() {}
    open fun refresh() {}
    open fun tick() {}
    /** True when the page handled Back itself. */
    open fun back(): Boolean = false
    open fun captureKey(event: KeyEvent): Boolean = false
    open fun captureMotion(event: MotionEvent): Boolean = false

    /** Rebuild navigation after the page changed its views, keeping focus by id. */
    protected fun renav(first: Boolean = false) {
        nav = navItems()
        // An item a builder named more precisely keeps that name, such as a modal button by its label.
        nav.forEach { item ->
            val view = item.view ?: return@forEach
            if (TestAttributes.id(view) == null) TestAttributes.mark(view, item.id)
            TestAttributes.markItem(view, item.id)
        }
        if (first) focusId = initialFocus(nav)
        // Nothing focused stays that way until a direction key; a lost item moves focus to the start.
        else if (focusId != null && nav.none { it.id == focusId }) focusId = initialFocus(nav) ?: nav.firstOrNull()?.id
        applyFocus(reveal = first)
    }

    protected fun focus(id: String?, reveal: Boolean = true) {
        if (id == null || nav.none { it.id == id }) return
        focusId = id
        applyFocus(reveal)
    }

    protected val focused: NavItem? get() = nav.firstOrNull { it.id == focusId }

    /** The focused item and every focusable item, in order, as hosts and fixtures see them. */
    val focusedItemId: String? get() = focusId
    fun itemIds(): List<String> = nav.map { it.id }
    fun itemView(id: String): View? = nav.firstOrNull { it.id == id }?.view

    private fun applyFocus(reveal: Boolean) {
        nav.forEach {
            it.show(it.id == focusId)
            it.view?.let { view -> TestAttributes.markFocused(view, it.id == focusId) }
        }
        if (reveal) focused?.let { item -> item.reveal?.invoke() ?: item.view?.let(::reveal) }
    }

    protected fun reveal(target: View) {
        val scroller = scroll ?: return
        target.post {
            if (!target.isAttachedToWindow) return@post
            val rect = Rect(0, 0, target.width, target.height)
            try { scroller.offsetDescendantRectToMyCoords(target, rect) } catch (_: IllegalArgumentException) { return@post }
            val margin = Ui.dp(context, 16)
            val top = scroller.scrollY
            val bottom = top + scroller.height
            // offsetDescendantRectToMyCoords leaves out the scroller's own scroll, so rect is
            // already in content coordinates.
            val y = rect.top
            val to = when {
                rect.height() + margin * 2 > scroller.height || y - margin < top -> (y - margin).coerceAtLeast(0)
                y + rect.height() + margin > bottom -> y + rect.height() + margin - scroller.height
                else -> top
            }
            // Only a real move: a smooth scroll runs its full duration even when it goes nowhere,
            // and the scroll view takes a tap made meanwhile for the start of a drag.
            if (to != top) scroller.smoothScrollTo(0, to)
        }
    }

    open fun handleKey(control: String?, event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0
        when (control) {
            "up", "down", "left", "right" -> { if (down) move(control); return true }
            "a" -> { if (first) focused?.activate?.invoke(); return true }
            "b" -> { if (first) stack.back(); return true }
            "menu" -> { if (first) stack.menuKey(); return true }
        }
        return false
    }

    /** Up and Down go to the nearest item in the next row; Left and Right stay in the row. */
    protected open fun move(direction: String) {
        val current = focused ?: run {
            focusId = nav.firstOrNull()?.id
            applyFocus(reveal = true)
            return
        }
        if (direction == "left" || direction == "right") {
            val row = nav.filter { it.r == current.r }.sortedBy { it.x }
            val next = row.getOrNull(row.indexOf(current) + if (direction == "right") 1 else -1)
            if (next != null) focus(next.id) else current.side?.invoke(if (direction == "right") 1 else -1)
            return
        }
        val rows = nav.map { it.r }.distinct().sorted()
        val target = rows.getOrNull(rows.indexOf(current.r) + if (direction == "down") 1 else -1)
        if (target == null) { edge(direction); return }
        nav.filter { it.r == target }.minByOrNull { abs(it.x - current.x) }?.let { focus(it.id) }
    }

    /** Up past the first row or Down past the last. */
    protected open fun edge(direction: String) {
        if (direction == "down") scroll?.smoothScrollBy(0, Ui.dp(context, 96))
        else if (direction == "up") scroll?.smoothScrollTo(0, 0)
    }

    protected fun dp(value: Int) = Ui.dp(context, value)

    /** Back arrow, then a title that is the label of the row that opened the page, then scope. */
    protected fun header(title: String, scope: String?): View {
        val chip = Ui.text(context, scope ?: "", Ui.LABEL, Ui.TEXT_MUTED).apply {
            background = Ui.rounded(context, android.graphics.Color.TRANSPARENT, 999, Ui.LINE)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            visibility = if (scope.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        scopeChip = chip
        val bar = HeaderBar(context, chip).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(18), 0)
        }
        bar.addView(Ui.iconButton(context, R.drawable.ic_back, "Back") { stack.back() }.apply {
            isFocusable = false
            testId("page.back")
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(4) })
        bar.addView(Ui.text(context, title, Ui.TITLE, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        return bar
    }
    protected var scopeChip: android.widget.TextView? = null
}

/**
 * A page header, whose scope chip takes at most 45% of its width. The cap is set while measuring,
 * and only when it changes: TextView.setMaxWidth always asks for another layout, so setting it on
 * every layout (as a layout listener once did) relaid the window out on every frame.
 */
private class HeaderBar(context: Context, private val chip: android.widget.TextView) : LinearLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cap = (MeasureSpec.getSize(widthMeasureSpec) * 0.45f).toInt()
        if (chip.maxWidth != cap) chip.maxWidth = cap
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
