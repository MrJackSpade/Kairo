package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offers a newer stable GitHub release of the app, once per launch. The check runs on its own
 * thread when the app starts; the prompt waits until the library is showing with nothing open
 * over it. "Not now" skips it until the next launch and "Skip this version" until a newer release.
 * Builds with `kairo_update_check` false (Google Play builds, which Play updates) never check. A
 * failed check is logged and nothing is shown.
 */
class AppUpdateCheck(
    private val activity: Activity,
    private val identity: KairoActivity.Identity,
    private val preferences: SharedPreferences,
    private val pages: PageStack,
    private val ready: () -> Boolean,
    private val showStatus: (String) -> Unit
) {
    private var pending: AppRelease? = null
    private val installed: String by lazy {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
            ?: error("${activity.packageName} has no version name")
    }

    fun start() {
        if (!activity.resources.getBoolean(R.bool.kairo_update_check) || checkedThisLaunch) return
        checkedThisLaunch = true
        Thread {
            try {
                val release = AppReleases.newer(fetchReleases(), installed) ?: return@Thread
                activity.runOnUiThread {
                    if (activity.isDestroyed) return@runOnUiThread
                    pending = release
                    showIfReady()
                }
            } catch (error: Exception) {
                // The error is in the message too: some devices keep only an entry's first line.
                Log.e(TAG, "Update check for ${identity.repository} failed: $error", error)
            }
        }.apply { name = "${identity.appName}-update-check"; start() }
    }

    /** Shows a waiting release once the library is showing with nothing open over it. */
    fun showIfReady() {
        val release = pending ?: return
        if (!ready() || pages.top != null) return
        pending = null
        if (preferences.getString(SKIPPED_VERSION, null) == release.tag) return
        pages.push(ModalPage(activity.getString(R.string.kairo_update_available),
            activity.getString(R.string.kairo_update_available_text, identity.appName, release.version, installed), listOf(
                ModalButton(activity.getString(R.string.kairo_update_not_now)),
                ModalButton(activity.getString(R.string.kairo_update_skip)) { preferences.edit().putString(SKIPPED_VERSION, release.tag).apply() },
                ModalButton(activity.getString(R.string.kairo_update_view), ButtonTone.PRIMARY) { open(release.url) })))
    }

    private fun open(url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (error: ActivityNotFoundException) {
            showStatus(activity.getString(R.string.kairo_update_no_app, url))
        }
    }

    private fun fetchReleases(): String {
        val connection = URL("https://api.github.com/repos/${identity.repository}/releases?per_page=100")
            .openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "${identity.appName}/$installed")
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "GitHub returned ${connection.responseCode} ${connection.responseMessage}"
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "KairoUpdateCheck"
        const val SKIPPED_VERSION = "update_skipped_version"
        /** Once per process, so recreating the activity does not check again. */
        var checkedThisLaunch = false
    }
}
