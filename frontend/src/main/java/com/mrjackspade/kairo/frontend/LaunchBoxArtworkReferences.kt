package com.mrjackspade.kairo.frontend

import org.json.JSONObject
import java.security.MessageDigest

/**
 * Catalog artwork stored as LaunchBox references (platform, game, image id) expands to its cache
 * path and URL before catalog layers merge. A reference that does not name real artwork fails
 * when its image is downloaded or opened, for that game alone.
 */
object LaunchBoxArtworkReferences {
    /**
     * Platform code -> cache folder, as shared/tools/launchbox_artwork.py PLATFORM_FOLDERS has
     * them. Codes are permanent once published.
     */
    private val PLATFORM_FOLDERS = mapOf(88 to "pc88", 98 to "pc98", 68 to "x68000", 86 to "fmtowns", 80 to "msx")

    fun expand(art: JSONObject): JSONObject {
        if (!art.has("platform")) return art
        val platform = art.optInt("platform")
        val folder = requireNotNull(PLATFORM_FOLDERS[platform]) { "Unknown artwork platform $platform" }
        val game = art.optInt("game")
        return JSONObject().apply {
            for ((kind, label) in listOf("boxArt" to "box", "preview" to "screenshot")) {
                val ref = art.optJSONObject(kind) ?: continue
                val id = ref.optString("id")
                val url = "https://images.launchbox-app.com/" +
                    (if (ref.optInt("revision") == 2) "r2_" else "") + id +
                    (if (ref.optInt("format") == 1) ".png" else ".jpg")
                put(kind, "art/catalog/$folder/$game/$label-${token(url)}.webp")
                put(kind + "Url", url)
            }
        }
    }

    // The same few URLs are expanded on every catalog lookup.
    private val tokens = java.util.concurrent.ConcurrentHashMap<String, String>()
    private fun token(url: String): String = tokens.getOrPut(url) {
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            .take(6).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    /** Layering copies every value it keeps, so only the replaced field needs a new object. */
    fun record(source: JSONObject?): JSONObject? = source?.let {
        JsonCopy.shallow(it).apply {
            optJSONObject("artwork")?.let { art -> put("artwork", expand(art)) }
        }
    }
}
