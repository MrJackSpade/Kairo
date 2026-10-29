// A machine a product boots from a launch, run frame by frame by the shared frame host
// (frame_machine.h) behind the JNI surface of com.mrjackspade.kairo.frontend.NativeMachine
// (machine_jni.cpp). A product implements MachineCore and kairo::create_core().

#pragma once

#include <cstdint>
#include <map>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#include "gpu_picture.h"
#include "startup_typer.h"

namespace kairo {

// What a game boots with, from NativeMachine.start.
struct Launch {
    // The folder of the firmware files the player imported.
    std::string firmware;
    // A private folder the core may keep its own files in.
    std::string home;
    // Media by slot, as the product numbers them; "" for an empty slot.
    std::vector<std::string> media;
    // The machine's settings, by the names the product gives them.
    std::map<std::string, std::string> settings;

    const std::string &setting(const std::string &name, const std::string &fallback) const {
        auto found = settings.find(name);
        return found == settings.end() ? fallback : found->second;
    }
};

class MachineCore {
public:
    // A save, load or media call the core does not support (MachineActivity.stateError).
    static constexpr int kUnsupported = -2;
    // A save, load or media call that failed; for a state, state_error() may say why.
    static constexpr int kFailed = 2;
    // A load that failed after it had changed the running machine, which cannot go on from there:
    // the frontend starts the game again (MachineBridge.LOST). state_error() may say why.
    static constexpr int kLost = 3;

    virtual ~MachineCore() = default;

    // Runs one guest frame; false stops the machine with error().
    virtual bool run_frame() = 0;
    virtual std::string error() = 0;
    // The last frame as RGBA8888 rows of width pixels; valid until the next run_frame(). A core with
    // a gpu_picture() returns null.
    virtual const uint8_t *frame(uint32_t &width, uint32_t &height) = 0;
    // The core's picture when the GPU composes it (gpu_picture.h), instead of frame(); the same
    // object for the core's whole life. Null for a core that draws its frames in software.
    virtual GpuPicture *gpu_picture() { return nullptr; }
    // How many picture rows each row of frame() stands for: 2 for a 200-line mode the core hands
    // over without doubling its lines, which the presenter then repeats exactly as the core would
    // have (Kairo#50). Read with each frame, so it may change with the guest's mode.
    virtual uint32_t line_repeat() { return 1; }
    // Whether frame()'s bytes are in B, G, R, A order (a core's native 0xAARRGGBB words), which the
    // presenter reorders on the GPU instead of the core converting every pixel. Its alpha byte is
    // ignored: the picture is opaque.
    virtual bool frame_bgra() { return false; }
    // Bytes from one row of frame() to the next, for a core whose bitmap is wider than its
    // picture (the picture starting inside a row); 0 for rows of exactly width * 4 bytes.
    virtual uint32_t frame_stride() { return 0; }
    // How long a guest frame lasts.
    virtual double frame_seconds() = 0;
    virtual int32_t audio_rate() = 0;
    // Takes up to max_frames interleaved stereo frames the guest produced.
    virtual uint32_t read_audio(int16_t *stereo, uint32_t max_frames) = 0;

    // Releases every key, button and pad control the frontend holds.
    virtual void release_inputs() = 0;
    virtual void key(int code, bool down) = 0;
    // A control in JoystickInputRouter's order.
    virtual void joystick(int control, bool down) = 0;
    virtual void mouse_move(int dx, int dy) = 0;
    // Button 1 (left) or 2 (right).
    virtual void mouse_button(int button, bool down) = 0;
    // Direct touch: moves the guest's pointer to frame point (x, y) over the next frames. Only a
    // product whose catalog sets directTouch asks; any other core has none.
    virtual void mouse_warp(int x, int y) {
        (void) x;
        (void) y;
        throw std::logic_error("This core has no direct touch");
    }
    // Whether the guest's pointer has reached the last warp's point, so a click lands there.
    virtual bool mouse_warp_done() { throw std::logic_error("This core has no direct touch"); }
    // The lock keys the guest has on, one bit each in the order the product's NativeMachine lists them.
    virtual int locks() { return 0; }

    // How the guest reads its input, for Auto touch mode (InputModeDecider.GuestInput): counts since
    // the machine started of the guest waiting for a key and reading the keyboard, and of it reading
    // the mouse; whether it waits for a key now; and whether it set its mouse up, for a core that
    // cannot see its mouse reads. False for a core that does not count them.
    struct GuestInput {
        uint64_t keyboard_waits = 0;
        uint64_t keyboard_polls = 0;
        uint64_t mouse_reads = 0;
        bool keyboard_waiting = false;
        bool mouse_enabled = false;
    };
    virtual bool guest_input(GuestInput &input) { (void) input; return false; }
    // True while the guest boots or loads from its media (a drive or tape motor running, a CD
    // read), which then runs as fast as the host can, as in fast-forward (Kairo#82).
    virtual bool loading() { return false; }
    // What the core reports of itself for tests and diagnostics, as name=value pairs separated by
    // ';', such as "tape=1200/25650"; names and values hold neither ';', '=' nor '|'. Read after
    // every frame on the emulation thread, so keep it cheap.
    virtual std::string details() { return ""; }

    virtual void reset() = 0;
    // Ends the machine as its session ends, before it is destroyed, writing back what it holds (a
    // hard disk's cache); why that failed, which ends the session in error, or "".
    virtual std::string close() { return ""; }
    // 0, kFailed or kUnsupported.
    virtual int save_state(const std::string &directory) { (void) directory; return kUnsupported; }
    virtual int load_state(const std::string &directory) { (void) directory; return kUnsupported; }
    // Why the last save_state or load_state failed, in the emulator's words; "" when it doesn't say.
    virtual std::string state_error() { return ""; }
    // Whether a state the core saves records its media and a load puts them back (reopened through
    // kairo_state_path). A core whose state holds no media returns false: the host then records
    // media_paths() beside the state and inserts them again after the load.
    virtual bool media_in_state() { return true; }

    // Inserts the image at path in slot, or ejects it for ""; 0 when it is in, else kFailed or kUnsupported.
    virtual int media(int slot, const std::string &path) { (void) slot; (void) path; return kUnsupported; }
    // What each slot holds, "" for an empty one.
    virtual std::vector<std::string> media_paths() { return {}; }

    // Startup commands (startup_typer.h), for a core whose games start from a prompt: what the
    // guest waits for now, the key that types a character (and whether Shift goes with it), a
    // special key's code (-1 for none), and a line changed before it is typed (false when it
    // cannot be). A core without a prompt keeps these, and its games' commands are never typed.
    virtual Prompt prompt() { return Prompt::None; }
    virtual bool typed_key(char c, int &code, bool &shift) { (void) c; (void) code; (void) shift; return false; }
    virtual int special_key(SpecialKey key) { (void) key; return -1; }
    virtual bool expand_command(std::string &line) { (void) line; return true; }
    // The screen the guest shows, as a signature of its video memory, for startup steps and disk
    // swaps that wait for a screen (StartupStep::screens). Only a product whose catalog names screens asks.
    virtual uint64_t screen_signature() { throw std::logic_error("This core has no screen signatures"); }
};

// The product's machine for a launch; null with error set when it cannot start.
std::unique_ptr<MachineCore> create_core(const Launch &launch, std::string &error);

} // namespace kairo
