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
    private val status = TextView(context)
    private val folder = TextView(context)
    private val list = ListView(context)
    private val emptyState = TextView(context)
    private val scanProgress = ProgressBar(context)
    private val artworkBanner = LinearLayout(context)
    private val artworkStatus = TextView(context)
    private val scrim = View(context)
    private val actionsScroll = ScrollView(context)
    private val actionsDrawer = LinearLayout(context)
    private val actionItems = ArrayList<View>()
    private val detailPage = GameDetailPage<T>(context, catalog, play, preview, details) { closeDetail() }
    private val settingValues = ArrayList<Pair<TextView, () -> String>>()
    private val search = android.widget.EditText(context)
    private var allEntries = emptyList<T>()
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

    private sealed interface ListItem
    private data class Header(val label: String, val pinned: Boolean) : ListItem
    private data class Game(val index: Int) : ListItem
    private var items = emptyList<ListItem>()

    private data class Row(val art: ImageView, val mark: TextView,
                           val title: TextView, val detail: TextView)

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
        val game = catalog.resolve(entry.contentId ?: "", entry.displayName)
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
        view.background = if (pinned) Ui.rowBackground(context, Ui.PIN_SURFACE, Ui.PIN, Ui.PIN_SELECTED, activatedOnly = true)
            else Ui.rowBackground(context, activatedOnly = true)
        view.isActivated = index == selectedIndex
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
        header.addView(Ui.iconButton(context, R.drawable.ic_menu, "Library menu") { openActions() },
            LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(10) })
        header.addView(PixelTextView(context).apply {
            text = strings.productName
            scale = 2
        }, LinearLayout.LayoutParams(0, -2, 1f))
        scanProgress.apply { visibility = View.GONE }
        header.addView(scanProgress, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
            marginEnd = dp(12)
        })
        header.addView(Ui.iconButton(context, R.drawable.ic_search, "Search games") { toggleSearch() },
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
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(text: android.text.Editable?) { applyFilter() }
            })
        }
        body.addView(search, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
            bottomMargin = dp(4)
        })
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
                this@LibraryScreen.adapter.notifyDataSetChanged()
                notifySelection()
                openDetail(entries[index])
            }
            setOnItemLongClickListener { _, _, position, _ ->
                val index = indexAt(position) ?: return@setOnItemLongClickListener false
                selectedIndex = index
                this@LibraryScreen.adapter.notifyDataSetChanged()
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
        drawerAction("Update game catalog", "Fetch game details and controller defaults", updateCatalog)
        if (downloadMissingImages != null) drawerAction("Download missing images",
            "Fetch artwork for games in this library", downloadMissingImages)
        actionsDrawer.addView(Ui.sectionLabel(context, "SETTINGS"))
        settings.forEach { entry ->
            settingValues.add(drawerAction(entry.title, entry.value(), entry.action) to entry.value)
        }
        addView(detailPage, FrameLayout.LayoutParams(-1, -1))
    }

    fun showFolder(label: String?) { folder.text = label ?: strings.noFolder }
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
        missingArt.clear()
        adapter.notifyDataSetChanged()
        detailPage.refreshArtwork()
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
        allEntries = items.filterNot { entry ->
            entry.contentId?.let(catalog::hiddenFromLibrary) == true
        }
        pinnedId = lastPlayedId()?.takeIf { id -> allEntries.any { it.id == id } }
        reportedSelection = "none"
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
                catalog.resolve(it.contentId ?: "", it.displayName).title.equals(needle, ignoreCase = true)
            }.takeIf { it >= 0 }
            ?: entries.indices.filter { entries[it].displayName.contains(needle, ignoreCase = true) }
                .singleOrNull() ?: -1
        if (index < 0) return false
        selectedIndex = index
        list.setSelection(positionOf(index))
        adapter.notifyDataSetChanged()
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
                catalog.resolve(it.contentId ?: "", it.displayName).title.equals(needle, ignoreCase = true)
            })
            ?: unique(playable.filter { it.displayName.contains(needle, ignoreCase = true) })
    }
    fun moveSelection(delta: Int) {
        if (entries.isEmpty()) return
        selectedIndex = (selectedIndex + delta).coerceIn(0, entries.lastIndex)
        this@LibraryScreen.adapter.notifyDataSetChanged()
        keepSelectionVisible()
        notifySelection()
    }

    private fun keepSelectionVisible() {
        if (list.height == 0) return
        // Keep the section label above the first game in view.
        val selectedPosition = if (selectedIndex == 0) 0 else positionOf(selectedIndex)
        val first = list.firstVisiblePosition
        val last = list.lastVisiblePosition
        val viewportBottom = list.height - list.paddingBottom
        val child = list.getChildAt(selectedPosition - first)
        when {
            selectedPosition < first -> list.setSelectionFromTop(selectedPosition, list.paddingTop)
            selectedPosition > last -> {
                val rowHeight = list.getChildAt(last - first)?.height ?: dp(75)
                list.setSelectionFromTop(selectedPosition,
                    (viewportBottom - rowHeight).coerceAtLeast(list.paddingTop))
            }
            child != null && child.top < list.paddingTop ->
                list.setSelectionFromTop(selectedPosition, list.paddingTop)
            child != null && child.bottom > viewportBottom ->
                list.setSelectionFromTop(selectedPosition,
                    (viewportBottom - child.height).coerceAtLeast(list.paddingTop))
        }
    }
    fun activateSelection() {
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
        actionItems.getOrNull(index)?.requestFocus()
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

    private fun toggleSearch() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        if (search.visibility == View.VISIBLE) {
            search.setText("")
            search.visibility = View.GONE
            imm.hideSoftInputFromWindow(search.windowToken, 0)
        } else {
            search.visibility = View.VISIBLE
            search.requestFocus()
            imm.showSoftInput(search, 0)
        }
    }

    private fun applyFilter() {
        val selectedId = entries.getOrNull(selectedIndex)?.id
        val selectedWasPinned = selectedIndex == 0 &&
            (items.firstOrNull() as? Header)?.pinned == true
        val query = search.text.toString().trim()
        val pinned = pinnedId?.takeIf { query.isEmpty() }?.let { id -> allEntries.firstOrNull { it.id == id } }
        // Keep the recent shortcut while leaving its game in the full library below.
        val ordered = pinned?.let { listOf(it) + allEntries } ?: allEntries
        entries = if (query.isEmpty()) ordered else ordered.filter { entry ->
            catalog.resolve(entry.contentId ?: "", entry.displayName).title.contains(query, ignoreCase = true) ||
                entry.displayName.contains(query, ignoreCase = true)
        }
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
                adapter.notifyDataSetChanged()
            }
        }
        return null
    }

    override fun onDetachedFromWindow() {
        artExecutor.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
