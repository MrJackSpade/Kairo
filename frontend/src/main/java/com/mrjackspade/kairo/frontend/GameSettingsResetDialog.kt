package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog

/** Confirmation for clearing every setting scoped to one game. */
object GameSettingsResetDialog {
    fun show(activity: Activity, onReset: () -> Unit, onFailure: (Exception) -> Unit) {
        val dialog = AlertDialog.Builder(activity).setTitle("Reset all game settings?")
            .setMessage("Remove every custom setting for this game and use its catalog and global defaults.")
            .setPositiveButton("Reset") { _, _ ->
                try { onReset() }
                catch (failure: Exception) { onFailure(failure) }
            }.setNegativeButton("Cancel", null).create()
        dialog.show()
        Ui.styleDialog(dialog)
    }
}
