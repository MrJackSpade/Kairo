// The shared PC boot test's harness (Kairo#115): runs an app's MachineCore on the PC the way
// NativeMachine runs it on the device (frame_machine.h), from the app's own table
// (core/test/boot-tests.json). The checks (checks.cpp) are written once against MachineCore.

#pragma once

#include <cstdint>
#include <filesystem>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#include "json.h"
#include "kairo/machine_core.h"

namespace kairo::host {

namespace fs = std::filesystem;

// An expectation that was not met: the test fails with this message (and the last picture).
struct Failure : std::runtime_error {
    using std::runtime_error::runtime_error;
};

// What every run shares: where the table's files are, and where the run writes.
struct Paths {
    // The app's unpacked UI test resources (uitest/TestResources.part1.dat): its games and firmware.
    fs::path resources;
    // This run's folder; each test writes under its own name in it.
    fs::path work;
};

// A session of the table's machine: one create_core, run frame by frame.
class Session {
public:
    // Starts [launch] as NativeMachine.start does; a core that cannot start fails the test.
    Session(const Launch &launch, fs::path pictures, std::string name);
    ~Session();
    Session(const Session &) = delete;
    Session &operator=(const Session &) = delete;

    MachineCore &core() { return *core_; }
    // Frames run in this session.
    uint64_t frames() const { return frames_; }
    // The screen signature of each frame run since [mark()], in order.
    const std::vector<uint64_t> &signatures() const { return signatures_; }
    void mark() { signatures_.clear(); }

    // Runs [count] frames; a frame that stops the machine fails the test.
    void run(uint64_t count);
    // Runs until [done] holds, checked after each frame, at most [limit] frames (the table's bound
    // for that wait); not reaching it fails with [what].
    template <class Done> void run_until(const std::string &what, uint64_t limit, Done done) {
        for (uint64_t i = 0; i < limit; ++i) {
            run(1);
            if (done()) return;
        }
        fail("still waiting after " + std::to_string(limit) + " frames for " + what);
    }
    // Holds [code] down for [frames] frames, then lets it go for as many.
    void press(int code, uint64_t frames = 4);

    // Saves the machine's state to the folder [path], or loads it, as NativeMachine does: with its
    // media for a core whose state holds none (kairo/state_media.h). A refusal fails the test.
    void save_state(const fs::path &path, const std::string &what = "save_state");
    void load_state(const fs::path &path);

    // Writes the last frame to <pictures>/<name>.ppm (nothing for a core whose GPU composes its
    // picture), and returns the file.
    fs::path picture(const std::string &name);
    // Fails the test with [why], keeping the last picture.
    [[noreturn]] void fail(const std::string &why);

    // Ends the session as the app does (MachineCore::close); its failure fails the test.
    void close();

private:
    std::unique_ptr<MachineCore> core_;
    fs::path pictures_;
    std::string name_;
    uint64_t frames_ = 0;
    std::vector<uint64_t> signatures_;
    std::vector<int16_t> audio_;
    bool closed_ = false;
};

// One entry of the table: its name, how it launches, and its checks.
struct Test {
    std::string name;
    const json::Value *entry = nullptr;
    Paths paths;
    // This test's own folder under the run's: working copies of its media, states, pictures.
    fs::path folder;

    // The launch the table gives, with its media copied into [folder] (so the guest's writes stay
    // there, as the app's working copies do) and [settings] over the table's settings.
    Launch launch(const json::Value *overrides = nullptr) const;
    // The table's media file [name] (relative to the resources), copied into [folder] once.
    fs::path working_copy(const std::string &name) const;
    // A launch value the table names as a frame count, such as "bootFrames".
    uint64_t frames(const json::Value &spec, const std::string &key) const;
};

// A check, by its name in the table's "checks": runs against [test] with its [spec].
using Check = void (*)(Test &test, const json::Value &spec);
Check find_check(const std::string &name);
std::vector<std::string> check_names();

} // namespace kairo::host
