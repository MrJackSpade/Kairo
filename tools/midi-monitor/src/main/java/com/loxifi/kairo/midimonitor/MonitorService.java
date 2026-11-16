package com.loxifi.kairo.midimonitor;

import android.media.midi.MidiDeviceService;
import android.media.midi.MidiReceiver;
import android.util.Log;

/**
 * The MIDI monitor's one input port: every message an app sends it goes to logcat (tag KairoMidiMonitor)
 * as hex bytes, one line per send, in the order they came.
 */
public class MonitorService extends MidiDeviceService {
    private static final String TAG = "KairoMidiMonitor";

    @Override
    public MidiReceiver[] onGetInputPortReceivers() {
        return new MidiReceiver[] {new MidiReceiver() {
            @Override
            public void onSend(byte[] message, int offset, int count, long timestamp) {
                StringBuilder text = new StringBuilder(count * 3);
                for (int i = 0; i < count; i++) {
                    if (i > 0) text.append(' ');
                    text.append(String.format("%02X", message[offset + i] & 0xff));
                }
                Log.i(TAG, text.toString());
            }
        }};
    }
}
