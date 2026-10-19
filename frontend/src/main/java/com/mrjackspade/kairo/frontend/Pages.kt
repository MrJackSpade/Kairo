package com.mrjackspade.kairo.frontend

import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A button in a page's fixed footer, used where a page holds a draft or finishes setup. */
class FooterButton(
    val id: String,
    val label: () -> String,
    val tone: ButtonTone = ButtonTone.SECONDARY,
    val enabled: () -> Boolean = { true },
    /** Shown when the disabled button is pressed. */
    val disabledReason: String? = null,
    val run: () -> Unit
)

/** Caps its width, centred by its parent, so rows stay readable on wide screens. */
internal class MaxWidthColumn(context: android.content.Context, private val max: Int) : LinearLayout(context) {
    init { orientation = VERTICAL }
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val size = MeasureSpec.getSize(widthSpec)
        super.onMeasure(if (size > max) MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY) else widthSpec, heightSpec)
    }
}

/** Shared page scaffolding: a scrolling, width-capped body and an optional fixed footer. */
internal object PageParts {
    fun scroll(page: Page, body: View, padding: IntArray): ScrollView {
        val context = page.context
        val column = MaxWidthColumn(context, Ui.dp(context, 640)).apply {
            setPadding(padding[0], padding[1], padding[2], padding[3])
            addView(body, LinearLayout.LayoutParams(-1, -2))
        }
        val frame = FrameLayout(context).apply {
            addView(column, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL))
        }
        return ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            addView(frame, FrameLayout.LayoutParams(-1, -2))
        }
    }
}

/** Builds and refreshes the footer bar; buttons sit at the end with the primary one last. */
internal class Footer(private val page: Page, private val buttons: () -> List<FooterButton>) {
    val view = LinearLayout(page.context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setBackgroundColor(Ui.BG)
        setPadding(Ui.dp(context, 18), Ui.dp(context, 10), Ui.dp(context, 18), Ui.dp(context, 12))
    }
    val bar = LinearLayout(page.context).apply {
        orientation = LinearLayout.VERTICAL
        addView(View(context).apply { setBackgroundColor(Ui.LINE) }, LinearLayout.LayoutParams(-1, Ui.dp(context, 1)))
        addView(view, LinearLayout.LayoutParams(-1, -2))
    }
    private val views = LinkedHashMap<String, Pair<FooterButton, LinearLayout>>()
    private var labels: List<String> = emptyList()

    fun render() {
        val current = buttons()
        if (current.map { it.id } != views.keys.toList() || current.map { it.label() } != labels) {
            view.removeAllViews()
            views.clear()
            current.forEachIndexed { index, button ->
                val item = Ui.toneButton(page.context, button.label(), button.tone, minWidth = 112) {
                    if (button.enabled()) button.run()
                    else button.disabledReason?.let { Ui.message(page.stack.activity, it) }
                }
                views[button.id] = button to item
                view.addView(item, LinearLayout.LayoutParams(-2, Ui.dp(page.context, 44)).apply {
                    if (index > 0) marginStart = Ui.dp(page.context, 8)
                })
            }
            labels = current.map { it.label() }
        }
        views.values.forEach { (button, item) -> item.alpha = if (button.enabled()) 1f else 0.45f }
        bar.visibility = if (current.isEmpty()) View.GONE else View.VISIBLE
    }

    fun nav(row: Int): List<NavItem> {
        val count = views.size
        return views.values.mapIndexed { index, (button, item) ->
            NavItem("footer:${button.id}", item, row, 0.6f + 0.35f * (index + 1) / count, { item.performClick() })
        }
    }
}

