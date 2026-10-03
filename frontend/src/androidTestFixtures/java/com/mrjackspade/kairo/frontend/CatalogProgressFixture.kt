package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.TextView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Controlled network stages rendered in each host's actual library. */
object CatalogProgressFixture {
    fun verify(test: Instrumentation) {
        val launch = test.targetContext.packageManager.getLaunchIntentForPackage(test.targetContext.packageName)!!
        val activity = test.startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun ui(action: () -> Unit) {
            var error: Throwable? = null
            test.runOnMainSync { try { action() } catch (caught: Throwable) { error = caught } }
            test.waitForIdleSync()
            error?.let { throw it }
        }
        fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
        lateinit var screen: LibraryScreen<*>
        lateinit var banner: View
        lateinit var spinner: View
        lateinit var label: TextView
        var controller: CatalogUpdateController? = null
        fun awaitState(state: CatalogUpdateState?) {
            repeat(100) {
                var matches = false
                ui {
                    matches = if (state == null) banner.visibility == View.GONE
                        else banner.isShown && label.text.toString() == state.label &&
                            (spinner.visibility == View.VISIBLE) == state.busy
                }
                if (matches) return
                Thread.sleep(20)
            }
            error("Missing catalog UI state $state: ${label.text}, banner=${banner.visibility}, spinner=${spinner.visibility}")
        }
        var changed = 0
        fun create(fetch: (CatalogUpdateTask) -> Boolean) = CatalogUpdateController(activity, fetch, {},
            { changed++ }, {}, screen::showCatalogUpdate).also { controller = it }
        try {
            Thread.sleep(8000)
            ui {
                (activity.javaClass.getDeclaredMethod("getCatalogUpdates").apply { isAccessible = true }
                    .invoke(activity) as CatalogUpdateController).cancel()
                val flow = field(activity, "libraryFlow")!!
                screen = flow.javaClass.getMethod("getScreen").invoke(flow) as LibraryScreen<*>
                banner = field(screen, "catalogBanner") as View
                spinner = field(screen, "catalogProgress") as View
                label = field(screen, "catalogStatus") as TextView
            }
            lateinit var menu: android.app.AlertDialog
            fun texts(view: View): List<String> = when (view) {
                is TextView -> listOf(view.text.toString())
                is android.view.ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                else -> emptyList()
            }
            fun key(code: Int) {
                for (action in listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP))
                    menu.window!!.callback.dispatchKeyEvent(android.view.KeyEvent(action, code))
            }
            ui {
                screen.openActions()
                @Suppress("UNCHECKED_CAST")
                val rows = field(screen, "actionItems") as List<View>
                check(rows.flatMap(::texts).none { it in setOf("Update game catalog", "Update installed catalogs", "Import catalog file", "Remove catalog", "Download missing images") })
                rows.single { "Catalog management" in texts(it) }.performClick()
                menu = field(screen, "catalogManagementDialog") as android.app.AlertDialog
                check((0 until menu.listView.count).map { menu.listView.adapter.getItem(it).toString() } ==
                    listOf("Update catalogs", "Import catalog file", "Remove catalog", "Download missing images"))
            }
            Thread.sleep(200) // Allow the new dialog window to acquire input focus.
            ui { menu.listView.requestFocusFromTouch(); menu.listView.setSelection(0) }
            repeat(3) { test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN); test.waitForIdleSync() }
            ui { check(menu.listView.selectedItemPosition == 3) { "D-pad selected ${menu.listView.selectedItemPosition}" } }
            ui {
                val now = android.os.SystemClock.uptimeMillis()
                val properties = arrayOf(android.view.MotionEvent.PointerProperties().apply { id = 0 })
                val coords = arrayOf(android.view.MotionEvent.PointerCoords().apply {
                    setAxisValue(android.view.MotionEvent.AXIS_HAT_Y, -1f)
                })
                val event = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_MOVE,
                    1, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_JOYSTICK, 0)
                menu.window!!.callback.dispatchGenericMotionEvent(event)
                event.recycle()
            }
            ui {
                check(menu.listView.selectedItemPosition == 2)
                key(android.view.KeyEvent.KEYCODE_BACK)
            }
            ui {
                check(!menu.isShowing && screen.actionsOpen)
                screen.closeActions()
            }
            ui {
                fun nav(code: Int) {
                    for (action in listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP))
                        activity.dispatchKeyEvent(android.view.KeyEvent(action, code))
                }
                screen.leaveHeader()
                screen.moveSelection(-100000)
                screen.leaveHeader()
                nav(android.view.KeyEvent.KEYCODE_DPAD_UP)
                check((field(screen, "menuButton") as View).hasFocus()) { "Up cannot reach Menu" }
                nav(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                check(screen.actionsOpen)
                nav(android.view.KeyEvent.KEYCODE_BACK)
                check(!screen.actionsOpen && (field(screen, "menuButton") as View).hasFocus())
                nav(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
                check((field(screen, "searchButton") as View).hasFocus()) { "Right cannot reach Search" }
                nav(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                check(screen.searchFocused) { "Confirm did not open Search" }
                nav(android.view.KeyEvent.KEYCODE_DPAD_UP)
                check(!screen.searchFocused && (field(screen, "searchButton") as View).hasFocus())
                val now = android.os.SystemClock.uptimeMillis()
                val properties = arrayOf(android.view.MotionEvent.PointerProperties().apply { id = 0 })
                val coords = arrayOf(android.view.MotionEvent.PointerCoords().apply {
                    setAxisValue(android.view.MotionEvent.AXIS_HAT_X, -1f)
                })
                val event = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_MOVE,
                    1, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_JOYSTICK, 0)
                activity.dispatchGenericMotionEvent(event)
                event.recycle()
                coords[0].setAxisValue(android.view.MotionEvent.AXIS_HAT_X, 0f)
                val neutral = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_MOVE,
                    1, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_JOYSTICK, 0)
                activity.dispatchGenericMotionEvent(neutral)
                neutral.recycle()
                check((field(screen, "menuButton") as View).hasFocus()) { "Hat cannot move across header" }
                nav(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                check(field(screen, "headerSelection") == -1 && field(screen, "selectedIndex") == 0)
                nav(android.view.KeyEvent.KEYCODE_DPAD_UP)
                nav(android.view.KeyEvent.KEYCODE_BACK)
                check(field(screen, "headerSelection") == -1) { "Back did not leave header" }
            }
            test.sendStatus(0, android.os.Bundle().apply { putString("stream", "Header Menu/Search key/hat navigation and return: OK\n") })
            // Reproduce the ANR: the real catalog monitor is held by indexing while
            // UI navigation, visibility refresh and detail rendering need metadata.
            val catalog = field(screen, "catalog")!!
            val locked = CountDownLatch(1)
            val unlock = CountDownLatch(1)
            val holder = Thread {
                synchronized(catalog) { locked.countDown(); unlock.await(15, TimeUnit.SECONDS) }
            }.apply { start() }
            check(locked.await(20, TimeUnit.SECONDS))
            val responsive = CountDownLatch(1)
            var navigationFailure: Throwable? = null
            val began = android.os.SystemClock.elapsedRealtime()
            android.os.Handler(Looper.getMainLooper()).post {
                try {
                    @Suppress("UNCHECKED_CAST")
                    (field(screen, "rowMetadata") as MutableMap<String, LibraryGame>).clear()
                    val before = field(screen, "selectedIndex") as Int
                    for (action in listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP))
                        activity.dispatchKeyEvent(android.view.KeyEvent(action, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
                    check(field(screen, "selectedIndex") == before + 1) { "D-pad did not advance" }
                    screen.activateSelection()
                    check(screen.detailOpen)
                    screen.closeDetail()
                    screen.refreshCatalog()
                } catch (error: Throwable) { navigationFailure = error }
                finally { responsive.countDown() }
            }
            val passed = responsive.await(2, TimeUnit.SECONDS)
            unlock.countDown()
            holder.join(2000)
            check(passed) { "UI waited for background catalog lock" }
            navigationFailure?.let { throw it }
            test.sendStatus(0, android.os.Bundle().apply { putString("stream",
                "D-pad/detail/refresh with catalog locked: ${android.os.SystemClock.elapsedRealtime() - began}ms\n") })
            val gates = List(3) { CountDownLatch(1) }
            ui { create { task ->
                check(Looper.myLooper() != Looper.getMainLooper())
                check(gates[0].await(10, TimeUnit.SECONDS))
                task.report(CatalogUpdateState.DOWNLOADING)
                check(gates[1].await(10, TimeUnit.SECONDS))
                task.report(CatalogUpdateState.APPLYING)
                check(gates[2].await(10, TimeUnit.SECONDS))
                true
            }.check(true) }
            awaitState(CatalogUpdateState.CHECKING)
            ui {
                val before = field(screen, "selectedIndex") as Int
                screen.moveSelection(1)
                check((field(screen, "selectedIndex") as Int) == before + 1) { "Library blocked during check" }
                controller!!.check(false) // duplicate must not replace the busy indicator
            }
            gates[0].countDown(); awaitState(CatalogUpdateState.DOWNLOADING)
            ui {
                val image = android.graphics.Bitmap.createBitmap(screen.width, screen.height, android.graphics.Bitmap.Config.ARGB_8888)
                screen.draw(android.graphics.Canvas(image))
                File(test.targetContext.cacheDir, "catalog-progress.png").outputStream().use {
                    image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                image.recycle()
            }
            gates[1].countDown(); awaitState(CatalogUpdateState.APPLYING)
            gates[2].countDown(); awaitState(CatalogUpdateState.UPDATED)
            ui { check(changed == 1) }
            Thread.sleep(3200)
            awaitState(null)
            ui { create { false }.check(true) }
            awaitState(CatalogUpdateState.CURRENT)
            ui { create { error("Fixture network failure") }.check(true) }
            awaitState(CatalogUpdateState.FAILED)
            val started = CountDownLatch(1)
            val finished = CountDownLatch(1)
            ui { create { task ->
                started.countDown()
                try { CountDownLatch(1).await(10, TimeUnit.SECONDS); task.ensureActive(); true }
                finally { finished.countDown() }
            }.check(true) }
            check(started.await(5, TimeUnit.SECONDS))
            awaitState(CatalogUpdateState.CHECKING)
            ui { controller!!.cancel() }
            check(finished.await(5, TimeUnit.SECONDS))
            awaitState(null)
            ui { check(changed == 1); create { false }.check(true) }
            awaitState(CatalogUpdateState.CURRENT)
            val beforeCombined = changed
            var installedChecks = 0
            ui {
                controller = CatalogUpdateController(activity, { false }, {}, { changed++ }, {},
                    screen::showCatalogUpdate, { installedChecks++; true })
                controller!!.check(false)
            }
            awaitState(CatalogUpdateState.UPDATED)
            ui { check(changed == beforeCombined + 1 && installedChecks == 1) }
            ui {
                controller = CatalogUpdateController(activity, { error("Core unavailable") }, {},
                    { changed++ }, {}, screen::showCatalogUpdate, { installedChecks++; true })
                controller!!.check(false)
            }
            awaitState(CatalogUpdateState.FAILED)
            ui { check(changed == beforeCombined + 2 && installedChecks == 2) }
            ui {
                controller = CatalogUpdateController(activity, { true }, {}, { changed++ }, {},
                    screen::showCatalogUpdate, { error("Installed source unavailable") })
                controller!!.check(false)
            }
            awaitState(CatalogUpdateState.FAILED)
            ui { check(changed == beforeCombined + 3) }
            snapshot(test)
            SearchIndexFixture.verify(test)
        } finally {
            ui { controller?.cancel(); activity.finish() }
        }
    }

    private fun snapshot(test: Instrumentation) {
        var body = "catalog fixture 1".toByteArray()
        var metadataRequests = 0
        var archiveRequests = 0
        var validations = 0
        URL.setURLStreamHandlerFactory { protocol -> if (protocol != "kairotest") null else object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection {
                val bytes = if (url.path == "/revision") {
                    metadataRequests++
                    val checksum = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
                    JSONObject().put("schemaVersion", 1).put("archive", "archive.bin")
                        .put("sha256", checksum).put("size", body.size).toString().toByteArray()
                } else { archiveRequests++; body }
                return object : HttpURLConnection(url) {
                    override fun connect() = Unit
                    override fun disconnect() = Unit
                    override fun usingProxy() = false
                    override fun getResponseCode() = HTTP_OK
                    override fun getContentLengthLong() = bytes.size.toLong()
                    override fun getInputStream() = bytes.inputStream()
                }
            }
        } }
        val name = "catalog-progress-fixture.bin"
        val file = File(test.targetContext.filesDir, name)
        val marker = File(test.targetContext.filesDir, "$name.apk")
        file.delete(); marker.delete()
        try {
            val store = CatalogSnapshotStore(test.targetContext, name, "kairotest://local/archive.bin",
                "kairotest://local/revision", 1024) { validations++; check(it.readBytes().contentEquals(body)) }
            val states = ArrayList<CatalogUpdateState>()
            check(store.download(CatalogUpdateTask(states::add)))
            check(states == listOf(CatalogUpdateState.CHECKING, CatalogUpdateState.DOWNLOADING, CatalogUpdateState.APPLYING))
            states.clear()
            check(!store.download(CatalogUpdateTask(states::add)))
            check(states == listOf(CatalogUpdateState.CHECKING))
            check(metadataRequests == 2 && archiveRequests == 1 && validations == 1)
            body = "catalog fixture 2".toByteArray()
            lateinit var task: CatalogUpdateTask
            task = CatalogUpdateTask { if (it == CatalogUpdateState.DOWNLOADING) task.cancel() }
            try { store.download(task); error("Cancelled update succeeded") }
            catch (_: java.util.concurrent.CancellationException) { }
            check(archiveRequests == 1 && validations == 1 && file.readText() == "catalog fixture 1")
            check(store.download())
            check(archiveRequests == 2 && validations == 2 && file.readText() == "catalog fixture 2")
        } finally { file.delete(); marker.delete() }
    }
}
