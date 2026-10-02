package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Exercise the actual shared secondary-display pipeline inside each host app. */
object LibraryArtworkFixture {
    fun verify(test: Instrumentation) {
        val launch = test.targetContext.packageManager.getLaunchIntentForPackage(test.targetContext.packageName)!!
        val activity = test.startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun ui(action: () -> Unit) {
            var failure: Throwable? = null
            test.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
            test.waitForIdleSync()
            failure?.let { throw it }
        }
        fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.get(owner)
        lateinit var coordinator: SecondaryDisplayCoordinator
        lateinit var art: ImageView
        lateinit var description: View
        lateinit var panel: View
        lateinit var loader: Any
        fun png(color: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            return ByteArrayOutputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                bitmap.recycle()
                it.toByteArray()
            }
        }
        val red = png(Color.RED)
        val blue = png(Color.BLUE)
        val opens = ConcurrentHashMap<String, AtomicInteger>()
        val slowStarted = CountDownLatch(1)
        val releaseSlow = CountDownLatch(1)
        val slowClosed = CountDownLatch(1)
        val stoppingStarted = CountDownLatch(1)
        val releaseStopping = CountDownLatch(1)
        val stoppingClosed = CountDownLatch(1)
        val failed = CountDownLatch(1)
        val open: (String) -> java.io.InputStream = { path ->
            opens.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            when (path) {
                "slow" -> {
                    slowStarted.countDown()
                    check(releaseSlow.await(5, TimeUnit.SECONDS))
                    object : ByteArrayInputStream(red) {
                        override fun close() { super.close(); slowClosed.countDown() }
                    }
                }
                "failed" -> { failed.countDown(); throw IOException("Fixture decode source failure") }
                "stopping" -> {
                    stoppingStarted.countDown()
                    val deadline = android.os.SystemClock.uptimeMillis() + 5000
                    var released = false
                    while (!released && android.os.SystemClock.uptimeMillis() < deadline) {
                        try { released = releaseStopping.await(50, TimeUnit.MILLISECONDS) }
                        catch (_: InterruptedException) { /* Native decodes may finish after cancellation. */ }
                    }
                    check(released)
                    object : ByteArrayInputStream(red) {
                        override fun close() { super.close(); stoppingClosed.countDown() }
                    }
                }
                "red" -> ByteArrayInputStream(red)
                "blue" -> ByteArrayInputStream(blue)
                else -> error("Stale queued artwork was opened: $path")
            }
        }
        fun info(title: String, hasArt: Boolean = true) =
            SecondaryDisplayCoordinator.LibraryInfo(title, emptyList(), "Description for $title", null, hasArt)
        fun color(): Int? = (art.drawable as? BitmapDrawable)?.bitmap?.getPixel(0, 0)
        fun awaitColor(expected: Int) {
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            var found = false
            while (!found && android.os.SystemClock.uptimeMillis() < deadline) {
                ui { found = color() == expected }
                if (!found) Thread.sleep(20)
            }
            check(found) { "Expected preview color $expected" }
        }
        try {
            ui {
                coordinator = activity.javaClass.declaredFields.single {
                    it.type == SecondaryDisplayCoordinator::class.java
                }.apply { isAccessible = true }.get(activity) as SecondaryDisplayCoordinator
                val companion = field(coordinator, "companion") as Activity
                val content = field(companion, "content")!!
                art = field(content, "libraryArt") as ImageView
                description = field(content, "libraryDescription") as View
                panel = field(content, "libraryPanel") as View
                loader = field(coordinator, "libraryArtwork")!!
                coordinator.setLibraryInfo(info("Uncached red"), "red", open)
                check(color() == null && art.visibility == View.INVISIBLE)
            }
            val reservedWidth = description.width
            awaitColor(Color.RED)
            ui { check(description.width == reservedWidth) }
            ui { coordinator.setLibraryInfo(info("Blue"), "blue", open) }
            awaitColor(Color.BLUE)
            ui {
                coordinator.setLibraryInfo(info("Cached red"), "red", open)
                check(color() == Color.RED) { "Cached artwork had an empty intermediate update" }
            }
            check(opens["red"]!!.get() == 1)

            ui { coordinator.setLibraryInfo(info("Old slow selection"), "slow", open) }
            check(slowStarted.await(5, TimeUnit.SECONDS))
            ui {
                coordinator.setLibraryInfo(info("Obsolete queued selection"), "queued", open)
                coordinator.setLibraryInfo(info("Latest blue"), "blue", open)
                check(color() == Color.BLUE)
            }
            releaseSlow.countDown()
            check(slowClosed.await(5, TimeUnit.SECONDS))
            Thread.sleep(100)
            ui { check(color() == Color.BLUE) { "Stale artwork replaced the latest selection" } }
            check(opens["queued"] == null) { "Superseded queued artwork was decoded" }

            ui {
                coordinator.setLibraryInfo(info("Absent", false), null, open)
                check(color() == null && art.visibility == View.GONE)
            }
            ui { coordinator.setLibraryInfo(info("Failed"), "failed", open) }
            val failedWidth = description.width
            check(failed.await(5, TimeUnit.SECONDS))
            Thread.sleep(100)
            ui {
                check(color() == null && art.visibility == View.INVISIBLE)
                check(description.width == failedWidth) { "Failure reflowed the page" }
                coordinator.setLibraryInfo(info("Loading during stop"), "stopping", open)
            }
            check(stoppingStarted.await(5, TimeUnit.SECONDS))
            ui {
                loader.javaClass.getMethod("suspend").invoke(loader)
                coordinator.setLibraryInfo(null)
                loader.javaClass.getMethod("resume").invoke(loader)
                check(panel.visibility == View.GONE) { "Resume resurrected a cleared selection" }
            }
            releaseStopping.countDown()
            check(stoppingClosed.await(5, TimeUnit.SECONDS))
            Thread.sleep(100)
            ui {
                check(panel.visibility == View.GONE) { "Late decode resurrected a cleared selection after resume" }
                coordinator.setLibraryInfo(info("Resumed cached red"), "red", open)
                check(color() == Color.RED)
            }
        } finally {
            releaseSlow.countDown()
            releaseStopping.countDown()
            ui { activity.finish() }
        }
    }
}
