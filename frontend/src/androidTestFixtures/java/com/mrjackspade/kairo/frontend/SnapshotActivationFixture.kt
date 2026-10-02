package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Intent
import android.os.Looper
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.MessageDigest
import org.json.JSONObject

object SnapshotActivationFixture {
    fun verify(test: Instrumentation) {
        var body = "v1:catalog".toByteArray()
        var offline = false
        var schema = "v1:"
        var archives = 0
        var metadata = 0
        var validations = 0
        fun checksum(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        URL.setURLStreamHandlerFactory { protocol -> if (protocol != "activationtest") null else object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection {
                val bytes = if (url.path == "/revision") {
                    metadata++
                    if (offline) throw IOException("Fixture offline")
                    JSONObject().put("schemaVersion", 1).put("archive", "archive.bin")
                        .put("sha256", checksum(body)).put("size", body.size).toString().toByteArray()
                } else { archives++; body }
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
        val name = "snapshot-activation-fixture.bin"
        val file = File(test.targetContext.filesDir, name)
        val marker = File(test.targetContext.filesDir, "$name.apk")
        fun store() = CatalogSnapshotStore(test.targetContext, name, "activationtest://local/archive.bin",
            "activationtest://local/revision", 1024) {
            check(Looper.myLooper() != Looper.getMainLooper()) { "Full validation on UI thread" }
            validations++
            require(it.readText().startsWith(schema)) { "Incompatible fixture schema" }
        }
        fun oldApk() { marker.writeText("previous-apk:${checksum(file.readBytes())}") }
        fun reject(action: () -> Unit) {
            var rejected = false
            try { action() } catch (_: IllegalArgumentException) { rejected = true }
            catch (_: IllegalStateException) { rejected = true }
            check(rejected) { "Invalid update accepted" }
        }
        file.delete(); marker.delete()
        try {
            check(store().download())
            check(archives == 1 && metadata == 1 && validations == 1)
            oldApk()
            val upgraded = store()
            check(upgraded.activeFile() == null) // expensive validation is deferred
            check(upgraded.download()) { "APK update left unchanged downloaded catalog inactive" }
            check(upgraded.activeFile()?.readText() == "v1:catalog")
            check(archives == 1 && metadata == 2 && validations == 2)
            check(!upgraded.download() && archives == 1 && validations == 2)
            check(store().activeFile()?.readText() == "v1:catalog")

            oldApk(); offline = true
            val offlineUpgrade = store()
            check(offlineUpgrade.download())
            check(offlineUpgrade.activeFile() != null && archives == 1)
            offline = false

            val savedMarker = marker.readText()
            body = "invalid:new revision".toByteArray()
            reject { offlineUpgrade.download() }
            check(file.readText() == "v1:catalog" && marker.readText() == savedMarker)
            check(offlineUpgrade.activeFile()?.readText() == "v1:catalog")
            body = "v1:changed".toByteArray()
            check(offlineUpgrade.download() && file.readText() == "v1:changed")

            // Corruption cannot inherit the marker's checksum, on the same or a new APK.
            for (newApk in listOf(false, true)) {
                if (newApk) oldApk()
                file.appendText("corruption")
                val broken = store()
                check(broken.activeFile() == null)
                val before = archives
                check(broken.download())
                check(archives == before + 1 && file.readBytes().contentEquals(body))
            }

            oldApk(); schema = "v2:"
            val incompatible = store()
            val before = archives
            reject { incompatible.download() }
            check(incompatible.activeFile() == null && archives == before)
            check(file.readText() == "v1:changed") // preserve old bytes; use bundled fallback
            body = "v2:compatible replacement".toByteArray()
            check(incompatible.download())
            check(incompatible.activeFile()?.readBytes()?.contentEquals(body) == true)
        } finally { file.delete(); marker.delete() }
        verifyHost(test)
    }

    private fun verifyHost(test: Instrumentation) {
        val launch = test.targetContext.packageManager.getLaunchIntentForPackage(test.targetContext.packageName)!!
        val activity = test.startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
        val dos = test.targetContext.packageName.endsWith("kairodos")
        try {
            var ready = false
            var diagnostic = ""
            repeat(1200) { attempt ->
                if (!ready) {
                    var error: Throwable? = null
                    test.runOnMainSync {
                        try {
                            val catalog = if (dos) activity.javaClass.getDeclaredMethod("getCatalog")
                                .apply { isAccessible = true }.invoke(activity)
                            else field(activity, "romLibrary")!!.let { it.javaClass.getMethod("getCatalog").invoke(it) }
                            if (dos) {
                                val online = field(catalog, "online")!!
                                val store = field(online, "store") as CatalogSnapshotStore
                                val profiles = field(catalog, "onlineControllerProfiles")
                                ready = store.activeFile() != null && profiles != null
                                diagnostic = "active=${store.activeFile()} pending=${field(store, "pendingSaved")} rejected=${field(store, "rejectedSaved")} profiles=${profiles != null} destroyed=${activity.isDestroyed}"
                                if (ready) check(catalog.javaClass.getDeclaredMethod("controllerProfileCatalog")
                                    .apply { isAccessible = true }.invoke(catalog) === profiles)
                            } else {
                                val store = field(catalog, "snapshot") as CatalogSnapshotStore
                                val update = field(catalog, "update") as JSONObject
                                ready = store.activeFile() != null && update.getJSONObject("games").length() > 0
                                if (ready) {
                                    val games = update.getJSONObject("games")
                                    val id = games.keys().asSequence().first { games.getJSONObject(it).has("controller") }
                                    val source = catalog.javaClass.getMethod("sourceOf", String::class.java, String::class.java, String::class.java)
                                        .invoke(catalog, id, "controller", null)
                                    check(source == "Updated catalog") { "Controller source: $source" }
                                }
                            }
                        } catch (caught: Throwable) { error = caught }
                    }
                    error?.let { throw it }
                    if (!ready && attempt % 200 == 199) {
                        val worker = Thread.getAllStackTraces().entries.firstOrNull { it.key.name == "Kairo-catalog-update" }
                        test.sendStatus(0, android.os.Bundle().apply {
                            putString("stream", "$diagnostic worker=${worker?.key?.state} ${worker?.value?.take(8)?.joinToString()}\n")
                        })
                    }
                    if (!ready) Thread.sleep(100)
                }
            }
            check(ready) { "Host did not activate its saved downloaded catalog: $diagnostic" }
        } finally { test.runOnMainSync { activity.finish() } }
    }
}
