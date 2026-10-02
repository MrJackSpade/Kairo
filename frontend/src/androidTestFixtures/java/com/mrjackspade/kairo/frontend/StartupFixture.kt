package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.ActivityOptions
import android.app.Instrumentation
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Debug
import android.os.SystemClock
import android.view.ViewTreeObserver
import android.widget.ListView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Measure the actual upper-display first frame and first populated library frame. */
object StartupFixture {
    @Volatile private var enabled = false
    private var began = 0L
    private var createMs = 0L
    private var firstMs = -1L
    private var rowsMs = -1L
    private var drawn = CountDownLatch(1)

    fun created(activity: Activity, beforeCreate: Long) {
        if (!enabled || !activity.javaClass.name.endsWith(".MainActivity")) return
        val flow = activity.javaClass.getDeclaredField("libraryFlow").apply { isAccessible = true }.get(activity) ?: return
        val screen = flow.javaClass.getMethod("getScreen").invoke(flow) as LibraryScreen<*>
        val list = screen.javaClass.getDeclaredField("list").apply { isAccessible = true }.get(screen) as ListView
        createMs = SystemClock.elapsedRealtime() - beforeCreate
        val observer = object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                val now = SystemClock.elapsedRealtime() - began
                if (firstMs < 0) firstMs = now
                if (rowsMs < 0 && screen.isShown && (0 until list.childCount).any { list.getChildAt(it).tag != null }) {
                    rowsMs = now
                    screen.post { screen.viewTreeObserver.removeOnDrawListener(this) }
                    drawn.countDown()
                }
            }
        }
        screen.viewTreeObserver.addOnDrawListener(observer)
    }

    fun measure(test: Instrumentation, trace: Boolean): String {
        val launch = test.targetContext.packageManager.getLaunchIntentForPackage(test.targetContext.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val display = (test.targetContext.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager).displays
            .firstOrNull { it.displayId != 0 && it.isValid }
        val options = ActivityOptions.makeBasic().apply { if (display != null) launchDisplayId = display.displayId }.toBundle()
        val out = StringBuilder()
        enabled = true
        try {
            if (trace) Debug.startMethodTracingSampling(File(test.targetContext.filesDir, "startup-profile.trace").path,
                96 * 1024 * 1024, 5000)
            repeat(if (trace) 1 else 3) { run ->
                firstMs = -1; rowsMs = -1; drawn = CountDownLatch(1)
                began = SystemClock.elapsedRealtime()
                val activity = test.startActivitySync(launch, options)
                try {
                    check(drawn.await(120, TimeUnit.SECONDS)) { "No populated library frame" }
                    out.append("run=$run onCreateMs=$createMs firstFrameMs=$firstMs libraryFrameMs=$rowsMs\n")
                    test.sendStatus(0, android.os.Bundle().apply { putString("stream", out.lines().last { it.isNotBlank() } + "\n") })
                    Thread.sleep(1000)
                } finally { test.runOnMainSync { activity.finish() }; test.waitForIdleSync() }
                Thread.sleep(1000)
            }
        } finally {
            if (trace) Debug.stopMethodTracing()
            enabled = false
        }
        return out.toString()
    }

    /** PC-98's format-specific fallback remains available after deferring its load. */
    fun verifyPc98Fallback(test: Instrumentation) {
        val type = Class.forName("com.mrjackspade.kairo98.GameCatalog")
        val catalog = type.getConstructor(android.content.Context::class.java).newInstance(test.targetContext)
        val delegate = type.getDeclaredField("nameIndex\$delegate").apply { isAccessible = true }
            .get(catalog) as Lazy<*>
        check(!delegate.isInitialized()) { "Bundled fallback index parsed eagerly" }
        val index = test.targetContext.assets.open("catalog/name-index-v1.json").use {
            org.json.JSONObject(it.readBytes().toString(Charsets.UTF_8))
        }
        val key = index.getJSONObject("names").keys().asSequence().first { it.all(Char::isLetterOrDigit) }
        val expected = index.getJSONObject("games").getJSONObject(index.getJSONObject("names").getString(key))
        val lookup = type.getDeclaredMethod("lookupByName", String::class.java).apply { isAccessible = true }
        val update = type.getDeclaredField("update").apply { isAccessible = true }
        val downloaded = org.json.JSONObject().put("nameIndex", org.json.JSONObject()
            .put("names", org.json.JSONObject().put(key, "fixture:1"))
            .put("games", org.json.JSONObject().put("fixture:1", org.json.JSONObject().put("title", "Downloaded match"))))
        update.set(catalog, downloaded)
        val online = lookup.invoke(catalog, "$key.hdi") as Pair<*, *>
        check(online.second == true && (online.first as org.json.JSONObject).getString("title") == "Downloaded match")
        check(!delegate.isInitialized()) { "Downloaded match unnecessarily parsed bundled fallback" }
        update.set(catalog, org.json.JSONObject())
        val bundled = lookup.invoke(catalog, "$key.hdi") as Pair<*, *>
        check(delegate.isInitialized() && bundled.second == false)
        check((bundled.first as org.json.JSONObject).toString() == expected.toString()) { "Bundled fallback changed" }
    }
}
