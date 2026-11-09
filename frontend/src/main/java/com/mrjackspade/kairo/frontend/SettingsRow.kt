package com.mrjackspade.kairo.frontend

import android.content.Context
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

/** A tray's text field; [multiline] takes several lines, each kept as typed, and Return starts a new one. */
class TrayInput(val hint: String, val initial: String = "", val numeric: Boolean = false,
                val multiline: Boolean = false)

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
    fun tray(context: Context) = TraySpec(message, listOf(
        TrayButton(context.getString(R.string.kairo_cancel)),
        TrayButton(verb, if (danger) ButtonTone.DANGER else ButtonTone.PRIMARY) { run() }),
        focus = 0)
}

/**
 * A choice's [label] and [description] are UiText, as a catalog's choices are declared without a Context.
 * A choice with [disabledReason] shows but cannot be picked, the reason on its row (such as firmware the
 * player has not given). A choice with [folder] lists in that folder of its choice page, with the other
 * choices of the same folder, for a long choice (Kairo#143).
 */
/**
 * An option of a choice: [folder] puts it in a folder of that name, [section] under that labeled group
 * of the list (an uppercase section label, such as "PRESETS"), where a list built from options groups them.
 */
class ChoiceOption(val id: String, val label: UiText, val description: UiText? = null,
                   val disabledReason: UiText? = null, val folder: String? = null, val section: UiText? = null) {
    /** A choice named by text that is never translated, such as a file or a game's name. */
    constructor(id: String, label: String, description: String? = null) :
        this(id, UiText.fixed(label), description?.let(UiText::fixed))
}

/**
 * A value typed instead of picked (Kairo#183): a number the choice's options need not list. The player
 * types in [hint]'s unit; [problem] says why typed text cannot be taken (null when it can), [valueOf]
 * turns typed text into the value kept and [typedOf] back, and [labelOf] says how a value reads ("700 MB").
 */
