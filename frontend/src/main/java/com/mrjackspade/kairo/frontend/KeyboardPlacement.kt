package com.mrjackspade.kairo.frontend

import android.content.Context
import android.content.SharedPreferences

/**
 * Where the game screen's keyboard sits. Split, on a screen 16:10 or wider, its halves fill the
 * free width at each side of the picture, which is drawn exactly as with no keyboard: the keyboard
 * never resizes, moves or covers it. Docked, its rows run along the bottom with the picture above.
 * Split Keyboard (Controls › KEYBOARD, on unless turned off) chooses between them on a wide screen.
 */
class KeyboardPlacement(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val changed: () -> Unit
) {
    var splitEnabled: Boolean
        get() = preferences.getBoolean(PREFERENCE, true)
        private set(value) { preferences.edit().putBoolean(PREFERENCE, value).apply() }

    /**
     * The halves on a screen [width] by [height] beside a picture [pictureLeft] from the left and
     * [pictureWidth] wide, as drawn with no keyboard; null to dock: a narrower screen, Split
     * Keyboard off, or a picture that leaves less than a sixth of the screen free at a side.
     */
    fun split(width: Int, height: Int, pictureLeft: Int, pictureWidth: Int): KeyboardSides? =
        if (splitEnabled && wide(width, height)) sides(width, pictureLeft, pictureWidth) else null

    /** Controls › KEYBOARD. */
    fun group() = RowGroup(context.getString(R.string.kairo_keyboard_group), listOf(
        SettingsRow.switch("split-keyboard", context.getString(R.string.kairo_split_keyboard), { splitEnabled }) { on ->
            splitEnabled = on
            changed()
        }))

    companion object {
        private const val PREFERENCE = "split_keyboard"

        /** A screen 16:10 or wider, such as 16:9. */
        fun wide(width: Int, height: Int) = width > 0 && height > 0 && width.toLong() * 10 >= height.toLong() * 16

        /**
         * The narrowest half, as a share of the screen's width: one sixth, the 320 of 1920 pixels
         * beside a 640x400 game at 2x on the RP6, tried on the device and usable.
         */
        const val MIN_SIDE_SHARE_DENOMINATOR = 6

        /** Whether a half [side] wide is wide enough on a screen [width] wide. */
        fun usable(side: Int, width: Int) = side > 0 && side.toLong() * MIN_SIDE_SHARE_DENOMINATOR >= width

        /**
         * The free width left and right of the picture, or null when either side is narrower than
         * a sixth of the screen ([usable]); the keyboard then stays along the bottom.
         */
        fun sides(width: Int, pictureLeft: Int, pictureWidth: Int): KeyboardSides? {
            val left = pictureLeft
            val right = width - pictureLeft - pictureWidth
            return if (usable(left, width) && usable(right, width)) KeyboardSides(left, right) else null
        }
    }
}

/** The widths of the split keyboard's halves, in pixels. */
data class KeyboardSides(val left: Int, val right: Int)