/** A titled page of row groups, with the scope chip naming what the page changes. */
open class GroupsPage(
    val title: String,
    private val scope: () -> String? = { null },
    private val content: () -> List<RowGroup>,
    private val footerButtons: (() -> List<FooterButton>)? = null
) : RowsPage() {
    private lateinit var scrollView: ScrollView
    private var footer: Footer? = null
    override val scroll: ScrollView get() = scrollView

    override fun groups() = content()

    override fun createView(): View {
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        page.addView(header(title, scope()), LinearLayout.LayoutParams(-1, dp(56)))
        scrollView = PageParts.scroll(this, rowsView(), intArrayOf(dp(18), 0, dp(18), dp(24)))
        page.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        footerButtons?.let { buttons ->
            footer = Footer(this, buttons).also {
                it.render()
                page.addView(it.bar, LinearLayout.LayoutParams(-1, -2))
            }
        }
        return page
    }

    override fun navItems(): List<NavItem> = rowNav(0) + (footer?.nav(Int.MAX_VALUE) ?: emptyList())

    override fun refresh() {
        super.refresh()
        scopeChip?.let { chip ->
            val value = scope()
            chip.text = value ?: ""
            chip.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        footer?.let { it.render(); renav() }
    }
}

/**
 * Picking an option applies it, fills the radio, and returns after 150 ms. A folder opens a
 * nested page of the same choice; a pick there returns past every page of the choice.
 */
class ChoicePage private constructor(
    private val spec: ChoiceSpec,
    private val rowId: String?,
    title: String,
    private val content: () -> List<ChoiceGroup>,
    /** The choice's first page, when this page is a folder inside it. */
    private val first: ChoicePage?
) : GroupsPage(title, spec.scope, { emptyList() }) {
    constructor(spec: ChoiceSpec, rowId: String? = null) : this(spec, rowId, spec.title, spec.groups, null)

    override fun groups(): List<RowGroup> {
        val options = content().map { group ->
            RowGroup(group.label, group.folders.map { folder ->
                SettingsRow.nav("f:${folder.id}", folder.label, { folder.find(spec.selected())?.label ?: "" },
                    { ValueTone.CURRENT }) { open(folder) }
            } + group.options.map { option ->
                SettingsRow.option("o:${option.id}", option.label, option.description,
                    { spec.selected() == option.id }) { pick(option.id) }
            }, group.foot)
        }
        val actions = if (first == null) spec.actions() else emptyList()
        return if (actions.isEmpty()) options else options + RowGroup(null, actions)
    }

    /** The current option, or the folder that holds it. */
    override fun initialFocus(items: List<NavItem>): String? {
        val target = spec.selected()?.let { id ->
            content().firstNotNullOfOrNull { group ->
                group.options.firstOrNull { it.id == id }?.let { "o:$id" }
                    ?: group.folders.firstOrNull { it.find(id) != null }?.let { "f:${it.id}" }
            }
        }
        return target?.takeIf { id -> items.any { it.id == id } } ?: items.firstOrNull()?.id
    }

    private fun open(folder: ChoiceFolder) =
        stack.push(ChoicePage(spec, rowId, folder.label, folder.groups, first ?: this))

    private fun pick(id: String) {
        spec.select(id)
        if (stack.top === this) refresh()
        if (spec.stay) return
        view.postDelayed({
            if (stack.top === this) {
                stack.popThrough(first ?: this)
                rowId?.let(stack::flash)
            }
        }, 150)
    }
}

/** The header and settings shown in a flyout. */
class FlyoutModel(
    val product: String,
    val title: () -> String,
    val status: () -> String,
    val busy: () -> Boolean = { false },
    val sections: () -> List<RowGroup>
)

/**
 * The library and game flyouts: product, title, status, then flat rows. [start] focuses a row; [confirm] also opens its confirmation, as controller
 * Restart and Exit actions do.
 */
class FlyoutPage(private val model: FlyoutModel, private val start: String? = null,
                 private val confirm: Boolean = false) : RowsPage(flat = true) {
    override val isFlyout: Boolean get() = true
    private lateinit var scrollView: ScrollView
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var spinner: View
    override val scroll: ScrollView get() = scrollView

    override fun groups() = model.sections()

    override fun width(): Int {
        val available = if (stack.width > 0) stack.width else context.resources.displayMetrics.widthPixels
        return minOf(dp(320), available - dp(40))
    }

    override fun layoutParams() = FrameLayout.LayoutParams(width(), -1, Gravity.START)

    override fun createView(): View {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(20), dp(12), dp(16))
        }
        content.addView(PixelTextView(context).apply {
            text = model.product
            scale = 2
            setPadding(dp(12), 0, dp(12), dp(14))
        })
        title = Ui.text(context, "", Ui.TITLE, bold = true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(12), 0)
        }
        content.addView(title)
        val statusLine = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(2), dp(12), dp(14))
        }
        spinner = Ui.spinner(context, 14).also {
            statusLine.addView(it, LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(8) })
        }
        status = Ui.text(context, "", Ui.SECONDARY, Ui.TEXT_MUTED).apply { maxLines = 4 }
        statusLine.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(statusLine)
        content.addView(rowsView(), LinearLayout.LayoutParams(-1, -2))
        scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            setBackgroundColor(Ui.SURFACE)
            elevation = dp(16).toFloat()
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        updateHeader()
        return scrollView
    }

    override fun navItems(): List<NavItem> = rowNav(0)

    override fun initialFocus(items: List<NavItem>) =
        start?.takeIf { id -> items.any { it.id == id } } ?: items.firstOrNull()?.id

    override fun onShown(first: Boolean) {
        if (first && confirm && start != null) rowById(start)?.let(::activate)
    }

    override fun refresh() {
        super.refresh()
        updateHeader()
    }

    override fun tick() = updateHeader()

    private fun updateHeader() {
        title.text = model.title()
        status.text = model.status()
        status.visibility = if (status.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        spinner.visibility = if (model.busy()) View.VISIBLE else View.GONE
    }
}

