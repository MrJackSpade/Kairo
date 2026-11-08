package com.mrjackspade.kairo.frontend

/**
 * An app's New game workflow (Kairo#181): the pages a player goes through to make a new game, each a
 * collection of [WorkflowField]s, then [create], which turns every choice into what is written to the
 * games folder. Fields are resolved in order, so a field's choices, range and default may depend on
 * any field before it ([resolve]): a choice that no longer fits is dropped for the field's default.
 * The shared runner ([NewGameFlow]) adds the name page last; an app with nothing to choose has no
 * pages of its own and goes straight to the name.
 */
class NewGameWorkflow(
    val pages: List<WorkflowPage>,
    /**
     * After the game is written: the choices and the game's path in the games folder (an image's
     * `<name>/<name>.<extension>`, a folder's `<name>`), for what is kept beside it, such as its settings.
     */
    val created: (Map<String, String>, String) -> Unit = { _, _ -> },
    /** What the choices make, by field id ([NAME] holds the game's name). */
    val create: (Map<String, String>) -> BlankDisk
) {
    init {
        val ids = pages.flatMap { page -> page.fields.map { it.id } }
        require(ids.size == ids.toSet().size) { "A New game workflow has two fields with one id: $ids" }
        require(NAME !in ids) { "$NAME is the shared name page's field" }
    }

    /** The pages shown for [values]: those whose [WorkflowPage.shown] holds. */
    fun shownPages(values: Map<String, String>): List<WorkflowPage> = pages.filter { it.shown(values) }

    /**
     * Every field's value: what the player chose ([chosen]) where it still fits the choices before
     * it, else the field's default, else its first choice. Pages not shown keep no values. Each
     * field sees the values resolved before it.
     */
    fun resolve(chosen: Map<String, String>): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        chosen[NAME]?.let { values[NAME] = it }
        for (page in pages) {
            if (!page.shown(values)) continue
            for (field in page.fields) field.resolve(chosen[field.id], values)?.let { values[field.id] = it }
        }
        return values
    }

    companion object {
        /** The name page's field: the new game's name. */
        const val NAME = "name"

        /** The kind page's field in [ofBlankDisks]. */
        const val KIND = "kind"

        /**
         * The workflow of an app that defines none of its own: which of its [blanks] to make, then the
         * name. An empty list of blank disks makes nothing.
         */
        fun ofBlankDisks(blanks: List<BlankDisk>, kindTitle: UiText, kindLabel: UiText): NewGameWorkflow =
            NewGameWorkflow(listOf(kindPage(blanks, kindTitle, kindLabel))) { values -> chosen(blanks, values) }

        /** The page that picks which of [blanks] to make, as [KIND]; one that cannot be made is shown but not taken. */
        fun kindPage(blanks: List<BlankDisk>, title: UiText, label: UiText) = WorkflowPage(KIND, title, listOf(WorkflowField.Select(KIND, label,
            { blanks.map { ChoiceOption(it.id, it.label, null, it.unavailable()) } },
            { blanks.firstOrNull { it.unavailable() == null }?.id ?: "" })))

        /** The one of [blanks] the [KIND] page picked. */
        fun chosen(blanks: List<BlankDisk>, values: Map<String, String>): BlankDisk = blanks.first { it.id == values.getValue(KIND) }
    }
}

/** A page of a [NewGameWorkflow]: its [title], its [fields], and whether it is shown for the choices so far. */
class WorkflowPage(
    val id: String,
    val title: UiText,
    val fields: List<WorkflowField>,
    val shown: (Map<String, String>) -> Boolean = { true }
)

/** A field of a [WorkflowPage]: [id] keys its value among the workflow's choices. */
sealed class WorkflowField(val id: String, val label: UiText) {
    /** The value the field takes for [chosen] (the player's, or null), given the [values] resolved before it. */
    internal abstract fun resolve(chosen: String?, values: Map<String, String>): String?

    /**
     * One of a list of choices, narrowed by the choices before it ([choices] gets them); [default]
     * names the choice it starts on. A choice with a disabled reason is shown but cannot be taken.
     */
    class Select(
        id: String,
        label: UiText,
        val choices: (Map<String, String>) -> List<ChoiceOption>,
        val default: (Map<String, String>) -> String
    ) : WorkflowField(id, label) {
        override fun resolve(chosen: String?, values: Map<String, String>): String? {
            val options = choices(values).filter { it.disabledReason == null }
            if (options.isEmpty()) return null
            return listOf(chosen, default(values)).firstOrNull { wanted -> options.any { it.id == wanted } } ?: options.first().id
        }
    }

    /**
     * A whole number from [range]'s minimum to its maximum by its step, which may depend on the
     * choices before it; [default] starts it, and [labelOf] shows a value (such as "504 MB").
     */
    class Range(
        id: String,
        label: UiText,
        val range: (Map<String, String>) -> Steps,
        val default: (Map<String, String>) -> Int,
        val labelOf: (Int) -> UiText
    ) : WorkflowField(id, label) {
        /** [min] to [max] by [step]. */
        class Steps(val min: Int, val max: Int, val step: Int) {
            init { require(step > 0 && min <= max) { "Range $min..$max by $step" } }
            val values: List<Int> get() = (min..max step step).toList()
            fun fits(value: Int) = value in min..max && (value - min) % step == 0
        }

        override fun resolve(chosen: String?, values: Map<String, String>): String {
            val steps = range(values)
            val wanted = listOfNotNull(chosen?.toIntOrNull(), default(values)).firstOrNull(steps::fits)
            return (wanted ?: steps.min).toString()
        }
    }

    /** Text, such as a name: [default] starts it, and [problem] says why a value cannot be taken (null when it can). */
    class Text(
        id: String,
        label: UiText,
        val default: (Map<String, String>) -> String,
        val problem: (String) -> UiText?
    ) : WorkflowField(id, label) {
        override fun resolve(chosen: String?, values: Map<String, String>): String = chosen ?: default(values)
    }
}
