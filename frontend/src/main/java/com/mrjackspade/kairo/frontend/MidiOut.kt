package com.mrjackspade.kairo.frontend

import android.content.Context
import android.content.SharedPreferences
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.IOException

/**
 * A guest's MIDI output to a MIDI input port of the device (KairoX86#305): a USB or Bluetooth MIDI
 * synth, or a synth app's virtual port. The player picks the port once for every game ([row], kept in
 * [preferences]); while a game runs ([start] to [stop]) the core's messages (cpp/kairo/midi_out.h) go
 * to it unchanged and in order, each sent whole as it arrives. With no port picked nothing is sent:
 * that is the player's choice. A picked port that is not there, cannot be opened, fails while sending
 * or cannot keep up is [report]ed to the player, and the game plays on without it.
 */
class MidiOut(private val context: Context, private val preferences: SharedPreferences, private val report: (String) -> Unit) {
    /** A MIDI input port of the device: [key] names it across connections (its device's identity and port number). */
    class Port(val key: String, val label: String, val info: MidiDeviceInfo, val number: Int)

    private val manager: MidiManager? = context.getSystemService(MidiManager::class.java)

    /** Every MIDI input port the device has now. */
    fun ports(): List<Port> {
        val manager = manager ?: return emptyList()
        val devices = if (Build.VERSION.SDK_INT >= 33) manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
            else @Suppress("DEPRECATION") manager.devices.toList()
        return devices.flatMap { info ->
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
                ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: context.getString(R.string.kairo_midi_unnamed)
            info.ports.filter { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }.map { port ->
                val portName = port.name.takeIf { it.isNotBlank() }
                val label = if (info.inputPortCount > 1) "$name: ${portName ?: (port.portNumber + 1)}" else name
                Port(keyOf(info, port.portNumber), label, info, port.portNumber)
            }
        }
    }

    private fun keyOf(info: MidiDeviceInfo, port: Int) = listOf(MidiDeviceInfo.PROPERTY_MANUFACTURER, MidiDeviceInfo.PROPERTY_PRODUCT,
        MidiDeviceInfo.PROPERTY_NAME, MidiDeviceInfo.PROPERTY_SERIAL_NUMBER)
        .joinToString("|") { info.properties.getString(it).orEmpty() } + "|${info.type}|$port"

    /** The port the player picked, by its key; null for none. */
    private val picked: String? get() = preferences.getString(KEY, null)

    /** The settings row that picks the port, for every game: none, or one of the ports there now. */
    fun row() = SettingsRow.choice(KEY, context.getString(R.string.kairo_midi_out), ChoiceSpec(context.getString(R.string.kairo_midi_out),
        { GameSource.ALL_GAMES }, {
            val ports = ports()
            val absent = picked?.takeIf { key -> ports.none { it.key == key } }
            listOf(ChoiceGroup(null, listOf(ChoiceOption(NONE, UiText.res(R.string.kairo_none))) +
                ports.map { ChoiceOption(it.key, UiText.fixed(it.label)) } +
                // A picked port that is not connected stays picked, under its name when it was picked.
                listOfNotNull(absent?.let { ChoiceOption(it, UiText.fixed(preferences.getString(LABEL, it)!!)) })))
        }, { picked ?: NONE }, select = { value ->
            val label = ports().firstOrNull { it.key == value }?.label
            preferences.edit().apply {
                if (value == NONE) remove(KEY).remove(LABEL) else putString(KEY, value).putString(LABEL, label ?: value)
            }.apply()
        }))

    private var session: Session? = null

    /** A game started on [bridge]: its MIDI goes to the picked port, once that is open. */
    fun start(bridge: NativeMachine) {
        stop()
        val key = picked ?: return
        val manager = manager ?: return report(context.getString(R.string.kairo_midi_unavailable))
        val port = ports().firstOrNull { it.key == key }
            ?: return report(context.getString(R.string.kairo_midi_not_connected, preferences.getString(LABEL, key)))
        session = Session(manager, bridge, port).also { it.open() }
    }

    /** The game ended: nothing more goes to the port, which is closed. */
    fun stop() {
        session?.close()
        session = null
    }

    private inner class Session(private val manager: MidiManager, private val bridge: NativeMachine, private val port: Port) {
        private val main = Handler(Looper.getMainLooper())
        @Volatile private var closed = false
        private var device: MidiDevice? = null
        private var input: MidiInputPort? = null
        private var sender: Thread? = null

        private val removed = object : MidiManager.DeviceCallback() {
            override fun onDeviceRemoved(info: MidiDeviceInfo) {
                if (info.id == port.info.id) fail(context.getString(R.string.kairo_midi_disconnected, port.label))
            }
        }

        fun open() {
            // The guest's messages queue from now on, while the device opens, and go once it has.
            bridge.midiOpen()
            manager.registerDeviceCallback(removed, main)
            manager.openDevice(port.info, { opened ->
                if (closed) {
                    opened?.close()
                    return@openDevice
                }
                val input = opened?.openInputPort(port.number)
                if (input == null) {
                    opened?.close()
                    fail(context.getString(R.string.kairo_midi_open_failed, port.label))
                    return@openDevice
                }
                device = opened
                this.input = input
                sender = Thread({ send(input) }, "kairo-midi-out").apply { start() }
            }, main)
        }

        /** Sends each message the core queues, whole, until the queue closes. */
        private fun send(input: MidiInputPort) {
            val buffer = ByteArray(bridge.midiLongest())
            while (true) {
                val length = bridge.midiTake(buffer)
                when {
                    length == CLOSED -> return
                    length == OVERFLOWED -> {
                        main.post { fail(context.getString(R.string.kairo_midi_behind, port.label)) }
                        return
                    }
                    length > 0 -> try {
                        input.send(buffer, 0, length, System.nanoTime())
                    } catch (error: IOException) {
                        main.post { fail(context.getString(R.string.kairo_midi_send_failed, port.label, error.message ?: error.javaClass.simpleName)) }
                        return
                    }
                }
            }
        }

        /** The port stopped working: the player is told, and the game plays on without it. */
        private fun fail(message: String) {
            if (closed) return
            close()
            if (session === this) session = null
            report(message)
        }

        fun close() {
            if (closed) return
            closed = true
            manager.unregisterDeviceCallback(removed)
            bridge.midiClose()
            sender?.join()
            input?.close()
            device?.close()
        }
    }

    companion object {
        private const val KEY = "midi_out_port"
        private const val LABEL = "midi_out_port_label"
        private const val NONE = "none"
        // cpp/kairo/midi_out.h's take() results
        private const val CLOSED = -1
        private const val OVERFLOWED = -2
    }
}
