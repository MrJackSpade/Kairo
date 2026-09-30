package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog

/** Shared validation for app-specific packaged artwork namespaces. */
object ArtworkOverridePath {
    fun valid(path: String, prefix: String, asciiOnly: Boolean = false,
              minLength: Int = 1): Boolean =
        path.length in minLength..256 && path.startsWith(prefix) &&
            !path.contains("..") && !path.contains('\\') && !path.startsWith('/') &&
            (!asciiOnly || path.matches(Regex("[a-zA-Z0-9/._-]+")))
}

/** The same per-game artwork editor for DOS and PC-98; storage stays with each catalog. */
object ArtworkOverrideEditor {
    data class Options(
        val title: String,
        val resetLabel: String,
        val onReset: () -> Unit,
        val onCancel: () -> Unit = {},
        val onChoose: () -> Unit
    )

    fun show(activity: Activity, options: Options): AlertDialog {
        return AlertDialog.Builder(activity).setTitle(options.title)
            .setMessage("Choose an image from this device. A copy is saved with your library.")
            .setPositiveButton("Choose image") { _, _ -> options.onChoose() }
            .setNeutralButton(options.resetLabel) { _, _ -> options.onReset() }
            .setNegativeButton("Cancel") { _, _ -> options.onCancel() }
            .setOnCancelListener { options.onCancel() }
            .create().also { it.show(); Ui.styleDialog(it) }
    }
}
