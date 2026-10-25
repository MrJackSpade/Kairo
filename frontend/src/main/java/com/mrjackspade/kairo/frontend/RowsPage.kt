package com.mrjackspade.kairo.frontend

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A page of rows: groups with a pixel-font label, a Surface card, and an optional footnote, or
 * flat rows in the flyout. Opens trays under rows and keeps focus and scroll through refreshes.
 *
 * The rows are drawn by a [RowsBody]: [StackedRows] builds a view for every row, and
 * [RecycledRows], for a page too long for that ([isLong]), keeps views only for the rows on screen.
 */
abstract class RowsPage(private val flat: Boolean = false) : Page(), RowHost {
    abstract fun groups(): List<RowGroup>

    private lateinit var body: RowsBody
    /** The rows as last shown, by id, in page order. */
    private var rows = LinkedHashMap<String, SettingsRow>()
    private var signature: String? = null
    private var tray: TrayView? = null
    private var trayRow: String? = null
    private var trayFocus = 0

    override val capturing: Boolean get() = tray?.spec?.capture != null
    /** The row whose tray is open, and the tray's button labels. */
    val trayRowId: String? get() = trayRow
    fun trayButtons(): List<String> = tray?.spec?.buttons?.map { it.label } ?: emptyList()
    /** The selected tray button, or null when the field or nothing is selected. */
    fun trayFocused(): String? = tray?.spec?.buttons?.getOrNull(trayFocus)?.label
    fun trayMessage(): String? = tray?.spec?.message
    /** The row's current value, for hosts and fixtures. */
    fun rowValue(id: String): String? = rows[id]?.value?.invoke()

    /** The rows of every view, for a page that builds them all. */
    protected fun rowsView(): LinearLayout = install(StackedRows(context, this, flat, ::bindRow)) as LinearLayout

    /** The rows as a recycled list, which scrolls by itself, for a page with more rows than [LONG_PAGE_ROWS]. */
    protected fun recycledRowsView(): View = install(RecycledRows(context, this, ::bindRow))

    private fun install(rowsBody: RowsBody): View {
        body = rowsBody
        render()
        return rowsBody.view
    }

    private fun signatureOf(groups: List<RowGroup>) = groups.joinToString("|") { group ->
        "${group.label}/${group.foot != null}/" + group.rows.joinToString(",") {
            "${it.id}:${it.kind}:${it.description != null}"
        }
    }

    /** Binds [view] to [row] as this page shows it: focused, and with its tray when open. */
    private fun bindRow(view: RowView, row: SettingsRow) {
        view.bind(row, row.id == focusId, row.id == trayRow)
        view.showTray(if (row.id == trayRow) tray else null)
    }

    private fun render() {
        val groups = groups()
        signature = signatureOf(groups)
        rows = LinkedHashMap<String, SettingsRow>().apply { groups.forEach { group -> group.rows.forEach { put(it.id, it) } } }
        body.render(groups)
        if (trayRow != null && trayRow !in rows) dropTray()
    }

    override fun refresh() {
        val groups = groups()
        if (signatureOf(groups) != signature) {
            val position = body.position()
            render()
            renav()
            body.restore(position)
            return
        }
        rows = LinkedHashMap<String, SettingsRow>().apply { groups.forEach { group -> group.rows.forEach { put(it.id, it) } } }
        body.rebind(groups)
    }

    /** Rows in nav order, from row coordinate [start]. Info rows are skipped. */
    protected fun rowNav(start: Int): List<NavItem> {
        var r = start
        return rows.values.mapNotNull { row ->
            if (row.kind == SettingsRow.Kind.INFO) return@mapNotNull null
            NavItem(row.id, { body.rowView(row.id)?.line }, r++, 0.5f, { activate(row) }, { side(row, it) },
                { selected -> body.rowView(row.id)?.let { view -> view.row?.let { view.bind(it, selected, it.id == trayRow) } } },
                body.revealer(row.id))
        }
    }

    protected fun rowById(id: String): SettingsRow? = rows[id]

    fun flash(id: String) { body.rowView(id)?.flash() }

    override fun edge(direction: String) {
        if (!body.edge(direction)) super.edge(direction)
    }

