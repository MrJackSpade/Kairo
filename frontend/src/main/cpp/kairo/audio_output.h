// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <aaudio/AAudio.h>
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <memory>
#include <string>

#include "audio_ring.h"

namespace kairo {

// A guest's stereo 16-bit sound, played through an AAudio data callback. The
// emulation thread queues samples with write(); the device pulls them on its own
// clock. The queue holds queue_target() frames: enough to cover the lateness the
// frame grid recovers from, so a late frame never underruns the device.
class AudioOutput {
public:
    // A started low-latency stream at sample_rate, or null when the device cannot
    // open one.
    static std::unique_ptr<AudioOutput> open(int32_t sample_rate, uint32_t queue_target) {
        std::unique_ptr<AudioOutput> output(new AudioOutput(queue_target));
        AAudioStreamBuilder *builder = nullptr;
        if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) return nullptr;
        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setSampleRate(builder, sample_rate);
        AAudioStreamBuilder_setChannelCount(builder, 2);
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setDataCallback(builder, on_data, output.get());
        AAudioStreamBuilder_setErrorCallback(builder, on_error, output.get());
        const aaudio_result_t opened = AAudioStreamBuilder_openStream(builder, &output->stream_);
        AAudioStreamBuilder_delete(builder);
        if (opened != AAUDIO_OK) return nullptr;
        if (AAudioStream_requestStart(output->stream_) != AAUDIO_OK) return nullptr;
        return output;
    }

    ~AudioOutput() {
        if (!stream_) return;
        AAudioStream_requestStop(stream_);
        AAudioStream_close(stream_);
    }

    AudioOutput(const AudioOutput &) = delete;
    AudioOutput &operator=(const AudioOutput &) = delete;

    // Queues frames; returns how many fit. The rest are dropped, which only
    // happens while frames run back to back, as in fast-forward.
    uint32_t write(const int16_t *frames, uint32_t count) { return ring_.write(frames, count); }

    // When frames have run back to back and real time resumes: the device skips what is queued
    // beyond the target, once. Frames that ran back to back fill the queue to capacity, and
    // playing all of it put a third of a second of sound behind the picture for over a minute
    // while pacing drained it (Kairo68#25).
    void trim_to_target() { trim_.store(true, std::memory_order_release); }

    void pause() { AAudioStream_requestPause(stream_); }
    void resume() { AAudioStream_requestStart(stream_); }

    // The frame-length multiplier that keeps the queue at its target.
    double pace_scale() const { return audio_pace_scale(ring_.size(), queue_target_); }

    // The error AAudio stopped the stream with, such as AAUDIO_ERROR_DISCONNECTED
    // when the output route changes, or AAUDIO_OK while it plays. A stopped
    // stream never restarts, so the owner opens a new one.
    aaudio_result_t lost_error() const { return lost_error_.load(std::memory_order_acquire); }

    uint32_t queued() const { return ring_.size(); }

    // Times the queue ran dry under the device, plus the device's own underruns.
    int32_t underruns() const {
        const int32_t device = AAudioStream_getXRunCount(stream_);
        return queue_underruns_.load(std::memory_order_relaxed) + (device > 0 ? device : 0);
    }

    // "buf <device buffer>/<capacity>/<burst> q<target>" for Diagnostics.
    std::string config() const {
        char text[80];
        std::snprintf(text, sizeof(text), "buf %d/%d/%d q%u",
                      AAudioStream_getBufferSizeInFrames(stream_),
                      AAudioStream_getBufferCapacityInFrames(stream_),
                      AAudioStream_getFramesPerBurst(stream_), queue_target_);
        return text;
    }

private:
    // Room for four times the target, so frames that run back to back after a
    // stall refill the queue instead of dropping samples.
    static uint32_t ring_capacity(uint32_t queue_target) {
        uint32_t capacity = 1;
        while (capacity < 4 * queue_target) capacity <<= 1;
        return capacity;
    }

    explicit AudioOutput(uint32_t queue_target)
        : queue_target_(queue_target), ring_(ring_capacity(queue_target)) {}

    static aaudio_data_callback_result_t on_data(AAudioStream *, void *self, void *audio,
                                                 int32_t frames) {
        auto *output = static_cast<AudioOutput *>(self);
        auto *samples = static_cast<int16_t *>(audio);
        const uint32_t wanted = static_cast<uint32_t>(frames);
        if (!output->primed_.load(std::memory_order_relaxed)) {
            if (output->ring_.size() < output->queue_target_) {
                std::memset(samples, 0, wanted * 2 * sizeof(int16_t));
                return AAUDIO_CALLBACK_RESULT_CONTINUE;
            }
            output->primed_.store(true, std::memory_order_relaxed);
        }
        if (output->trim_.exchange(false, std::memory_order_acquire)) {
            const uint32_t queued = output->ring_.size();
            if (queued > output->queue_target_) output->ring_.skip(queued - output->queue_target_);
        }
        const uint32_t got = output->ring_.read(samples, wanted);
        if (got < wanted) {
            std::memset(samples + got * 2, 0, (wanted - got) * 2 * sizeof(int16_t));
            output->queue_underruns_.fetch_add(1, std::memory_order_relaxed);
            output->primed_.store(false, std::memory_order_relaxed);
        }
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    static void on_error(AAudioStream *, void *self, aaudio_result_t error) {
        static_cast<AudioOutput *>(self)->lost_error_.store(error, std::memory_order_release);
    }

    AAudioStream *stream_ = nullptr;
    const uint32_t queue_target_;
    AudioRing ring_;
    // The callback plays silence until the queue first reaches its target, and
    // again after it runs dry, so playback resumes with the full cushion rather
    // than crackling frame by frame at the edge of empty.
    std::atomic<bool> primed_{false};
    std::atomic<bool> trim_{false}; // trim_to_target, for the callback
    std::atomic<int32_t> queue_underruns_{0};
    std::atomic<aaudio_result_t> lost_error_{AAUDIO_OK};
};

} // namespace kairo
