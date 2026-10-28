package com.mrjackspade.kairo.frontend

import com.mrjackspade.kairo.frontend.PixelTextView
import com.mrjackspade.kairo.frontend.Ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * The part of a file name that tells revisions of one game apart: its bracketed and
 * parenthesized tags, such as a translation or bug-fix credit.
 */
fun variantLabel(name: String): String? =
    variantTags(name).takeIf { it.isNotEmpty() }?.joinToString("  ·  ")

private val VARIANT_TAG = Regex("""[\[(]([^\])]+)[\])]""")

/** The tags [variantLabel] joins, one per bracketed or parenthesized part of the name. */
fun variantTags(name: String): List<String> =
    VARIANT_TAG.findAll(name.substringAfterLast('/'))
        .map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().toList()

/**
 * Where a library entry lives: its folder and file name without the extension, plus the image
 * inside a ZIP when that name differs and [inner] asks for it. Keep this filename below the
 * catalog title: different revisions can share a catalog title. Do not replace it with the
 * title or a media label.
 */
fun fileLabel(entry: LibraryItem, inner: Boolean = true): String {
    fun stem(name: String) = name.substringBeforeLast('.').takeIf { name.contains('.') } ?: name
    val file = stem(entry.path)
    val image = entry.zipEntry?.takeIf { inner }?.substringAfterLast('/')?.let(::stem)
    return if (image == null || image == file.substringAfterLast('/')) file else "$file  ›  $image"
}

