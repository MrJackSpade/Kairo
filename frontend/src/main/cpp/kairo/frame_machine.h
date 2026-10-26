// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <android/log.h>
#include <android/native_window.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <deque>
#include <functional>
#include <future>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include "thread_priority.h"
#include "audio_output.h"
#include "frame_report.h"
#include "surface_presenter.h"
#include "gl_frame_presenter.h"

namespace kairo {

// Runs a software-rendered machine core one guest frame at a time on its own
// thread, paced to the audio device's clock, and shows its frames in an Android
// window. A product supplies the core; the host owns threads, pacing, audio,
// presentation, pause and fast-forward.
//
// Core is any class with these members, all called on the emulation thread:
//
//   bool run_frame();            Runs one guest frame. False when the core can
//                                not continue; error() then says why.
//   std::string error();
//   const uint8_t *frame(uint32_t &width, uint32_t &height);
//                                The current picture: width x height RGBA8
//                                pixels, rows without padding.
//   double frame_seconds();      How long the frame run_frame() just ran lasts
//                                in guest time.
//   int32_t audio_rate();        Stereo 16-bit frames per second.
//   uint32_t read_audio(int16_t *stereo, uint32_t max_frames);
//                                Takes up to max_frames of the audio produced
//                                so far; returns how many.
//   void release_inputs();       Releases every key and control held down.
//
// One session runs at a time. A session's core is created, run and destroyed on
// its emulation thread; products reach it only through post() and call().
template <class Core>
class FrameMachine {
public:
    // Creates the session's core, or returns null and sets error.
    using Factory = std::function<std::unique_ptr<Core>(std::string &error)>;

    // call()'s result when the session ended before the task ran.
    static constexpr int kNotRunning = -1000;

    ~FrameMachine() { stop(); }

    // Starts a session; false when one is already running.
    bool start(Factory factory) {
        std::lock_guard<std::mutex> lifecycle(lifecycle_mutex_);
        if (worker_.joinable()) return false;
        {
            std::lock_guard<std::mutex> guard(command_mutex_);
            commands_.clear();
            frames_ = 0;
            pictured_ = false;
            pictured_seen_ = false;
            xruns_ = 0;
            audio_state_ = "off";
            audio_config_.clear();
            details_.clear();
            error_.clear();
            state_ = "Starting";
            active_ = true;
            ++started_;
        }
        fast_forward_.store(false);
        worker_ = std::thread(&FrameMachine::run, this, std::move(factory));
        return true;
    }

    // Ends the session, if any, and waits for its thread.
    void stop() {
        std::lock_guard<std::mutex> lifecycle(lifecycle_mutex_);
        enqueue({Command::Stop});
        if (worker_.joinable()) worker_.join();
    }

    void pause(bool paused) { enqueue({paused ? Command::Pause : Command::Resume}); }

    // Runs task on the emulation thread before the next frame, as for input.
    void post(std::function<void(Core &)> task) {
        Command command{Command::Task};
        command.task = std::move(task);
        enqueue(std::move(command));
    }

    // On the emulation thread, from a task: while the session is paused, runs one
    // guest frame, drops its sound and shows it. A core draws its picture only as it
    // runs, so after a state load the paused screen would otherwise keep the picture
    // from before the load.
    void redraw_paused(Core &core) {
        if (!paused_) return;
        if (!core.run_frame()) {
            report("Error", core.error());
            return;
        }
        publish(core);
        int16_t discarded[kAudioChunk * 2];
        while (core.read_audio(discarded, kAudioChunk) > 0) {}
    }

    // Runs task on the emulation thread between frames and waits for its result,
    // or returns kNotRunning when the session ends first.
    int call(std::function<int(Core &)> task) {
        auto completion = std::make_shared<std::promise<int>>();
        auto result = completion->get_future();
        Command command{Command::Task};
        command.task = [completion, task = std::move(task)](Core &core) {
            completion->set_value(task(core));
        };
        {
            std::lock_guard<std::mutex> guard(command_mutex_);
            if (!active_) return kNotRunning;
            commands_.push_back(std::move(command));
        }
        command_ready_.notify_one();
        try {
            return result.get();
        } catch (const std::future_error &) {
            // The session ended with the task still queued.
            return kNotRunning;
        }
    }

