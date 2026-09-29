package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText

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
        val currentPath: String,
        val hint: String,
        val explanation: String,
        val resetLabel: String,
        val onSave: (String?) -> Unit,
        val onReset: () -> Unit,
        val onCancel: () -> Unit = {}
    )

    fun show(activity: Activity, options: Options): AlertDialog {
        val input = EditText(activity).apply {
            setSingleLine(true)
            setText(options.currentPath)
            setSelection(text.length)
            hint = options.hint
        }
        return AlertDialog.Builder(activity).setTitle(options.title)
            .setMessage(options.explanation).setView(input)
            .setPositiveButton("Save") { _, _ ->
                options.onSave(input.text.toString().trim().takeIf(String::isNotEmpty))
            }
            .setNeutralButton(options.resetLabel) { _, _ -> options.onReset() }
            .setNegativeButton("Cancel") { _, _ -> options.onCancel() }
            .create().also { it.show(); Ui.styleDialog(it) }
    }
}