    override fun activate(row: SettingsRow) {
        if (tray != null && trayRow != row.id) closeTray()
        focus(row.id, reveal = false)
        if (!row.enabled()) {
            row.disabledReason()?.let { Ui.message(stack.activity, it) }
            return
        }
        when (row.kind) {
            SettingsRow.Kind.CHOICE -> row.choice?.let { stack.push(ChoicePage(it, row.id)) }
            SettingsRow.Kind.SWITCH -> { row.setChecked?.invoke(!row.checked()); stack.refresh() }
            SettingsRow.Kind.SLIDER, SettingsRow.Kind.INFO -> Unit
            SettingsRow.Kind.ACTION -> when {
                row.confirm?.title != null -> stack.push(ConfirmPage(row.confirm))
                row.confirm != null -> openTray(row.id, row.confirm.tray())
                row.tray != null -> openTray(row.id, row.tray.invoke())
                else -> { row.run?.invoke(); stack.refresh() }
            }
            else -> row.run?.invoke()
        }
    }

    override fun side(row: SettingsRow, direction: Int) {
        if (!row.enabled() || tray != null) return
        when (row.kind) {
            SettingsRow.Kind.CHOICE -> {
                val spec = row.choice ?: return
                if (!spec.cycles) return
                val options = spec.options
                if (options.isEmpty()) return
                val index = options.indexOfFirst { it.id == spec.selected() }.coerceAtLeast(0)
                spec.select(options[(index + direction + options.size) % options.size].id)
                stack.refresh()
                flash(row.id)
            }
            SettingsRow.Kind.SLIDER -> row.slider?.let { slider ->
                val next = (slider.get() + direction * slider.keyStep).coerceIn(slider.min, slider.max)
                if (next != slider.get()) { slider.set(next); stack.refresh() }
            }
            SettingsRow.Kind.SWITCH -> if ((direction > 0) != row.checked()) {
                row.setChecked?.invoke(direction > 0)
                stack.refresh()
            }
            else -> Unit
        }
    }

    override fun slide(row: SettingsRow, value: Int) {
        focus(row.id, reveal = false)
        val slider = row.slider ?: return
        if (!row.enabled()) return
        val snapped = (slider.min + Math.round((value - slider.min).toFloat() / slider.step) * slider.step)
            .coerceIn(slider.min, slider.max)
        if (snapped != slider.get()) { slider.set(snapped); stack.refresh() }
    }

    /** Open [spec] under the row. Listening and confirm trays start on their first button. */
    fun openTray(rowId: String, spec: TraySpec) {
        dropTray()
        val view = body.rowView(rowId) ?: return
        val trayView = TrayView(context, spec, flat) { button, text -> press(button, text) }
        tray = trayView
        trayRow = rowId
        view.showTray(trayView)
        view.row?.let { view.bind(it, true, true) }
        trayFocus = when {
            trayView.entry != null -> -1
            else -> spec.focus ?: if (spec.listening) 0 else spec.buttons.lastIndex
        }
        trayView.select(trayFocus)
        trayView.entry?.let { field ->
            field.requestFocus()
            field.post {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(field, 0)
            }
        }
        reveal(view.root)
    }

    fun replaceTray(spec: TraySpec) { trayRow?.let { openTray(it, spec) } }

    fun closeTray() {
        if (tray == null) return
        dropTray()
        refresh()
    }

    private fun dropTray() {
        val row = trayRow
        val hadField = tray?.entry != null
        tray = null
        trayRow = null
        row?.let { id -> body.rowView(id)?.let { view ->
            view.showTray(null)
            view.row?.let { view.bind(it, it.id == focusId, false) }
        } }
        if (hadField) {
            stack.hideKeyboard()
            stack.requestFocus()
        }
    }

    private fun press(button: TrayButton, text: String) {
        if (!button.keepOpen) closeTray()
        button.run(text)
        stack.refresh()
    }

    override fun back(): Boolean {
        if (tray != null) { closeTray(); return true }
        return false
    }

    override fun captureKey(event: KeyEvent): Boolean {
        val capture = tray?.spec?.capture ?: return false
        if (capture.key(event)) return true
        // Only Android Back cancels listening; keyboard keys may still use the tray.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) closeTray()
            return true
        }
        if (KeyEvent.isGamepadButton(event.keyCode) || event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return true
        return false
    }

    override fun captureMotion(event: MotionEvent): Boolean = tray?.spec?.capture?.motion(event) ?: false

