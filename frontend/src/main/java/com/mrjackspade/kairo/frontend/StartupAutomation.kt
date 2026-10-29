package com.mrjackspade.kairo.frontend

import org.json.JSONArray

/**
 * A catalog's startup automation for a game (Kairo#83), which the frontend carries out through the
 * machine's startup typer and screen signatures (MachineBridge.startupSteps, screenSignature):
 *
 * - `startupChoices`: a game's startup menus. The player picks an option for each before the game
 *   starts ([CatalogSetting.StartupChoices]), and the typer enters it when the game shows the menu.
 * - `diskSwaps`: a disk the frontend changes for the player when the game shows a screen asking for
 *   it ([CatalogSetting.DiskSwaps]), then the keys that answer the screen.
 *
 * A screen is named by the machine's screen signature, 16 hex digits. An empty list is a player's
 * setting that turns that automation off for the game.
 */
object StartupAutomation {
    const val CHOICES = "startupChoices"
    const val SWAPS = "diskSwaps"

    private val SHORT_ID = Regex("[a-z0-9-]{1,40}")
    private val KEY = Regex("[A-Za-z0-9]")

    fun validChoices(value: Any): Boolean = value is JSONArray && value.length() in 0..4 &&
        (0 until value.length()).all { index ->
            value.optJSONObject(index)?.let { choice ->
                validShortId(choice.optString("id")) && validLabel(choice.optString("title")) &&
                    validHashes(choice.optJSONArray("screenHashes")) &&
                    choice.optJSONArray("options")?.let { options ->
                        options.length() in 1..12 && (0 until options.length()).all { optionIndex ->
                            options.optJSONObject(optionIndex)?.let { option ->
                                validShortId(option.optString("id")) && validLabel(option.optString("label")) &&
                                    (if (option.has("steps")) {
                                        !option.has("key") && !option.has("enter") &&
                                            option.optJSONArray("steps")?.let { steps ->
                                                steps.length() in 2..4 && (0 until steps.length()).all { stepIndex ->
                                                    steps.optJSONObject(stepIndex)?.let { step ->
                                                        step.length() == 3 && step.optString("key").matches(KEY) &&
                                                            step.opt("enter") is Boolean &&
                                                            validHashes(step.optJSONArray("screenHashes"))
                                                    } == true
                                                }
                                            } == true
                                    } else option.optString("key").matches(KEY) && option.opt("enter") is Boolean)
                            } == true
                        } && (0 until options.length()).map { options.getJSONObject(it).getString("id") }
                            .distinct().size == options.length()
                    } == true
            } == true
        } && (0 until value.length()).map { value.getJSONObject(it).getString("id") }.distinct().size == value.length()

    /**
     * Whether [value] is a valid `diskSwaps` list: each swap's drive one of [drives] media slots, its
     * disk a [contentId] the drive [accepts], and no screen asking for two swaps.
     */
    fun validSwaps(value: Any, drives: Int, contentId: Regex, accepts: (Int, String) -> Boolean): Boolean =
        value is JSONArray && value.length() in 0..16 &&
            (0 until value.length()).all { index ->
                value.optJSONObject(index)?.let { swap ->
                    swap.length() == 6 && listOf("id", "drive", "contentId", "screenHashes", "key", "enter").all(swap::has) &&
                        validShortId(swap.optString("id")) &&
                        swap.opt("drive") is Int && swap.optInt("drive") in 0 until drives &&
                        contentId.matches(swap.optString("contentId")) &&
                        accepts(swap.optInt("drive"), swap.optString("contentId")) &&
                        validHashes(swap.optJSONArray("screenHashes")) &&
                        (swap.opt("key") as? String)?.matches(Regex("[A-Za-z0-9]?")) == true &&
                        swap.opt("enter") is Boolean
                } == true
            } && (0 until value.length()).map { value.getJSONObject(it).getString("id") }.distinct().size == value.length() &&
            (0 until value.length()).flatMap { index ->
                value.getJSONObject(index).getJSONArray("screenHashes").let { hashes -> (0 until hashes.length()).map(hashes::getString) }
            }.distinct().size == (0 until value.length()).sumOf { index ->
                value.getJSONObject(index).getJSONArray("screenHashes").length()
            }

