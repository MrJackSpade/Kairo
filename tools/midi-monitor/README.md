# MIDI monitor

A test tool for the apps' MIDI out (`kairo/midi_out.h`, `MidiOut.kt`): an Android app with a virtual MIDI
device, "MIDI monitor", whose one input port logs every message it gets to logcat (tag `KairoMidiMonitor`)
as hex bytes. Picked in an app's Configurations › MIDI out like a synth, it shows the bytes the guest
wrote, in order.

Build and install (Windows, from this folder):

```
..\..\gradlew.bat -p . assembleDebug
adb -s <serial> install -r build\outputs\apk\debug\midi-monitor-debug.apk
adb -s <serial> logcat -s KairoMidiMonitor
```
