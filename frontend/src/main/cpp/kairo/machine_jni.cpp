// The JNI surface of com.mrjackspade.kairo.frontend.NativeMachine: the product's MachineCore
// (machine_core.h) run by the shared frame host. Every frame-driven app links this file into
// its own library; the product supplies kairo::create_core().

#include <jni.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <unistd.h>

#include <cstdio>
#include <fstream>
#include <map>
#include <string>
#include <thread>
#include <vector>

#include "kairo/frame_machine.h"
#include "kairo/jni_strings.h"
#include "kairo/machine_core.h"
#include "kairo/state_paths.h"

namespace {

kairo::FrameMachine<kairo::MachineCore> machine;

// The launch setting with a game's startup commands, newline-separated (StartupCommands.SETTING).
constexpr const char *kCommands = "commands";

// A core whose launch has startup commands: the typer types them after each frame, and the machine
// runs flat out while a line goes in (startup_typer.h). Every MachineCore member goes to the core.
class TypingCore : public kairo::MachineCore {
public:
    TypingCore(std::unique_ptr<kairo::MachineCore> core, const std::string &commands) : core_(std::move(core)) {
        typer_.start(commands);
    }

    bool run_frame() override {
        if (!core_->run_frame()) return false;
        if (!typer_.frame(*core_, error_)) return false;
        return true;
    }
    std::string error() override { return error_.empty() ? core_->error() : error_; }
    const uint8_t *frame(uint32_t &width, uint32_t &height) override { return core_->frame(width, height); }
    uint32_t line_repeat() override { return core_->line_repeat(); }
    bool frame_bgra() override { return core_->frame_bgra(); }
    uint32_t frame_stride() override { return core_->frame_stride(); }
    double frame_seconds() override { return core_->frame_seconds(); }
    int32_t audio_rate() override { return core_->audio_rate(); }
    uint32_t read_audio(int16_t *stereo, uint32_t max_frames) override { return core_->read_audio(stereo, max_frames); }
    void release_inputs() override { core_->release_inputs(); }
    void key(int code, bool down) override { core_->key(code, down); }
    void joystick(int control, bool down) override { core_->joystick(control, down); }
    void mouse_move(int dx, int dy) override { core_->mouse_move(dx, dy); }
    void mouse_button(int button, bool down) override { core_->mouse_button(button, down); }
    int locks() override { return core_->locks(); }
    // While a line goes in, so typing does not take the guest's real time.
    bool booting() override { return core_->booting() || typer_.typing(); }
    std::string details() override { return core_->details(); }
    void reset() override { core_->reset(); }
    int save_state(const std::string &directory) override { return core_->save_state(directory); }
    int load_state(const std::string &directory) override { return core_->load_state(directory); }
    std::string state_error() override { return core_->state_error(); }
    bool media_in_state() override { return core_->media_in_state(); }
    int media(int slot, const std::string &path) override { return core_->media(slot, path); }
    std::vector<std::string> media_paths() override { return core_->media_paths(); }
    kairo::Prompt prompt() override { return core_->prompt(); }
    bool typed_key(char c, int &code, bool &shift) override { return core_->typed_key(c, code, shift); }
    int special_key(kairo::SpecialKey key) override { return core_->special_key(key); }
    bool expand_command(std::string &line) override { return core_->expand_command(line); }

private:
    std::unique_ptr<kairo::MachineCore> core_;
    kairo::StartupTyper typer_;
    std::string error_;
};

// The product's core for a launch, typing the game's startup commands when it has any.
std::unique_ptr<kairo::MachineCore> launch_core(const kairo::Launch &launch, std::string &error) {
    auto core = kairo::create_core(launch, error);
    const auto commands = launch.settings.find(kCommands);
    if (!core || commands == launch.settings.end() || commands->second.empty()) return core;
    return std::make_unique<TypingCore>(std::move(core), commands->second);
}

using kairo::utf8;

// Cores report problems on stdout and stderr, which Android discards.
void forward_output_to_log() {
    static int pipe_fds[2];
    if (pipe(pipe_fds) != 0) return;
    dup2(pipe_fds[1], STDOUT_FILENO);
    dup2(pipe_fds[1], STDERR_FILENO);
    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);
    std::thread([] {
        char buffer[512];
        std::string line;
        ssize_t count;
        while ((count = read(pipe_fds[0], buffer, sizeof(buffer))) > 0) {
            for (ssize_t i = 0; i < count; ++i) {
                if (buffer[i] == '\n') {
                    __android_log_write(ANDROID_LOG_WARN, "KairoCore", line.c_str());
                    line.clear();
                } else {
                    line += buffer[i];
                }
            }
        }
    }).detach();
}

} // namespace

