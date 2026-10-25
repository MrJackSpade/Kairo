// A machine a product boots from a launch, run frame by frame by the shared frame host
// (frame_machine.h) behind the JNI surface of com.mrjackspade.kairo.frontend.NativeMachine
// (machine_jni.cpp). A product implements MachineCore and kairo::create_core().

#pragma once

#include <cstdint>
#include <map>
#include <memory>
#include <string>
#include <vector>

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
    // A state call the core does not support; NativeMachine reports it as a failure.
    static constexpr int kUnsupported = -2;

    virtual ~MachineCore() = default;

    // Runs one guest frame; false stops the machine with error().
    virtual bool run_frame() = 0;
    virtual std::string error() = 0;
    // The last frame as RGBA8888 rows of width pixels; valid until the next run_frame().
    virtual const uint8_t *frame(uint32_t &width, uint32_t &height) = 0;
    // How many picture rows each row of frame() stands for: 2 for a 200-line mode the core hands
    // over without doubling its lines, which the presenter then repeats exactly as the core would
    // have (Kairo#50). Read with each frame, so it may change with the guest's mode.
    virtual uint32_t line_repeat() { return 1; }
    // Whether frame()'s bytes are in B, G, R, A order (a core's native 0xAARRGGBB words), which the
    // presenter reorders on the GPU instead of the core converting every pixel.
    virtual bool frame_bgra() { return false; }
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
    // The lock keys the guest has on, one bit each in the order the product's NativeMachine lists them.
    virtual int locks() { return 0; }
    // True while the guest boots, which then runs as fast as the host can, as in fast-forward.
    virtual bool booting() { return false; }
    // What the core reports of itself for tests and diagnostics, as name=value pairs separated by
    // ';', such as "tape=1200/25650"; names and values hold neither ';', '=' nor '|'. Read after
    // every frame on the emulation thread, so keep it cheap.
    virtual std::string details() { return ""; }

    virtual void reset() = 0;
    // 0, or a code the product's stateError explains.
    virtual int save_state(const std::string &directory) { (void) directory; return kUnsupported; }
    virtual int load_state(const std::string &directory) { (void) directory; return kUnsupported; }

    // Inserts the image at path in slot, or ejects it for ""; 0 when it is in.
    virtual int media(int slot, const std::string &path) { (void) slot; (void) path; return kUnsupported; }
    // What each slot holds, "" for an empty one.
    virtual std::vector<std::string> media_paths() { return {}; }
};

// The product's machine for a launch; null with error set when it cannot start.
std::unique_ptr<MachineCore> create_core(const Launch &launch, std::string &error);

} // namespace kairo
