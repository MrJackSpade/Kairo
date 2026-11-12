package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

/** A web page the app links to: [label] names it on its row, [url] is opened in the player's browser. */
class WebLink(val label: UiText, val url: String) {
    /** Opens [url] in the player's browser; [failed] is told when no app can open it. */
    fun open(activity: Activity, failed: (String) -> Unit) = open(activity, url, failed)

    companion object {
        /** Opens [url] in the player's browser; [failed] is told when no app can open it. */
        fun open(activity: Activity, url: String, failed: (String) -> Unit) {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (error: ActivityNotFoundException) {
                failed(activity.getString(R.string.kairo_update_no_app, url))
            }
        }
    }
}
