package com.mrjackspade.kairo.frontend

/** A product supplies the value and action; both apps render the same settings row. */
data class SettingsEntry(val title: String, val value: () -> String, val action: () -> Unit) {
    companion object {
        /** Both hosts display the sound state; audio implementation stays in the host. */
        fun sound(muted: () -> Boolean, toggle: () -> Unit) =
            SettingsEntry("Sound", { if (muted()) "Muted" else "On" }, toggle)
    }
}
