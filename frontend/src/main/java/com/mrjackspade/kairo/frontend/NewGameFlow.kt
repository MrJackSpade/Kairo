package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout

/**
 * Runs an app's [NewGameWorkflow] (Kairo#181): one page for each of its pages shown for the choices so
 * far, then the name, each with Next (Create on the name) in its footer and Back to the page before.
 * What the player chose is kept while they go back and forth; a page shown again lists what fits
 * the choices before it. Nothing is written until Create, which closes the workflow and writes the
 * game ([CreateGameFlow.create]); leaving it writes nothing.
 */
class NewGameFlow<T : LibraryItem>(
    private val activity: Activity,
    private val pages: PageStack,
    private val library: LibraryFlow<T>,
    private val workflow: NewGameWorkflow,
    /** What the games folder is called, in messages. */
    private val folder: String,
    /** The workflow opened (true) or closed (false), as a two-screen device's lower screen follows it (Kairo#179). */
    private val shown: (Boolean) -> Unit = {}
) {
    private val chosen = HashMap<String, String>()
    private val values: Map<String, String> get() = workflow.resolve(chosen)
    /** The workflow's first page, which Create closes along with every page above it. */
    private var first: Page? = null

    /** Starts the workflow, from the beginning. */
    fun open() {
        val tree = library.tree
        if (tree == null) {
            Ui.message(activity, activity.getString(R.string.kairo_library_choose_in_manage))
            return
        }
        if (!library.hasWriteGrant(tree)) {
            pages.push(ModalPage(null, activity.getString(R.string.kairo_create_grant, folder), listOf(
                ModalButton(activity.getString(R.string.kairo_cancel)),
                ModalButton(activity.getString(R.string.kairo_choose_named, folder), ButtonTone.PRIMARY) { library.chooseFolder() }),
                initialButton = 1))
            return
        }
        chosen.clear()
        first = null
        show(0)
    }

    private fun show(index: Int) {
        val shown = workflow.shownPages(values)
        val page = if (index < shown.size) choicesPage(shown[index], index) else NamePage()
        if (first == null) {
            first = page
            this.shown(true)
        }
        pages.push(page)
    }

    /** The label of a step's primary button: Next, or Create on the last. */
    private fun nextLabel() = activity.getString(R.string.kairo_next)

    private fun choicesPage(page: WorkflowPage, index: Int): Page = StepPage(page.title.resolve(activity), {
        val now = values
        listOf(RowGroup(null, page.fields.mapNotNull { field -> row(field, now) }))
    }, { listOf(FooterButton("next", ::nextLabel, ButtonTone.PRIMARY) { show(index + 1) }) })

    /** The workflow's first page went (Back from it, or Create): the workflow is over. */
    private fun removed(page: Page) {
        if (page !== first) return
        first = null
        shown(false)
    }

    private inner class StepPage(title: String, content: () -> List<RowGroup>, footer: () -> List<FooterButton>) :
        GroupsPage(title, { null }, content, footer) {
        override fun onRemoved() = removed(this)
    }

    private fun row(field: WorkflowField, now: Map<String, String>): SettingsRow? = when (field) {
        is WorkflowField.Select -> choiceRow(field.id, field.label, { field.choices(values) })
        is WorkflowField.Range -> choiceRow(field.id, field.label, {
            field.range(values).values.map { ChoiceOption(it.toString(), field.labelOf(it)) }
        })
        // Text is typed on a page of its own, where the keyboard leaves it in view: only the name page has one.
        is WorkflowField.Text -> error("${field.id}: a text field goes on the name page only")
    }.takeIf { now.containsKey(field.id) }

    private fun choiceRow(id: String, label: UiText, options: () -> List<ChoiceOption>): SettingsRow {
        val title = label.resolve(activity)
        return SettingsRow.choice("new-$id", title, ChoiceSpec(title, { null }, { groups(options()) }, { values[id] }, select = { picked ->
            chosen[id] = picked
            pages.refresh()
        }))
    }

    /** Options in folders (a processor's family) open as folders; the rest list as they are. */
    private fun groups(options: List<ChoiceOption>): List<ChoiceGroup> {
        val folders = options.mapNotNull { it.folder }.distinct().map { name ->
            ChoiceFolder(name, name) { listOf(ChoiceGroup(null, options.filter { it.folder == name })) }
        }
        return listOf(ChoiceGroup(null, options.filter { it.folder == null }, null, folders))
    }

    private fun create(name: String) {
        val trimmed = name.trim()
        if (!CreateGameFlow.usable(trimmed)) {
            Ui.message(activity, activity.getString(R.string.kairo_create_bad_name))
            return
        }
        chosen[NewGameWorkflow.NAME] = trimmed
        val blank = workflow.create(values)
        first?.let(pages::popThrough)
        val made = values
        CreateGameFlow.create(activity, library, blank, trimmed, folder) { path -> workflow.created(made, path) }
    }

    /**
     * The name: a field at the top of its own page, so the on-screen keyboard leaves it in view, and
     * Create in the footer, which the field's Done also presses.
     */
    private inner class NamePage : Page() {
        private lateinit var field: EditText
        private lateinit var footer: Footer
        override val testName: String get() = "new-game-name"

        override fun createView(): View {
            val page = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.BG)
            }
            page.addView(header(activity.getString(R.string.kairo_create_name_title), null), LinearLayout.LayoutParams(-1, dp(56)))
            val column = MaxWidthColumn(context, dp(640)).apply { setPadding(dp(18), dp(8), dp(18), dp(8)) }
            field = EditText(context).testId("new-game.name").apply {
                setText(chosen[NewGameWorkflow.NAME] ?: activity.getString(R.string.kairo_create_name_default))
                setSelection(text.length)
                hint = activity.getString(R.string.kairo_create_name_hint)
                textSize = Ui.BODY
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT
                // Typed in place: a landscape keyboard's full-screen entry would hide the page
                imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                setTextColor(Ui.TEXT)
                setHintTextColor(Ui.TEXT_FAINT)
                background = Ui.rounded(context, Ui.BG, Ui.RADIUS_SMALL, Ui.ACCENT)
                setPadding(dp(12), 0, dp(12), 0)
                setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_DONE) { create(text.toString()); true } else false
                }
            }
            column.addView(field, LinearLayout.LayoutParams(-1, dp(48)))
            page.addView(android.widget.FrameLayout(context).apply {
                addView(column, android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            footer = Footer(this) { listOf(FooterButton("create", { activity.getString(R.string.kairo_create) }, ButtonTone.PRIMARY) {
                create(field.text.toString())
            }) }.also {
                it.render()
                page.addView(it.bar, LinearLayout.LayoutParams(-1, -2))
            }
            return page
        }

        override fun navItems(): List<NavItem> =
            listOf(NavItem("name", field, 0, 0.5f, { editName() })) + footer.nav(1)

        override fun onShown(first: Boolean) {
            if (first) editName()
        }

        override fun onHidden() = hideKeyboard()
        override fun onRemoved() {
            hideKeyboard()
            removed(this)
        }

        override fun handleKey(control: String?, event: KeyEvent): Boolean {
            // Typing goes to the field; the controller's directions and buttons still move and press.
            if (field.hasFocus() && control == null) return false
            return super.handleKey(control, event)
        }

        private fun editName() {
            field.requestFocus()
            field.post { keyboard().showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) }
        }

        private fun hideKeyboard() {
            if (::field.isInitialized) keyboard().hideSoftInputFromWindow(field.windowToken, 0)
        }

        private fun keyboard() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }
}