class CustomValue(val hint: UiText, val labelOf: (String) -> UiText, val problem: (String) -> UiText?,
                  val valueOf: (String) -> String = { it }, val typedOf: (String) -> String = { it }) {
    /** Whether the kept [value] is one the player could type. */
    fun fits(value: String): Boolean = problem(typedOf(value)) == null

    companion object {
        /**
         * A number typed with up to [decimals] places, counted in units of its last place (7.16 MHz with two
         * places is 716), from [min] to [max] by [step] such units, kept as the units times [scale] (memory
         * typed in MB and kept in KB; a clock typed in MHz to two places and kept in Hz), read as [labelOf]
         * makes a kept value. A number off them is refused, never moved.
         */
        fun range(min: Int, max: Int, step: Int, scale: Int = 1, decimals: Int = 0, labelOf: (Int) -> UiText): CustomValue {
            require(step > 0 && min <= max && scale > 0 && decimals >= 0) { "Range $min..$max by $step, scale $scale" }
            val unit = (1..decimals).fold(1) { total, _ -> total * 10 }
            val form = Regex(if (decimals == 0) "\\d+" else "\\d+(\\.\\d{1,$decimals})?")
            fun label(units: Int) = labelOf(units * scale)
            fun unitsOf(typed: String): Int? = typed.takeIf(form::matches)?.toBigDecimalOrNull()
                ?.movePointRight(decimals)?.toBigInteger()?.takeIf { it.bitLength() < 31 }?.toInt()
            fun typedOf(units: Int) = if (decimals == 0) units.toString() else "${units / unit}.${(units % unit).toString().padStart(decimals, '0')}"
            return CustomValue(UiText.res(R.string.kairo_custom_hint, label(min), label(max)), { labelOf(it.toInt()) }, { typed ->
                val units = unitsOf(typed)
                when {
                    units == null -> UiText.res(if (decimals == 0) R.string.kairo_custom_not_number else R.string.kairo_custom_not_decimal, decimals)
                    units < min || units > max -> UiText.res(R.string.kairo_custom_out_of_range, label(min), label(max))
                    (units - min) % step != 0 -> UiText.res(R.string.kairo_custom_off_step, label(step))
                    else -> null
                }
            }, { (checkNotNull(unitsOf(it)) * scale).toString() },
                { value -> value.toIntOrNull()?.takeIf { it % scale == 0 }?.let { typedOf(it / scale) } ?: "" })
        }
    }
}

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
    val cycles: Boolean = true,
    /** After a pick has closed the choice's pages (not for [stay]): what the pick leads to, such as another question. */
    val closed: () -> Unit = {},
    /** A value the player can type instead (Custom…, Kairo#183), for a setting that could be any number in a range. */
    val custom: CustomValue? = null
) {
    /** How the value [id] reads: its option's label, else its typed value's. */
    fun labelOf(id: String): UiText? = find(groups(), id)?.label ?: custom?.takeIf { it.fits(id) }?.labelOf?.invoke(id)

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
    private val valueOf: (Context) -> String,
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
    val confirm: Confirm?,
    /** The label is a name the app quotes, such as a product's (TestAttributes.markQuoted). */
    val quoted: Boolean = false
) {
    enum class Kind { NAV, CHOICE, SWITCH, SLIDER, ACTION, INFO, OPTION }

    /** The value the row shows now. */
    fun value(context: Context): String = valueOf(context)

    companion object {
        private val never: () -> Boolean = { false }
        private val always: () -> Boolean = { true }
        private val none: () -> String? = { null }
        private val blank: () -> String = { "" }
        private fun shown(value: () -> String): (Context) -> String = { value() }

        /** Opens a page. The value is a muted summary of that page. */
        fun nav(id: String, label: String, value: () -> String = blank,
                tone: () -> ValueTone = { ValueTone.SUMMARY }, tag: () -> String? = none,
                enabled: () -> Boolean = always, disabledReason: () -> String? = none,
                open: () -> Unit) = SettingsRow(id, Kind.NAV, label, shown(value), tone, tag, null,
            enabled, disabledReason, false, never, open, null, never, null, null, null, null)

        /** Shows the current choice. A opens the choice page; Left and Right step through it. */
        fun choice(id: String, label: String, spec: ChoiceSpec, value: () -> String? = { null },
                   tone: () -> ValueTone = { ValueTone.CURRENT }, tag: () -> String? = none,
                   enabled: () -> Boolean = always, disabledReason: () -> String? = none) =
            SettingsRow(id, Kind.CHOICE, label,
                { context -> value() ?: spec.selected()?.let(spec::labelOf)?.resolve(context) ?: "" },
                tone, tag, null, enabled, disabledReason, false, never, null, spec, never, null,
                null, null, null)

        fun switch(id: String, label: String, checked: () -> Boolean,
                   enabled: () -> Boolean = always, disabledReason: () -> String? = none,
                   set: (Boolean) -> Unit) = SettingsRow(id, Kind.SWITCH, label, shown(blank),
            { ValueTone.CURRENT }, none, null, enabled, disabledReason, false, never, null, null,
            checked, set, null, null, null)

        fun slider(id: String, label: String, value: () -> String, spec: SliderSpec,
                   enabled: () -> Boolean = always, disabledReason: () -> String? = none) =
            SettingsRow(id, Kind.SLIDER, label, shown(value), { ValueTone.CURRENT }, none, null, enabled,
                disabledReason, false, never, null, null, never, null, spec, null, null)

        /**
         * Runs at once, asks first with [confirm], or opens [tray] for file actions, text entry,
         * or listening for input. Progress shows on the row while [busy] is true.
         */
        fun action(id: String, label: String, value: () -> String = blank,
                   tone: () -> ValueTone = { ValueTone.SUMMARY }, tag: () -> String? = none,
                   danger: Boolean = false, busy: () -> Boolean = never,
                   enabled: () -> Boolean = always, disabledReason: () -> String? = none,
                   confirm: Confirm? = null, tray: (() -> TraySpec)? = null, description: String? = null,
                   run: (() -> Unit)? = null) = SettingsRow(id, Kind.ACTION, label, shown(value), tone, tag,
            description, enabled, disabledReason, danger, busy, run, null, never, null, null, tray, confirm)

        /** Read-only. Skipped by D-pad focus; long values wrap at the right edge. */
        fun info(id: String, label: String, value: () -> String) = SettingsRow(id, Kind.INFO,
            label, shown(value), { ValueTone.SUMMARY }, none, null, never, none, false, never, null, null,
            never, null, null, null, null)

        /** A choice page option, the only row with a description line. */
        fun option(id: String, label: String, description: String?, selected: () -> Boolean,
                   enabled: () -> Boolean = always, disabledReason: () -> String? = none, quoted: Boolean = false,
                   pick: () -> Unit) =
            SettingsRow(id, Kind.OPTION, label, shown(blank), { ValueTone.CURRENT }, none, description, enabled,
                disabledReason, false, never, pick, null, selected, null, null, null, null, quoted)
    }
}