    override fun handleKey(control: String?, event: KeyEvent): Boolean {
        val open = tray ?: return super.handleKey(control, event)
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0
        if (trayFocus < 0) {
            // The field has Android focus; text and cursor keys belong to it.
            when (control) {
                "b" -> { if (first) closeTray(); return true }
                "a" -> { if (first) open.pressPrimary(); return true }
                "down" -> { if (down) { trayFocus = open.buttons.lastIndex; open.select(trayFocus) }; return true }
                "menu" -> { if (first) stack.menuKey(); return true }
            }
            return false
        }
        when (control) {
            "left", "right" -> if (down) {
                trayFocus = (trayFocus + if (control == "right") 1 else -1).coerceIn(0, open.buttons.lastIndex)
                open.select(trayFocus)
            }
            "a" -> if (first) open.buttons.getOrNull(trayFocus)?.performClick()
            "b" -> if (first) closeTray()
            "up" -> if (down) {
                if (open.entry != null) { trayFocus = -1; open.select(-1); open.entry.requestFocus() }
                else closeTray()
            }
            "down" -> if (down) closeTray()
            "menu" -> if (first) stack.menuKey()
            else -> return false
        }
        return true
    }

    companion object {
        /** A page with more rows than this shows them as a recycled list ([RecycledRows]). */
        const val LONG_PAGE_ROWS = 80

        fun isLong(groups: List<RowGroup>) = groups.sumOf { it.rows.size } > LONG_PAGE_ROWS
    }
}

/** How a [RowsPage] draws its rows. */
internal interface RowsBody {
    val view: View
    /** Rebuilds the rows for [groups], whose rows changed. */
    fun render(groups: List<RowGroup>)
    /** Shows [groups]' current values; the rows are the same. */
    fun rebind(groups: List<RowGroup>)
    /** The row's view, while it has one. */
    fun rowView(id: String): RowView?
    /** Scrolls [id]'s row into view, for a body whose rows are not all built; null otherwise. */
    fun revealer(id: String): (() -> Unit)?
    /** Up past the first row or Down past the last; false to leave it to the page. */
    fun edge(direction: String): Boolean
    /** The scroll position, kept through a render. */
    fun position(): Int
    fun restore(position: Int)
}

/** Every row built at once, in group cards, inside the page's scroll view. */
internal class StackedRows(private val context: Context, private val host: RowHost, private val flat: Boolean,
                           private val bind: (RowView, SettingsRow) -> Unit) : RowsBody {
    override val view = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val rowViews = HashMap<String, RowView>()
    private val foots = HashMap<Int, TextView>()
    private val scroll: android.widget.ScrollView? get() = generateSequence(view.parent) { it.parent }
        .filterIsInstance<android.widget.ScrollView>().firstOrNull()

    override fun render(groups: List<RowGroup>) {
        view.removeAllViews()
        rowViews.clear()
        foots.clear()
        groups.forEachIndexed { index, group ->
            if (flat) {
                if (group.label == null && index > 0) view.addView(View(context).apply {
                    setBackgroundColor(Ui.LINE)
                }, LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(12), dp(12), dp(12), dp(8)) })
                group.label?.let { view.addView(Ui.sectionLabel(context, it).apply {
                    setPadding(dp(14), dp(16), dp(14), dp(6))
                }) }
            } else if (group.label != null) view.addView(Ui.sectionLabel(context, group.label).apply {
                setPadding(dp(14), dp(18), dp(14), dp(8))
            }) else view.addView(View(context), LinearLayout.LayoutParams(-1, dp(16)))
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                if (!flat) {
                    background = Ui.rounded(context, Ui.SURFACE, Ui.RADIUS_LARGE)
                    clipToOutline = true
                    dividerDrawable = GradientDrawable().apply {
                        setColor(Ui.LINE)
                        setSize(0, dp(1))
                    }
                    showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
                }
            }
            group.rows.forEach { row ->
                val rowView = RowView(context, host, flat)
                rowViews[row.id] = rowView
                bind(rowView, row)
                card.addView(rowView.root, LinearLayout.LayoutParams(-1, -2))
            }
            view.addView(card, LinearLayout.LayoutParams(-1, -2))
            if (!flat) group.foot?.let { foot ->
                val text = Ui.text(context, foot, Ui.SECONDARY, Ui.TEXT_MUTED).apply {
                    setPadding(dp(14), dp(8), dp(14), 0)
                }
                foots[index] = text
                view.addView(text)
            }
        }
    }

    override fun rebind(groups: List<RowGroup>) {
        groups.forEachIndexed { index, group ->
            group.rows.forEach { row -> rowViews[row.id]?.let { bind(it, row) } }
            group.foot?.let { foots[index]?.text = it }
        }
    }

    override fun rowView(id: String) = rowViews[id]
    override fun revealer(id: String): (() -> Unit)? = null
    override fun edge(direction: String) = false
    override fun position() = scroll?.scrollY ?: 0
    override fun restore(position: Int) { scroll?.let { it.post { it.scrollTo(0, position) } } }

    private fun dp(value: Int) = Ui.dp(context, value)
}