    // The window frames are shown in, or null. The host takes over the caller's
    // reference. Returns once the presenter has let go of the window before it, as
    // GLSurfaceView does: Android tears a surface down as soon as surfaceDestroyed
    // returns, and a frame still drawing into it then fails (the screens' swap on the
    // RG DS hands the game's window from one display to the other).
    void set_window(ANativeWindow *window) {
        ANativeWindow *previous = nullptr;
        unsigned generation;
        {
            std::lock_guard<std::mutex> guard(window_mutex_);
            previous = window_;
            window_ = window;
            generation = window_generation_.fetch_add(1, std::memory_order_relaxed) + 1;
        }
        present_ready_.notify_one();
        {
            std::unique_lock<std::mutex> guard(window_mutex_);
            window_taken_.wait(guard, [&] {
                return !presenting_ || static_cast<int>(window_taken_generation_ - generation) >= 0;
            });
        }
        if (previous) ANativeWindow_release(previous);
    }

    // A RetroArch .slangp preset to filter frames through, or "" for none.
    void set_shader_preset(std::string path) {
        {
            std::lock_guard<std::mutex> guard(window_mutex_);
            shader_preset_ = std::move(path);
            shader_generation_.fetch_add(1, std::memory_order_relaxed);
        }
        present_ready_.notify_one();
    }

    void set_muted(bool muted) { muted_.store(muted); }

    // While set, frames run back to back instead of on the frame grid, and audio
    // fills the output queue; when it ends, what is queued beyond the target is skipped.
    void set_fast_forward(bool enabled) { fast_forward_.store(enabled); }

    // Blocks until the session started last has ended, however it ended, and returns
    // "<state>|<error>" as it ended: Stopped, or Error with the core's message. Returns at
    // once when no session has run since the last one ended.
    std::string await_end() {
        std::unique_lock<std::mutex> guard(command_mutex_);
        ended_ready_.wait(guard, [this] { return ended_ == started_; });
        return end_state_ + "|" + end_error_;
    }

    // "<state>|<error>|<frames>|<audio>|<xruns>|<audio config>|<pictured>": state is
    // Stopped, Starting, Running, Paused or Error; pictured is 1 once a frame that is
    // not all black has been published.
    std::string status() {
        std::lock_guard<std::mutex> guard(command_mutex_);
        return state_ + "|" + error_ + "|" + std::to_string(frames_) + "|" + audio_state_ + "|" +
               std::to_string(xruns_) + "|" + audio_config_ + "|" + (pictured_ ? "1" : "0") + "|" + details_;
    }

private:
    // Whether the session is paused; the emulation thread's alone.
    bool paused_ = false;

    struct Command {
        enum Kind { Task, Pause, Resume, Stop } kind;
        std::function<void(Core &)> task;
    };

    // How far the frame grid may fall behind before it re-anchors to the present;
    // the audio queue holds this much so recoverable lateness never underruns.
    static constexpr auto kFrameStall = std::chrono::milliseconds(50);
    static constexpr uint32_t kAudioChunk = 1024;

    void enqueue(Command command) {
        {
            std::lock_guard<std::mutex> guard(command_mutex_);
            if (!active_) return;
            commands_.push_back(std::move(command));
        }
        command_ready_.notify_one();
    }

    void report(const char *state, std::string error = "") {
        std::lock_guard<std::mutex> guard(command_mutex_);
        state_ = state;
        error_ = std::move(error);
    }

