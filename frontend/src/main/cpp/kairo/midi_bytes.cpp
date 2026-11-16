// A guest's MIDI out bytes cut into whole messages (midi_bytes.h).

#include "kairo/midi_bytes.h"

#include <cstddef>
#include <vector>

#include "kairo/midi_out.h"

namespace {

// The data bytes a status byte's message takes; a SysEx (F0) takes until its F7.
size_t data_bytes(uint8_t status) {
    if (status < 0xf0) return (status & 0xe0) == 0xc0 ? 1 : 2;  // C0-DF: one; the other channel messages: two
    switch (status) {
        case 0xf1: case 0xf3: return 1;
        case 0xf2: return 2;
        default: return 0;
    }
}

struct Stream {
    std::vector<uint8_t> message;
    // The data bytes a message under the running status takes; 0 when there is none to run (none
    // yet, or after a system message, which ends running status).
    size_t running = 0;
    bool sysex = false;

    void flush() {
        if (!message.empty()) kairo::midi_out::send(message.data(), message.size());
        message.clear();
    }

    void put(uint8_t byte) {
        // A SysEx longer than the sender's longest message goes in pieces of that size.
        if (message.size() == kairo::midi_out::kLongestMessage) flush();
        message.push_back(byte);
    }

    void write(uint8_t byte) {
        if (byte >= 0xf8) {
            // Real time: alone, now, inside any message.
            kairo::midi_out::send(&byte, 1);
        } else if (sysex && byte == 0xf7) {
            put(byte);
            flush();
            sysex = false;
        } else if (byte & 0x80) {
            // A status byte ends what was open (a SysEx without its F7 too) and starts its own message.
            flush();
            sysex = byte == 0xf0;
            put(byte);
            const size_t takes = data_bytes(byte);
            running = byte < 0xf0 ? takes : 0;
            if (!sysex && takes == 0) flush();
        } else if (sysex) {
            put(byte);
        } else {
            // The open message's data byte, or under running status the first of a message without its
            // status byte, as the guest wrote it.
            put(byte);
            const bool has_status = message[0] & 0x80;
            const size_t data = has_status ? message.size() - 1 : message.size();
            const size_t takes = has_status ? data_bytes(message[0]) : running;
            if (data >= takes) flush();
        }
    }
};

Stream &stream() {
    static Stream instance;
    return instance;
}

}  // namespace

extern "C" void kairo_midi_out_byte(uint8_t byte) { stream().write(byte); }

extern "C" void kairo_midi_out_flush(void) {
    Stream &s = stream();
    s.flush();
    s.running = 0;
    s.sysex = false;
}
