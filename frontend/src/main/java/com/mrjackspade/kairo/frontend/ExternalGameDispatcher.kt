package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Resolves launcher game requests and prevents superseded inspections from opening. A request
 * names a library game when it is that game's document, or when the file it names holds the same
 * content as exactly one library game: a frontend app hands over its own URI or a path for a file
 * in the library folder, and the library's game keeps its saves and settings (Kairo98#63).
 */
class ExternalGameDispatcher<T>(
    private val activity: Activity,
    private val resolver: ContentResolver,
    private val knownEntries: () -> List<T>,
    private val sourceUri: (T) -> Uri,
    private val contentId: (T) -> String?,
    private val inspect: (ExternalGameFile, AtomicBoolean) -> List<T>,
    private val onResolved: (List<T>, Boolean) -> Unit,
    private val showStatus: (String) -> Unit,
    private val threadName: String
) {
    private var generation = 0
    private var cancellation: AtomicBoolean? = null

    /** Every new intent supersedes the previous one, including known or invalid requests. */
    fun dispatch(intent: Intent) {
        cancellation?.set(true)
        cancellation = null
        val requestGeneration = ++generation
        val request = try { ExternalGameIntent.file(intent, resolver) }
        catch (failure: Exception) {
            showStatus(failure.message ?: "Invalid game file")
            return
        } ?: return
        val matching = knownEntries().filter {
            ExternalGameIntent.sameDocument(sourceUri(it), request.uri)
        }
        if (matching.isNotEmpty()) {
            onResolved(matching, false)
            return
        }
        val cancelled = AtomicBoolean(false)
        cancellation = cancelled
        showStatus("Opening ${request.name}…")
        Thread {
            val result = runCatching { inspect(request, cancelled) }
            activity.runOnUiThread {
                if (requestGeneration != generation || cancelled.get() || activity.isDestroyed)
                    return@runOnUiThread
                cancellation = null
                result.onSuccess { inspected ->
                    val library = knownEntries()
                    val entries = inspected.map { entry ->
                        contentId(entry)?.let { id -> library.singleOrNull { contentId(it) == id } } ?: entry
                    }
                    onResolved(entries, entries.any { it in inspected })
                }.onFailure { failure ->
                    showStatus("Could not open ${request.name}: ${failure.message}")
                }
            }
        }.apply { name = threadName; start() }
    }

    fun cancel() {
        cancellation?.set(true)
        cancellation = null
        generation++
    }
}
