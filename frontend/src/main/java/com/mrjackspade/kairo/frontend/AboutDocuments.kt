package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog
import android.view.KeyEvent
import android.widget.ScrollView

/** Shared About navigation and offline text-document viewer. The host supplies its own content. */
object AboutDocuments {
    fun show(activity: Activity, appName: String, message: String,
             privacyAsset: String, noticesAsset: String) {
        val version = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        val dialog = AlertDialog.Builder(activity).setTitle("$appName $version")
            .setMessage(message)
            .setNeutralButton("Licenses") { _, _ ->
                showAsset(activity, "Third-party licenses", noticesAsset, Ui.LABEL)
            }
            .setNegativeButton("Privacy policy") { _, _ ->
                showAsset(activity, "$appName Privacy Policy", privacyAsset, Ui.SECONDARY)
            }
            .setPositiveButton("Done", null).create()
        dialog.show()
        Ui.styleDialog(dialog)
    }

    fun showAsset(activity: Activity, title: String, asset: String, textSize: Float) {
        val content = try {
            activity.assets.open(asset).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            "This document could not be opened."
        }
        val padding = Ui.dp(activity, 20)
        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            isFocusableInTouchMode = true
            addView(Ui.text(activity, content, textSize).apply {
                setPadding(padding, padding, padding, padding)
            })
        }
        val dialog = AlertDialog.Builder(activity).setTitle(title)
            .setView(scroll).setPositiveButton("Done", null).create()
        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN ||
                (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN))
                false
            else {
                scroll.smoothScrollBy(0, Ui.dp(activity,
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) 96 else -96))
                true
            }
        }
        dialog.show()
        Ui.styleDialog(dialog)
        scroll.requestFocus()
    }
}
