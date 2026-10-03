package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.concurrent.Executors

/** A controller-accessible browser over host-supplied, opaque directory IDs.
 * Filesystem work runs off the UI thread; stale results cannot change the page. */
class DirectoryPicker private constructor(
    private val activity: Activity,
    title: String,
    root: Location,
    private val load: (String) -> List<Entry>,
    private val selected: (Entry) -> Unit,
    private val cancelled: () -> Unit,
    private val rootEntries: List<Entry>
) : AutoCloseable {
    data class Location(val id: String, val label: String)
    data class Entry(val id: String, val label: String, val directory: Boolean)

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "Kairo-file-browser") }
    private val history = mutableListOf(root)
    private var generation = 0
    private var finished = false
    private var entries = emptyList<Entry>()
    private val path = TextView(activity).apply {
        textSize = Ui.LABEL; setTextColor(Ui.ACCENT); maxLines = 2
        setPadding(Ui.dp(activity, 16), Ui.dp(activity, 8), Ui.dp(activity, 16), Ui.dp(activity, 8))
    }
    private val status = TextView(activity).apply {
        textSize = Ui.BODY; setTextColor(Ui.TEXT_MUTED)
        setPadding(Ui.dp(activity, 16), Ui.dp(activity, 8), Ui.dp(activity, 16), Ui.dp(activity, 8))
    }
    private val adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            (super.getView(position, convertView, parent) as TextView).apply {
                textSize = Ui.BODY; setTextColor(Ui.TEXT); maxLines = 2
                background = Ui.rowBackground(activity)
            }
    }
    private val list = ListView(activity).apply {
        adapter = this@DirectoryPicker.adapter
        setOnItemClickListener { _, _, index, _ ->
            entries.getOrNull(index)?.let { entry ->
                if (entry.directory) {
                    history += Location(entry.id, entry.label)
                    refresh()
                } else {
                    finish(false)
                    selected(entry)
                }
            }
        }
    }
    private val dialog = AlertDialog.Builder(activity).setTitle(title)
        .setView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(path); addView(status)
            addView(list, LinearLayout.LayoutParams(-1,
                (activity.resources.displayMetrics.heightPixels * 0.45f).toInt()))
        })
        .setNegativeButton("Cancel", null)
        .setNeutralButton("Up", null)
        .setPositiveButton("Retry", null)
        .create()

    init {
        dialog.setOnDismissListener { finish(true) }
        dialog.show()
        Ui.styleDialog(dialog)
        DialogControllerNavigation.setBackAction(dialog, ::back)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { back() }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { refresh() }
        refresh()
    }

    private fun back() {
        if (history.size == 1) close()
        else { history.removeAt(history.lastIndex); refresh() }
    }

    private fun refresh() {
        val request = ++generation
        val location = history.last()
        path.text = history.joinToString(" / ") { it.label }
        status.text = "Loading files…"
        status.visibility = View.VISIBLE
        entries = emptyList()
        adapter.clear()
        list.isEnabled = false
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility = if (history.size > 1) View.VISIBLE else View.GONE
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.GONE
        worker.execute {
            val result = runCatching { load(location.id).sortedWith(
                compareByDescending<Entry> { it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }) }
            handler.post {
                if (finished || request != generation) return@post
                if (activity.isFinishing || activity.isDestroyed) { close(); return@post }
                result.onSuccess { loaded ->
                    entries = (if (history.size == 1) rootEntries else emptyList()) + loaded
                    adapter.addAll(entries.map { if (it.directory) "${it.label}/" else it.label })
                    list.isEnabled = entries.isNotEmpty()
                    status.text = "No files here"
                    status.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
                    if (entries.isNotEmpty()) { list.requestFocus(); list.setSelection(0) }
                }.onFailure { error ->
                    status.text = error.message ?: "Could not list files"
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.VISIBLE
                }
            }
        }
    }

    private fun finish(notify: Boolean) {
        if (finished) return
        finished = true
        generation++
        worker.shutdownNow()
        dialog.dismiss()
        if (notify) cancelled()
    }

    override fun close() = finish(true)

    companion object {
        fun show(activity: Activity, title: String, root: Location,
                 load: (String) -> List<Entry>, selected: (Entry) -> Unit,
                 cancelled: () -> Unit, rootEntries: List<Entry> = emptyList()): DirectoryPicker =
            DirectoryPicker(activity, title, root, load, selected, cancelled, rootEntries)
    }
}