/**
 * First-run and required setup: the product and step, a title and description, ordinary rows,
 * a note, and footer buttons, over the setup gradient. Menu does nothing here.
 */
open class SetupPage(
    private val product: String,
    private val step: String?,
    private val title: String,
    private val description: String,
    private val note: String? = null,
    private val content: () -> List<RowGroup> = { emptyList() },
    private val footerButtons: () -> List<FooterButton>,
    private val onBack: () -> Unit,
    private val startFocus: String? = null,
    /** Start with nothing focused, so a touch-only device sees no choice highlighted. */
    private val startUnfocused: Boolean = false
) : RowsPage() {
    private lateinit var scrollView: ScrollView
    private lateinit var footer: Footer
    override val scroll: ScrollView get() = scrollView

    override fun groups() = content()

    override fun createView(): View {
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xff142832.toInt(), Ui.BG, 0xff111a29.toInt()))
        }
        val head = MaxWidthColumn(context, dp(640)).apply { setPadding(dp(24), dp(20), dp(24), dp(4)) }
        val brand = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brand.addView(PixelTextView(context).apply {
            text = product
            scale = 2
            contentDescription = product
        }, LinearLayout.LayoutParams(0, -2, 1f))
        step?.let { brand.addView(PixelTextView(context).apply { text = it; color = Ui.TEXT_FAINT }) }
        head.addView(brand)
        head.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Ui.ACCENT, Ui.LINE))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(0, dp(14), 0, dp(18)) })
        head.addView(Ui.text(context, title, Ui.DISPLAY, bold = true))
        head.addView(Ui.text(context, description, Ui.BODY, Ui.TEXT_MUTED).apply {
            setLineSpacing(dp(4).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        page.addView(FrameLayout(context).apply {
            addView(head, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL))
        })
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(rowsView())
        note?.let { body.addView(Ui.text(context, it, Ui.SECONDARY, Ui.TEXT_MUTED).apply {
            setPadding(0, dp(12), 0, 0)
        }) }
        scrollView = PageParts.scroll(this, body, intArrayOf(dp(24), 0, dp(24), dp(16)))
        page.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        footer = Footer(this, footerButtons).also {
            it.render()
            it.view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            page.addView(it.bar, LinearLayout.LayoutParams(-1, -2))
        }
        return page
    }

    override fun navItems(): List<NavItem> = rowNav(0) + footer.nav(Int.MAX_VALUE)

    override fun initialFocus(items: List<NavItem>) = if (startUnfocused) null
        else startFocus?.let { id -> items.firstOrNull { it.id == id || it.id == "footer:$id" }?.id }
            ?: items.firstOrNull()?.id

    override fun refresh() {
        super.refresh()
        footer.render()
        renav()
    }

    override fun back(): Boolean {
        if (super.back()) return true
        onBack()
        return true
    }

    override fun onShown(first: Boolean) {
        FirstRunVisibility.forActivity(stack.activity).setVisible(this, true)
    }

    override fun onRemoved() {
        FirstRunVisibility.forActivity(stack.activity).setVisible(this, false)
    }

    override fun handleKey(control: String?, event: KeyEvent): Boolean {
        if (control == "menu") return true
        return super.handleKey(control, event)
    }
}

/**
 * A modal confirmation over the page below: a title, a message, and Cancel then the verb,
 * opening on Cancel. Any direction moves between the two buttons; B or a tap outside cancels.
 */
class ConfirmPage(private val confirm: Confirm) : Page() {
    override val isModal: Boolean get() = true
    private lateinit var cancel: View
    private lateinit var accept: View
    val title: String? get() = confirm.title
    val message: String get() = confirm.message
    val buttons: List<String> get() = listOf("Cancel", confirm.verb)
    val focusedButton: String? get() = when (focusedItemId) {
        "cancel" -> "Cancel"
        "confirm" -> confirm.verb
        else -> null
    }

