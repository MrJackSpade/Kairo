package com.mrjackspade.kairo.uitest

import android.graphics.Bitmap
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import org.json.JSONObject
import java.io.OutputStream

/**
 * One test's record: every step, wait, assertion and picture, as JSON lines in `log.jsonl`, with
 * screenshots and game pictures beside it. Files go through the AndroidX test storage service,
 * which keeps them outside the app's data (cleared between tests) under
 * /sdcard/googletest/test_outputfiles/<run>/<test class>/<test>/; tools/uitest.ps1 pulls them.
 */
class TestLog(testClass: String, testName: String) {
    private val storage = TestStorage()
    private val run = InstrumentationRegistry.getArguments().getString("runId") ?: "run"
    val directory = "$run/$testClass/$testName"
    private val test = "$testClass.$testName"
    private val lines: OutputStream = storage.openOutputFile("$directory/log.jsonl")
    private var files = 0

    /** Records a step: its [kind], such as "tap", "assert" or "waiting", and what it did. */
    @Synchronized fun step(kind: String, message: String, extra: Map<String, Any?> = emptyMap()) {
        val line = JSONObject().put("time", System.currentTimeMillis()).put("test", test).put("kind", kind)
            .put("message", message)
        extra.forEach { (name, value) -> line.put(name, value) }
        lines.write((line.toString() + "\n").toByteArray())
        lines.flush()
        Log.i(TAG, "$test $kind: $message")
    }

    /** Saves [bitmap] as a numbered PNG named for [name] and records it; returns the file name. */
    @Synchronized fun image(name: String, bitmap: Bitmap): String {
        val file = "%03d-%s.png".format(++files, name.replace(Regex("[^A-Za-z0-9._-]+"), "_"))
        storage.openOutputFile("$directory/$file").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        step("image", file, mapOf("width" to bitmap.width, "height" to bitmap.height))
        return file
    }

    /** Saves [text] as a numbered text file named for [name] and records it; returns the file name. */
    @Synchronized fun text(name: String, text: String): String {
        val file = "%03d-%s.txt".format(++files, name.replace(Regex("[^A-Za-z0-9._-]+"), "_"))
        storage.openOutputFile("$directory/$file").use { it.write(text.toByteArray()) }
        step("text", file)
        return file
    }

    fun close() = lines.close()

    companion object {
        const val TAG = "KairoUiTest"
    }
}
