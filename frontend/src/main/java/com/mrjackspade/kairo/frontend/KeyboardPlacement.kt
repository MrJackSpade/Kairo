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
     * Keyboard off, or a picture that leaves no free width at a side.
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

        /** The free width left and right of the picture, or null when either side has none. */
        fun sides(width: Int, pictureLeft: Int, pictureWidth: Int): KeyboardSides? {
            val left = pictureLeft
            val right = width - pictureLeft - pictureWidth
            return if (left > 0 && right > 0) KeyboardSides(left, right) else null
        }
    }
}

/** The widths of the split keyboard's halves, in pixels. */
data class KeyboardSides(val left: Int, val right: Int)
