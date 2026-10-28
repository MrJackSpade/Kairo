package com.mrjackspade.kairo.frontend

import android.content.Context

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

/**
 * Source tags name exactly where a value comes from. A source is compared as it is here and put
 * into the player's language only where it shows ([text]): on a row's tag, a page's scope, or a
 * value that names it.
 */
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

    /** [source] as the player reads it; an installed catalog's own name shows as it is. */
    fun text(context: Context, source: String): String = SHOWN[source]?.let(context::getString) ?: source

    private val SHOWN = mapOf(CUSTOM to R.string.kairo_source_custom, ALL_GAMES to R.string.kairo_source_all_games,
        BUILT_IN to R.string.kairo_source_built_in, "Shipped catalog" to R.string.kairo_source_shipped_catalog,
        "Updated catalog" to R.string.kairo_source_updated_catalog, "User catalog" to R.string.kairo_source_user_catalog,
        "Filename" to R.string.kairo_source_filename)
}

/** Per-game rows shared by both apps. Each app supplies its storage and machine rows. */
object GameSettingsRows {
    /** Why a game's settings cannot change before it is hashed. */
    fun noHash(context: Context): String = context.getString(R.string.kairo_settings_no_hash)

    /**
     * A choice whose first option, Default, names where its value comes from. The row shows the
     * value with a source tag; values the user chose are tagged Custom and shown in Accent.
     */
    fun choice(context: Context, id: String, label: String, scope: () -> String, options: List<ChoiceOption>,
               value: GameValue<String>, enabled: () -> Boolean = { true },
               disabledReason: () -> String? = { null }, foot: String? = null,
               defaultLabel: (() -> String)? = null): SettingsRow =
        choice(context, id, label, scope, { options }, value, enabled, disabledReason, foot, defaultLabel)

    /**
     * A choice whose [options] are read each time they show, with [actions] below them that get
     * the function picking a value for this game (such as importing a file and choosing it).
     */
    fun choice(context: Context, id: String, label: String, scope: () -> String, options: () -> List<ChoiceOption>,
               value: GameValue<String>, enabled: () -> Boolean = { true },
               disabledReason: () -> String? = { null }, foot: String? = null,
               defaultLabel: (() -> String)? = null,
               actions: (select: (String) -> Unit) -> List<SettingsRow> = { emptyList() }): SettingsRow {
        fun optionLabel(option: String) = options().firstOrNull { it.id == option }?.label?.resolve(context) ?: option
        fun fallbackLabel() = defaultLabel?.invoke() ?: optionLabel(value.fallback())
        val select = { picked: String -> value.set(picked.takeIf { it != DEFAULT }) }
        val spec = ChoiceSpec(label, { scope() }, {
            listOf(ChoiceGroup(null, listOf(ChoiceOption(DEFAULT, context.getString(R.string.kairo_default),
                context.getString(R.string.kairo_settings_default_from, GameSource.text(context, value.source()), fallbackLabel()))) +
                options(), foot))
        }, { value.custom() ?: DEFAULT }, select, actions = { actions(select) })
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
    fun text(context: Context, id: String, label: String, hint: String, current: () -> String?, custom: () -> Boolean,
             source: () -> String?, save: (String) -> Unit, reset: () -> Unit,
             empty: String = context.getString(R.string.kairo_none), enabled: () -> Boolean = { true },
             disabledReason: () -> String? = { null }, multiline: Boolean = false) = SettingsRow.action(id, label,
        // Several lines show on the row as one, each separated by a slash.
        value = { current()?.lines()?.filter(String::isNotBlank)?.joinToString(" / ") ?: empty },
        tone = { when {
            custom() -> ValueTone.CUSTOM
            current() == null -> ValueTone.EMPTY
            else -> ValueTone.CURRENT
        } },
        tag = { if (custom()) GameSource.CUSTOM else source() },
        enabled = enabled, disabledReason = disabledReason,
        tray = {
            TraySpec(input = TrayInput(hint, current() ?: "", multiline = multiline), buttons = listOfNotNull(
                if (custom()) TrayButton(context.getString(R.string.kairo_reset)) { reset() } else null,
                TrayButton(context.getString(R.string.kairo_cancel)),
                TrayButton(context.getString(R.string.kairo_save), ButtonTone.PRIMARY) { save(it) }))
        })

    /** Location, ZIP entry, and content ID, readable without opening anything. */
    fun file(context: Context, path: String, zipEntry: String?, contentId: String?, error: String?) = listOfNotNull(
        SettingsRow.info("location", context.getString(R.string.kairo_file_location)) { path },
        zipEntry?.let { SettingsRow.info("zip-entry", context.getString(R.string.kairo_file_zip_entry)) { it } },
        SettingsRow.info("content-id", context.getString(R.string.kairo_file_content_id)) {
            contentId ?: error ?: context.getString(R.string.kairo_file_not_hashed)
        })

    fun reset(context: Context, enabled: () -> Boolean, run: () -> Unit) = SettingsRow.action("reset",
        context.getString(R.string.kairo_settings_reset_all), enabled = enabled, disabledReason = { noHash(context) },
        confirm = Confirm(context.getString(R.string.kairo_settings_reset_all_confirm), context.getString(R.string.kairo_reset),
            run = run))

    private const val DEFAULT = "default"
}

/** The Game settings page: the game's name in the scope chip, and rows the app assembles. */
fun gameSettingsPage(context: Context, title: () -> String, groups: () -> List<RowGroup>) =
    GroupsPage(context.getString(R.string.kairo_game_settings), title, groups)
