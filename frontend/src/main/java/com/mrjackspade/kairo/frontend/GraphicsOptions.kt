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
    /** The pages the anchor picker opens on. */
    private val pages: () -> PageStack,
    private val onChanged: () -> Unit
) {
    /** The picture's size and its offset from the screen's top left; an offset is negative where the picture is cut. */
    data class Viewport(val width: Int, val height: Int, val leftMargin: Int, val topMargin: Int)

    /** How the picture fills the area it is given; kept per orientation by [id]. */
    enum class Scaling(val id: String) {
        INTEGER("integer"), CROP("crop"), FIT("fit"),
        /** Fills the whole area and ignores the aspect ratio (Kairo#128). */
        STRETCH("stretch");

        companion object {
            fun of(id: String) = requireNotNull(entries.firstOrNull { it.id == id }) { "Unknown scaling: $id" }
        }
    }

    private var scaling = Scaling.INTEGER
    private var portraitNotchPadding = 0

    private fun isPortrait() = activity.resources.configuration.orientation ==
        Configuration.ORIENTATION_PORTRAIT

    /** "portrait" or "landscape", as the scaling and anchor settings are kept. */
    fun orientation() = if (isPortrait()) "portrait" else "landscape"

    /** Where the picture sits for all games in [orientation] (Kairo#54). */
    fun anchor(orientation: String): Anchor =
        preferences.getString("scaling_anchor_$orientation", null)?.let(Anchor::of) ?: Anchor.default(orientation)

    private fun anchorRow(orientation: String, label: String): SettingsRow {
        val id = "anchor-$orientation"
        return SettingsRow.action(id, label, { activity.getString(anchor(orientation).label) }, { ValueTone.CURRENT }) {
            pages().push(AnchorPage(label, { GameSource.ALL_GAMES }, { anchor(orientation).id },
                { anchor(orientation).id }, null, { picked ->
                    preferences.edit().putString("scaling_anchor_$orientation", requireNotNull(picked)).apply()
                    onChanged()
                }, id))
        }
    }

    fun load() {
        scaling = scaling(orientation())
        portraitNotchPadding = preferences.getInt("portrait_notch_padding", 0).coerceIn(0, 240)
    }

    /**
     * Scaling is saved per orientation. Installs from before Stretch kept it as two switches, and
     * older ones as one pair for both orientations; those still read as they were set.
     */
    fun scaling(orientation: String): Scaling {
        preferences.getString("scaling_$orientation", null)?.let { return Scaling.of(it) }
        val integer = preferences.getBoolean("integer_scaling_$orientation", preferences.getBoolean("integer_scaling", true))
        val crop = preferences.getBoolean("integer_crop_$orientation", preferences.getBoolean("integer_crop", false))
        return if (!integer) Scaling.FIT else if (crop) Scaling.CROP else Scaling.INTEGER
    }

    private fun str(id: Int, vararg args: Any): String = activity.getString(id, *args)

    private fun scalingChoice(orientation: String, label: String) = ChoiceSpec(label, { GameSource.ALL_GAMES },
        { listOf(ChoiceGroup(null, listOf(
            ChoiceOption("integer", UiText.res(R.string.kairo_scaling_integer), UiText.res(R.string.kairo_scaling_integer_help)),
            ChoiceOption("crop", UiText.res(R.string.kairo_scaling_crop), UiText.res(R.string.kairo_scaling_crop_help)),
            ChoiceOption("fit", UiText.res(R.string.kairo_scaling_fit), UiText.res(R.string.kairo_scaling_fit_help)),
            ChoiceOption("stretch", UiText.res(R.string.kairo_scaling_stretch), UiText.res(R.string.kairo_scaling_stretch_help))))) },
        { scaling(orientation).id },
        { choice ->
            preferences.edit().putString("scaling_$orientation", Scaling.of(choice).id).apply()
            load()
            onChanged()
        })

    /** The DISPLAY group of the Graphics page. */
    fun displayGroup() = RowGroup(str(R.string.kairo_display_group), listOf(
        SettingsRow.choice("scale-landscape", str(R.string.kairo_scaling_landscape),
            scalingChoice("landscape", str(R.string.kairo_scaling_landscape))),
        SettingsRow.choice("scale-portrait", str(R.string.kairo_scaling_portrait),
            scalingChoice("portrait", str(R.string.kairo_scaling_portrait))),
        anchorRow("landscape", str(R.string.kairo_anchor_landscape)),
        anchorRow("portrait", str(R.string.kairo_anchor_portrait)),
        SettingsRow.slider("notch", str(R.string.kairo_notch_padding), { str(R.string.kairo_dp, portraitNotchPadding) },
            SliderSpec(0, 240, 1, 4, { portraitNotchPadding }) { value ->
                portraitNotchPadding = value
                preferences.edit().putInt("portrait_notch_padding", value).apply()
                onChanged()
            })))

    /** The Graphics page: display and shader settings, plus any groups the app adds. */
    /** The page; [shader] is null in an app without shaders, which then lists none. */
    fun page(shader: ShaderOptions?, extra: () -> List<RowGroup> = { emptyList() }) =
        GroupsPage(str(R.string.kairo_graphics), { GameSource.ALL_GAMES }, { listOfNotNull(displayGroup(), shader?.group()) + extra() })

    /**
     * The picture's size and place: [anchor] pins it within the screen above the keyboard, and in
     * portrait below the notch padding.
     */
    fun viewport(containerWidth: Int, containerHeight: Int, keyboardHeight: Int,
                 sourceWidth: Int, sourceHeight: Int, sourceAspect: Double, anchor: Anchor): Viewport? {
        if (containerWidth <= 0 || containerHeight <= 0) return null
        val width = sourceWidth.coerceAtLeast(1)
        val height = sourceHeight.coerceAtLeast(1)
        val portrait = containerHeight.toLong() * 4 >= containerWidth.toLong() * 5
        val topPadding = if (portrait) Ui.dp(activity, portraitNotchPadding) else 0
        val availableHeight = (containerHeight - keyboardHeight - topPadding).coerceAtLeast(1)
        if (scaling == Scaling.STRETCH) return Viewport(containerWidth, availableHeight, 0, topPadding)
        val aspect = sourceAspect.takeIf { it in 0.5..3.0 } ?: width.toDouble() / height
        val correctedHeight = width / aspect
        val fit = minOf(containerWidth / width.toDouble(), availableHeight / correctedHeight)
        if (fit <= 0.0) return null
        val scale = when {
            fit < 1.0 || scaling == Scaling.FIT -> fit
            scaling == Scaling.CROP -> ceil(fit)
            else -> floor(fit)
        }
        val outputWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val outputHeight = (correctedHeight * scale).roundToInt().coerceAtLeast(1)
        return Viewport(outputWidth, outputHeight, anchor.offset(containerWidth, outputWidth, row = false),
            topPadding + anchor.offset(availableHeight, outputHeight, row = true))
    }

    /**
     * The picture's place on the second screen, [width] by [height]: the whole screen with Stretch
     * picked for the screen's orientation, else as large as it fits, centred.
     */
    fun secondaryViewport(width: Int, height: Int, sourceAspect: Double): Viewport {
        val orientation = if (height.toLong() * 4 >= width.toLong() * 5) "portrait" else "landscape"
        if (scaling(orientation) == Scaling.STRETCH) return Viewport(width, height, 0, 0)
        val aspect = sourceAspect.takeIf { it in 0.5..3.0 } ?: 1.6
        val sourceHeight = 640.0 / aspect
        val scale = minOf(width / 640.0, height / sourceHeight)
        val gameWidth = (640 * scale).roundToInt().coerceAtLeast(1)
        val gameHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        return Viewport(gameWidth, gameHeight, (width - gameWidth) / 2, (height - gameHeight) / 2)
    }
}
