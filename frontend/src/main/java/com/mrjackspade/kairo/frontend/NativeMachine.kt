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

    override fun media(slot: Int, path: String?): Boolean = nativeMedia(slot, path) == 0
    override fun mediaPaths(): List<String>? = nativeMediaPaths()?.toList()

    override fun stop() = nativeStop()
    override fun pause(paused: Boolean) = nativePause(paused)
    override fun reset() = nativeReset()
    override fun key(code: Int, down: Boolean) = nativeKey(code, down)
    override fun joystick(control: Int, down: Boolean) = nativeJoystick(control, down)
    override fun mouseMove(dx: Int, dy: Int) = nativeMouseMove(dx, dy)
    override fun mouseButton(button: Int, down: Boolean) = nativeMouseButton(button, down)
    override fun saveState(directory: String) = nativeSaveState(directory)
    override fun loadState(directory: String) = nativeLoadState(directory)
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

    private external fun nativeStart(firmware: String, home: String, media: Array<String>,
                                     settings: Array<String>): Boolean
    private external fun nativeStop()
    private external fun nativePause(paused: Boolean)
    private external fun nativeReset()
    private external fun nativeKey(code: Int, down: Boolean)
    private external fun nativeJoystick(control: Int, down: Boolean)
    private external fun nativeMouseMove(dx: Int, dy: Int)
    private external fun nativeMouseButton(button: Int, down: Boolean)
    private external fun nativeMedia(slot: Int, path: String?): Int
    private external fun nativeMediaPaths(): Array<String>?
    private external fun nativeSaveState(directory: String): Int
    private external fun nativeLoadState(directory: String): Int
    private external fun nativeLocks(): Int
    private external fun nativeStatus(): String
    private external fun nativeSetSurface(surface: Surface?)
    private external fun nativeSetShaderPreset(path: String?)
    private external fun nativeSetMuted(muted: Boolean)
    private external fun nativeSetFastForward(enabled: Boolean)
}
