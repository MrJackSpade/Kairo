// A guest's MIDI output on its way to a MIDI input port of the device (Kairo's MidiOut.kt, KairoX86#305):
// a core hands each whole message here on its emulation thread, unchanged and in order, and the app's
// MIDI thread takes them and sends them to the port the player chose (android.media.midi). With no port
// open, a message goes nowhere: the player chose none. The core never waits for the port: messages
// queue, and a port that cannot keep up is reported to the player and closed.

#pragma once

#include <cstddef>
#include <cstdint>

namespace kairo::midi_out {

// One whole MIDI message: a channel or system message of 1 to 3 bytes, or a System Exclusive one
// from its F0 to its F7.
void send(const uint8_t *message, size_t length);

// The queue's size: a megabyte holds seconds of the densest MIDI a guest can send (31 250 baud is
// about 3 KB a second) and SysEx sample dumps. The longest message is what it holds besides its length.
constexpr size_t kQueueBytes = 1u << 20;
constexpr size_t kLongestMessage = kQueueBytes - sizeof(uint32_t);

// What take() returns besides a message's length.
constexpr int kClosed = -1;
constexpr int kOverflowed = -2;

// The app's MIDI thread: a port is open (messages queue from now on) or closed (they go nowhere, and
// a take() waiting returns kClosed).
void open();
void close();

// Waits for the next message and copies it into buffer (kLongestMessage bytes), returning its length;
// kClosed once closed, kOverflowed once a message was lost because the queue was full (the port took
// them too slowly).
int take(uint8_t *buffer);

}  // namespace kairo::midi_out
