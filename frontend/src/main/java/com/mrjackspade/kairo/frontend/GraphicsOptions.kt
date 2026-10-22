package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.Configuration
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Shared scaling preferences, rows, and placement for emulator video surfaces. */
class GraphicsOptions(
    private val activity: Activity,
    private val preferences: SharedPreferences,
    private val onChanged: () -> Unit
) {
    data class Viewport(val width: Int, val height: Int, val topMargin: Int)

    private var integerScaling = true
    private var integerCrop = false
    private var portraitNotchPadding = 0

    private fun isPortrait() = activity.resources.configuration.orientation ==
        Configuration.ORIENTATION_PORTRAIT

    fun load() {
        val (integer, crop) = scaling(if (isPortrait()) "portrait" else "landscape")
        integerScaling = integer
        integerCrop = crop
        portraitNotchPadding = preferences.getInt("portrait_notch_padding", 0).coerceIn(0, 240)
    }

    /** Scaling is saved per orientation; older installs had one value for both. */
    private fun scaling(orientation: String) = preferences.getBoolean("integer_scaling_$orientation",
        preferences.getBoolean("integer_scaling", true)) to preferences.getBoolean("integer_crop_$orientation",
        preferences.getBoolean("integer_crop", false))

    private fun scalingChoice(orientation: String, label: String) = ChoiceSpec(label, { "All games" },
        { listOf(ChoiceGroup(null, listOf(
            ChoiceOption("integer", "Integer", "Full image (default)"),
            ChoiceOption("crop", "Integer crop", "Crops the edges"),
            ChoiceOption("fit", "Fit display", "Fractional scale")))) },
        {
            val (integer, crop) = scaling(orientation)
            if (!integer) "fit" else if (crop) "crop" else "integer"
        },
        { choice ->
            preferences.edit().putBoolean("integer_scaling_$orientation", choice != "fit")
                .putBoolean("integer_crop_$orientation", choice == "crop").apply()
            load()
            onChanged()
        })

    /** The DISPLAY group of the Graphics page. */
    fun displayGroup() = RowGroup("DISPLAY", listOf(
        SettingsRow.choice("scale-landscape", "Landscape scaling", scalingChoice("landscape", "Landscape scaling")),
        SettingsRow.choice("scale-portrait", "Portrait scaling", scalingChoice("portrait", "Portrait scaling")),
        SettingsRow.slider("notch", "Portrait notch padding", { "$portraitNotchPadding dp" },
            SliderSpec(0, 240, 1, 4, { portraitNotchPadding }) { value ->
                portraitNotchPadding = value
                preferences.edit().putInt("portrait_notch_padding", value).apply()
                onChanged()
            })))

    /** The Graphics page: display and shader settings, plus any groups the app adds. */
    /** The page; [shader] is null in an app without shaders, which then lists none. */
    fun page(shader: ShaderOptions?, extra: () -> List<RowGroup> = { emptyList() }) =
        GroupsPage("Graphics", { "All games" }, { listOfNotNull(displayGroup(), shader?.group()) + extra() })

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