/** Library landing page. A short tap or A opens the selected game's page. */
class LibraryScreen<T : LibraryItem>(
    context: Context,
    private val catalog: LibraryCatalog,
    val strings: LibraryStrings,
    private val pages: PageStack,
    private val chooseFolder: () -> Unit,
    private val lastPlayedId: () -> String?,
    private val play: (T) -> Unit,
    private val preview: (T) -> Unit,
    private val details: (T) -> Unit,
    private val selectionChanged: (T?) -> Unit,
    private val commandLaunch: ((T) -> Unit)? = null
) : FrameLayout(context), FocusRegion {
    /** The game whose page is open, or null once it closed: a lower screen shows its settings. */
    var detailOpened: (T?) -> Unit = {}
    /** Whether another screen shows the open game's settings, so its page has no Game settings. */
    var settingsElsewhere: () -> Boolean = { false }
    private var reportedSelection: String? = "none"
    private var catalogEntries = emptyList<T>()
    /** The latest status, shown in the empty state and the library flyout. */
    var status: String = ""
        private set
    private var statusKind = StatusKind.MESSAGE
    /** The selected folder's name, or null when there is none. */
    var folderName: String? = null
        private set
    /** Set by the library flow: true when there is no folder or its access expired. */
    var needsFolder: () -> Boolean = { false }
    /** Opens the library flyout. */
    var openMenuAction: () -> Unit = {}
    /** A new entry list is on screen; [showEntries] publishes it from a worker. */
    var entriesPublished: () -> Unit = {}
    private val list = ListView(context).testId("library.list")
    private val emptyState = LinearLayout(context)
    private val emptyText = Ui.text(context, "", Ui.BODY, Ui.TEXT_MUTED).testId("library.status")
    private val emptyButton = Ui.primaryButton(context, context.getString(R.string.kairo_choose_named, strings.folder.resolve(context))) {
        chooseFolder()
    }.testId("library.choose-folder")
    private val scanProgress = Ui.spinner(context, 24, 3).testId("library.scanning")
    val tasks = TaskStrip(context)
    private var detailPage: GameDetailPage<T>? = null
    private val menuButton = Ui.iconButton(context, R.drawable.ic_menu, context.getString(R.string.kairo_library_menu)) {
        openMenu()
    }.testId("library.menu")
    private val searchButton = Ui.iconButton(context, R.drawable.ic_search, context.getString(R.string.kairo_library_search)) {
        toggleSearch()
    }.testId("library.search")
    /** Header focus: 0 menu, 1 search, 2 the task strip's Stop. -1 is the game list. */
    private var headerSelection = -1
    private var lastHeaderSelection = 0
    private val searchRow = LinearLayout(context)
    private val search = object : android.widget.EditText(context) {
        override fun onKeyPreIme(keyCode: Int, event: android.view.KeyEvent): Boolean {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && hasFocus()) {
                if (event.action == android.view.KeyEvent.ACTION_UP) dismissSearchKeyboard()
                return true
            }
            return super.onKeyPreIme(keyCode, event)
        }
    }.testId("library.search.field")
    val searchFocused: Boolean get() = search.hasFocus()
    /**
     * Search typed on another screen (a dual-screen device's lower screen), so the keyboard leaves
     * the results visible: given the query, where its changes go and what runs when the player
     * finishes, it is true when that screen took the search. [endSearchElsewhere] closes it.
     */
    var searchElsewhere: ((String, (String) -> Unit, () -> Unit) -> Boolean)? = null
    var endSearchElsewhere: (() -> Unit)? = null
    private var allEntries = emptyList<T>()
    /** Sources with more than one visible entry; only their rows name the image inside. */
    private var sharedSources = emptySet<String>()
    // A library snapshot can span far more catalog shards than either backend's
    // small shard cache. Retain the resolved row records, not entire shard JSON.
    private val rowMetadata = HashMap<String, LibraryGame>()
    private val searchIndex = LibrarySearchIndex<T>(catalog, { action -> post(action) }) { records ->
        rowMetadata.putAll(records)
        adapter.notifyDataSetChanged()
        if (entries.getOrNull(selectedIndex)?.id in records) reannounceSelection()
        detailPage?.let { page ->
            if (page.entryId in records) allEntries.firstOrNull { it.id == page.entryId }?.let(page::show)
        }
    }
    private var searchSnapshot = 0L
    /** Reuse the row snapshot for selection previews instead of rereading catalog shards. */
    // UI rendering and D-pad selection must never wait for the catalog's worker lock.
    fun metadata(entry: T): LibraryGame = rowMetadata[entry.id] ?: object : LibraryGame {
        override val title = entry.displayName
        override val description: String? = null
        override val boxArt: String? = null
        override val preview: String? = null
        override val tags = emptyList<String>()
    }
    private var visibilityExecutor: java.util.concurrent.ExecutorService? = null
    @Volatile private var entriesGeneration = 0L
    private var pinnedId: String? = null
    val detailOpen: Boolean get() = detailPage?.inStack == true
    private val artCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val artExecutor = Executors.newSingleThreadExecutor()
    private val pendingArt = HashSet<String>()
    private val missingArt = HashSet<String>()
    private var entries = emptyList<T>()
    private var selectedIndex = 0
    private var selectionVisibilityPending = false

    private sealed interface ListItem
    private data class Header(val label: String, val pinned: Boolean) : ListItem
    private data class Game(val index: Int) : ListItem
    private var items = emptyList<ListItem>()

    private data class Row(val art: ImageView, val mark: TextView,
                           val title: TextView, val detail: TextView,
                           var index: Int = -1, var pinned: Boolean? = null,
                           var artwork: String? = null)

    private fun positionOf(index: Int) = items.indexOfFirst { it is Game && it.index == index }
    private fun indexAt(position: Int) = (items.getOrNull(position) as? Game)?.index

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = when (val item = items[position]) {
            is Game -> entries[item.index].id.hashCode().toLong()
            is Header -> item.label.hashCode().toLong()
        }
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (items[position] is Header) 1 else 0
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int) = items[position] is Game
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            when (val item = items[position]) {
                is Header -> headerView(item, convertView)
                is Game -> gameView(item.index, convertView)
            }
    }

    private fun headerView(item: Header, convertView: View?): View {
        val view = convertView as? LinearLayout ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(2))
            addView(Ui.icon(context, R.drawable.ic_pin, Ui.PIN, 16).apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(6)
            })
            addView(PixelTextView(context))
        }
        view.getChildAt(0).visibility = if (item.pinned) View.VISIBLE else View.GONE
        (view.getChildAt(1) as PixelTextView).apply {
            text = item.label
            color = if (item.pinned) Ui.PIN else Ui.ACCENT
        }
        return view
    }

    private fun gameView(index: Int, convertView: View?): View {
        val view: LinearLayout
        val holder: Row
        if (convertView is LinearLayout && convertView.tag is Row) {
            view = convertView
            holder = view.tag as Row
        } else {
            view = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                minimumHeight = dp(80)
            }
            val cover = FrameLayout(context).apply {
                background = Ui.rounded(context, Ui.RAISED, 4)
                clipToOutline = true
            }
            val art = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            val mark = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextColor(Ui.ACCENT_SOFT)
                textSize = Ui.TITLE
            }
            cover.addView(art, FrameLayout.LayoutParams(-1, -1))
            cover.addView(mark, FrameLayout.LayoutParams(-1, -1))
            view.addView(cover, LinearLayout.LayoutParams(dp(48), dp(64)))
            val text = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, 0, 0)
            }
            val title = Ui.text(context, "", Ui.BODY).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val detail = Ui.text(context, "", Ui.SECONDARY, Ui.TEXT_MUTED).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            text.addView(title.testId("title"))
            text.addView(detail.testId("detail"))
            view.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            holder = Row(art, mark, title, detail)
            view.tag = holder
        }
        val entry = entries[index]
        TestAttributes.mark(view, "library.entry", entry.id)
        val pinned = index == 0 && pinnedId != null && items.firstOrNull() is Header
        val game = metadata(entry)
        holder.title.text = game.title
        // Keep the source filename intact; the marker is only a prefix on its display row.
        val file = (if ("♥" in game.tags) "♥ " else "") + fileLabel(entry, entry.uri in sharedSources)
        holder.detail.text = entry.error?.let { context.getString(R.string.kairo_library_entry_error, file, it) }
            ?: file
        holder.detail.setTextColor(if (entry.error != null) Ui.DANGER else Ui.TEXT_MUTED)
        val artwork = listOfNotNull(game.boxArt, game.preview).takeIf { it.isNotEmpty() }
            ?.joinToString("\n")
        val bitmap = artwork?.let(::loadArt)
        holder.art.visibility = if (bitmap == null) View.GONE else View.VISIBLE
        holder.mark.visibility = if (bitmap == null) View.VISIBLE else View.GONE
        holder.art.setImageBitmap(bitmap)
        holder.mark.text = game.title.firstOrNull()?.uppercase() ?: "?"
        holder.index = index
        holder.artwork = artwork
        if (holder.pinned != pinned) {
            view.background = if (pinned) Ui.rowBackground(context, Ui.PIN_SURFACE, Ui.PIN, Ui.PIN_SELECTED, activatedOnly = true)
                else Ui.rowBackground(context, activatedOnly = true)
            holder.pinned = pinned
        }
        view.isActivated = headerSelection < 0 && index == selectedIndex && !focusAway
        view.alpha = if (entry.playable) 1f else 0.6f
        return view
    }

    init {
        setBackgroundColor(Ui.BG)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), 0)
        }
        addView(body, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(menuButton,
            LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(10) })
        header.addView(PixelTextView(context).apply {
            text = strings.productName
            scale = 2
            brand = true
            testId("library.brand")
        }, LinearLayout.LayoutParams(0, -2, 1f))
        scanProgress.visibility = View.GONE
        header.addView(scanProgress, LinearLayout.LayoutParams(dp(24), dp(24)).apply {
            marginEnd = dp(6)
        })
        header.addView(searchButton,
            LinearLayout.LayoutParams(dp(48), dp(48)))
        body.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        search.apply {
            visibility = View.GONE
            hint = context.getString(R.string.kairo_search_hint)
            textSize = Ui.BODY
            isSingleLine = true
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.TEXT_FAINT)
            background = Ui.rounded(context, Ui.SURFACE, Ui.RADIUS_SMALL, Ui.LINE)
            setPadding(dp(14), 0, dp(14), 0)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, action, event ->
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                    action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    dismissSearchKeyboard()
                    true
                } else false
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(text: android.text.Editable?) { applyFilter() }
            })
        }
        searchRow.visibility = View.GONE
        searchRow.addView(search, LinearLayout.LayoutParams(-1, dp(44)))
        body.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        body.addView(tasks, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        val gameArea = FrameLayout(context)
        list.apply {
            viewTreeObserver.addOnPreDrawListener {
                if (!selectionVisibilityPending || !isShown) true
                else {
                    // Several repeats can arrive before ListView applies a requested
                    // scroll. Recheck the latest selection against the resulting rows,
                    // and do not draw a stale scroll with the selection clipped away.
                    val visible = keepSelectionVisible()
                    if (visible) {
                        selectionVisibilityPending = false
                        updateSelectionHighlight()
                    }
                    visible
                }
            }
            divider = ColorDrawable(Color.TRANSPARENT)
            dividerHeight = dp(4)
            selector = ColorDrawable(Color.TRANSPARENT)
            // The library moves its own selection; the list must not keep a second one.
            isFocusable = false
            itemsCanFocus = false
            setPadding(0, 0, 0, dp(12))
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            adapter = this@LibraryScreen.adapter
            setOnItemClickListener { _, _, position, _ ->
                val index = indexAt(position) ?: return@setOnItemClickListener
                selectedIndex = index
                focusGames()
                notifySelection()
                openDetail(entries[index])
            }
            setOnItemLongClickListener { _, _, position, _ ->
                val index = indexAt(position) ?: return@setOnItemLongClickListener false
                selectedIndex = index
                focusGames()
                notifySelection()
                // Game settings opens over the game page, so Play is one Back away.
                openDetail(entries[index])
                details(entries[index])
                true
            }
        }
        gameArea.addView(list, FrameLayout.LayoutParams(-1, -1))
        emptyState.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(40), dp(20), dp(40))
            visibility = View.GONE
        }
        emptyText.gravity = Gravity.CENTER
        Ui.oneLineKeepingEnd(emptyText)
        emptyState.addView(emptyText, LinearLayout.LayoutParams(-1, -2))
        emptyState.addView(emptyButton, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(16) })
        emptyButton.setPadding(dp(20), 0, dp(20), 0)
        gameArea.addView(emptyState, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        body.addView(gameArea, LinearLayout.LayoutParams(-1, 0, 1f))
        pages.onActiveChanged { active -> if (!active && visibility == View.VISIBLE) restoreFocus() }
    }

    /** The folder name for the flyout title, or null when no folder is selected. */
    fun showFolder(name: String?) {
        folderName = name
        updateEmptyState()
    }

    fun refreshCatalog() {
        searchIndex.forgetMetadata()
        artCache.evictAll()
        missingArt.clear()
        showEntries(catalogEntries)
        detailPage?.refreshArtwork()
    }

    /** One game's record changed, as after editing its title, artwork or settings. */
    fun metadataChanged(game: GameKey) {
        rowMetadata.keys.remove(game.path)
        searchIndex.forget(listOf(game.path))
        showEntries(catalogEntries)
    }

    /** Newly downloaded images: retry rows that had none. Records name paths, not files. */
    fun refreshArtwork() {
        missingArt.clear()
        adapter.notifyDataSetChanged()
        detailPage?.refreshArtwork()
    }

    /** Whether a scan, hash, or launch is running, which the header spinner shows. */
    val working: Boolean get() = statusKind == StatusKind.WORK

    /** What a status is, which decides where it shows. */
    enum class StatusKind {
        /** Shown in the empty state; it also reaches the message bar while the library lists games. */
        MESSAGE,
        /** A scan's result, such as "7 games": shown in the empty state and the flyout only. */
        SUMMARY,
        /** Work the task strip and the header spinner show, such as a scan. */
        WORK,
        /** Work only the task strip shows, such as deleting a file. */
        TASK
    }

    /**
     * Progress goes to the task strip and the header spinner. Other text becomes the status
     * the empty state shows, and errors also reach the message bar.
     */
    fun showStatus(message: String, kind: StatusKind = StatusKind.MESSAGE) {
        status = message
        statusKind = kind
        val busy = kind == StatusKind.WORK || kind == StatusKind.TASK
        scanProgress.visibility = if (working) View.VISIBLE else View.GONE
        if (busy) tasks.show("status", message, 1) else tasks.hide("status")
        updateEmptyState()
        if (!busy && entries.isNotEmpty() && message.isNotEmpty() && kind != StatusKind.SUMMARY)
            (context as? android.app.Activity)?.let { Ui.message(it, message, long = true) }
        // An open page's rows can depend on whether a task runs; the flyout's status line follows
        // each message by itself (its tick). A scan sends a message per file, and rebuilding the
        // open page for each one kept the main thread busy for most of a scan.
        if (busy != pagesBusy || !busy) {
            pagesBusy = busy
            pages.refresh()
        }
    }

    /** Whether the open page was last refreshed while a task ran. */
    private var pagesBusy = false

    private fun updateEmptyState() {
        if (entries.isNotEmpty()) {
            emptyState.visibility = View.GONE
            return
        }
        val query = search.text.toString().trim()
        emptyText.text = if (query.isNotEmpty() && allEntries.isNotEmpty()) context.getString(R.string.kairo_library_no_match, query)
            else status
        emptyButton.visibility = if (query.isEmpty() && needsFolder()) View.VISIBLE else View.GONE
        emptyState.visibility = if (emptyText.text.isNullOrEmpty() && emptyButton.visibility != View.VISIBLE)
            View.GONE else View.VISIBLE
    }

    fun showEntries(items: List<T>) {
        catalogEntries = items
        val generation = ++entriesGeneration
        // Invalidate pending metadata results, retaining the displayed snapshot while
        // the replacement's visibility flags are read on a worker.
        searchSnapshot++
        searchIndex.replace(emptyList())
        val worker = visibilityExecutor ?: Executors.newSingleThreadExecutor().also { visibilityExecutor = it }
        worker.execute {
            // Until the catalog has been read for these entries, a library with nothing on screen
            // shows them with the entries the last pass hid left out, so a large library appears
            // at once at startup, while its folder is checked (Kairo#46).
            if (allEntries.isEmpty() && items.isNotEmpty()) {
                val lastHidden = hiddenSnapshot.read()
                if (lastHidden != null) {
                    val quick = items.filter { it.holdsGame && it.id !in lastHidden }
                    post { if (generation == entriesGeneration && allEntries.isEmpty()) publishEntries(quick) }
                }
            }
            val started = android.os.SystemClock.elapsedRealtime()
            val shown = entryPoints(items, catalog) {
                generation != entriesGeneration || Thread.currentThread().isInterrupted
            } ?: return@execute
            val hidden = items.filterNot { it.id in shown }.mapTo(HashSet()) { it.id }
            val visible = items.filter { it.id in shown }
            android.util.Log.i("KairoLibrary", "Shown: ${visible.size} of ${items.size} entries in " +
                "${android.os.SystemClock.elapsedRealtime() - started} ms")
            hiddenSnapshot.write(hidden)
            post {
                if (generation == entriesGeneration) publishEntries(visible)
            }
        }
    }

    /** The entries the last visibility pass hid, by id, kept for the next start. */
    private val hiddenSnapshot = HiddenEntries(java.io.File(context.filesDir, "library-hidden-v1.txt"))

    private fun publishEntries(visible: List<T>) {
        val previous = allEntries.associateBy { it.id }
        val current = visible.associateBy { it.id }
        allEntries = visible
        sharedSources = visible.groupingBy { it.uri }.eachCount().filterValues { it > 1 }.keys
        // A record belongs to an entry only while its content and name are unchanged.
        rowMetadata.keys.retainAll { id ->
            val before = previous[id]
            val after = current[id]
            before != null && after != null &&
                before.contentId == after.contentId && before.catalogId == after.catalogId &&
                    before.path == after.path &&
                    before.zipEntry == after.zipEntry
        }
        pinnedId = currentPinnedId()
        reportedSelection = "none"
        resetSearchIndex()
        val query = search.text.toString().trim()
        // Until the search reruns, results must not offer games that left the library.
        if (query.isNotEmpty()) publishFilter(query, entries.mapNotNull { current[it.id] }, null)
        applyFilter()
        if (entries.isNotEmpty()) list.setSelection(if (selectedIndex == 0) 0 else positionOf(selectedIndex))
        detailPage?.let { page ->
            val refreshed = allEntries.firstOrNull { it.id == page.entryId }
            if (refreshed == null) closeDetail() else page.show(refreshed)
        }
        entriesPublished()
    }
    /** Used by the debug ADB selector after cached entries or a scan arrive. */
    fun selectGame(query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return false
        if (search.text.isNotEmpty()) search.setText("")
        val index = entries.indexOfFirst { it.displayName.equals(needle, ignoreCase = true) }
            .takeIf { it >= 0 }
            ?: entries.indexOfFirst {
                metadata(it).title.equals(needle, ignoreCase = true)
            }.takeIf { it >= 0 }
            ?: entries.indices.filter { entries[it].displayName.contains(needle, ignoreCase = true) }
                .singleOrNull() ?: -1
        if (index < 0) return false
        selectedIndex = index
        list.setSelection(positionOf(index))
        updateSelectionHighlight()
        openDetail(entries[index])
        return true
    }

    /** Debug launch requires an unambiguous library entry. A full content ID
     * can select a particular patch revision when names overlap. */
    fun findGameForDebugLaunch(query: String): T? {
        val needle = query.trim()
        if (needle.isEmpty()) return null
        val playable = allEntries.filter { it.playable }
        fun unique(matches: List<T>): T? =
            matches.firstOrNull()?.takeIf {
                matches.all { other -> other.contentId == it.contentId }
            }
        return unique(playable.filter { it.contentId?.equals(needle, ignoreCase = true) == true })
            ?: unique(playable.filter { it.displayName.equals(needle, ignoreCase = true) })
            ?: unique(playable.filter {
                metadata(it).title.equals(needle, ignoreCase = true)
            })
            ?: unique(playable.filter { it.displayName.contains(needle, ignoreCase = true) })
    }
    fun moveSelection(delta: Int) {
        if (headerSelection >= 0) {
            if (delta > 0) {
                if (headerSelection < 2 && tasks.canStop) focusHeader(2) else focusGames()
            } else if (headerSelection == 2) focusHeader(lastHeaderSelection)
            return
        }
        if (delta < 0 && (entries.isEmpty() || selectedIndex == 0)) {
            focusHeader(if (tasks.canStop) 2 else lastHeaderSelection)
            return
        }
        if (entries.isEmpty()) return
        val next = (selectedIndex + delta).coerceIn(0, entries.lastIndex)
        if (next == selectedIndex) {
            requestSelectionVisibility()
            if (delta > 0) crossEdge("down")
            return
        }
        selectedIndex = next
        updateSelectionHighlight()
        requestSelectionVisibility()
        notifySelection()
    }

    private fun focusHeader(index: Int) {
        headerSelection = if (index == 2 && tasks.canStop) 2 else index.coerceIn(0, 1)
        if (headerSelection < 2) lastHeaderSelection = headerSelection
        menuButton.isSelected = headerSelection == 0
        searchButton.isSelected = headerSelection == 1
        tasks.stopButton.isSelected = headerSelection == 2
        emptyButton.isSelected = false
        updateSelectionHighlight()
    }

    private fun focusGames() {
        headerSelection = -1
        menuButton.isSelected = false
        searchButton.isSelected = false
        tasks.stopButton.isSelected = false
        emptyButton.isSelected = entries.isEmpty() && emptyButton.visibility == View.VISIBLE
        isFocusableInTouchMode = true
        requestFocus()
        updateSelectionHighlight()
        requestSelectionVisibility()
    }

    /** Back on the library after its pages close: the game list or header as it was. */
    private fun restoreFocus() {
        if (headerSelection >= 0) focusHeader(headerSelection) else focusGames()
    }

    fun moveHeaderSelection(delta: Int) {
        if (headerSelection in 0..1) focusHeader(headerSelection + delta)
    }

    fun leaveHeader(): Boolean {
        if (headerSelection < 0) return false
        focusGames()
        return true
    }

    fun leaveSearch(up: Boolean) {
        dismissSearchKeyboard()
        if (up) focusHeader(1)
    }

    private fun requestSelectionVisibility() {
        selectionVisibilityPending = true
        keepSelectionVisible()
        list.invalidate()
    }

    /** Selection only changes activation, not row data. Rebinding the whole viewport
     * performs catalog IO/text layout for every visible game on every repeat event. */
    /** The other screen takes the focus only from the last game, so only the game list shows it away. */
    override var focusAway = false
        set(value) {
            field = value
            updateSelectionHighlight()
        }
    override var crossEdge: (String) -> Boolean = { false }
    /** Back from the other screen: the game it left from. */
    override fun enter(direction: String) = requestSelectionVisibility()

    private fun updateSelectionHighlight() {
        for (index in 0 until list.childCount) {
            val child = list.getChildAt(index)
            val row = child.tag as? Row ?: continue
            child.isActivated = headerSelection < 0 && row.index == selectedIndex && !focusAway
        }
    }

    private fun keepSelectionVisible(): Boolean {
        if (list.height == 0 || entries.isEmpty()) return true
        // Section headers are not selectable; visibility belongs to the game row.
        val selectedPosition = positionOf(selectedIndex)
        val first = list.firstVisiblePosition
        val last = list.lastVisiblePosition
        val viewportBottom = list.height - list.paddingBottom
        val child = list.getChildAt(selectedPosition - first)
        // A row taller than the available viewport can only be top-aligned.
        if (child != null && child.height > viewportBottom - list.paddingTop &&
            child.top == list.paddingTop) return true
        when {
            // setSelectionFromTop adds list padding itself: offsets are relative
            // to the padded origin, unlike child.top/bottom coordinates.
            selectedPosition < first -> list.setSelectionFromTop(selectedPosition, 0)
            selectedPosition > last -> {
                val rowHeight = list.getChildAt(last - first)?.height ?: dp(75)
                list.setSelectionFromTop(selectedPosition,
                    (viewportBottom - rowHeight - list.paddingTop).coerceAtLeast(0))
            }
            child != null && child.top < list.paddingTop ->
                list.setSelectionFromTop(selectedPosition, 0)
            child != null && child.bottom > viewportBottom ->
                list.setSelectionFromTop(selectedPosition,
                    (viewportBottom - child.height - list.paddingTop).coerceAtLeast(0))
            else -> return true
        }
        return false
    }
    fun activateSelection() {
        when (headerSelection) {
            0 -> menuButton.performClick()
            1 -> searchButton.performClick()
            2 -> tasks.stopButton.performClick()
            else -> if (entries.isEmpty()) {
                if (emptyButton.visibility == View.VISIBLE) emptyButton.performClick()
            } else entries.getOrNull(selectedIndex)?.let(::openDetail)
        }
    }

    fun openMenu() = openMenuAction()

    /** Menu on the library pages: the game page opens Game settings; the flyout closes. */
    fun menuKey() {
        val top = pages.top
        when {
            pages.flyoutOpen -> pages.closeAll()
            top is GameDetailPage<*> && top === detailPage -> detailPage?.current?.let(details)
        }
    }

    fun openDetail(entry: T) {
        detailPage?.let { if (it.inStack) { it.show(entry); detailOpened(entry); return } }
        val page = GameDetailPage(catalog, play, preview, details, ::metadata, commandLaunch, settingsElsewhere) {
            if (detailPage === it) {
                detailPage = null
                detailOpened(null)
            }
        }
        detailPage = page
        pages.push(page)
        page.show(entry)
        detailOpened(entry)
    }

    /** Another screen started or stopped showing game settings. */
    fun settingsMoved() { detailPage?.takeIf { it.inStack }?.settingsMoved() }

    /** Close the game page and anything above it, as after deleting its file. */
    fun closeDetail(): Boolean {
        val page = detailPage?.takeIf { it.inStack } ?: return false
        pages.popTo(page)
        pages.pop()
        return true
    }

    /** Drop library search focus before a guest session takes over the window. */
    fun dismissSystemKeyboard() {
        endSearchElsewhere?.invoke()
        search.clearFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(search.windowToken, 0)
    }

    /** Keep the query and results; only end Android text entry. */
    fun dismissSearchKeyboard(): Boolean {
        if (!search.hasFocus()) return false
        // Give focus a stable destination so Android cannot immediately refocus the editor.
        focusGames()
        dismissSystemKeyboard()
        return true
    }

    private fun toggleSearch() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        if (search.visibility == View.VISIBLE) {
            endSearchElsewhere?.invoke()
            search.setText("")
            search.visibility = View.GONE
            searchRow.visibility = View.GONE
            imm.hideSoftInputFromWindow(search.windowToken, 0)
        } else {
            searchRow.visibility = View.VISIBLE
            search.visibility = View.VISIBLE
            if (searchElsewhere?.invoke(search.text.toString(), { query -> search.setText(query) }, ::focusGames) == true) return
            search.requestFocus()
            imm.showSoftInput(search, 0)
        }
    }

    private fun applyFilter() {
        val query = search.text.toString().trim()
        if (query.isNotEmpty()) {
            searchIndex.search(query) { matches -> publishFilter(query, matches, null) }
            return
        }
        searchIndex.cancelQuery()
        // CONTINUE repeats the last-played game; it keeps its place in ALL GAMES.
        val pinned = pinnedId?.let { id -> allEntries.firstOrNull { it.id == id } }
        publishFilter(query, pinned?.let { game -> listOf(game) + allEntries } ?: allEntries, pinned)
    }

    private fun currentPinnedId(): String? =
        lastPlayedId()?.takeIf { id -> allEntries.any { it.id == id } }

    /** Re-reads the last-played game, as when the library returns after a session. */
    fun refreshPin() {
        val id = currentPinnedId()
        if (id == pinnedId) return
        pinnedId = id
        applyFilter()
    }

    private fun resetSearchIndex() {
        searchIndex.replace(allEntries)
        val generation = ++searchSnapshot
        postOnAnimation { post {
            if (generation == searchSnapshot && isAttachedToWindow) searchIndex.warm()
        } }
    }

    private fun publishFilter(query: String, matches: List<T>, pinned: T?) {
        val selectedId = entries.getOrNull(selectedIndex)?.id
        val selectedWasPinned = selectedIndex == 0 &&
            (items.firstOrNull() as? Header)?.pinned == true
        entries = matches
        updateEmptyState()
        val selectedMatch = when {
            selectedWasPinned && pinned?.id == selectedId -> 0
            selectedId != null -> entries.indexOfLast { it.id == selectedId }
            else -> -1
        }
        selectedIndex = if (selectedMatch >= 0) selectedMatch else
            selectedIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        items = if (pinned != null && entries.isNotEmpty()) listOf(Header(context.getString(R.string.kairo_library_continue_group), true), Game(0)) +
            (if (entries.size > 1) listOf(Header(context.getString(R.string.kairo_library_all_games_group), false)) else emptyList()) +
            (1 until entries.size).map(::Game)
            else entries.indices.map(::Game)
        adapter.notifyDataSetChanged()
        requestSelectionVisibility()
        notifySelection()
    }

    /** Tells the host which game is selected, once per change. */
    private fun notifySelection() {
        val entry = entries.getOrNull(selectedIndex)
        if (entry?.id == reportedSelection) return
        reportedSelection = entry?.id
        selectionChanged(entry)
    }

    /** Reannounce a cached selection when the library returns after gameplay.
     * The companion display may have cleared its game information while hidden. */
    fun reannounceSelection() {
        requestSelectionVisibility()
        val entry = entries.getOrNull(selectedIndex)
        reportedSelection = entry?.id
        selectionChanged(entry)
    }

    /** [path] lists a row's images in preference order, one per line. */
    private fun loadArt(path: String): Bitmap? {
        artCache.get(path)?.let { return it }
        if (path in missingArt || !pendingArt.add(path)) return null
        artExecutor.execute {
            val bitmap = path.lineSequence().firstNotNullOfOrNull { candidate ->
                try {
                    catalog.openArtwork(candidate).use { stream ->
                        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                            inSampleSize = 4
                        })
                    }
                } catch (_: Exception) { null }
            }
            post {
                pendingArt.remove(path)
                if (bitmap == null) missingArt.add(path) else artCache.put(path, bitmap)
                // Async artwork must only update rows still bound to this image.
                // A dataset notification would repeat catalog lookup and text layout.
                for (index in 0 until list.childCount) {
                    val row = list.getChildAt(index).tag as? Row ?: continue
                    if (row.artwork != path) continue
                    row.art.setImageBitmap(bitmap)
                    row.art.visibility = if (bitmap == null) View.GONE else View.VISIBLE
                    row.mark.visibility = if (bitmap == null) View.VISIBLE else View.GONE
                }
            }
        }
        return null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (catalogEntries.isNotEmpty()) showEntries(catalogEntries)
    }

    override fun onDetachedFromWindow() {
        entriesGeneration++
        visibilityExecutor?.shutdownNow()
        visibilityExecutor = null
        searchSnapshot++
        searchIndex.close()
        selectionVisibilityPending = false
        artExecutor.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

}
