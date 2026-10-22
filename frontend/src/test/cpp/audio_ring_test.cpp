// SPDX-License-Identifier: GPL-2.0-or-later
// Host test for kairo/audio_ring.h:
//   clang++ -std=c++17 -I../../main/cpp audio_ring_test.cpp -o audio_ring_test && ./audio_ring_test
#include "kairo/audio_ring.h"

using kairo::AudioRing;
using kairo::audio_pace_scale;

#include <cmath>
#include <cstdio>
#include <thread>
#include <vector>

static int failures = 0;
static void check(bool condition, const char *message) {
    if (!condition) {
        std::fprintf(stderr, "%s\n", message);
        ++failures;
    }
}

static bool near(double a, double b) { return std::fabs(a - b) < 1e-12; }

static std::vector<int16_t> frames(int16_t first, uint32_t count) {
    std::vector<int16_t> out;
    for (uint32_t i = 0; i < count; ++i) {
        out.push_back(static_cast<int16_t>(first + i));
        out.push_back(static_cast<int16_t>(-(first + i)));
    }
    return out;
}

int main() {
    {
        AudioRing ring(8);
        const auto in = frames(1, 10);
        check(ring.write(in.data(), 10) == 8, "a full ring accepts only its capacity");
        check(ring.size() == 8, "size counts queued frames");
        std::vector<int16_t> out(2 * 5);
        check(ring.read(out.data(), 5) == 5, "read takes what was asked");
        check(out[0] == 1 && out[1] == -1 && out[8] == 5, "frames come out in order, channels intact");
        // The next write wraps around the end of the storage.
        const auto more = frames(100, 4);
        check(ring.write(more.data(), 4) == 4, "freed room is writable");
        std::vector<int16_t> rest(2 * 8);
        check(ring.read(rest.data(), 8) == 7, "read stops at what is queued");
        check(rest[0] == 6 && rest[4] == 8 && rest[6] == 100 && rest[13] == -103,
              "wrapped frames read back in order");
        check(ring.size() == 0, "the ring is empty after reading everything");
    }
    {
        // One writer and one reader on separate threads see every frame once, in order.
        AudioRing ring(256);
        constexpr uint32_t total = 200000;
        std::thread writer([&ring] {
            uint32_t sent = 0;
            while (sent < total) {
                const uint32_t chunk = std::min<uint32_t>(37, total - sent);
                const auto in = frames(static_cast<int16_t>(sent), chunk);
                sent += ring.write(in.data(), chunk);
            }
        });
        uint32_t received = 0;
        bool ordered = true;
        std::vector<int16_t> out(2 * 53);
        while (received < total) {
            const uint32_t got = ring.read(out.data(), 53);
            for (uint32_t i = 0; i < got; ++i) {
                const auto expected = static_cast<int16_t>(received + i);
                if (out[i * 2] != expected || out[i * 2 + 1] != static_cast<int16_t>(-expected)) ordered = false;
            }
            received += got;
        }
        writer.join();
        check(ordered, "concurrent frames arrive once each, in order");
    }
    check(near(audio_pace_scale(1000, 1000), 1.0), "a queue at its target keeps the frame length");
    check(near(audio_pace_scale(2000, 1000), 1.005), "a full queue lengthens frames by 0.5%");
    check(near(audio_pace_scale(9000, 1000), 1.005), "lengthening stops at 0.5%");
    check(near(audio_pace_scale(0, 1000), 0.995), "an empty queue shortens frames by 0.5%");
    check(audio_pace_scale(1500, 1000) > 1.0 && audio_pace_scale(500, 1000) < 1.0,
          "the correction follows the queue's distance from its target");
    if (failures == 0) std::puts("audio_ring_test: all checks passed");
    return failures == 0 ? 0 : 1;
}
