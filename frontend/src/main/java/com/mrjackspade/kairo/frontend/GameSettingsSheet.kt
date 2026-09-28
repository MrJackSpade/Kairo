package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

data class GameSettingsRow(
    val title: String,
    val value: String,
    val needsHash: Boolean,
    val destructive: Boolean = false,
    val action: () -> Unit
)

/** Product-specific options rendered with the same game settings UI. */
object GameSettingsSheet {
    fun show(activity: Activity, title: String, playable: Boolean, hashed: Boolean,
             sections: List<Pair<String, List<GameSettingsRow>>>,
             play: () -> Unit, reset: (() -> Unit)?, noHash: () -> Unit): AlertDialog {
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(4))
        }
        val builder = AlertDialog.Builder(activity).setTitle(title)
            .setView(ScrollView(activity).apply { addView(list) })
            .setNegativeButton("Close", null)
        if (playable) builder.setPositiveButton("Play") { _, _ -> play() }
        if (reset != null) builder.setNeutralButton("Reset defaults") { _, _ -> reset() }
        val dialog = builder.create()
        dialog.show()
        Ui.styleDialog(dialog)
        sections.forEach { (heading, rows) ->
            list.addView(TextView(activity).apply {
                text = heading
                textSize = Ui.LABEL
                letterSpacing = 0.14f
                setTextColor(Ui.ACCENT)
                setPadding(dp(12), dp(if (heading.isEmpty()) 6 else 14), dp(12), dp(4))
            })
            rows.forEach { row ->
                val item = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                    isFocusable = true
                    isClickable = true
                    background = Ui.rowBackground(activity)
                    setOnClickListener {
                        if (!hashed && row.needsHash) {
                            noHash()
                            return@setOnClickListener
                        }
                        dialog.dismiss()
                        row.action()
                    }
                }
                item.addView(TextView(activity).apply {
                    text = row.title
                    textSize = Ui.BODY
                    setTextColor(if (row.destructive) Ui.DANGER else Color.WHITE)
                })
                item.addView(TextView(activity).apply {
                    text = row.value
                    textSize = Ui.LABEL
                    setTextColor(Ui.TEXT_MUTED)
                })
                list.addView(item, LinearLayout.LayoutParams(-1, -2))
            }
        }
        (0 until list.childCount).map(list::getChildAt).firstOrNull { it.isFocusable }?.requestFocus()
        return dialog
    }
}
