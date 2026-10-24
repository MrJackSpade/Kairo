// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once
#include <algorithm>
#include <android/log.h>
#include <chrono>
#include <cstdint>
#include <vector>

namespace kairo {
// What the frame host did over each run of kFrames frames, logged as one line (Kairo68#20, #21,
// #24, #25, #28): the rate against the guest's, where the worker's time went, how late frames
// started against their slots, frames the presenter never showed, and the audio queue.
class FrameReport {
public:
    static constexpr uint32_t kFrames = 600;
    using Clock = std::chrono::steady_clock;

    // A frame that started lateness after its slot on the grid (zero when on time).
    void frame_started(Clock::time_point now, Clock::duration lateness) {
        if (count_ == 0) start_ = now;
        late_ += lateness > kLate ? 1 : 0;
        lateness_max_ = std::max(lateness_max_, lateness);
    }
    void ran(Clock::duration run, Clock::duration publish, Clock::duration audio) {
        run_.push_back(run);
        publish_ += publish;
        audio_ += audio;
    }
    // A published frame replaced one the presenter had not shown.
    void replaced() { ++replaced_; }
    // The audio queue after the frame's sound was written, in frames.
    void queued(uint32_t frames) {
        queue_min_ = std::min(queue_min_, frames);
        queue_max_ = std::max(queue_max_, frames);
    }
    // Ends the frame; logs and starts over after kFrames. guest_rate is the guest's frames a second.
    void frame_done(Clock::time_point now, double guest_rate, int32_t underruns, bool fast_forward) {
        if (++count_ < kFrames) return;
        std::sort(run_.begin(), run_.end());
        const double seconds = std::chrono::duration<double>(now - start_).count();
        const auto ms = [](Clock::duration d) { return std::chrono::duration<double, std::milli>(d).count(); };
        __android_log_print(ANDROID_LOG_INFO, "KairoMachine",
                            "frames: %u in %.2f s, %.2f/s against the guest's %.2f%s; run ms mean %.2f median %.2f "
                            "p95 %.2f max %.2f; publish %.3f ms, audio %.3f ms a frame; late %u (max %.1f ms); "
                            "unshown %u; audio queue %u-%u frames, underruns %d",
                            count_, seconds, count_ / seconds, guest_rate, fast_forward ? " (fast-forward)" : "",
                            ms(sum(run_)) / count_, ms(run_[run_.size() / 2]), ms(run_[run_.size() * 95 / 100]),
                            ms(run_.back()), ms(publish_) / count_, ms(audio_) / count_, late_, ms(lateness_max_),
                            replaced_, queue_min_ == UINT32_MAX ? 0 : queue_min_, queue_max_,
                            underruns - underruns_before_);
        reset(underruns);
    }

private:
    void reset(int32_t underruns) {
        count_ = 0;
        run_.clear();
        publish_ = audio_ = lateness_max_ = Clock::duration{};
        late_ = replaced_ = 0;
        queue_min_ = UINT32_MAX;
        queue_max_ = 0;
        underruns_before_ = underruns;
    }

    // Later than this is a late frame: about a third of a 55-60 Hz frame.
    static constexpr auto kLate = std::chrono::milliseconds(5);
    static Clock::duration sum(const std::vector<Clock::duration> &all) {
        Clock::duration total{};
        for (const auto d : all) total += d;
        return total;
    }
    uint32_t count_ = 0;
    Clock::time_point start_{};
    std::vector<Clock::duration> run_;
    Clock::duration publish_{}, audio_{}, lateness_max_{};
    uint32_t late_ = 0, replaced_ = 0;
    uint32_t queue_min_ = UINT32_MAX, queue_max_ = 0;
    int32_t underruns_before_ = 0;
};
} // namespace kairo
