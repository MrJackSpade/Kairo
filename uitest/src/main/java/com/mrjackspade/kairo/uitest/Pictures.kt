package com.mrjackspade.kairo.uitest

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.Window
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.CountDownLatch
import kotlin.math.abs

/** A picture the app showed, and what the tests ask of it. */
class Picture(val bitmap: Bitmap) {
    /** The share of sampled pixels that are not nearly black: 0 for a blank screen. */
    fun litFraction(): Double = sample().count { (Color.red(it) + Color.green(it) + Color.blue(it)) > 48 }.toDouble() / samples

    /**
     * Whether the game drew something: more lit than a line of text, about 0.005 of the screen. A
     * cursor alone is far less; a game's own text prompt, such as Digger's, is more.
     */
    fun drawn(): Boolean = litFraction() > DRAWN

    /** How many colours the sampled pixels use: 1 for a blank screen, more for a picture. */
    fun distinctColors(): Int = sample().toSet().size

    /** The share of sampled pixels that differ clearly from [other]: 0 for the same picture. */
    fun difference(other: Picture): Double {
        require(bitmap.width == other.bitmap.width && bitmap.height == other.bitmap.height) {
            "Pictures of different sizes: ${bitmap.width}x${bitmap.height} and ${other.bitmap.width}x${other.bitmap.height}"
        }
        return sample().zip(other.sample()).count { (a, b) ->
            abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b)) > 48
        }.toDouble() / samples
    }

    private val samples = GRID * GRID
    private fun sample(): List<Int> = (0 until GRID).flatMap { row ->
        (0 until GRID).map { column ->
            bitmap.getPixel((column * 2 + 1) * bitmap.width / (GRID * 2), (row * 2 + 1) * bitmap.height / (GRID * 2))
        }
    }

    private companion object {
        const val GRID = 64
        const val DRAWN = 0.005
    }
}

/** Copies what the app shows: the game's picture, and screenshots of every window. */
class Pictures internal constructor(private val driver: Driver) {
    private val thread = HandlerThread("KairoUiTest-pixels").apply { start() }
    private val handler = Handler(thread.looper)

    /** The game's picture as the player sees it now, from the game surface; saved to the log. */
    fun game(name: String = "game"): Picture {
        val surface = driver.find(By.id("game.surface")).waitUntilShown()
        val view = driver.onMain { driver.resolve(listOf(By.id("game.surface"))).last() as SurfaceView }
        val picture = Picture(copy(view))
        driver.log.image(name, picture.bitmap)
        driver.log.step("picture", surface.description, mapOf("lit" to picture.litFraction(), "colors" to picture.distinctColors()))
        return picture
    }

    /** A screenshot of each of the app's windows (both screens of a dual-screen device), saved to the log. */
    fun screenshot(name: String) {
        val activities = driver.onMain {
            val registry = ActivityLifecycleMonitorRegistry.getInstance()
            (registry.getActivitiesInStage(Stage.RESUMED) + registry.getActivitiesInStage(Stage.PAUSED)).distinct()
        }
        for (activity in activities) {
            val label = "$name-${activity.localClassName.substringAfterLast('.')}-display${driver.onMain { activity.display?.displayId }}"
            driver.log.image(label, window(activity))
        }
    }

    /** An activity's window, with its surfaces (which a window copy leaves blank) drawn in place. */
    private fun window(activity: Activity): Bitmap {
        val window: Window = activity.window
        val decor = driver.onMain { window.decorView }
        val (width, height) = driver.onMain { decor.width to decor.height }
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(window, bitmap, { result = it; latch.countDown() }, handler)
        latch.await()
        check(result == PixelCopy.SUCCESS) { "Copying the window of ${activity.localClassName} failed ($result)" }
        val surfaces = driver.onMain { Views.descendants(decor).filterIsInstance<SurfaceView>().filter(Views::onScreen).toList() }
        val canvas = Canvas(bitmap)
        for (surface in surfaces) {
            val bounds = driver.onMain { Views.boundsInWindow(surface) }
            canvas.drawBitmap(copy(surface), null, bounds, null)
        }
        return bitmap
    }

    private fun copy(view: SurfaceView): Bitmap {
        val (width, height) = driver.onMain { view.width to view.height }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(view, bitmap, { result = it; latch.countDown() }, handler)
        latch.await()
        check(result == PixelCopy.SUCCESS) { "Copying ${Views.describe(view as View)} failed ($result)" }
        return bitmap
    }
}
