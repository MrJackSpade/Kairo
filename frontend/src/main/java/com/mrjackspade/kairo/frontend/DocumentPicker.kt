package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Intent
import android.net.Uri

/** Native document-provider selection for files copied into an app's own storage. */
object DocumentPicker {
    fun open(activity: Activity, requestCode: Int, type: String = "*/*",
             failed: (String) -> Unit): Boolean = try {
        @Suppress("DEPRECATION")
        activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = type
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, requestCode)
        true
    } catch (error: Exception) {
        failed(error.message ?: activity.getString(R.string.kairo_file_chooser_failed))
        false
    }

    /**
     * The system folder picker at [start] (FolderStart); the result is a tree URI ([selectedUri]).
     * Without a start, the RG DS's picker crashed reading where it was last.
     */
    fun openFolder(activity: Activity, requestCode: Int, start: Uri?, failed: (String) -> Unit): Boolean = try {
        @Suppress("DEPRECATION")
        activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { if (start != null) putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, start) }, requestCode)
        true
    } catch (error: Exception) {
        failed(error.message ?: activity.getString(R.string.kairo_folder_chooser_failed))
        false
    }

    fun selectedUri(resultCode: Int, data: Intent?): Uri? =
        data?.data?.takeIf { resultCode == Activity.RESULT_OK }
}
