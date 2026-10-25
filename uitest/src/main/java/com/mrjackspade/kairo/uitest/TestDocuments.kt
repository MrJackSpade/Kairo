package com.mrjackspade.kairo.uitest

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.DocumentsContract
import com.mrjackspade.kairo.frontend.UiTestDocuments
import java.io.File

/**
 * The test's games and BIOS files, as the system picker would offer them. The test APK carries
 * them as assets under `uitest/` (exploded from the encrypted test resources at build time,
 * tools/test_resources.py); [install] copies them into the app's own storage, where the UI test
 * build's document provider (shared/uitest/provider) serves them. [answerFolder] and [answerFile]
 * then stand in for the person picking in the system picker, which the tests never drive.
 */
class TestDocuments(private val instrumentation: Instrumentation, private val log: TestLog) {
    private val target = instrumentation.targetContext
    private val root = File(target.filesDir, UiTestDocuments.DIRECTORY)
    private val authority = UiTestDocuments.authority(target.packageName)

    /** Copies every test resource into the provider's folder, replacing what was there. */
    fun install() {
        root.deleteRecursively()
        var files = 0L
        var bytes = 0L
        fun copy(asset: String, into: File) {
            val children = instrumentation.context.assets.list(asset).orEmpty()
            if (children.isEmpty()) {
                into.parentFile!!.mkdirs()
                instrumentation.context.assets.open(asset).use { input -> into.outputStream().use { bytes += input.copyTo(it) } }
                files++
            } else for (child in children) copy("$asset/$child", File(into, child))
        }
        val top = instrumentation.context.assets.list(ASSETS).orEmpty()
        check(top.isNotEmpty()) { "The test APK has no test resources under assets/$ASSETS (see tools/test_resources.py)" }
        for (child in top) copy("$ASSETS/$child", File(root, child))
        // An empty folder a test can choose for the app to write into, such as a save folder.
        check(File(root, "saves").mkdirs()) { "Could not create the test saves folder" }
        log.step("resources", "installed $files files, $bytes bytes", mapOf("folders" to top.joinToString()))
    }

    /** Moves a test document, as a player moving a file in another app would; [to] is created. */
    fun move(from: String, to: String) {
        val source = File(root, from)
        val target = File(root, to)
        check(source.isFile) { "No test document $from" }
        target.parentFile!!.mkdirs()
        check(source.renameTo(target)) { "Could not move $from to $to" }
        log.step("documents", "moved $from to $to")
    }

    fun exists(path: String) = File(root, path).exists()

    /**
     * Adds [count] small files named "Filler N.<[extension]>" to [folder], each with its own bytes,
     * so a library holds thousands of disks as a player's does. They are not real disks; nothing
     * starts them.
     */
    fun addFiller(folder: String, extension: String, count: Int) {
        val target = File(root, folder)
        check(target.mkdirs() || target.isDirectory) { "Could not create $folder" }
        for (index in 1..count) {
            File(target, "Filler %05d.%s".format(index, extension)).writeBytes(ByteArray(256) { (index + it).toByte() } +
                index.toString().toByteArray())
        }
        log.step("documents", "added $count filler .$extension files to $folder")
    }

    /** The files under a folder of the test documents, as paths below the documents' root. */
    fun files(folder: String): List<String> =
        File(root, folder).walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toList()

    /** A folder of the test resources, such as "games" or "bios", as a picked document tree. */
    fun folder(path: String): Uri {
        check(File(root, path).isDirectory) { "The test resources have no folder $path" }
        return DocumentsContract.buildTreeDocumentUri(authority, path)
    }

    /** A file of the test resources, such as "bios/n88.rom", as a picked document. */
    fun file(path: String): Uri {
        check(File(root, path).isFile) { "The test resources have no file $path" }
        return DocumentsContract.buildDocumentUri(authority, path)
    }

    /** Runs [action], answering the folder picker it opens with [folder]; fails if it opens none. */
    fun answerFolder(folder: Uri, action: () -> Unit) = answer(Intent.ACTION_OPEN_DOCUMENT_TREE, folder, action)

    /** Runs [action], answering the file picker it opens with [file]; fails if it opens none. */
    fun answerFile(file: Uri, action: () -> Unit) = answer(Intent.ACTION_OPEN_DOCUMENT, file, action)

    private fun answer(pickerAction: String, uri: Uri, action: () -> Unit) {
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
        val monitor = instrumentation.addMonitor(IntentFilter(pickerAction), result, true)
        log.step("picker", "answering $pickerAction with $uri")
        try {
            action()
            Patience.until(log, "the app to open the picker ($pickerAction)") { monitor.hits > 0 }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private companion object { const val ASSETS = "uitest" }
}