    override fun createView(): View {
        val card = MaxWidthColumn(context, dp(440)).apply {
            background = Ui.rounded(context, Ui.RAISED, Ui.RADIUS_LARGE, Ui.LINE)
            elevation = dp(24).toFloat()
            setPadding(dp(24), dp(20), dp(20), dp(16))
            isClickable = true
        }
        card.addView(Ui.text(context, confirm.title ?: "", Ui.TITLE, bold = true))
        card.addView(Ui.text(context, confirm.message, Ui.SECONDARY, Ui.TEXT_BODY).apply {
            setLineSpacing(dp(4).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val buttons = WrapLayout(context, dp(8))
        cancel = Ui.choiceButton(context, "Cancel", ButtonTone.SECONDARY) { stack.pop() }
        accept = Ui.choiceButton(context, confirm.verb,
            if (confirm.danger) ButtonTone.DANGER else ButtonTone.PRIMARY) {
            stack.pop()
            confirm.run()
        }
        buttons.addView(cancel, ViewGroup.LayoutParams(-2, dp(44)))
        buttons.addView(accept, ViewGroup.LayoutParams(-2, dp(44)))
        card.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        return FrameLayout(context).apply {
            setBackgroundColor(Ui.SCRIM)
            setOnClickListener { stack.pop() }
            addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
                setMargins(dp(24), dp(24), dp(24), dp(24))
            })
        }
    }

    override fun navItems(): List<NavItem> = listOf(
        NavItem("cancel", cancel, 0, 0.25f, { cancel.performClick() }),
        NavItem("confirm", accept, 0, 0.75f, { accept.performClick() }))

    override fun initialFocus(items: List<NavItem>) = "cancel"

    /** Two buttons in a row: Up and Left go to Cancel, Down and Right to the verb. */
    override fun move(direction: String) = focus(if (direction == "up" || direction == "left") "cancel" else "confirm")
}

/** A text document in a card. Up and Down scroll 96 dp. */
class DocumentPage(private val title: String, private val text: String,
                   private val textSize: Float = Ui.SECONDARY) : Page() {
    private lateinit var scrollView: ScrollView
    override val scroll: ScrollView get() = scrollView

    override fun createView(): View {
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        page.addView(header(title, null), LinearLayout.LayoutParams(-1, dp(56)))
        val card = Ui.text(context, text, textSize, Ui.TEXT_BODY).apply {
            background = Ui.rounded(context, Ui.SURFACE, Ui.RADIUS_LARGE)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setLineSpacing(dp(4).toFloat(), 1f)
            setTextIsSelectable(false)
        }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        scrollView = PageParts.scroll(this, body, intArrayOf(dp(18), 0, dp(18), dp(24)))
        page.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    override fun navItems(): List<NavItem> = emptyList()

    override fun move(direction: String) {
        when (direction) {
            "down" -> scrollView.smoothScrollBy(0, dp(96))
            "up" -> scrollView.smoothScrollBy(0, -dp(96))
        }
    }
}

/** A full-size image with a hint line. A tap or A runs [load], such as fetching a larger one. */
class ImagePage(private val title: String, private val scope: String?, bitmap: Bitmap,
                hint: String?, private val load: ((ImagePage) -> Unit)? = null,
                private val release: () -> Unit = {}) : Page() {
    private lateinit var scrollView: ScrollView
    private lateinit var image: ImageView
    private lateinit var frame: FrameLayout
    private lateinit var hintView: TextView
    private var current = bitmap
    private var hintText = hint
    override val scroll: ScrollView get() = scrollView

    override fun createView(): View {
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        page.addView(header(title, scope), LinearLayout.LayoutParams(-1, dp(56)))
        image = ImageView(context).apply {
            setImageBitmap(current)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        frame = FrameLayout(context).apply {
            background = Ui.focusable(context, Ui.SURFACE, null)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            contentDescription = title
            addView(image, FrameLayout.LayoutParams(-1, -2))
            if (load != null) setOnClickListener { load.invoke(this@ImagePage) }
        }
        hintView = Ui.text(context, hintText ?: "", Ui.SECONDARY, Ui.TEXT_MUTED).apply {
            setPadding(dp(14), dp(10), dp(14), 0)
            visibility = if (hintText.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(frame, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(hintView)
        }
        scrollView = PageParts.scroll(this, body, intArrayOf(dp(18), 0, dp(18), dp(24)))
        page.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    fun setHint(value: String?) {
        hintText = value
        hintView.text = value ?: ""
        hintView.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun setImage(bitmap: Bitmap) {
        val old = current
        current = bitmap
        image.setImageBitmap(bitmap)
        if (old !== bitmap) old.recycle()
    }

    override fun navItems(): List<NavItem> =
        if (load == null) emptyList() else listOf(NavItem("image", frame, 0, 0.5f, { frame.performClick() }))

    override fun onRemoved() {
        image.setImageDrawable(null)
        current.recycle()
        release()
    }
}

/** A page around a view the caller draws, such as the on-screen controls arrangement. */
class ViewPage(private val build: (ViewPage) -> View, private val items: (ViewPage) -> List<NavItem> = { emptyList() },
               private val removed: () -> Unit = {}) : Page() {
    override fun createView(): View = build(this)
    override fun navItems(): List<NavItem> = items(this)
    override fun onRemoved() = removed()
}
