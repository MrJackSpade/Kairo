// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <vector>

namespace kairo {

// Interleaved stereo 16-bit frames from the emulation thread (the only writer) to
// the audio device's callback (the only reader). Neither side ever waits on the
// other, so a device burst cannot stall emulation and a late frame only drains
// what is queued.
class AudioRing {
public:
    // capacity_frames must be a power of two.
    explicit AudioRing(uint32_t capacity_frames)
        : samples_(capacity_frames * 2), mask_(capacity_frames - 1) {}

    uint32_t capacity() const { return mask_ + 1; }

    uint32_t size() const {
        return static_cast<uint32_t>(head_.load(std::memory_order_acquire) -
                                     tail_.load(std::memory_order_acquire));
    }

    // Queues as many of count frames as fit and returns how many that was.
    uint32_t write(const int16_t *frames, uint32_t count) {
        const uint64_t head = head_.load(std::memory_order_relaxed);
        const uint64_t tail = tail_.load(std::memory_order_acquire);
        count = std::min<uint32_t>(count, capacity() - static_cast<uint32_t>(head - tail));
        const uint32_t start = static_cast<uint32_t>(head) & mask_;
        const uint32_t first = std::min(count, capacity() - start);
        std::memcpy(samples_.data() + start * 2, frames, first * kFrameBytes);
        std::memcpy(samples_.data(), frames + first * 2, (count - first) * kFrameBytes);
        head_.store(head + count, std::memory_order_release);
        return count;
    }

    // Takes up to count frames and returns how many there were.
    uint32_t read(int16_t *frames, uint32_t count) {
        const uint64_t tail = tail_.load(std::memory_order_relaxed);
        const uint64_t head = head_.load(std::memory_order_acquire);
        count = std::min<uint32_t>(count, static_cast<uint32_t>(head - tail));
        const uint32_t start = static_cast<uint32_t>(tail) & mask_;
        const uint32_t first = std::min(count, capacity() - start);
        std::memcpy(frames, samples_.data() + start * 2, first * kFrameBytes);
        std::memcpy(frames + first * 2, samples_.data(), (count - first) * kFrameBytes);
        tail_.store(tail + count, std::memory_order_release);
        return count;
    }

private:
    static constexpr uint32_t kFrameBytes = 2 * sizeof(int16_t);
    std::vector<int16_t> samples_;
    const uint32_t mask_;
    std::atomic<uint64_t> head_{0};
    std::atomic<uint64_t> tail_{0};
};

// The frame-length multiplier that holds the queue at its target. A fuller queue
// lengthens frames and an emptier one shortens them, by at most 0.5%: too little
// to see or hear, but far more than the drift between the host clock and the
// audio device's, so the queue neither fills nor runs dry over a long session.
inline double audio_pace_scale(uint32_t queued, uint32_t target) {
    const double error = (static_cast<double>(queued) - target) / target;
    return 1.0 + std::clamp(error, -1.0, 1.0) * 0.005;
}

} // namespace kairo
