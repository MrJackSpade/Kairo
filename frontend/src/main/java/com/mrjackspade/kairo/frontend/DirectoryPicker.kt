package com.mrjackspade.kairo.frontend

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.concurrent.Executors

/**
 * A controller-accessible browser over host-supplied, opaque directory IDs, as a page. Folders
 * open in place; Back goes up a level, then closes. Filesystem work runs off the UI thread, and
 * stale results cannot change the page.
 */
class DirectoryPicker private constructor(
    private val title: String,
    root: Location,
    private val load: (String) -> List<Entry>,
    private val selected: (Entry) -> Unit,
    private val cancelled: () -> Unit,
    private val rootEntries: List<Entry>
) : RowsPage(), AutoCloseable {
    data class Location(val id: String, val label: String)
    data class Entry(val id: String, val label: String, val directory: Boolean)

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "Kairo-file-browser") }
    private val history = mutableListOf(root)
    private var generation = 0
    private var finished = false
    private var loading = true
    private var failure: String? = null
    private var entries = emptyList<Entry>()
    private lateinit var scrollView: ScrollView
    override val scroll: ScrollView get() = scrollView

    override fun groups(): List<RowGroup> {
        failure?.let { message ->
            return listOf(RowGroup(null, listOf(SettingsRow.action("retry", context.getString(R.string.kairo_retry), run = ::reload)), message))
        }
        if (loading) return listOf(RowGroup(null, listOf(SettingsRow.info("loading", context.getString(R.string.kairo_files_loading)) { "" })))
        if (entries.isEmpty()) return listOf(RowGroup(null, listOf(SettingsRow.info("empty", context.getString(R.string.kairo_files_none)) { "" })))
        return listOf(RowGroup(null, entries.mapIndexed { index, entry ->
            if (entry.directory) SettingsRow.nav("entry$index:${entry.id}", entry.label) { open(entry) }
            else SettingsRow.action("entry$index:${entry.id}", entry.label) { open(entry) }
        }))
    }

    override fun createView(): View {
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        page.addView(header(title, path()), LinearLayout.LayoutParams(-1, dp(56)))
        scrollView = PageParts.scroll(this, rowsView(), intArrayOf(dp(18), 0, dp(18), dp(24)))
        page.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    private fun path() = history.joinToString(" › ") { it.label }

    override fun navItems(): List<NavItem> = rowNav(0)

    override fun onShown(first: Boolean) { if (first) reload() }

    private fun open(entry: Entry) {
        if (entry.directory) {
            history += Location(entry.id, entry.label)
            reload()
        } else {
            finish(false)
            selected(entry)
        }
    }

    override fun back(): Boolean {
        if (super.back()) return true
        if (history.size == 1) {
            finish(true)
            return true
        }
        history.removeAt(history.lastIndex)
        reload()
        return true
    }

    private fun reload() {
        val request = ++generation
        val location = history.last()
        scopeChip?.text = path()
        loading = true
        failure = null
        entries = emptyList()
        super.refresh()
        renav(first = true)
        worker.execute {
            val result = runCatching { load(location.id).sortedWith(
                compareByDescending<Entry> { it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }) }
            handler.post {
                if (finished || request != generation) return@post
                if (stack.activity.isFinishing || stack.activity.isDestroyed) { close(); return@post }
                loading = false
                result.onSuccess { loaded ->
                    entries = (if (history.size == 1) rootEntries else emptyList()) + loaded
                }.onFailure { error -> failure = error.message ?: context.getString(R.string.kairo_files_list_failed) }
                super.refresh()
                renav(first = true)
            }
        }
    }

    private fun finish(notify: Boolean) {
        if (finished) return
        finished = true
        generation++
        worker.shutdownNow()
        if (inStack) stack.popTo(this).also { if (stack.top === this) stack.pop() }
        if (notify) cancelled()
    }

    override fun onRemoved() {
        if (!finished) {
            finished = true
            generation++
            worker.shutdownNow()
            cancelled()
        }
    }

    override fun close() = finish(true)

    companion object {
        fun show(pages: PageStack, title: String, root: Location,
                 load: (String) -> List<Entry>, selected: (Entry) -> Unit,
                 cancelled: () -> Unit, rootEntries: List<Entry> = emptyList()): DirectoryPicker =
            DirectoryPicker(title, root, load, selected, cancelled, rootEntries).also(pages::push)
    }
}
