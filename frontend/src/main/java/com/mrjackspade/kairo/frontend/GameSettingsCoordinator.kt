package com.mrjackspade.kairo.frontend

/**
 * One per-game value: the user's own choice, if any, over a default that comes from a catalog,
 * the all-games setting, or the app.
 */
class GameValue<V>(
    /** The user's choice, or null when the game uses its default. */
    val custom: () -> V?,
    /** The value without the user's choice. */
    val fallback: () -> V,
    /** Where [fallback] comes from, as a source tag. */
    val source: () -> String,
    /** Null removes the user's choice. */
    val set: (V?) -> Unit
) {
    val effective: V get() = custom() ?: fallback()
}

/** Source tags name exactly where a value comes from. */
object GameSource {
    const val CUSTOM = "Custom"
    const val ALL_GAMES = "All games"
    const val BUILT_IN = "Built-in"

    /** Catalog layer names, mapped to the words the interface uses. */
    fun label(layer: String): String = when (layer) {
        "User override" -> CUSTOM
        "App default" -> BUILT_IN
        "Global" -> ALL_GAMES
        else -> layer
    }
}

/** Per-game rows shared by both apps. Each app supplies its storage and machine rows. */
object GameSettingsRows {
    const val NO_HASH = "This file needs a successful hash before settings can be saved"

    /**
     * A choice whose first option, Default, names where its value comes from. The row shows the
     * value with a source tag; values the user chose are tagged Custom and shown in Accent.
     */
    fun choice(id: String, label: String, scope: () -> String, options: List<ChoiceOption>,
               value: GameValue<String>, enabled: () -> Boolean = { true },
               disabledReason: () -> String? = { null }, foot: String? = null,
               defaultLabel: (() -> String)? = null): SettingsRow {
        fun optionLabel(option: String) = options.firstOrNull { it.id == option }?.label ?: option
        fun fallbackLabel() = defaultLabel?.invoke() ?: optionLabel(value.fallback())
        val spec = ChoiceSpec(label, { scope() }, {
            listOf(ChoiceGroup(null, listOf(ChoiceOption(DEFAULT, "Default",
                "${value.source()}: ${fallbackLabel()}")) + options, foot))
        }, { value.custom() ?: DEFAULT }, { picked -> value.set(picked.takeIf { it != DEFAULT }) })
        return SettingsRow.choice(id, label, spec,
            value = { value.custom()?.let(::optionLabel) ?: fallbackLabel() },
            tone = { if (value.custom() != null) ValueTone.CUSTOM else ValueTone.CURRENT },
            tag = { if (value.custom() != null) GameSource.CUSTOM else value.source() },
            enabled = enabled, disabledReason = disabledReason)
    }

    /**
     * A text value edited in a tray named after its row, with Reset only when the value is the
     * user's own. [empty] is shown, in faint text, when there is no value.
     */
    fun text(id: String, label: String, hint: String, current: () -> String?, custom: () -> Boolean,
             source: () -> String?, save: (String) -> Unit, reset: () -> Unit,
             empty: String = "None", enabled: () -> Boolean = { true },
             disabledReason: () -> String? = { null }) = SettingsRow.action(id, label,
        value = { current() ?: empty },
        tone = { when {
            custom() -> ValueTone.CUSTOM
            current() == null -> ValueTone.EMPTY
            else -> ValueTone.CURRENT
        } },
        tag = { if (custom()) GameSource.CUSTOM else source() },
        enabled = enabled, disabledReason = disabledReason,
        tray = {
            TraySpec(input = TrayInput(hint, current() ?: ""), buttons = listOfNotNull(
                if (custom()) TrayButton("Reset") { reset() } else null,
                TrayButton("Cancel"),
                TrayButton("Save", ButtonTone.PRIMARY) { save(it) }))
        })

    /** Location, ZIP entry, and content ID, readable without opening anything. */
    fun file(path: String, zipEntry: String?, contentId: String?, error: String?) = listOfNotNull(
        SettingsRow.info("location", "Location") { path },
        zipEntry?.let { SettingsRow.info("zip-entry", "ZIP entry") { it } },
        SettingsRow.info("content-id", "Content ID") { contentId ?: error ?: "Not hashed" })

    fun reset(enabled: () -> Boolean, run: () -> Unit) = SettingsRow.action("reset", "Reset all game settings",
        enabled = enabled, disabledReason = { NO_HASH },
        confirm = Confirm("Remove every custom setting for this game and use its catalog and global defaults.",
            "Reset", run = run))

    private const val DEFAULT = "default"
}

/** The Game settings page: the game's name in the scope chip, and rows the app assembles. */
fun gameSettingsPage(title: () -> String, groups: () -> List<RowGroup>) =
    GroupsPage("Game settings", title, groups)
