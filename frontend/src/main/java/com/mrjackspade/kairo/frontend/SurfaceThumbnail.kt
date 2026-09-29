package com.mrjackspade.kairo.frontend

import android.graphics.Bitmap
import android.os.Handler
import android.view.PixelCopy
import android.view.SurfaceView

/** Captures a small preview of the guest surface for a save-state slot. */
object SurfaceThumbnail {
    fun capture(surface: SurfaceView?, handler: Handler, done: (Bitmap?) -> Unit) {
        if (surface == null || surface.width <= 0 || surface.height <= 0 ||
            !surface.holder.surface.isValid) {
            done(null)
            return
        }
        val bitmap = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(surface, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) done(bitmap)
                else { bitmap.recycle(); done(null) }
            }, handler)
        } catch (_: IllegalArgumentException) {
            bitmap.recycle()
            done(null)
        }
    }
}
