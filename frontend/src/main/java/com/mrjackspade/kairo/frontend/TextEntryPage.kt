package com.mrjackspade.kairo.frontend

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * One value typed on a page of its own (Kairo#180, #183): the field at the top, so the on-screen
 * keyboard leaves it in view (the app is full screen: the page never shrinks for the keyboard), why
 * a value cannot be taken under it, and [button] in the footer, which the keyboard's Done also
 * presses. [problem] says why the typed text (trimmed) cannot be taken, null when it can; [done] then
 * gets it.
 */
open class TextEntryPage(
    private val title: String,
    private val initial: String,
    private val hint: String,
    private val numeric: Boolean,
    private val button: String,
    private val fieldId: String,
    private val problem: (String) -> String?,
    private val done: (String) -> Unit
) : Page() {
    private lateinit var field: EditText
    private lateinit var message: TextView
    private lateinit var footer: Footer
    override val testName: String get() = title

    override fun createView(): View {
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        page.addView(header(title, null), LinearLayout.LayoutParams(-1, dp(56)))
        val column = MaxWidthColumn(context, dp(640)).apply { setPadding(dp(18), dp(8), dp(18), dp(8)) }
        field = EditText(context).testId(fieldId).apply {
            setText(initial)
            setSelection(text.length)
            this.hint = this@TextEntryPage.hint
            textSize = Ui.BODY
            isSingleLine = true
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT
            // Typed in place: a landscape keyboard's full-screen entry would hide the page
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.TEXT_FAINT)
            background = Ui.rounded(context, Ui.BG, Ui.RADIUS_SMALL, Ui.ACCENT)
            setPadding(dp(12), 0, dp(12), 0)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
            }
        }
        column.addView(field, LinearLayout.LayoutParams(-1, dp(48)))
        message = Ui.text(context, "", Ui.SECONDARY, Ui.DANGER).apply {
            setPadding(dp(4), dp(8), dp(4), 0)
            visibility = View.GONE
        }
        column.addView(message, LinearLayout.LayoutParams(-1, -2))
        page.addView(FrameLayout(context).apply {
            addView(column, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        footer = Footer(this) { listOf(FooterButton("done", { button }, ButtonTone.PRIMARY) { submit() }) }.also {
            it.render()
            page.addView(it.bar, LinearLayout.LayoutParams(-1, -2))
        }
        return page
    }

    /** Takes the typed value, or says in place why it cannot be taken. */
    private fun submit() {
        val text = field.text.toString().trim()
        val reason = problem(text)
        if (reason != null) {
            message.text = reason
            message.visibility = View.VISIBLE
            return
        }
        done(text)
    }

    override fun navItems(): List<NavItem> = listOf(NavItem("field", field, 0, 0.5f, { edit() })) + footer.nav(1)

    override fun onShown(first: Boolean) {
        if (first) edit()
    }

    override fun onHidden() = hideKeyboard()
    override fun onRemoved() = hideKeyboard()

    override fun handleKey(control: String?, event: KeyEvent): Boolean {
        // Typing goes to the field; the controller's directions and buttons still move and press.
        if (field.hasFocus() && control == null) return false
        return super.handleKey(control, event)
    }

    private fun edit() {
        field.requestFocus()
        field.post { keyboard().showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun hideKeyboard() {
        if (::field.isInitialized) keyboard().hideSoftInputFromWindow(field.windowToken, 0)
    }

    private fun keyboard() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
}
