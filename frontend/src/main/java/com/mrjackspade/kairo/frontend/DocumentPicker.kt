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
        failed(error.message ?: "Could not open file chooser")
        false
    }

    fun selectedUri(resultCode: Int, data: Intent?): Uri? =
        data?.data?.takeIf { resultCode == Activity.RESULT_OK }
}
