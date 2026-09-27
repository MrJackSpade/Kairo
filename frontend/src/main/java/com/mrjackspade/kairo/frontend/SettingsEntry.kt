package com.mrjackspade.kairo.frontend

/** A product supplies the value and action; both apps render the same settings row. */
data class SettingsEntry(val title: String, val value: () -> String, val action: () -> Unit)
