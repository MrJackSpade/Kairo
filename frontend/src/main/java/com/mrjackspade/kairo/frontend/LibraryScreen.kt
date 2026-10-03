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
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import com.mrjackspade.kairo.frontend.SettingsEntry

/**
 * The part of a file name that tells revisions of one game apart: its bracketed and
 * parenthesized tags, such as a translation or bug-fix credit.
 */
fun variantLabel(name: String): String? {
    val tags = Regex("""[\[(]([^\])]+)[\])]""").findAll(name.substringAfterLast('/'))
        .map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().toList()
    return tags.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

/**
 * Where a library entry lives: its folder and file name without the extension, plus the image
 * inside a ZIP when that name differs. Keep this filename below the catalog title: different
 * revisions can share a catalog title. Do not replace it with the title or a media label.
 */
fun fileLabel(entry: LibraryItem): String {
    fun stem(name: String) = name.substringBeforeLast('.').takeIf { name.contains('.') } ?: name
    val file = stem(entry.path)
    val inner = entry.zipEntry?.substringAfterLast('/')?.let(::stem)
    return if (inner == null || inner == file.substringAfterLast('/')) file else "$file  ›  $inner"
}

/** Library landing page. A short tap or A opens the selected game's page. */
class LibraryScreen<T : LibraryItem>(
    context: Context,
    private val catalog: LibraryCatalog,
    private val strings: LibraryStrings,
    private val chooseFolder: () -> Unit,
    private val refresh: () -> Unit,
    private val rehash: () -> Unit,
    private val updateCatalog: () -> Unit,
    private val downloadMissingImages: (() -> Unit)?,
    private val cancelArtworkDownload: () -> Unit,
    private val settings: List<SettingsEntry>,
    private val lastPlayedId: () -> String?,
    private val play: (T) -> Unit,
    private val preview: (T) -> Unit,
    private val details: (T) -> Unit,
    private val selectionChanged: (T?) -> Unit
) : FrameLayout(context) {
    private var reportedSelection: String? = "none"
    private var catalogManagementDialog: android.app.AlertDialog? = null
    private val catalogInstaller by lazy { catalog.installedCatalogs?.let {
        CatalogInstallController(context as android.app.Activity, it, {
            artCache.evictAll()
            missingArt.clear()
            showEntries(catalogEntries)
            detailPage.refreshArtwork()
        }, ::showCatalogUpdate)
    } }
    private var catalogEntries = emptyList<T>()
    fun handleCatalogResult(request: Int, result: Int, data: android.content.Intent?): Boolean =
        catalogInstaller?.handleActivityResult(request, result, data) ?: false
    private val status = TextView(context)
    private val folder = TextView(context)
    private val list = ListView(context)
    private val emptyState = TextView(context)
    private val scanProgress = ProgressBar(context)
    private val catalogBanner = LinearLayout(context)
    private val catalogProgress = ProgressBar(context)
    private val catalogStatus = TextView(context)
    private var catalogStatusVersion = 0
    private val artworkBanner = LinearLayout(context)
    private val artworkStatus = TextView(context)
    private val scrim = View(context)
    private val actionsScroll = ScrollView(context)
    private val actionsDrawer = LinearLayout(context)
    private val actionItems = ArrayList<View>()
    private val detailPage = GameDetailPage<T>(context, catalog, play, preview, details, { closeDetail() }, ::metadata)
    private val settingValues = ArrayList<Pair<TextView, () -> String>>()
    private val menuButton = Ui.iconButton(context, R.drawable.ic_menu, "Library menu") { openActions() }
    private val searchButton = Ui.iconButton(context, R.drawable.ic_search, "Search games") { toggleSearch() }
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
    }
    val searchFocused: Boolean get() = search.hasFocus()
    private var allEntries = emptyList<T>()
    // A library snapshot can span far more catalog shards than either backend's
    // small shard cache. Retain the resolved row records, not entire shard JSON.
    private val rowMetadata = HashMap<String, LibraryGame>()
    private val searchIndex = LibrarySearchIndex<T>(catalog, { action -> post(action) }) { records ->
        rowMetadata.putAll(records)
        adapter.notifyDataSetChanged()
        if (entries.getOrNull(selectedIndex)?.id in records) reannounceSelection()
        if (detailOpen && detailEntryId in records) {
            allEntries.firstOrNull { it.id == detailEntryId }?.let(detailPage::show)
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
    private var detailEntryId: String? = null
    private var selectedAction = 0
    var actionsOpen = false
        private set
    val detailOpen: Boolean get() = detailPage.isOpen
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
            text.addView(title)
            text.addView(detail)
            view.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            holder = Row(art, mark, title, detail)
            view.tag = holder
        }
        val entry = entries[index]
        val pinned = index == 0 && pinnedId != null && items.firstOrNull() is Header
        val game = metadata(entry)
        holder.title.text = game.title
        // Keep the source filename intact; the marker is only a prefix on its display row.
        val file = (if ("♥" in game.tags) "♥ " else "") + fileLabel(entry)
        holder.detail.text = entry.error?.let { "$file  ·  $it. Fix the source, then Refresh." }
            ?: file
        holder.detail.setTextColor(if (entry.error != null) Ui.DANGER else Ui.TEXT_MUTED)
        val artwork = game.boxArt ?: game.preview
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
        view.isActivated = headerSelection < 0 && index == selectedIndex
        view.alpha = if (entry.playable) 1f else 0.6f
        return view
    }

    init {
        setBackgroundColor(Ui.BG)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
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
        }, LinearLayout.LayoutParams(0, -2, 1f))
        scanProgress.apply { visibility = View.GONE }
        header.addView(scanProgress, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
            marginEnd = dp(12)
        })
        header.addView(searchButton,
            LinearLayout.LayoutParams(dp(48), dp(48)))
        body.addView(header)
        search.apply {
            visibility = View.GONE
            hint = "Search by title"
            textSize = Ui.BODY
            isSingleLine = true
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.TEXT_FAINT)
            background = Ui.rounded(context, Ui.SURFACE, Ui.RADIUS_SMALL, Ui.LINE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
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
        searchRow.gravity = Gravity.CENTER_VERTICAL
        searchRow.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        searchRow.addView(Ui.text(context, "View results", Ui.SECONDARY, Ui.ACCENT).apply {
            setPadding(dp(12), dp(12), dp(4), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { dismissSearchKeyboard() }
        }, LinearLayout.LayoutParams(-2, -2))
        body.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
            bottomMargin = dp(4)
        })
        catalogBanner.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundColor(Ui.RAISED)
        }
        catalogBanner.addView(catalogProgress, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10) })
        catalogStatus.apply { textSize = Ui.SECONDARY; setTextColor(Ui.TEXT) }
        catalogBanner.addView(catalogStatus, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(catalogBanner, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        artworkBanner.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setBackgroundColor(Ui.RAISED)
            setPadding(dp(12), dp(7), dp(8), dp(7))
        }
        artworkStatus.apply {
            textSize = Ui.SECONDARY
            setTextColor(Ui.TEXT)
        }
        artworkBanner.addView(artworkStatus, LinearLayout.LayoutParams(0, -2, 1f))
        artworkBanner.addView(TextView(context).apply {
            text = "Cancel"
            textSize = Ui.SECONDARY
            setTextColor(Ui.ACCENT)
            setPadding(dp(12), dp(6), dp(6), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                artworkStatus.text = "Stopping image download…"
                cancelArtworkDownload()
            }
        })
        body.addView(artworkBanner, LinearLayout.LayoutParams(-1, -2))
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
            setPadding(0, dp(4), 0, dp(12))
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
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
                details(entries[index])
                true
            }
        }
        gameArea.addView(list, FrameLayout.LayoutParams(-1, -1))
        emptyState.apply {
            textSize = Ui.BODY
            gravity = Gravity.CENTER
            setTextColor(Ui.TEXT_MUTED)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        gameArea.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        body.addView(gameArea, LinearLayout.LayoutParams(-1, 0, 1f))

        scrim.apply {
            visibility = View.GONE
            alpha = 0f
            setBackgroundColor(Ui.SCRIM)
            setOnClickListener { closeActions() }
        }
        addView(scrim, FrameLayout.LayoutParams(-1, -1))
        actionsDrawer.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.SURFACE)
            setPadding(dp(12), dp(20), dp(12), dp(16))
        }
        actionsScroll.apply {
            visibility = View.GONE
            elevation = dp(16).toFloat()
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Ui.SURFACE)
            addView(actionsDrawer, FrameLayout.LayoutParams(-1, -2))
        }
        val drawerWidth = minOf(dp(320), resources.displayMetrics.widthPixels - dp(40))
        addView(actionsScroll, FrameLayout.LayoutParams(drawerWidth, -1, Gravity.START))
        actionsDrawer.addView(PixelTextView(context).apply {
            text = strings.productName
            scale = 2
            setPadding(dp(12), 0, dp(12), dp(4))
        })
        folder.apply {
            textSize = Ui.SECONDARY
            setTextColor(Ui.TEXT_BODY)
            setPadding(dp(12), 0, dp(12), dp(4))
        }
        actionsDrawer.addView(folder)
        status.apply {
            textSize = Ui.LABEL
            setTextColor(Ui.TEXT_MUTED)
            setPadding(dp(12), 0, dp(12), dp(18))
            maxLines = 2
        }
        actionsDrawer.addView(status)
        actionsDrawer.addView(Ui.sectionLabel(context, "LIBRARY"))
        drawerAction(strings.selectFolder, strings.folderHint, chooseFolder)
        drawerAction("Refresh", "Scan for added, changed, or removed games", refresh)
        drawerAction("Rehash", "Recheck every game image", rehash)
        drawerAction("Catalog management", "Update catalogs, import files, and manage artwork", ::showCatalogManagement)
        actionsDrawer.addView(Ui.sectionLabel(context, "SETTINGS"))
        settings.forEach { entry ->
            settingValues.add(drawerAction(entry.title, entry.value(), entry.action) to entry.value)
        }
        addView(detailPage, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showCatalogManagement() {
        if (catalogManagementDialog?.isShowing == true) return
        val actions = mutableListOf<Pair<String, () -> Unit>>("Update catalogs" to updateCatalog)
        if (catalog.installedCatalogs != null) {
            actions += "Import catalog file" to { catalogInstaller?.chooseFile(); Unit }
            actions += "Remove catalog" to { catalogInstaller?.remove(); Unit }
        }
        downloadMissingImages?.let { actions += "Download missing images" to it }
        val dialog = android.app.AlertDialog.Builder(context).setTitle("Catalog management")
            .setItems(actions.map { it.first }.toTypedArray(), null)
            .setNegativeButton("Back") { _, _ -> openActions() }
            .create()
        dialog.setOnCancelListener { openActions() }
        catalogManagementDialog = dialog
        dialog.setOnDismissListener { catalogManagementDialog = null }
        dialog.show()
        Ui.styleDialog(dialog)
        dialog.listView.setOnItemClickListener { _, _, index, _ ->
            // Keep the parent menu underneath the removal picker/confirmation.
            if (actions[index].first != "Remove catalog") dialog.dismiss()
            actions[index].second()
        }
    }

    fun showFolder(label: String?) { folder.text = label ?: strings.noFolder }
    fun refreshCatalog() {
        artCache.evictAll()
        missingArt.clear()
        showEntries(catalogEntries)
        detailPage.refreshArtwork()
    }
    fun showCatalogUpdate(state: CatalogUpdateState?) {
        val version = ++catalogStatusVersion
        catalogBanner.visibility = if (state == null) View.GONE else View.VISIBLE
        catalogProgress.visibility = if (state?.busy == true) View.VISIBLE else View.GONE
        catalogStatus.text = state?.label ?: ""
        requestSelectionVisibility()
        if (state != null && !state.busy) postDelayed({
            if (catalogStatusVersion == version) {
                catalogBanner.visibility = View.GONE
                requestSelectionVisibility()
            }
        }, 3000)
    }
    fun showArtworkProgress(completed: Int, total: Int, failures: Int) {
        artworkBanner.visibility = View.VISIBLE
        artworkStatus.text = if (total == 0) "Checking library artwork…"
            else "Downloading images $completed/$total" +
                (if (failures == 0) "" else " · $failures failed")
    }
    fun finishArtworkDownload(message: String) {
        artworkBanner.visibility = View.GONE
        showStatus(message)
        (context as? android.app.Activity)?.let { Ui.message(it, message, long = true) }
    }
    fun refreshArtwork() {
        rowMetadata.clear()
        resetSearchIndex()
        missingArt.clear()
        adapter.notifyDataSetChanged()
        detailPage.refreshArtwork()
        if (search.text.isNotEmpty()) applyFilter()
    }
    fun showStatus(message: String) {
        status.text = message
        scanProgress.visibility = if (message.startsWith("Scanning") ||
            message.startsWith("Rehashing") || message.startsWith("Hashing") ||
            message.startsWith("Found") || message.startsWith("Skipping") ||
            message.startsWith("Preparing"))
            View.VISIBLE else View.GONE
        if (entries.isEmpty()) emptyState.text = message
        if (entries.isNotEmpty() && (message.startsWith("Scan failed") ||
            message.startsWith("Launch failed") || message.startsWith("Cannot keep folder access"))) {
            (context as? android.app.Activity)?.let { Ui.message(it, message, long = true) }
        }
    }
    fun showEntries(items: List<T>) {
        catalogEntries = items
        val generation = ++entriesGeneration
        // Invalidate pending metadata results, retaining the displayed snapshot while
        // the replacement's visibility flags are read on a worker.
        searchSnapshot++
        searchIndex.replace(emptyList(), emptyMap())
        val worker = visibilityExecutor ?: Executors.newSingleThreadExecutor().also { visibilityExecutor = it }
        worker.execute {
            val visible = items.filterNot { entry ->
                if (generation != entriesGeneration || Thread.currentThread().isInterrupted) return@execute
                entry.contentId?.let(catalog::hiddenFromLibrary) == true
            }
            post {
                if (generation == entriesGeneration) publishEntries(visible)
            }
        }
    }

    private fun publishEntries(visible: List<T>) {
        allEntries = visible
        rowMetadata.keys.retainAll(visible.map { it.id }.toSet())
        pinnedId = lastPlayedId()?.takeIf { id -> allEntries.any { it.id == id } }
        reportedSelection = "none"
        resetSearchIndex()
        applyFilter()
        if (entries.isNotEmpty()) list.setSelection(if (selectedIndex == 0) 0 else positionOf(selectedIndex))
        if (detailOpen) {
            val refreshed = allEntries.firstOrNull { it.id == detailEntryId }
            if (refreshed == null) closeDetail() else detailPage.show(refreshed)
        }
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
            if (delta > 0) focusGames()
            return
        }
        if (delta < 0 && (entries.isEmpty() || selectedIndex == 0)) {
            focusHeader(lastHeaderSelection)
            return
        }
        if (entries.isEmpty()) return
        val next = (selectedIndex + delta).coerceIn(0, entries.lastIndex)
        if (next == selectedIndex) {
            requestSelectionVisibility()
            return
        }
        selectedIndex = next
        updateSelectionHighlight()
        requestSelectionVisibility()
        notifySelection()
    }

    private fun focusHeader(index: Int) {
        headerSelection = index.coerceIn(0, 1)
        lastHeaderSelection = headerSelection
        val button = if (headerSelection == 0) menuButton else searchButton
        button.isFocusableInTouchMode = true
        button.requestFocusFromTouch()
        updateSelectionHighlight()
    }

    private fun focusGames() {
        headerSelection = -1
        isFocusableInTouchMode = true
        requestFocus()
        updateSelectionHighlight()
        requestSelectionVisibility()
    }

    fun moveHeaderSelection(delta: Int) {
        if (headerSelection >= 0) focusHeader(headerSelection + delta)
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
    private fun updateSelectionHighlight() {
        for (index in 0 until list.childCount) {
            val child = list.getChildAt(index)
            val row = child.tag as? Row ?: continue
            child.isActivated = headerSelection < 0 && row.index == selectedIndex
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
        if (headerSelection >= 0) {
            (if (headerSelection == 0) menuButton else searchButton).performClick()
            return
        }
        entries.getOrNull(selectedIndex)?.let(::openDetail)
    }
    fun detailsSelection() { entries.getOrNull(selectedIndex)?.let(details) }

    fun openDetail(entry: T) {
        closeActions()
        detailEntryId = entry.id
        detailPage.show(entry)
    }

    fun closeDetail(): Boolean {
        if (!detailPage.close()) return false
        detailEntryId = null
        list.requestFocus()
        return true
    }

    fun activateDetail() { detailPage.activateFocused() }

    fun openActions() {
        if (actionsOpen) return
        actionsOpen = true
        scrim.animate().cancel()
        actionsScroll.animate().cancel()
        scrim.visibility = View.VISIBLE
        scrim.alpha = 0f
        actionsScroll.visibility = View.VISIBLE
        actionsScroll.translationX = -actionsScroll.layoutParams.width.toFloat()
        refreshSettingValues()
        focusAction(0)
        scrim.animate().alpha(1f).setDuration(180).start()
        actionsScroll.animate().translationX(0f).setDuration(180).start()
    }

    fun closeActions(): Boolean {
        if (!actionsOpen) return false
        actionsOpen = false
        actionItems.forEach { it.isSelected = false; it.isPressed = false }
        if (headerSelection >= 0) focusHeader(headerSelection) else focusGames()
        scrim.animate().cancel()
        actionsScroll.animate().cancel()
        scrim.animate().alpha(0f).setDuration(160).withEndAction {
            if (!actionsOpen) scrim.visibility = View.GONE
        }.start()
        actionsScroll.animate().translationX(-actionsScroll.layoutParams.width.toFloat())
            .setDuration(160).withEndAction {
                if (!actionsOpen) actionsScroll.visibility = View.GONE
            }.start()
        return true
    }

    fun moveActionSelection(delta: Int) {
        focusAction((selectedAction + delta).coerceIn(0, actionItems.lastIndex))
    }

    fun activateAction() { actionItems.getOrNull(selectedAction)?.performClick() }

    private fun focusAction(index: Int) {
        selectedAction = index
        actionItems.forEachIndexed { position, item -> item.isSelected = position == index }
        actionItems.getOrNull(index)?.let { item ->
            item.isFocusableInTouchMode = true
            item.requestFocusFromTouch()
        }
        revealAction()
        // Opening or refreshing the menu can change row bounds on the next layout.
        actionsScroll.post { if (actionsOpen) revealAction() }
    }

    private fun revealAction() {
        val item = actionItems.getOrNull(selectedAction) ?: return
        item.requestRectangleOnScreen(android.graphics.Rect(0, 0, item.width, item.height), true)
    }

    private fun drawerAction(title: String, description: String, action: () -> Unit): TextView {
        val row = Ui.actionRow(context, title, { description }, 66, 10) {
            closeActions()
            action()
        }
        actionsDrawer.addView(row.view, LinearLayout.LayoutParams(-1, -2))
        actionItems.add(row.view)
        return row.detail
    }

    fun refreshSettingValues() {
        settingValues.forEach { (view, value) -> view.text = value() }
    }

    /** Drop library search focus before a guest session takes over the window. */
    fun dismissSystemKeyboard() {
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
            search.setText("")
            search.visibility = View.GONE
            searchRow.visibility = View.GONE
            imm.hideSoftInputFromWindow(search.windowToken, 0)
        } else {
            searchRow.visibility = View.VISIBLE
            search.visibility = View.VISIBLE
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
        val pinned = pinnedId?.let { id -> allEntries.firstOrNull { it.id == id } }
        publishFilter(query, pinned?.let { listOf(it) + allEntries } ?: allEntries, pinned)
    }

    private fun resetSearchIndex() {
        searchIndex.replace(allEntries, emptyMap())
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
        emptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        if (entries.isEmpty() && allEntries.isNotEmpty()) emptyState.text = "No games match \"$query\""
        val selectedMatch = when {
            selectedWasPinned && pinned?.id == selectedId -> 0
            selectedId != null -> entries.indexOfLast { it.id == selectedId }
            else -> -1
        }
        selectedIndex = if (selectedMatch >= 0) selectedMatch else
            selectedIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        items = if (pinned != null && entries.isNotEmpty()) listOf(Header("CONTINUE", true), Game(0)) +
            (if (entries.size > 1) listOf(Header("ALL GAMES", false)) else emptyList()) +
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

    private fun loadArt(path: String): Bitmap? {
        artCache.get(path)?.let { return it }
        if (path in missingArt || !pendingArt.add(path)) return null
        artExecutor.execute {
            val bitmap = try {
                catalog.openArtwork(path).use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                        inSampleSize = 4
                    })
                }
            } catch (_: Exception) { null }
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
