package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog

/** Readable session diagnostics for the drawer's status action. */
object SessionStatusDialog {
    fun show(activity: Activity, title: String, details: List<Pair<String, String>>) {
        val message = details.joinToString("\n") { (label, value) -> "$label: $value" }
        val dialog = AlertDialog.Builder(activity).setTitle(title).setMessage(message)
            .setPositiveButton("Close", null).create()
        dialog.show()
        Ui.styleDialog(dialog)
    }
}
