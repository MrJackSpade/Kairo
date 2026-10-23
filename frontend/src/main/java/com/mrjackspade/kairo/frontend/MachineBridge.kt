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
    fun pause(paused: Boolean)
    fun reset()
    fun key(code: Int, down: Boolean)
    fun joystick(control: Int, down: Boolean)
    /** Relative guest mouse movement, and button 1 (left) or 2 (right). */
    fun mouseMove(dx: Int, dy: Int)
    fun mouseButton(button: Int, down: Boolean)
    /** Saves the machine into [directory]; 0, or a code [MachineActivity.stateError] explains. */
    fun saveState(directory: String): Int
    fun loadState(directory: String): Int
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
