package com.mrjackspade.kairo.frontend

import android.view.Surface

/**
 * The machine in a product's native [library], which links the shared JNI surface
 * (cpp/kairo/machine_jni.cpp) with the product's MachineCore. [lockKeys] are the guest key codes
 * of the lock keys, in the order of the bits the core reports them in.
 */
class NativeMachine(library: String, private val lockKeys: List<Int> = emptyList()) : MachineBridge {
    init { System.loadLibrary(library) }

    /**
     * Starts the machine with [media] by slot (null for empty) and [settings] by name; false while
     * one is already running. The machine reports Starting until it has booted or failed.
     */
    fun start(firmware: String, home: String, media: List<String?>, settings: Map<String, String>): Boolean =
        nativeStart(firmware, home, media.map { it ?: "" }.toTypedArray(),
            settings.flatMap { (name, value) -> listOf(name, value) }.toTypedArray())
            .also { started -> if (started) startedSettings = settings }

    /** The settings the running machine started with, as the UI tests read them (KairoActivity.gameProbe). */
    @Volatile var startedSettings: Map<String, String> = emptyMap()
        private set

    override fun media(slot: Int, path: String?): Boolean = nativeMedia(slot, path) == 0
    override fun mediaPaths(): List<String>? = nativeMediaPaths()?.toList()

    override fun stop() = nativeStop()
    override fun createBlankDisk(kind: String, path: String) {
        val error = nativeCreateBlankDisk(kind, path)
        check(error.isEmpty()) { error }
    }
    override fun awaitEnd(): MachineStatus = MachineStatus(nativeAwaitEnd())
    override fun pause(paused: Boolean) = nativePause(paused)
    override fun reset() = nativeReset()
    override fun key(code: Int, down: Boolean) = nativeKey(code, down)
    override fun joystick(control: Int, down: Boolean) = nativeJoystick(control, down)
    override fun mouseMove(dx: Int, dy: Int) = nativeMouseMove(dx, dy)
    override fun mouseButton(button: Int, down: Boolean) = nativeMouseButton(button, down)
    override fun warpMouse(x: Int, y: Int) = nativeMouseWarp(x, y)
    override fun mouseWarpDone(): Boolean = nativeMouseWarpDone()
    override fun saveState(directory: String) = nativeSaveState(directory)
    override fun loadState(directory: String, moved: Map<String, String>): Int {
        val entries = moved.entries.toList()
        return nativeLoadState(directory, entries.map { it.key }.toTypedArray(), entries.map { it.value }.toTypedArray())
    }
    override fun stateErrorReason(): String = nativeStateError()
    override fun inputTelemetry(): LongArray? = nativeGuestInput()
    override fun lockedKeys(): Set<Int> {
        if (lockKeys.isEmpty()) return emptySet()
        val locks = nativeLocks()
        check(locks >= 0) { "The machine stopped before reporting its lock keys" }
        return lockKeys.filterIndexed { bit, _ -> locks and (1 shl bit) != 0 }.toSet()
    }
    override fun status(): String = nativeStatus()
    override fun setSurface(surface: Surface?) = nativeSetSurface(surface)
    override fun setShaderPreset(path: String?) = nativeSetShaderPreset(path)
    override fun setMuted(muted: Boolean) = nativeSetMuted(muted)
    override fun setFastForward(enabled: Boolean) = nativeSetFastForward(enabled)

    override fun startupSteps(steps: List<StartupStep>, replace: Boolean) = nativeStartupSteps(
        steps.map { it.text }.toTypedArray(), BooleanArray(steps.size) { steps[it].enter },
        steps.map { it.screens.toLongArray() }.toTypedArray(), IntArray(steps.size) { steps[it].timeoutMs }, replace)
    override fun startupKeys(text: String, enter: Boolean) = nativeStartupKeys(text, enter)
    override fun startupResume() = nativeStartupResume()
    override fun startupStatus(): StartupStatus = nativeStartupStatus().let { (state, stalled) ->
        when (state) {
            STARTUP_IDLE -> StartupStatus.Idle
            STARTUP_TYPING -> StartupStatus.Typing
            STARTUP_STALLED -> StartupStatus.Stalled(stalled)
            else -> error("Unknown startup typer state $state")
        }
    }
    override fun screenSignature(): Long? = nativeScreenSignature()?.single()

    /** A product's own request to its core (MachineCore::command), answered between frames; null when no machine runs. */
    fun command(line: String): String? = nativeCommand(line)

    private companion object {
        // machine_jni.cpp StartupState.
        const val STARTUP_IDLE = 0
        const val STARTUP_TYPING = 1
        const val STARTUP_STALLED = 2
    }

    private external fun nativeStart(firmware: String, home: String, media: Array<String>,
                                     settings: Array<String>): Boolean
    private external fun nativeStop()
    private external fun nativeCreateBlankDisk(kind: String, path: String): String
    private external fun nativeAwaitEnd(): String
    private external fun nativePause(paused: Boolean)
    private external fun nativeReset()
    private external fun nativeKey(code: Int, down: Boolean)
    private external fun nativeJoystick(control: Int, down: Boolean)
    private external fun nativeMouseMove(dx: Int, dy: Int)
    private external fun nativeMouseButton(button: Int, down: Boolean)
    private external fun nativeMouseWarp(x: Int, y: Int)
    private external fun nativeMouseWarpDone(): Boolean
    private external fun nativeMedia(slot: Int, path: String?): Int
    private external fun nativeMediaPaths(): Array<String>?
    private external fun nativeSaveState(directory: String): Int
    private external fun nativeLoadState(directory: String, from: Array<String>, to: Array<String>): Int
    private external fun nativeLocks(): Int
    private external fun nativeGuestInput(): LongArray?
    private external fun nativeStateError(): String
    private external fun nativeStatus(): String
    private external fun nativeSetSurface(surface: Surface?)
    private external fun nativeSetShaderPreset(path: String?)
    private external fun nativeSetMuted(muted: Boolean)
    private external fun nativeSetFastForward(enabled: Boolean)
    private external fun nativeStartupSteps(texts: Array<String>, enters: BooleanArray, screens: Array<LongArray>,
                                            timeoutsMs: IntArray, replace: Boolean)
    private external fun nativeStartupKeys(text: String, enter: Boolean)
    private external fun nativeStartupResume()
    private external fun nativeStartupStatus(): IntArray
    private external fun nativeScreenSignature(): LongArray?
    private external fun nativeCommand(line: String): String?
}
