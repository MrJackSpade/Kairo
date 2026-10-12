package com.mrjackspade.kairo.frontend

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration

/**
 * Kairo activities handle configuration changes in place so a running machine survives
 * them. Views still take pixel and text sizes from density and font settings when they
 * are built, so this records those settings for the owner to rebuild after they change.
 */
class ViewScale(private val context: Context) {
    private var built = Configuration(context.resources.configuration)

    /** Density or font settings differ from those the current views were built with. */
    val changed: Boolean
        get() = (built.diff(context.resources.configuration) and CHANGES) != 0

    /** Records the current settings once the views have been rebuilt for them. */
    fun rebuilt() { built = Configuration(context.resources.configuration) }

    private companion object {
        const val CHANGES = ActivityInfo.CONFIG_DENSITY or ActivityInfo.CONFIG_FONT_SCALE or
            ActivityInfo.CONFIG_FONT_WEIGHT_ADJUSTMENT
    }
}
