package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLStreamHandler
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Runs the real host adapter and shared storage against isolated app directories. */
object CatalogInstallFixture {
    fun verify(test: Instrumentation, packageFile: String? = null): String {
        val product = if (test.targetContext.packageName.endsWith("kairodos")) "dos" else "pc98"
        val namespace = if (product == "dos") "kairodos.DosGameCatalog" else "kairo98.GameCatalog"
        val directory = File(test.targetContext.cacheDir, "catalog-fixture-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(test.targetContext) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
            override fun getApplicationContext(): Context = this
        }
        val type = Class.forName("com.mrjackspade.$namespace")
        fun create() = type.getConstructor(Context::class.java).newInstance(isolated) as LibraryCatalog
        val id = "sha256-${if (product == "dos") "dos-manifest" else "hdi"}-v1:" + "a".repeat(64)
        val path = if (product == "dos") "art/catalog/dos/aa/${"a".repeat(64)}/000000000000/boxArt.webp"
            else "art/catalog/pc98/999999/box-000000000000.webp"
        val source = "https://catalog-fixture.invalid/installed.json"
        val download = "https://catalog-fixture.invalid/package.zip"
        val replies = HashMap<String, ByteArray>()
        val redirects = HashMap<String, String>()
        val requests = ArrayList<String>()
        URL.setURLStreamHandlerFactory { protocol -> if (protocol != "https") null else object : URLStreamHandler() {
            override fun openConnection(url: URL) = object : HttpURLConnection(url) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
                override fun getResponseCode(): Int { requests += url.toString(); return if (redirects.containsKey(url.toString())) 302 else if (replies.containsKey(url.toString())) 200 else 404 }
                override fun getHeaderField(name: String): String? = if (name == "Location") redirects[url.toString()] else null
                override fun getInputStream() = (replies[url.toString()] ?: error("Uninstalled source requested")).inputStream()
                override fun getContentLengthLong() = replies[url.toString()]?.size?.toLong() ?: -1L
            }
        } }
        fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val image = ByteArrayOutputStream().also { output ->
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            check(bitmap.compress(Bitmap.CompressFormat.WEBP, 90, output)); bitmap.recycle()
        }.toByteArray()
        fun pack(revision: Long, title: String = "Installed fixture $revision", bad: String? = null): ByteArray {
            val record = JSONObject().put("title", title).put("artwork", JSONObject().put("boxArt", path))
            if (bad == "record") record.put("executablePlugin", "no")
            val data = JSONObject().put("schemaVersion", 1).put("games", JSONObject().put(id, record))
            if (product == "dos") {
                data.put("folders", JSONObject().put("fixture", JSONArray().put(id)))
                data.put("controllers", JSONObject().put("schemaVersion", 1).put("profiles", JSONObject())
                    .put("assignments", JSONObject()).put("presets", JSONObject()))
            } else data.put("nameIndex", JSONObject().put("schemaVersion", 1)
                .put("games", JSONObject().put("pc98:999999", record))
                .put("names", JSONObject().put("fixturelookup", "pc98:999999")))
            val files = linkedMapOf("data.json" to data.toString().toByteArray(), path to image)
            if (bad == "image") files[path] = "not an image".toByteArray()
            if (bad == "path") files["../escape.webp"] = image
            val inventory = JSONObject()
            for ((name, bytes) in files) inventory.put(name, JSONObject().put("size", bytes.size)
                .put("sha256", if (bad == "checksum" && name == path) "0".repeat(64) else sha(bytes)))
            val header = JSONObject().put("schemaVersion", 1).put("product", if (bad == "product") "other" else product)
                .put("id", if (bad == "identity") "different" else "fixture").put("name", "Fixture catalog")
                .put("revision", revision).put("updateManifest", if (bad == "source") "https://other.invalid/source.json" else source)
                .put("files", inventory)
            files["catalog.json"] = header.toString().toByteArray()
            if (bad == "json") files["data.json"] = "{broken".toByteArray()
            return ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip ->
                for ((name, bytes) in files) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            } }.toByteArray()
        }
        fun offer(revision: Long, bytes: ByteArray, checksum: String = sha(bytes)) {
            replies[download] = bytes
            replies[source] = JSONObject().put("schemaVersion", 1).put("id", "fixture").put("product", product)
                .put("revision", revision).put("size", bytes.size).put("sha256", checksum).put("archive", download)
                .toString().toByteArray()
        }
        try {
            val catalog = create()
            val store = catalog.installedCatalogs!!
            check(store.catalogs().isEmpty() && !store.update() && requests.isEmpty())
            check(catalog.resolve(id, "fixture.zip").title == "fixture.zip" || catalog.resolve(id, "fixture.zip").title == "fixture")
            val ownedGame = File(isolated.filesDir, "owned-game.zip").apply { writeText("user data") }
            for (bad in listOf("product", "json", "path")) {
                check(runCatching { store.importFile(pack(1, bad = bad).inputStream()) }.isFailure)
                check(store.catalogs().isEmpty())
            }
            // Content mistakes are handled on access, not by a full installation audit.
            for (bad in listOf("record", "checksum", "image")) {
                store.importFile(pack(1, bad = bad).inputStream())
                check(catalog.resolve(id, "fixture.zip").title == "Installed fixture 1")
                if (bad == "image") {
                    val bytes = store.openArtwork(path)!!.use { it.readBytes() }
                    check(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null)
                }
                check(!File(isolated.filesDir, "escape.webp").exists())
                store.remove("fixture")
            }
            store.importFile(pack(1).inputStream())
            check(catalog.resolve(id, "fixture.zip").title == "Installed fixture 1")
            check(catalog.openArtwork(catalog.resolve(id, "fixture.zip").boxArt!!).use { it.readBytes() }.contentEquals(image))
            if (product == "pc98") check(catalog.resolve("sha256-hdi-v1:" + "b".repeat(64), "fixturelookup.hdi").title == "Installed fixture 1")
            val reopened = create()
            check(reopened.resolve(id, "fixture.zip").title == "Installed fixture 1")
            offer(2, pack(2))
            check(store.update())
            check(catalog.resolve(id, "fixture.zip").title == "Installed fixture 2")
            check(requests.toSet() == setOf(source, download))
            for (bad in listOf("identity", "source", "json")) {
                offer(3, pack(3, bad = bad))
                check(runCatching { store.update() }.isFailure)
                check(catalog.resolve(id, "fixture.zip").title == "Installed fixture 2")
                check(create().resolve(id, "fixture.zip").title == "Installed fixture 2")
            }
            offer(3, pack(3), "0".repeat(64))
            check(runCatching { store.update() }.isFailure)
            offer(1, pack(1))
            check(runCatching { store.update() }.isFailure)
            if (product == "dos") type.getMethod("setTitle", String::class.java, String::class.java).invoke(catalog, id, "Personal title")
            else type.getMethod("setOverride", String::class.java, String::class.java, Any::class.java).invoke(catalog, id, "title", "Personal title")
            val artStore = type.getMethod("getArtworkStore").invoke(catalog) as CatalogArtworkStore
            val local = artStore.importLocal(image.inputStream())
            type.getMethod("setArtworkOverride", String::class.java, String::class.java, String::class.java).invoke(catalog, id, "boxArt", local)
            offer(3, pack(3))
            redirects[download] = "http://catalog-fixture.invalid/insecure.zip"
            check(runCatching { store.update() }.isFailure)
            redirects[download] = download
            check(runCatching { store.update() }.isFailure)
            val cdn = "https://cdn-fixture.invalid/package.zip"
            replies[cdn] = replies.getValue(download)
            redirects[download] = cdn
            check(store.update())
            redirects.clear()
            check(catalog.resolve(id, "fixture.zip").title == "Personal title")
            check(catalog.resolve(id, "fixture.zip").boxArt == local)
            store.remove("fixture")
            check(store.catalogs().isEmpty() && store.artwork(path) == null)
            check(File(isolated.filesDir, "installed-catalogs").listFiles()!!.isEmpty())
            check(catalog.resolve(id, "fixture.zip").title == "Personal title")
            check(catalog.resolve(id, "fixture.zip").boxArt == local && catalog.openArtwork(local).use { it.read() } >= 0)
            check(ownedGame.readText() == "user data")
            val before = requests.size
            check(!store.update() && requests.size == before)
            packageFile?.let { filename ->
                // Supplied separately by the test operator, never packaged in an APK.
                test.sendStatus(0, android.os.Bundle().apply { putString("stream", "Importing supplied catalog archive…\n") })
                val started = android.os.SystemClock.elapsedRealtime()
                store.importFile(File(filename).inputStream())
                val elapsed = android.os.SystemClock.elapsedRealtime() - started
                test.sendStatus(0, android.os.Bundle().apply { putString("stream", "Full package import: ${elapsed}ms\n") })
                test.sendStatus(0, android.os.Bundle().apply { putString("stream", "Supplied archive installed; sampling records and images…\n") })
                val installed = store.catalogs().single()
                for (key in installed.data.getJSONObject("games").keys().asSequence().take(3)) {
                    check(catalog.resolve(key, "fixture.zip").title.isNotBlank())
                }
                for (art in installed.images.take(3)) check(catalog.openArtwork(art).use { it.read() } >= 0)
                // Measure the same inspect + atomic activation phase used after download,
                // separately from reading a large document through the file picker.
                val staged = File(isolated.cacheDir, "application-timing.zip")
                File(filename).inputStream().use { input -> staged.outputStream().use {
                    input.copyTo(it)
                    it.fd.sync() // Download/import finishes persisting bytes before APPLYING.
                } }
                val applyingStarted = android.os.SystemClock.elapsedRealtime()
                val candidate = InstalledCatalogs::class.java.getDeclaredMethod("inspect", File::class.java)
                    .apply { isAccessible = true }.invoke(store, staged)
                val parsed = android.os.SystemClock.elapsedRealtime()
                test.sendStatus(0, android.os.Bundle().apply { putString("stream", "Artwork header/data read: ${parsed - applyingStarted}ms\n") })
                InstalledCatalogs::class.java.getDeclaredMethod("activate", InstalledCatalogs.Catalog::class.java)
                    .apply { isAccessible = true }.invoke(store, candidate)
                val applyingElapsed = android.os.SystemClock.elapsedRealtime() - applyingStarted
                test.sendStatus(0, android.os.Bundle().apply { putString("stream", "Full artwork application: ${applyingElapsed}ms\n") })
                check(applyingElapsed < 5000) { "Artwork application exceeded five seconds: ${applyingElapsed}ms" }
                store.remove(installed.id)
                check(store.catalogs().isEmpty())
            }
            packageFile?.let { filename ->
                val core = File("$filename.core")
                if (core.isFile) {
                    val url = if (product == "dos") "https://raw.githubusercontent.com/MrJackSpade/Kairo/main/catalog/dos/core-v2.zip"
                        else "https://raw.githubusercontent.com/MrJackSpade/Kairo98/main/catalog/core-v2.json"
                    val metadata = if (product == "dos") url.removeSuffix("zip") + "json"
                        else url.removeSuffix("json") + "meta.json"
                    val bytes = core.readBytes()
                    replies[url] = bytes
                    replies[metadata] = JSONObject().put("schemaVersion", 1)
                        .put("archive", url.substringAfterLast('/')).put("sha256", sha(bytes))
                        .put("size", bytes.size).toString().toByteArray()
                    var applying = 0L
                    val task = CatalogUpdateTask { if (it == CatalogUpdateState.APPLYING)
                        applying = android.os.SystemClock.elapsedRealtime() }
                    check(type.getMethod("downloadUpdate", CatalogUpdateTask::class.java).invoke(catalog, task) == true)
                    check(applying > 0)
                    val elapsed = android.os.SystemClock.elapsedRealtime() - applying
                    test.sendStatus(0, android.os.Bundle().apply { putString("stream", "Full core application: ${elapsed}ms\n") })
                    check(elapsed < 5000) { "Core application exceeded five seconds: ${elapsed}ms" }
                }
            }
            store.close()
            reopened.installedCatalogs!!.close()
            return "$product: fresh core, format rejection, deferred content errors, installation, artwork, restart, matching, update, checksum/identity/source/schema rollback, overrides and removal: OK\n"
        } finally { directory.deleteRecursively() }
    }
}
