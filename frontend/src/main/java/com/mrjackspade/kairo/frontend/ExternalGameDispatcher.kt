package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Intent
import android.net.Uri
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Resolves launcher game requests and prevents superseded inspections from opening. A request
 * names a library game when the file it names is at that game's path in the library folder
 * (Kairo#56) or is that game's document; any other file is read, keyed by its path relative to
 * the library folder, or by its URI where it has none ([ExternalGameIntent.key]). [claim] then
 * moves data an earlier version kept for the file's games, before they open.
 */
class ExternalGameDispatcher<T : LibraryItem>(
    private val activity: Activity,
    private val knownEntries: () -> List<T>,
    private val libraryTree: () -> Uri?,
    private val sourceUri: (T) -> Uri,
    private val inspect: (ExternalGameFile, AtomicBoolean) -> List<T>,
    private val claim: (ExternalGameFile, List<T>) -> List<T>,
    private val onResolved: (List<T>, Boolean) -> Unit,
    private val showStatus: (String, LibraryScreen.StatusKind) -> Unit,
    private val threadName: String
) {
    private var generation = 0
    private var cancellation: AtomicBoolean? = null

    /** Every new intent supersedes the previous one, including known or invalid requests. */
    fun dispatch(intent: Intent) {
        cancellation?.set(true)
        cancellation = null
        val requestGeneration = ++generation
        val request = try { ExternalGameIntent.file(intent, activity, libraryTree()) }
        catch (failure: Exception) {
            showStatus(failure.message ?: activity.getString(R.string.kairo_external_invalid), LibraryScreen.StatusKind.MESSAGE)
            return
        } ?: return
        val library = knownEntries().filter {
            it.path == request.key || ExternalGameIntent.sameDocument(sourceUri(it), request.uri)
        }
        val cancelled = AtomicBoolean(false)
        cancellation = cancelled
        showStatus(activity.getString(R.string.kairo_external_opening, request.name), LibraryScreen.StatusKind.WORK)
        Thread {
            val result = runCatching {
                val entries = library.ifEmpty { inspect(request, cancelled) }
                claim(request, entries) to library.isEmpty()
            }
            activity.runOnUiThread {
                if (requestGeneration != generation || cancelled.get() || activity.isDestroyed)
                    return@runOnUiThread
                cancellation = null
                result.onSuccess { (entries, inspected) -> onResolved(entries, inspected) }
                    .onFailure { failure ->
                        showStatus(activity.getString(R.string.kairo_external_open_failed, request.name, failure.message.toString()),
                            LibraryScreen.StatusKind.MESSAGE)
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
