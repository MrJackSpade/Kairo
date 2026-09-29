package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.res.Configuration
import android.text.InputType
import android.widget.EditText
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Shared scaling preferences, dialogs, and placement for emulator video surfaces. */
class GraphicsOptions(
    private val activity: Activity,
    private val preferences: SharedPreferences,
    private val showDialog: (AlertDialog.Builder) -> AlertDialog,
    private val onChanged: () -> Unit,
    private val reportInvalid: (String) -> Unit
) {
    data class Viewport(val width: Int, val height: Int, val topMargin: Int)

    private var integerScaling = true
    private var integerCrop = false
    private var portraitNotchPadding = 0

    private fun isPortrait() = activity.resources.configuration.orientation ==
        Configuration.ORIENTATION_PORTRAIT

    fun load() {
        val suffix = if (isPortrait()) "portrait" else "landscape"
        integerScaling = preferences.getBoolean("integer_scaling_$suffix",
            preferences.getBoolean("integer_scaling", true))
        integerCrop = preferences.getBoolean("integer_crop_$suffix",
            preferences.getBoolean("integer_crop", false))
        portraitNotchPadding = preferences.getInt("portrait_notch_padding", 0).coerceIn(0, 240)
    }

    private fun scalingLabel() = when {
        !integerScaling -> "Fit display"
        integerCrop -> "Integer crop"
        else -> "Integer full image"
    }

    fun settingsLabel(): String = scalingLabel() +
        if (isPortrait()) " · notch $portraitNotchPadding dp" else ""

    fun show() {
        val orientation = if (isPortrait()) "portrait" else "landscape"
        val items = if (isPortrait()) arrayOf(
            "Scaling  ·  ${scalingLabel()}", "Notch padding  ·  $portraitNotchPadding dp")
        else arrayOf("Scaling  ·  ${scalingLabel()}")
        showDialog(AlertDialog.Builder(activity).setTitle("Graphics · $orientation")
            .setItems(items) { _, which ->
                if (which == 0) showScalingChoices(orientation) else showNotchPadding()
            }.setNegativeButton("Close", null))
    }

    private fun showScalingChoices(orientation: String) {
        val options = arrayOf("Integer  ·  full image (default)",
            "Integer  ·  crop edges", "Fit display  ·  fractional scale")
        val selected = if (!integerScaling) 2 else if (integerCrop) 1 else 0
        showDialog(AlertDialog.Builder(activity).setTitle("Scaling · $orientation")
            .setSingleChoiceItems(options, selected) { dialog, choice ->
                integerScaling = choice != 2
                integerCrop = choice == 1
                preferences.edit()
                    .putBoolean("integer_scaling_$orientation", integerScaling)
                    .putBoolean("integer_crop_$orientation", integerCrop).apply()
                onChanged()
                dialog.dismiss()
            }.setNegativeButton("Cancel", null))
    }

    private fun showNotchPadding() {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(portraitNotchPadding.toString())
            selectAll()
        }
        showDialog(AlertDialog.Builder(activity).setTitle("Portrait notch padding (dp)")
            .setView(input).setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().toIntOrNull()?.coerceIn(0, 240)
                if (value == null) {
                    reportInvalid("Enter a number from 0 to 240")
                    return@setPositiveButton
                }
                portraitNotchPadding = value
                preferences.edit().putInt("portrait_notch_padding", value).apply()
                onChanged()
            }.setNegativeButton("Cancel", null))
    }

    /** Kairo98's portrait policy pins the image below the notch; landscape centers it. */
    fun viewport(containerWidth: Int, containerHeight: Int, keyboardHeight: Int,
                 sourceWidth: Int, sourceHeight: Int, sourceAspect: Double): Viewport? {
        if (containerWidth <= 0 || containerHeight <= 0) return null
        val width = sourceWidth.coerceAtLeast(1)
        val height = sourceHeight.coerceAtLeast(1)
        val portrait = containerHeight.toLong() * 4 >= containerWidth.toLong() * 5
        val topPadding = if (portrait) Ui.dp(activity, portraitNotchPadding) else 0
        val availableHeight = (containerHeight - keyboardHeight - topPadding).coerceAtLeast(1)
        val aspect = sourceAspect.takeIf { it in 0.5..3.0 } ?: width.toDouble() / height
        val correctedHeight = width / aspect
        val fit = minOf(containerWidth / width.toDouble(), availableHeight / correctedHeight)
        if (fit <= 0.0) return null
        val scale = if (integerScaling && fit >= 1.0) {
            if (integerCrop) ceil(fit) else floor(fit)
        } else fit
        val outputWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val outputHeight = (correctedHeight * scale).roundToInt().coerceAtLeast(1)
        val top = if (portrait) topPadding
            else ((availableHeight - outputHeight) / 2).coerceAtLeast(0)
        return Viewport(outputWidth, outputHeight, top)
    }
}
