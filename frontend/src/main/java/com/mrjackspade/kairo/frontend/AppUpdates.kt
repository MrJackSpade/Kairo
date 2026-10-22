package com.mrjackspade.kairo.frontend

import org.json.JSONArray

/** A published release of the app on GitHub. */
data class AppRelease(val tag: String, val version: String, val url: String)

/**
 * Which GitHub release, if any, is newer than the installed app. A release whose version has a
 * hyphen (a prerelease such as 1.2.0-beta) and a draft are never offered.
 */
object AppReleases {
    /** The newest stable release in a GitHub releases list newer than [installed], or null. */
    fun newer(releasesJson: String, installed: String): AppRelease? {
        val releases = JSONArray(releasesJson)
        val current = parse(installed.substringBefore('-'))
        val installedIsPrerelease = '-' in installed
        var newest: Pair<List<Int>, AppRelease>? = null
        for (index in 0 until releases.length()) {
            val release = releases.getJSONObject(index)
            val tag = release.getString("tag_name")
            val version = tag.removePrefix("v")
            if (release.getBoolean("draft") || '-' in version) continue
            val parsed = parse(version)
            if (newest == null || compare(parsed, newest.first) > 0)
                newest = parsed to AppRelease(tag, version, release.getString("html_url"))
        }
        val (version, release) = newest ?: return null
        val order = compare(version, current)
        // A stable release of the installed prerelease's own version is newer than it.
        return release.takeIf { order > 0 || order == 0 && installedIsPrerelease }
    }

    /** A dotted numeric version, such as 1.2.0. */
    fun parse(version: String): List<Int> {
        val parts = version.split('.')
        require(parts.isNotEmpty() && parts.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
            "Not a release version: $version"
        }
        return parts.map(String::toInt)
    }

    fun compare(a: List<Int>, b: List<Int>): Int {
        for (index in 0 until maxOf(a.size, b.size)) {
            val difference = a.getOrElse(index) { 0 } - b.getOrElse(index) { 0 }
            if (difference != 0) return difference
        }
        return 0
    }
}
