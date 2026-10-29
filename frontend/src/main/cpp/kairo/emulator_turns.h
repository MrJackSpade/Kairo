// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <condition_variable>
#include <functional>
#include <mutex>
#include <thread>

namespace kairo {

// An emulator that runs its own loop on its own thread (KairoST's Hatari, KairoDos's DOSBox
// Staging), taking turns with the host's emulation thread so a MachineCore can run it a frame at a
// time: exactly one of the two runs at a time. The host lets the emulator run until its next frame
// ends, and the emulator waits there until the host lets it run again. The mutex hand-off orders
// every access to the emulator's state, so the host may call into it during its own turn.
class EmulatorTurns {
public:
    EmulatorTurns() = default;
    EmulatorTurns(const EmulatorTurns &) = delete;
    EmulatorTurns &operator=(const EmulatorTurns &) = delete;
    // The owner ends the emulator (finish()) before it goes.
    ~EmulatorTurns() {
        if (thread_.joinable()) std::terminate();
    }

    // Host: starts run on the emulator's thread and waits for the end of its first frame; false
    // when run returned first.
    bool start(std::function<void()> run) {
        thread_ = std::thread([this, run = std::move(run)] {
            run();
            std::lock_guard<std::mutex> lock(mutex_);
            ended_ = true;
            changed_.notify_all();
        });
        std::unique_lock<std::mutex> lock(mutex_);
        changed_.wait(lock, [&] { return turn_ == Turn::kHost || ended_; });
        return !ended_;
    }

    // Host: lets the emulator run until its next frame ends; false when it ended instead.
    bool run_frame() {
        std::unique_lock<std::mutex> lock(mutex_);
        if (ended_) return false;
        turn_ = Turn::kEmulator;
        changed_.notify_all();
        changed_.wait(lock, [&] { return turn_ == Turn::kHost || ended_; });
        return !ended_;
    }

    // Emulator's thread, at the end of each frame: hands the turn to the host and waits for it back.
    void frame_end() {
        std::unique_lock<std::mutex> lock(mutex_);
        turn_ = Turn::kHost;
        changed_.notify_all();
        changed_.wait(lock, [&] { return turn_ == Turn::kEmulator; });
    }

    // Host: once the emulator was asked to end, lets it run to its end, and joins its thread.
    void finish() {
        if (!thread_.joinable()) return;
        while (run_frame()) {}
        thread_.join();
    }

    bool ended() {
        std::lock_guard<std::mutex> lock(mutex_);
        return ended_;
    }

private:
    enum class Turn { kEmulator, kHost };

    std::mutex mutex_;
    std::condition_variable changed_;
    Turn turn_ = Turn::kEmulator;
    bool ended_ = false;
    std::thread thread_;
};

} // namespace kairo
