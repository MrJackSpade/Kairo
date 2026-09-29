package com.mrjackspade.kairo.frontend

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

/** Rendering and selection behavior shared by global and per-game touch settings. */
object TouchInputSettingsDialog {
    data class Options(
        val title: String,
        val modeLabels: List<String>,
        val modeIndex: Int,
        val directTouch: Boolean,
        val secondaryTouchpad: Boolean? = null,
        val directTouchExplanation: String,
        val onSave: (modeIndex: Int, directTouch: Boolean, secondaryTouchpad: Boolean?) -> Unit,
        val onReset: (() -> Unit)? = null,
        val resetLabel: String = "Use catalog defaults"
    ) {
        init { require(modeLabels.isNotEmpty() && modeIndex in modeLabels.indices) }
    }

    fun builder(context: Context, options: Options): AlertDialog.Builder {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 22), Ui.dp(context, 8), Ui.dp(context, 22), 0)
        }
        fun heading(value: String) = Ui.sectionLabel(context, value).apply {
            setPadding(0, Ui.dp(context, 14), 0, Ui.dp(context, 4))
        }
        fun group(labels: List<String>, selected: Int) = RadioGroup(context).apply {
            labels.forEachIndexed { index, label ->
                addView(RadioButton(context).apply {
                    id = View.generateViewId()
                    text = label
                    textSize = Ui.SECONDARY
                    isChecked = index == selected
                })
            }
        }
        body.addView(heading("SCREEN TAP"))
        val modeGroup = group(options.modeLabels, options.modeIndex)
        body.addView(modeGroup)
        body.addView(heading("MOUSE TOUCH"))
        val touchGroup = group(listOf("Touchpad (drag to move, tap to click)",
            "Direct tap (click where you touch)"), if (options.directTouch) 1 else 0)
        body.addView(touchGroup)
        val secondaryGroup = options.secondaryTouchpad?.let { enabled ->
            body.addView(heading("SECOND SCREEN START MODE"))
            group(listOf("Keyboard", "Mouse touchpad"), if (enabled) 1 else 0)
                .also { body.addView(it) }
        }
        body.addView(TextView(context).apply {
            text = options.directTouchExplanation
            textSize = Ui.LABEL
            setTextColor(Ui.TEXT_MUTED)
            setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 8))
        })
        fun checkedIndex(group: RadioGroup) =
            (0 until group.childCount).indexOfFirst {
                (group.getChildAt(it) as RadioButton).isChecked
            }.coerceAtLeast(0)
        val dialog = AlertDialog.Builder(context)
            .setTitle(options.title)
            .setView(ScrollView(context).apply { addView(body) })
            .setPositiveButton("Save") { _, _ ->
                options.onSave(checkedIndex(modeGroup), checkedIndex(touchGroup) == 1,
                    secondaryGroup?.let { checkedIndex(it) == 1 })
            }
            .setNegativeButton("Cancel", null)
        options.onReset?.let { dialog.setNeutralButton(options.resetLabel) { _, _ -> it() } }
        return dialog
    }
}
