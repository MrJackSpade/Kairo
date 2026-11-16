// Checks kairo/midi_bytes.cpp: a guest's MIDI bytes cut into the messages the shared sender gets.
// Build and run on the host:
//   clang++ -std=c++17 -I../../main/cpp midi_bytes_test.cpp ../../main/cpp/kairo/midi_bytes.cpp -o midi_bytes_test && ./midi_bytes_test

#include <cstdio>
#include <vector>

#include "kairo/midi_bytes.h"
#include "kairo/midi_out.h"

namespace {
std::vector<std::vector<uint8_t>> sent;
int failures = 0;

void check(const char *what, const std::vector<std::vector<uint8_t>> &expected) {
    if (sent != expected) {
        ++failures;
        std::printf("FAIL %s: got", what);
        for (const auto &message : sent) {
            std::printf(" [");
            for (uint8_t byte : message) std::printf(" %02X", byte);
            std::printf(" ]");
        }
        std::printf("\n");
    }
    sent.clear();
}

void write(std::initializer_list<int> bytes) {
    for (int byte : bytes) kairo_midi_out_byte(static_cast<uint8_t>(byte));
}
}  // namespace

// The sender, as the test sees it: every message it is given.
namespace kairo::midi_out {
void send(const uint8_t *message, size_t length) { sent.emplace_back(message, message + length); }
}  // namespace kairo::midi_out

int main() {
    write({0x90, 0x3c, 0x7f});
    check("a note on", {{0x90, 0x3c, 0x7f}});

    write({0x90, 0x3c, 0x7f, 0x40, 0x00, 0x43, 0x50});
    check("running status, as written", {{0x90, 0x3c, 0x7f}, {0x40, 0x00}, {0x43, 0x50}});

    write({0xc0, 0x05, 0x06});
    check("a program change and one under running status", {{0xc0, 0x05}, {0x06}});

    write({0xf0, 0x41, 0x10, 0x42, 0x12, 0xf7});
    check("a SysEx whole", {{0xf0, 0x41, 0x10, 0x42, 0x12, 0xf7}});

    write({0x90, 0x3c, 0xf8, 0x7f});
    check("real time inside a message goes alone, at once", {{0xf8}, {0x90, 0x3c, 0x7f}});

    write({0xf0, 0x43, 0x10, 0x90, 0x3c, 0x7f});
    check("a status byte ends a SysEx without its F7", {{0xf0, 0x43, 0x10}, {0x90, 0x3c, 0x7f}});

    write({0xf0, 0x7e});
    check("an open SysEx waits", {});
    kairo_midi_out_flush();
    check("flush sends it", {{0xf0, 0x7e}});

    write({0x90, 0x3c, 0x7f, 0xf2, 0x01, 0x02, 0x10});
    check("a system message ends running status; a stray data byte goes alone",
          {{0x90, 0x3c, 0x7f}, {0xf2, 0x01, 0x02}, {0x10}});

    write({0xf6, 0xf1, 0x22, 0xf3, 0x01});
    check("system common messages", {{0xf6}, {0xf1, 0x22}, {0xf3, 0x01}});

    write({0xb0, 0x07});
    kairo_midi_out_flush();
    write({0x64});
    check("flush forgets the running status", {{0xb0, 0x07}, {0x64}});

    if (failures == 0) std::puts("midi_bytes_test: all checks passed");
    return failures == 0 ? 0 : 1;
}
