package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText

/** A displayed value and the layer that supplied it. */
data class GameSettingsValue(val effective: String, val source: String? = null) {
    fun label(): String = source?.let { "$effective \u00b7 $it" } ?: effective
}

/** Common game settings; each emulator supplies only its own machine rows and actions. */
data class CommonGameSettings(
    val title: GameSettingsValue,
    val touch: GameSettingsValue,
    val controller: GameSettingsValue,
    val boxArt: GameSettingsValue,
    val screenshot: GameSettingsValue,
    val filePath: String,
    val zipEntry: String?,
    val contentId: String?,
    val error: String?,
    val deleteKind: String? = null
)

data class CommonGameSettingsActions(
    val play: () -> Unit,
    val editTouch: () -> Unit,
    val editController: () -> Unit,
    val editTitle: () -> Unit,
    val editBoxArt: () -> Unit,
    val editScreenshot: () -> Unit,
    val viewScreenshot: () -> Unit,
    val delete: (() -> Unit)? = null,
    val reset: (() -> Unit)? = null,
    val resetFailed: (Exception) -> Unit = {},
    val resetCancelled: (() -> Unit)? = null,
    val noHash: () -> Unit
)

object GameSettingsCoordinator {
    fun show(activity: Activity, settings: CommonGameSettings,
             playable: Boolean, machineRows: List<GameSettingsRow>,
             actions: CommonGameSettingsActions): AlertDialog {
        val fileDescription = if (settings.zipEntry == null) "Path and content ID"
            else "Path, ZIP entry, and content ID"
        val controls = listOf(
            GameSettingsRow("Touch input", settings.touch.label(), true, action = actions.editTouch),
            GameSettingsRow("Controller mapping", settings.controller.label(), true,
                action = actions.editController))
        val library = listOf(
            GameSettingsRow("Title", settings.title.label(), true, action = actions.editTitle),
            GameSettingsRow("Box art", settings.boxArt.label(), true,
                action = actions.editBoxArt),
            GameSettingsRow("Screenshot", settings.screenshot.label(), true,
                action = actions.editScreenshot),
            GameSettingsRow("View screenshot",
                if (settings.screenshot.effective == "None") "No screenshot available"
                else "Open full size", false, action = actions.viewScreenshot),
            GameSettingsRow("File information", fileDescription, false) {
                val location = settings.filePath +
                    (settings.zipEntry?.let { "\n$it" } ?: "")
                val dialog = AlertDialog.Builder(activity).setTitle("File information")
                    .setMessage("$location\n\n${settings.contentId ?: settings.error ?: "Not hashed"}")
                    .setPositiveButton("Close", null).create()
                dialog.show()
                Ui.styleDialog(dialog)
            }) + (if (settings.deleteKind != null && actions.delete != null)
                listOf(GameSettingsRow("Delete ${settings.deleteKind}",
                    "Permanently remove from device storage", false,
                    destructive = true, action = actions.delete)) else emptyList())
        val sections = listOf("CONTROLS" to controls,
            "MACHINE" to machineRows, "LIBRARY" to library)
        val reset = if (settings.contentId == null || actions.reset == null) null else {
            { GameSettingsResetDialog.show(activity, actions.reset, actions.resetFailed,
                actions.resetCancelled) }
        }
        return GameSettingsSheet.show(activity, settings.title.effective, playable,
            settings.contentId != null, sections, actions.play, reset, actions.noHash)
    }
}

/** The same title editing controls and per-field reset in both libraries. */
object GameTitleEditor {
    fun show(activity: Activity, current: String, save: (String) -> Unit,
             reset: () -> Unit, cancel: () -> Unit) {
        val input = EditText(activity).apply {
            setSingleLine(true)
            setText(current)
            setSelection(text.length)
            hint = "Game title"
        }
        val dialog = AlertDialog.Builder(activity).setTitle("Game title").setView(input)
            .setPositiveButton("Save") { _, _ -> save(input.text.toString().trim()) }
            .setNeutralButton("Reset") { _, _ -> reset() }
            .setNegativeButton("Cancel") { _, _ -> cancel() }.create()
        dialog.show()
        Ui.styleDialog(dialog)
    }
}
