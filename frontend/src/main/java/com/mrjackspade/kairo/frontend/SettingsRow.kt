package com.mrjackspade.kairo.frontend

import android.view.KeyEvent
import android.view.MotionEvent

/** What a row value's color tells the user. See docs/interface.md. */
enum class ValueTone {
    /** The current value of something this row changes. */
    CURRENT,
    /** A summary of the page the row opens. */
    SUMMARY,
    /** Nothing is set: Unassigned, None, Empty, Not installed. */
    EMPTY,
    /** This game overrides the default. */
    CUSTOM
}

enum class ButtonTone { SECONDARY, PRIMARY, DANGER }

/**
 * A tray button receives the tray's text field contents, or "" when it has none. The tray
 * closes before [run] unless [keepOpen] is set, as for a button that changes the tray.
 */
class TrayButton(val label: String, val tone: ButtonTone = ButtonTone.SECONDARY,
                 val keepOpen: Boolean = false, val run: (String) -> Unit = {})

class TrayInput(val hint: String, val initial: String = "", val numeric: Boolean = false)

/** Raw controller input for a tray that is listening for a button, stick, or trigger. */
interface InputCapture {
    fun key(event: KeyEvent): Boolean
    fun motion(event: MotionEvent): Boolean
}

/** Opens under its row: a confirmation, file actions, text entry, or listening for input. */
class TraySpec(
    val message: String? = null,
    val buttons: List<TrayButton>,
    val input: TrayInput? = null,
    val listening: Boolean = false,
    /** The button focused when the tray opens. Confirmations focus Cancel. */
    val focus: Int? = null,
    val capture: InputCapture? = null
)

/**
 * Confirmation verbs are the action itself, never OK or Confirm. A confirmation with a
 * [title] is a modal, used for leaving a game; one without is a tray under its row. Both
 * open on Cancel.
 */
class Confirm(val message: String, val verb: String, val danger: Boolean = false,
              val title: String? = null, val run: () -> Unit) {
    fun tray() = TraySpec(message, listOf(
        TrayButton("Cancel"),
        TrayButton(verb, if (danger) ButtonTone.DANGER else ButtonTone.PRIMARY) { run() }),
        focus = 0)
}

class ChoiceOption(val id: String, val label: String, val description: String? = null)

/** Folders list before options; each opens its own choice page within the same choice. */
class ChoiceGroup(val label: String? = null, val options: List<ChoiceOption>, val foot: String? = null,
                  val folders: List<ChoiceFolder> = emptyList())

/** Part of a large choice, such as a directory of presets, opened as a nested choice page. */
class ChoiceFolder(val id: String, val label: String, val groups: () -> List<ChoiceGroup>) {
    /** The option with [id] in this folder or any folder inside it. */
    fun find(id: String?): ChoiceOption? = if (id == null) null else ChoiceSpec.find(groups(), id)
}

/**
 * A choice page. Picking an option applies it and returns to the page the choice opened
 * from, including from inside folders.
 */
class ChoiceSpec(
    val title: String,
    val scope: () -> String? = { null },
    val groups: () -> List<ChoiceGroup>,
    val selected: () -> String?,
    val select: (String) -> Unit,
    /** Action rows below the options, such as importing a file. */
    val actions: () -> List<SettingsRow> = { emptyList() },
    /** Keep the page open after a pick, for pages whose pick starts something else. */
    val stay: Boolean = false,
    /** Whether Left and Right on the row step through the options in place. */
    val cycles: Boolean = true
) {
    /** Every option, folders included, in page order. */
    val options: List<ChoiceOption> get() = flatten(groups())

    companion object {
        fun flatten(groups: List<ChoiceGroup>): List<ChoiceOption> = groups.flatMap { group ->
            group.folders.flatMap { flatten(it.groups()) } + group.options
        }

        fun find(groups: List<ChoiceGroup>, id: String): ChoiceOption? = groups.firstNotNullOfOrNull { group ->
            group.options.firstOrNull { it.id == id } ?: group.folders.firstNotNullOfOrNull { it.find(id) }
        }
    }
}

/** Left and Right move by [keyStep]; dragging moves by [step]. */
class SliderSpec(val min: Int, val max: Int, val step: Int = 1, val keyStep: Int = step,
                 val get: () -> Int, val set: (Int) -> Unit)

class RowGroup(val label: String? = null, val rows: List<SettingsRow>, val foot: String? = null)