    void run(Factory factory) {
        std::string error;
        std::unique_ptr<Core> core = factory(error);
        if (!core) {
            {
                std::lock_guard<std::mutex> guard(command_mutex_);
                state_ = "Error";
                error_ = error;
                active_ = false;
                // Unanswered commands break their promises, so a caller waiting on one returns.
                commands_.clear();
                end_session();
            }
            ended_ready_.notify_all();
            return;
        }
        report("Running");
        name_thread("kairo-frames");
        raise_emulation_priority("worker");
        {
            std::lock_guard<std::mutex> guard(present_mutex_);
            present_stop_ = false;
            pending_.clear();
            has_pending_ = false;
        }
        std::thread presenter(&FrameMachine::present_loop, this);

        const int32_t rate = core->audio_rate();
        std::unique_ptr<AudioOutput> audio;
        int32_t earlier_underruns = 0;
        // Opens the stream, or a new one after Android stops the old one, and shows
        // it in Diagnostics.
        const auto open_audio = [&] {
            if (audio) earlier_underruns += audio->underruns();
            audio.reset();
            audio = AudioOutput::open(rate, static_cast<uint32_t>(
                static_cast<int64_t>(rate) * kFrameStall.count() / 1000));
            std::lock_guard<std::mutex> guard(command_mutex_);
            audio_state_ = audio ? "on" : "off";
            audio_config_ = audio ? audio->config() : "";
        };
        open_audio();

        std::vector<int16_t> samples(kAudioChunk * 2);
        FrameReport pacing;
        bool was_flat_out = false;
        paused_ = false;
        bool stop = false;
        auto next_frame = std::chrono::steady_clock::now();
        while (!stop) {
            std::deque<Command> pending;
            {
                // The worker's only wait. A command wakes it early so input is applied at
                // once, but the frame itself still waits for its slot on the grid below.
                std::unique_lock<std::mutex> guard(command_mutex_);
                const auto has_commands = [this] { return !commands_.empty(); };
                if (paused_) command_ready_.wait(guard, has_commands);
                else command_ready_.wait_until(guard, next_frame, has_commands);
                pending.swap(commands_);
            }
            for (auto &command : pending) {
                switch (command.kind) {
                    case Command::Task:
                        command.task(*core);
                        break;
                    case Command::Pause:
                        paused_ = true;
                        core->release_inputs();
                        if (audio) audio->pause();
                        report("Paused");
                        break;
                    case Command::Resume:
                        paused_ = false;
                        if (audio) audio->resume();
                        next_frame = std::chrono::steady_clock::now();
                        report("Running");
                        break;
                    case Command::Stop:
                        core->release_inputs();
                        stop = true;
                        break;
                }
            }
            if (stop || paused_) continue;
            // Running a frame for every input event would run the guest faster than
            // its refresh rate, overfill the audio stream, and push next_frame ahead
            // of real time so audio starves once the input stops.
            const auto started = std::chrono::steady_clock::now();
            if (started < next_frame) continue;
            const bool flat_out = fast_forward_.load(std::memory_order_relaxed) || core->booting();
            if (audio && was_flat_out && !flat_out) audio->trim_to_target();
            was_flat_out = flat_out;
            pacing.frame_started(started, started - next_frame);
            if (!core->run_frame()) {
                report("Error", core->error());
                break;
            }
            const auto ran = std::chrono::steady_clock::now();
            if (publish(*core)) pacing.replaced();
            const auto published = std::chrono::steady_clock::now();
            uint32_t got;
            while ((got = core->read_audio(samples.data(), kAudioChunk)) > 0) {
                if (muted_.load(std::memory_order_relaxed))
                    std::memset(samples.data(), 0, got * 2 * sizeof(int16_t));
                if (audio) audio->write(samples.data(), got);
            }
            pacing.ran(ran - started, published - ran, std::chrono::steady_clock::now() - published);
            if (audio) pacing.queued(audio->queued());
            {
                std::string details = core->details();
                // Whether the core still loads flat out (MachineCore::booting), as the UI tests
                // read it ("core.booting") before timing the game.
                if (core->booting()) details += details.empty() ? "booting=1" : ";booting=1";
                std::lock_guard<std::mutex> guard(command_mutex_);
                ++frames_;
                details_.swap(details);
                if (audio) xruns_ = earlier_underruns + audio->underruns();
            }
            // Each frame's slot lasts as long as the guest frame, stretched or shrunk
            // by at most 0.5% to hold the audio queue at its target, so the host
            // clock cannot drift from the audio device's. A frame that overruns its
            // slot leaves next_frame in the past, so the following frames start at
            // once and the grid is recovered rather than shifted; only a backlog
            // beyond kFrameStall (a stall, not an overrun) re-anchors the grid.
            const double pace = audio ? audio->pace_scale() : 1.0;
            next_frame += std::chrono::nanoseconds(
                static_cast<int64_t>(core->frame_seconds() * 1e9 * pace));
            const auto now = std::chrono::steady_clock::now();
            pacing.frame_done(now, 1.0 / core->frame_seconds(), audio ? earlier_underruns + audio->underruns() : 0,
                              fast_forward_.load(std::memory_order_relaxed) || core->booting());
            if (fast_forward_.load(std::memory_order_relaxed) || core->booting()) next_frame = now;
            else if (now - next_frame > kFrameStall) next_frame = now;
            if (audio && audio->lost_error() != AAUDIO_OK) {
                __android_log_print(ANDROID_LOG_ERROR, "KairoMachine", "audio stream stopped with %s; reopening",
                                    AAudio_convertResultToText(audio->lost_error()));
                open_audio();
            }
        }
        audio.reset();
        {
            std::lock_guard<std::mutex> guard(present_mutex_);
            present_stop_ = true;
        }
        present_ready_.notify_one();
        presenter.join();
        core.reset();
        {
            std::lock_guard<std::mutex> guard(command_mutex_);
            active_ = false;
            // Unanswered commands break their promises, so a caller waiting on one returns.
            commands_.clear();
            audio_state_ = "off";
            if (state_ != "Error") state_ = "Stopped";
            end_session();
        }
        ended_ready_.notify_all();
    }

