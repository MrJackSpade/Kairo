package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.DateFormat
import java.util.Date

/** Shared save/load slot picker. Emulator backends own the state and thumbnail files. */
object StateSlotDialog {
    data class Slot(val index: Int, val savedAt: Long?, val thumbnail: Bitmap?)

    fun show(activity: Activity, title: String?, saving: Boolean, slots: List<Slot>,
             onSave: (Int) -> Unit, onLoad: (Int) -> Unit) {
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 12), Ui.dp(activity, 4),
                Ui.dp(activity, 12), Ui.dp(activity, 4))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle((if (saving) "Save state" else "Load state") +
                (title?.let { " · $it" } ?: ""))
            .setView(ScrollView(activity).apply { addView(list) })
            .setNegativeButton("Cancel", null).create()
        dialog.show()
        Ui.styleDialog(dialog)
        slots.forEach { slot ->
            val available = saving || slot.savedAt != null
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(activity, 8), Ui.dp(activity, 8),
                    Ui.dp(activity, 8), Ui.dp(activity, 8))
                isFocusable = available
                isClickable = available
                background = Ui.rowBackground(activity)
                alpha = if (available) 1f else 0.45f
            }
            val preview = if (slot.thumbnail != null) ImageView(activity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = Ui.rounded(activity, Ui.BG, 4)
                clipToOutline = true
                setImageBitmap(slot.thumbnail)
            } else FrameLayout(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = Ui.dp(activity, 4).toFloat()
                    setStroke(Ui.dp(activity, 1), Ui.LINE,
                        Ui.dp(activity, 4).toFloat(), Ui.dp(activity, 3).toFloat())
                }
            }
            row.addView(preview, LinearLayout.LayoutParams(Ui.dp(activity, 128), Ui.dp(activity, 80)))
            val labels = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(activity, 14), 0, 0, 0)
            }
            labels.addView(Ui.text(activity, "Slot ${slot.index}", Ui.BODY))
            labels.addView(Ui.text(activity,
                slot.savedAt?.let { format.format(Date(it)) }
                    ?: if (saving) "Empty · tap to save here" else "Empty",
                Ui.SECONDARY, Ui.TEXT_MUTED))
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            if (available) row.setOnClickListener {
                dialog.dismiss()
                val savedAt = slot.savedAt
                when {
                    saving && savedAt == null -> onSave(slot.index)
                    saving -> {
                        val confirm = AlertDialog.Builder(activity)
                            .setTitle("Overwrite slot ${slot.index}?")
                            .setMessage("The save from ${format.format(Date(savedAt!!))} is replaced.")
                            .setPositiveButton("Overwrite") { _, _ -> onSave(slot.index) }
                            .setNegativeButton("Cancel", null).create()
                        confirm.show()
                        Ui.styleDialog(confirm)
                    }
                    else -> {
                        val confirm = AlertDialog.Builder(activity)
                            .setTitle("Load slot ${slot.index}?")
                            .setMessage("Progress since that save is lost.")
                            .setPositiveButton("Load") { _, _ -> onLoad(slot.index) }
                            .setNegativeButton("Cancel", null).create()
                        confirm.show()
                        Ui.styleDialog(confirm)
                    }
                }
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = Ui.dp(activity, 4)
            })
        }
        (0 until list.childCount).map(list::getChildAt).firstOrNull { it.isFocusable }?.requestFocus()
    }
}