/**
 * One settings row. Every row is a single line: a label, and the current value when it is
 * useful. Explanations belong in the group footnote. Options on choice pages are the only rows
 * with a second line.
 */
class SettingsRow private constructor(
    val id: String,
    val kind: Kind,
    val label: String,
    val value: () -> String,
    val tone: () -> ValueTone,
    val tag: () -> String?,
    val description: String?,
    val enabled: () -> Boolean,
    val disabledReason: () -> String?,
    val danger: Boolean,
    val busy: () -> Boolean,
    val run: (() -> Unit)?,
    val choice: ChoiceSpec?,
    val checked: () -> Boolean,
    val setChecked: ((Boolean) -> Unit)?,
    val slider: SliderSpec?,
    val tray: (() -> TraySpec)?,
    val confirm: Confirm?
) {
    enum class Kind { NAV, CHOICE, SWITCH, SLIDER, ACTION, INFO, OPTION }

    companion object {
        private val never: () -> Boolean = { false }
        private val always: () -> Boolean = { true }
        private val none: () -> String? = { null }
        private val blank: () -> String = { "" }

        /** Opens a page. The value is a muted summary of that page. */
        fun nav(id: String, label: String, value: () -> String = blank,
                tone: () -> ValueTone = { ValueTone.SUMMARY }, tag: () -> String? = none,
                enabled: () -> Boolean = always, disabledReason: () -> String? = none,
                open: () -> Unit) = SettingsRow(id, Kind.NAV, label, value, tone, tag, null,
            enabled, disabledReason, false, never, open, null, never, null, null, null, null)

        /** Shows the current choice. A opens the choice page; Left and Right step through it. */
        fun choice(id: String, label: String, spec: ChoiceSpec, value: () -> String? = { null },
                   tone: () -> ValueTone = { ValueTone.CURRENT }, tag: () -> String? = none,
                   enabled: () -> Boolean = always, disabledReason: () -> String? = none) =
            SettingsRow(id, Kind.CHOICE, label,
                { value() ?: spec.selected()?.let { ChoiceSpec.find(spec.groups(), it) }?.label ?: "" },
                tone, tag, null, enabled, disabledReason, false, never, null, spec, never, null,
                null, null, null)

        fun switch(id: String, label: String, checked: () -> Boolean,
                   enabled: () -> Boolean = always, disabledReason: () -> String? = none,
                   set: (Boolean) -> Unit) = SettingsRow(id, Kind.SWITCH, label, blank,
            { ValueTone.CURRENT }, none, null, enabled, disabledReason, false, never, null, null,
            checked, set, null, null, null)

        fun slider(id: String, label: String, value: () -> String, spec: SliderSpec,
                   enabled: () -> Boolean = always, disabledReason: () -> String? = none) =
            SettingsRow(id, Kind.SLIDER, label, value, { ValueTone.CURRENT }, none, null, enabled,
                disabledReason, false, never, null, null, never, null, spec, null, null)

        /**
         * Runs at once, asks first with [confirm], or opens [tray] for file actions, text entry,
         * or listening for input. Progress shows on the row while [busy] is true.
         */
        fun action(id: String, label: String, value: () -> String = blank,
                   tone: () -> ValueTone = { ValueTone.SUMMARY }, tag: () -> String? = none,
                   danger: Boolean = false, busy: () -> Boolean = never,
                   enabled: () -> Boolean = always, disabledReason: () -> String? = none,
                   confirm: Confirm? = null, tray: (() -> TraySpec)? = null,
                   run: (() -> Unit)? = null) = SettingsRow(id, Kind.ACTION, label, value, tone, tag,
            null, enabled, disabledReason, danger, busy, run, null, never, null, null, tray, confirm)

        /** Read-only. Skipped by D-pad focus; long values wrap at the right edge. */
        fun info(id: String, label: String, value: () -> String) = SettingsRow(id, Kind.INFO,
            label, value, { ValueTone.SUMMARY }, none, null, never, none, false, never, null, null,
            never, null, null, null, null)

        /** A choice page option, the only row with a description line. */
        fun option(id: String, label: String, description: String?, selected: () -> Boolean,
                   enabled: () -> Boolean = always, pick: () -> Unit) = SettingsRow(id, Kind.OPTION,
            label, blank, { ValueTone.CURRENT }, none, description, enabled, none, false, never,
            pick, null, selected, null, null, null, null)
    }
}