#define NATIVE_MACHINE(name) Java_com_mrjackspade_kairo_frontend_NativeMachine_##name

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *, void *) {
    forward_output_to_log();
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
NATIVE_MACHINE(nativeStart)(JNIEnv *env, jobject, jstring firmware, jstring home, jobjectArray media,
                            jobjectArray settings) {
    kairo::Launch launch{utf8(env, firmware), utf8(env, home), kairo::utf8_strings(env, media), {}};
    const std::vector<std::string> pairs = kairo::utf8_strings(env, settings);
    for (size_t i = 0; i + 1 < pairs.size(); i += 2) launch.settings[pairs[i]] = pairs[i + 1];
    return machine.start([launch](std::string &error) { return launch_core(launch, error); }) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL NATIVE_MACHINE(nativeStop)(JNIEnv *, jobject) { machine.stop(); }

extern "C" JNIEXPORT jstring JNICALL NATIVE_MACHINE(nativeAwaitEnd)(JNIEnv *env, jobject) {
    return env->NewStringUTF(machine.await_end().c_str());
}

extern "C" JNIEXPORT void JNICALL NATIVE_MACHINE(nativePause)(JNIEnv *, jobject, jboolean paused) {
    machine.pause(paused == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL NATIVE_MACHINE(nativeReset)(JNIEnv *, jobject) {
    machine.post([](kairo::MachineCore &core) {
        core.release_inputs();
        core.reset();
    });
}

extern "C" JNIEXPORT void JNICALL NATIVE_MACHINE(nativeKey)(JNIEnv *, jobject, jint code, jboolean down) {
    machine.post([code, down](kairo::MachineCore &core) { core.key(code, down == JNI_TRUE); });
}

extern "C" JNIEXPORT void JNICALL NATIVE_MACHINE(nativeJoystick)(JNIEnv *, jobject, jint control, jboolean down) {
    machine.post([control, down](kairo::MachineCore &core) { core.joystick(control, down == JNI_TRUE); });
}

extern "C" JNIEXPORT void JNICALL NATIVE_MACHINE(nativeMouseMove)(JNIEnv *, jobject, jint dx, jint dy) {
    machine.post([dx, dy](kairo::MachineCore &core) { core.mouse_move(dx, dy); });
}

extern "C" JNIEXPORT void JNICALL NATIVE_MACHINE(nativeMouseButton)(JNIEnv *, jobject, jint button, jboolean down) {
    if (button != 1 && button != 2) return;
    machine.post([button, down](kairo::MachineCore &core) { core.mouse_button(button, down == JNI_TRUE); });
}

extern "C" JNIEXPORT jint JNICALL NATIVE_MACHINE(nativeMedia)(JNIEnv *env, jobject, jint slot, jstring path) {
    std::string image = utf8(env, path);
    return machine.call([slot, image](kairo::MachineCore &core) { return core.media(slot, image); });
}

extern "C" JNIEXPORT jobjectArray JNICALL NATIVE_MACHINE(nativeMediaPaths)(JNIEnv *env, jobject) {
    std::vector<std::string> paths;
    const int result = machine.call([&paths](kairo::MachineCore &core) {
        paths = core.media_paths();
        return 0;
    });
    if (result != 0) return nullptr;
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(paths.size()), env->FindClass("java/lang/String"),
                                             nullptr);
    for (size_t slot = 0; slot < paths.size(); ++slot)
        env->SetObjectArrayElement(array, static_cast<jsize>(slot), env->NewStringUTF(paths[slot].c_str()));
    return array;
}

// For a core whose state holds no media (MachineCore::media_in_state): each slot's image when the
// state was saved, one line per slot ("" for an empty one), beside the core's own state file.
constexpr const char *kStateMedia = "/media-v1.txt";

extern "C" JNIEXPORT jint JNICALL NATIVE_MACHINE(nativeSaveState)(JNIEnv *env, jobject, jstring directory) {
    std::string path = utf8(env, directory);
    return machine.call([path](kairo::MachineCore &core) {
        const int result = core.save_state(path);
        if (result != 0 || core.media_in_state()) return result;
        std::ofstream media(path + kStateMedia, std::ios::binary);
        for (const std::string &image : core.media_paths()) media << image << '\n';
        return media.good() ? 0 : kairo::MachineCore::kFailed;
    });
}

// After a core's load: the state's media back in their slots, where they are now (kairo_state_path),
// for a core whose state holds none. A state saved before this record existed has none to restore.
static int restore_state_media(kairo::MachineCore &core, const std::string &path, const kairo::StatePaths &paths) {
    std::ifstream media(path + kStateMedia, std::ios::binary);
    if (!media) return 0;
    const std::vector<std::string> now = core.media_paths();
    std::string recorded;
    for (size_t slot = 0; std::getline(media, recorded); ++slot) {
        if (slot >= now.size()) return kairo::MachineCore::kFailed;
        const std::string image = recorded.empty() ? "" : paths.resolve(recorded.c_str());
        if (image == now[slot]) continue;
        const int result = core.media(static_cast<int>(slot), image);
        if (result != 0) return result;
    }
    return 0;
}

// [from] and [to]: each disk path a state may name, and where that disk is now (kairo/state_paths.h).
extern "C" JNIEXPORT jint JNICALL NATIVE_MACHINE(nativeLoadState)(JNIEnv *env, jobject, jstring directory,
                                                                  jobjectArray from, jobjectArray to) {
    std::string path = utf8(env, directory);
    std::map<std::string, std::string> moved;
    const auto earlier = kairo::utf8_strings(env, from), now = kairo::utf8_strings(env, to);
    for (size_t i = 0; i < earlier.size(); ++i) moved[earlier[i]] = now.at(i);
    return machine.call([path, moved](kairo::MachineCore &core) {
        core.release_inputs();
        kairo::StatePaths paths(moved);
        int result = core.load_state(path);
        if (result == 0 && !core.media_in_state()) result = restore_state_media(core, path, paths);
        if (result == 0) machine.redraw_paused(core);
        return result;
    });
}

// Why the last save or load failed, in the emulator's words; "" when it doesn't say or has stopped.
extern "C" JNIEXPORT jstring JNICALL NATIVE_MACHINE(nativeStateError)(JNIEnv *env, jobject) {
    std::string reason;
    machine.call([&reason](kairo::MachineCore &core) {
        reason = core.state_error();
        return 0;
    });
    return env->NewStringUTF(reason.c_str());
}

extern "C" JNIEXPORT jint JNICALL NATIVE_MACHINE(nativeLocks)(JNIEnv *, jobject) {
    return machine.call([](kairo::MachineCore &core) { return core.locks(); });
}

extern "C" JNIEXPORT jstring JNICALL NATIVE_MACHINE(nativeStatus)(JNIEnv *env, jobject) {
    return env->NewStringUTF(machine.status().c_str());
}

extern "C" JNIEXPORT void JNICALL NATIVE_MACHINE(nativeSetSurface)(JNIEnv *env, jobject, jobject surface) {
    machine.set_window(surface ? ANativeWindow_fromSurface(env, surface) : nullptr);
}

extern "C" JNIEXPORT void JNICALL NATIVE_MACHINE(nativeSetShaderPreset)(JNIEnv *env, jobject, jstring path) {
    machine.set_shader_preset(utf8(env, path));
}

extern "C" JNIEXPORT void JNICALL NATIVE_MACHINE(nativeSetMuted)(JNIEnv *, jobject, jboolean muted) {
    machine.set_muted(muted == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL NATIVE_MACHINE(nativeSetFastForward)(JNIEnv *, jobject, jboolean enabled) {
    machine.set_fast_forward(enabled == JNI_TRUE);
}