    // With command_mutex_ held: records how the session ended, for await_end.
    void end_session() {
        end_state_ = state_;
        end_error_ = error_;
        ended_ = started_;
    }

    // Hands the core's current picture to the presenter, replacing one it has
    // not shown yet; true when it did replace one.
    bool publish(Core &core) {
        uint32_t width = 0, height = 0;
        const uint8_t *pixels = core.frame(width, height);
        if (!pixels || !width || !height) return false;
        const size_t row = static_cast<size_t>(width) * 4;
        const size_t stride = core.frame_stride() ? core.frame_stride() : row;
        if (stride < row)
            __android_log_assert("stride", "KairoMachine", "A frame row of %zu bytes is shorter than its %u pixels", stride, width);
        if (!pictured_seen_ && !black(pixels, width, height, stride)) {
            pictured_seen_ = true;
            std::lock_guard<std::mutex> guard(command_mutex_);
            pictured_ = true;
        }
        const FrameShape shape{width, height, core.line_repeat(), core.frame_bgra()};
        if (shape.repeat < 1 || shape.repeat > 4)
            __android_log_assert("repeat", "KairoMachine", "A frame row cannot stand for %u rows", shape.repeat);
        bool replaced;
        {
            std::lock_guard<std::mutex> guard(present_mutex_);
            replaced = has_pending_;
            if (stride == row) pending_.assign(pixels, pixels + row * height);
            else {
                pending_.resize(row * height);
                for (uint32_t y = 0; y < height; ++y)
                    std::memcpy(pending_.data() + y * row, pixels + y * stride, row);
            }
            pending_shape_ = shape;
            has_pending_ = true;
        }
        present_ready_.notify_one();
        return replaced;
    }

    // Whether every pixel's colour channels are at most 16 of 255, as a booting
    // machine's screen is before it shows anything.
    static bool black(const uint8_t *pixels, uint32_t width, uint32_t height, size_t stride) {
        for (uint32_t y = 0; y < height; ++y) {
            const uint8_t *line = pixels + y * stride;
            for (size_t i = 0; i < static_cast<size_t>(width) * 4; i += 4)
                if (line[i] > 16 || line[i + 1] > 16 || line[i + 2] > 16) return false;
        }
        return true;
    }

