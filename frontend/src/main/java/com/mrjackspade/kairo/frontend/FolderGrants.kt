package com.mrjackspade.kairo.frontend

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Access to the folders and files the player chose through the system pickers, which Android
 * keeps as persisted URI grants. A document from a provider in this app's own package needs no
 * grant: the app reads its own provider, as the UI tests' document provider is (shared/uitest).
 */
object FolderGrants {
    /** Keeps access to [uri] across restarts: reading, and writing when [write] is set. */
    fun take(context: Context, uri: Uri, write: Boolean) {
        if (ownProvider(context, uri)) return
        context.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0))
    }

    /** Whether the app can still read [uri], and write it when [write] is set. */
    fun has(context: Context, uri: Uri, write: Boolean): Boolean = ownProvider(context, uri) ||
        context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && (!write || it.isWritePermission)
        }

    private fun ownProvider(context: Context, uri: Uri): Boolean {
        val authority = uri.authority ?: return false
        return context.packageManager.resolveContentProvider(authority, 0)?.packageName == context.packageName
    }
}
