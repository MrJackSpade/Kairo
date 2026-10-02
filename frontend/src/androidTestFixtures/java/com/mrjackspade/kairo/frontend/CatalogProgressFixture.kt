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
            snapshot(test)
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