    // Presentation runs on its own thread: ANativeWindow_lock and eglSwapBuffers
    // wait on the compositor, which must not hold up guest time. It shows the
    // newest frame; one it could not show in time is replaced. With a shader
    // preset the frame goes through the GPU filter; without one, or when the preset
    // cannot run, the GPU scales it to the window (GlFramePresenter). One of the two
    // holds the window at a time.
    void present_loop() {
        name_thread("kairo-present");
        SurfacePresenter shader;
        GlFramePresenter plain;
        std::vector<uint8_t> frame;
        FrameShape shape{};
        ANativeWindow *window = nullptr;
        unsigned window_generation = ~0u, shader_generation = ~0u;
        {
            std::lock_guard<std::mutex> guard(window_mutex_);
            presenting_ = true;
        }
        for (;;) {
            {
                std::unique_lock<std::mutex> guard(present_mutex_);
                present_ready_.wait(guard, [&] {
                    return present_stop_ || has_pending_ ||
                           window_generation_.load(std::memory_order_relaxed) != window_generation ||
                           shader_generation_.load(std::memory_order_relaxed) != shader_generation;
                });
                if (present_stop_) break;
                if (has_pending_) {
                    frame.swap(pending_);
                    shape = pending_shape_;
                    has_pending_ = false;
                }
            }
            {
                std::lock_guard<std::mutex> guard(window_mutex_);
                if (window_generation_.load(std::memory_order_relaxed) != window_generation) {
                    shader.destroy();
                    plain.destroy();
                    if (window) ANativeWindow_release(window);
                    window = window_;
                    // Keep the window alive after the UI replaces its reference.
                    if (window) ANativeWindow_acquire(window);
                    window_generation = window_generation_.load(std::memory_order_relaxed);
                    // Nothing draws to the windows before this one any more (set_window).
                    window_taken_generation_ = window_generation;
                    window_taken_.notify_all();
                }
                if (shader_generation_.load(std::memory_order_relaxed) != shader_generation) {
                    // The window goes to whichever presenter draws the next frame.
                    plain.destroy();
                    shader.setPreset(shader_preset_);
                    shader_generation = shader_generation_.load(std::memory_order_relaxed);
                }
            }
            if (!window || frame.empty()) continue;
            if (shader.render(window, frame.data(), shape)) continue;
            plain.render(window, frame.data(), shape);
        }
        shader.destroy();
        plain.destroy();
        if (window) ANativeWindow_release(window);
        {
            std::lock_guard<std::mutex> guard(window_mutex_);
            presenting_ = false;
        }
        window_taken_.notify_all();
    }

    std::mutex lifecycle_mutex_;
    std::thread worker_;

    std::mutex command_mutex_;
    std::condition_variable command_ready_;
    std::deque<Command> commands_;
    bool active_ = false;
    std::string state_ = "Stopped";
    std::string error_;
    // Sessions started and ended, and how the last one ended, for await_end.
    uint64_t started_ = 0;
    uint64_t ended_ = 0;
    std::string end_state_ = "Stopped";
    std::string end_error_;
    std::condition_variable ended_ready_;
    uint64_t frames_ = 0;
    // Whether a frame that is not all black has been published since the start;
    // pictured_seen_ is the worker's own copy, so later frames are not scanned.
    bool pictured_ = false;
    bool pictured_seen_ = false;
    int32_t xruns_ = 0;
    std::string audio_state_ = "off";
    std::string audio_config_;
    // The core's own details (Core::details), as of its last frame.
    std::string details_;

    std::atomic<bool> muted_{false};
    std::atomic<bool> fast_forward_{false};

    std::mutex window_mutex_;
    ANativeWindow *window_ = nullptr;
    std::string shader_preset_;
    std::atomic<unsigned> window_generation_{0};
    // With window_mutex_: whether present_loop runs, and the last window generation it took,
    // having let go of every window before it (set_window waits for it).
    bool presenting_ = false;
    unsigned window_taken_generation_ = 0;
    std::condition_variable window_taken_;
    std::atomic<unsigned> shader_generation_{0};

    std::mutex present_mutex_;
    std::condition_variable present_ready_;
    bool present_stop_ = false;
    std::vector<uint8_t> pending_;
    FrameShape pending_shape_{};
    bool has_pending_ = false;
};

} // namespace kairo
