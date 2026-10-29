package com.mrjackspade.kairo.frontend

/** Decides only how a screen touch is routed. It never changes guest execution. */
class InputModeDecider {
    enum class Mode { AUTO, KEYBOARD, MOUSE }

    /**
     * How the guest reads its input. [mouseEnabled]: the guest set its mouse up, for a core that
     * cannot see the reads themselves (Kairo68: a program reading the mouse's data from RAM).
     */
    data class GuestInput(
        val keyboardWaits: Long,
        val keyboardPolls: Long,
        val mouseReads: Long,
        val keyboardWaiting: Boolean,
        val mouseEnabled: Boolean = false
    ) {
        companion object {
            fun fromNative(values: LongArray): GuestInput {
                require(values.size == 5) { "Guest input has ${values.size} values, not 5" }
                return GuestInput(values[0], values[1], values[2], values[3] != 0L, values[4] != 0L)
            }
        }
    }

    private var previous: GuestInput? = null
    private var lastKeyboardSignalAt = Long.MIN_VALUE
    private var automatic = Mode.KEYBOARD

    fun reset() {
        previous = null
        lastKeyboardSignalAt = Long.MIN_VALUE
        automatic = Mode.KEYBOARD
    }

    fun observe(sample: GuestInput, nowMs: Long) {
        val prior = previous
        previous = sample
        if (prior == null || sample.keyboardWaits < prior.keyboardWaits ||
            sample.keyboardPolls < prior.keyboardPolls || sample.mouseReads < prior.mouseReads) {
            automatic = Mode.KEYBOARD
            lastKeyboardSignalAt = nowMs
            return
        }

        val waits = sample.keyboardWaits - prior.keyboardWaits
        val polls = sample.keyboardPolls - prior.keyboardPolls
        val mouse = sample.mouseReads - prior.mouseReads
        if (sample.keyboardWaiting || waits > 0 || polls >= 2) {
            automatic = Mode.KEYBOARD
            lastKeyboardSignalAt = nowMs
            return
        }

        // A resident driver may poll the mouse while a keyboard menu runs. Require
        // sustained recent mouse reads without recent keyboard activity.
        if ((mouse >= 4 || sample.mouseEnabled) && nowMs - lastKeyboardSignalAt >= 750) automatic = Mode.MOUSE
    }

    fun resolve(configured: Mode): Mode = if (configured == Mode.AUTO) automatic else configured

    companion object {
        fun parse(value: String?): Mode = when (value?.lowercase()) {
            "keyboard" -> Mode.KEYBOARD
            "mouse" -> Mode.MOUSE
            else -> Mode.AUTO
        }

        fun storageValue(mode: Mode): String = mode.name.lowercase()
    }
}
