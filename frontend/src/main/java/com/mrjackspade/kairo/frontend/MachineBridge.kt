package com.mrjackspade.kairo.frontend

import android.view.Surface

/**
 * The native side of a frame-driven machine (shared/frontend/src/main/cpp/kairo/frame_machine.h).
 * Starting a machine takes product-specific media and settings, so products start it themselves;
 * everything else a running game needs from the frontend is here.
 */
interface MachineBridge {
    /** Ends the running machine, if any, and waits for its thread. */
    fun stop()
    /**
     * Blocks until the machine started last has ended, by [stop] or by itself, and returns how it
     * ended (Stopped, or Error with the core's message); null for a machine that ends only when
     * the frontend stops it.
     */
    fun awaitEnd(): MachineStatus? = null
    fun pause(paused: Boolean)
    fun reset()
    fun key(code: Int, down: Boolean)
    fun joystick(control: Int, down: Boolean)
    /** Relative guest mouse movement, and button 1 (left) or 2 (right). */
    fun mouseMove(dx: Int, dy: Int)
    fun mouseButton(button: Int, down: Boolean)
    /**
     * Direct touch: the guest's pointer to the point ([x], [y]) in 1/65536ths of the picture's width and
     * height, for a machine whose catalog sets directTouch.
     */
    fun warpMouse(x: Int, y: Int): Unit = error("This machine has no direct touch")
    /** Whether the guest's pointer has reached the last [warpMouse] point. */
    fun mouseWarpDone(): Boolean = error("This machine has no direct touch")
    /** Saves the machine into [directory]; 0, [FAILED], [UNSUPPORTED] or [NOT_RUNNING]. */
    fun saveState(directory: String): Int
    /**
     * Loads the machine from [directory]. [moved] maps each disk path a state may name to where
     * that disk is now (MachineLibrary.statePaths); the machine reopens its disks through it.
     */
    fun loadState(directory: String, moved: Map<String, String>): Int
    /** Why the last save or load failed, in the emulator's words; "" when it doesn't say. */
    fun stateErrorReason(): String = ""
    /** The lock keys the machine has on, as after loading a state. */
    fun lockedKeys(): Set<Int>
    /** `<state>|<error>|<frames>|<audio>|<xruns>|<audio config>`, as [MachineStatus] reads it. */
    fun status(): String
    fun setSurface(surface: Surface?)
    fun setShaderPreset(path: String?)
    fun setMuted(muted: Boolean)
    fun setFastForward(enabled: Boolean)
    /** Inserts the image at [path] in a media [slot] the product numbers, or ejects it for null; true when it is in. */
    fun media(slot: Int, path: String?): Boolean = error("This machine cannot change media")
    /** What each media slot holds, "" for an empty one; null when no machine runs. */
    fun mediaPaths(): List<String>? = null
    /** The guest's keyboard and mouse reads for Auto touch mode ([InputModeDecider.GuestInput]), or null when it has none. */
    fun inputTelemetry(): LongArray? = null

    companion object {
        /** A state call made while no machine runs, or one that stopped before answering. */
        const val NOT_RUNNING = -1000
        /** A save or load that failed ([stateErrorReason] may say why): MachineCore::kFailed. */
        const val FAILED = 2
        /** A save or load the machine can't do: MachineCore::kUnsupported. */
        const val UNSUPPORTED = -2
        /**
         * A load that failed after it had changed the running machine, which cannot go on from there
         * ([stateErrorReason] may say why): MachineCore::kLost. The frontend starts the game again.
         */
        const val LOST = 3
    }
}

/** A machine's state as [MachineBridge.status] reports it. */
class MachineStatus(text: String) {
    private val parts = text.split('|')
    /** Stopped, Starting, Running, Paused, or Error. */
    val state: String = parts[0]
    val error: String = parts.getOrElse(1) { "" }
    val frames: String = parts.getOrElse(2) { "" }
    val audio: String = parts.getOrElse(3) { "" }
    val xruns: String = parts.getOrElse(4) { "" }
    val audioConfig: String = parts.getOrElse(5) { "" }
    /** Whether a frame that is not all black has been shown; null from a bridge that does not say. */
    val pictured: Boolean? = parts.getOrNull(6)?.let { it == "1" }
    /** What the core reports of itself (MachineCore::details), such as tape → "1200/25650". */
    val core: Map<String, String> = parts.getOrNull(7).orEmpty().split(';').filter { '=' in it }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
    val running: Boolean get() = state == "Running" || state == "Paused"
}
