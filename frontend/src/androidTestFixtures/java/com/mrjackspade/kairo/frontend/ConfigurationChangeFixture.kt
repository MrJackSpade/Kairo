package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.ParcelFileDescriptor

/** A running game keeps its activity, session and machine through in-place configuration changes. */
object ConfigurationChangeFixture {
    private const val HANDLED = ActivityInfo.CONFIG_MCC or ActivityInfo.CONFIG_MNC or
        ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_TOUCHSCREEN or
        ActivityInfo.CONFIG_KEYBOARD or ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
        ActivityInfo.CONFIG_NAVIGATION or ActivityInfo.CONFIG_SCREEN_LAYOUT or
        ActivityInfo.CONFIG_FONT_SCALE or ActivityInfo.CONFIG_UI_MODE or
        ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_DENSITY or
        ActivityInfo.CONFIG_SCREEN_SIZE or ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
        ActivityInfo.CONFIG_LAYOUT_DIRECTION or ActivityInfo.CONFIG_COLOR_MODE or
        ActivityInfo.CONFIG_FONT_WEIGHT_ADJUSTMENT or ActivityInfo.CONFIG_GRAMMATICAL_GENDER

    fun verify(test: Instrumentation, uri: String): String {
        val context = test.targetContext
        val dos = context.packageName.endsWith("kairodos")
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse(uri))
            .putExtra("kairo98.skipStartupChoices", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (component in listOf(launch.component!!,
                ComponentName(context, SecondaryDisplayActivity::class.java))) {
            val declared = context.packageManager.getActivityInfo(component, 0).configChanges
            check((declared and HANDLED) == HANDLED) {
                "${component.className} recreates for configChanges 0x" +
                    Integer.toHexString(HANDLED and declared.inv())
            }
        }
        val activity = test.startActivitySync(launch)
        fun ui(action: () -> Unit) {
            var error: Throwable? = null
            test.runOnMainSync { try { action() } catch (caught: Throwable) { error = caught } }
            test.waitForIdleSync()
            error?.let { throw it }
        }
        fun field(name: String) = activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity)
        fun status() = activity.javaClass.getDeclaredMethod("nativeStatus")
            .apply { isAccessible = true }.invoke(activity).toString()
        fun await(message: String, condition: () -> Boolean) {
            repeat(600) {
                var ready = false
                ui { ready = condition() }
                if (ready) return
                Thread.sleep(100)
            }
            error("$message; native=${status()}")
        }
        fun session() = field(if (dos) "currentGame" else "currentDisk")
        fun running() = session() != null &&
            (if (dos) status() == "2" else status().startsWith("Running"))
        fun companion(): Activity? = SecondaryDisplayCoordinator::class.java
            .getDeclaredField("companion").apply { isAccessible = true }
            .get(field(if (dos) "secondaryDisplay" else "secondaryKeyboard")) as Activity?
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
            test.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        fun survives(change: String, apply: () -> Unit, applied: (Configuration) -> Boolean) {
            var identity: Any? = null
            var secondary: Activity? = null
            ui {
                identity = session()
                secondary = companion()
            }
            apply()
            await("$change did not reach the game") { activity.isDestroyed || applied(activity.resources.configuration) }
            Thread.sleep(500)
            ui {
                check(!activity.isDestroyed) { "$change recreated the game activity" }
                check(session() === identity) { "$change replaced the game session" }
                secondary?.let { check(!it.isDestroyed && companion() === it) { "$change recreated the companion" } }
            }
            await("Game stopped after $change", ::running)
        }
        val night = shell("cmd uimode night").substringAfter(':').trim()
        val fontScale = shell("settings get system font_scale")
        fun restore() {
            shell("cmd uimode night $night")
            shell(if (fontScale == "null") "settings delete system font_scale"
                else "settings put system font_scale $fontScale")
        }
        try {
            await("Game did not start", ::running)
            fun isNight(configuration: Configuration) =
                (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            var nightMode = false
            ui { nightMode = isNight(activity.resources.configuration) }
            for (enable in listOf(!nightMode, nightMode)) {
                survives("Dark mode ${if (enable) "on" else "off"}",
                    { shell("cmd uimode night ${if (enable) "yes" else "no"}") }) { isNight(it) == enable }
            }
            var scale = 1f
            ui { scale = activity.resources.configuration.fontScale }
            for (target in listOf(scale + 0.15f, scale)) {
                survives("Font scale $target",
                    { shell("settings put system font_scale $target") }) { it.fontScale == target }
            }
        } finally {
            restore()
            ui { activity.finish() }
        }
        return "${context.packageName}: dark mode and font scale changes kept the game " +
            "activity, session and machine running: OK\n"
    }
}