    /** Screen signatures as a catalog stores them, 16 hex digits each. */
    fun validHashes(value: JSONArray?): Boolean = value != null && value.length() in 1..16 &&
        (0 until value.length()).all { (value.opt(it) as? String)?.matches(Regex("[0-9a-f]{16}")) == true }

    fun parseHashes(value: JSONArray?): Set<Long> =
        if (value == null) emptySet() else (0 until value.length()).map {
            java.lang.Long.parseUnsignedLong(value.getString(it), 16)
        }.toSet()

    private fun validShortId(value: String) = value.matches(SHORT_ID)
    private fun validLabel(value: String) = value.isNotBlank() && value.length <= 100
}

/** A key the typer enters for a startup choice, with Return when [enter], when the game shows one of [screenHashes]. */
data class StartupInput(val key: Char, val enter: Boolean, val screenHashes: Set<Long>)
data class StartupOption(val id: String, val label: String, val inputs: List<StartupInput>)
/** A startup menu: [options] for the player to pick from, entered when the game shows one of [screenHashes]. */
data class StartupChoice(val id: String, val title: String, val screenHashes: Set<Long>, val options: List<StartupOption>)
/**
 * The disk [contentId] the frontend puts in media slot [drive] when the game shows one of
 * [screenHashes], then [key] (none for "") and Return when [enter].
 */
data class DiskSwap(val id: String, val drive: Int, val contentId: String, val screenHashes: Set<Long>,
                    val key: String, val enter: Boolean)

/** The game's startup choices; read when the game launches, so malformed data fails that launch. */
val CatalogGame.startupChoices: List<StartupChoice>
    get() {
        val array = record.optJSONArray(StartupAutomation.CHOICES) ?: return emptyList()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val options = item.getJSONArray("options")
            val screens = StartupAutomation.parseHashes(item.getJSONArray("screenHashes"))
            StartupChoice(item.getString("id"), item.getString("title"), screens,
                (0 until options.length()).map { optionIndex ->
                    val option = options.getJSONObject(optionIndex)
                    val steps = option.optJSONArray("steps")
                    val inputs = if (steps == null) listOf(StartupInput(option.getString("key")[0], option.getBoolean("enter"), screens))
                    else (0 until steps.length()).map { stepIndex ->
                        val step = steps.getJSONObject(stepIndex)
                        StartupInput(step.getString("key")[0], step.getBoolean("enter"),
                            StartupAutomation.parseHashes(step.getJSONArray("screenHashes")))
                    }
                    StartupOption(option.getString("id"), option.getString("label"), inputs)
                })
        }
    }

/** The game's disk swaps; read when the game launches, so malformed data fails that launch. */
val CatalogGame.diskSwaps: List<DiskSwap>
    get() {
        val array = record.optJSONArray(StartupAutomation.SWAPS) ?: return emptyList()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            DiskSwap(item.getString("id"), item.getInt("drive"), item.getString("contentId"),
                StartupAutomation.parseHashes(item.getJSONArray("screenHashes")), item.getString("key"),
                item.getBoolean("enter"))
        }
    }

/**
 * Watches the screen the guest shows, by its [signature] (null when no machine runs), every 100 ms,
 * and swaps the disk a [DiskSwap] names when its screen shows; a screen must go away before it can
 * swap again.
 */
class DiskSwapMatcher(
    private val signature: () -> Long?,
    private val running: () -> Boolean,
    private val cancelled: () -> Boolean,
    private val swap: (DiskSwap) -> Unit
) {
    fun run(rules: List<DiskSwap>) {
        if (rules.isEmpty()) return
        val armed = BooleanArray(rules.size) { true }
        val misses = IntArray(rules.size)
        while (!cancelled()) {
            val hash = signature()
            if (hash != null && running()) {
                for ((index, rule) in rules.withIndex()) {
                    if (hash in rule.screenHashes) {
                        misses[index] = 0
                        if (armed[index]) {
                            armed[index] = false
                            swap(rule)
                        }
                    } else if (++misses[index] >= 3) {
                        armed[index] = true
                    }
                }
            }
            Thread.sleep(100)
        }
    }
}
