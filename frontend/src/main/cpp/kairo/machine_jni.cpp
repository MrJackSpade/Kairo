// The JNI surface of com.mrjackspade.kairo.frontend.NativeMachine: the product's MachineCore
// (machine_core.h) run by the shared frame host. Every frame-driven app links this file into
// its own library; the product supplies kairo::create_core().

#include <jni.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <unistd.h>

#include <cstdio>
#include <string>
#include <thread>
#include <vector>

#include "kairo/frame_machine.h"
#include "kairo/machine_core.h"

namespace {

kairo::FrameMachine<kairo::MachineCore> machine;

std::string utf8(JNIEnv *env, jstring value) {
    if (!value) return "";
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::vector<std::string> strings(JNIEnv *env, jobjectArray array) {
    std::vector<std::string> result;
    const jsize count = array ? env->GetArrayLength(array) : 0;
    for (jsize i = 0; i < count; ++i) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        result.push_back(utf8(env, item));
        env->DeleteLocalRef(item);
    }
    return result;
}

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
    kairo::Launch launch{utf8(env, firmware), utf8(env, home), strings(env, media), {}};
    const std::vector<std::string> pairs = strings(env, settings);
    for (size_t i = 0; i + 1 < pairs.size(); i += 2) launch.settings[pairs[i]] = pairs[i + 1];
    return machine.start([launch](std::string &error) { return kairo::create_core(launch, error); }) ? JNI_TRUE
                                                                                                     : JNI_FALSE;
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

extern "C" JNIEXPORT jint JNICALL NATIVE_MACHINE(nativeSaveState)(JNIEnv *env, jobject, jstring directory) {
    std::string path = utf8(env, directory);
    return machine.call([path](kairo::MachineCore &core) { return core.save_state(path); });
}

extern "C" JNIEXPORT jint JNICALL NATIVE_MACHINE(nativeLoadState)(JNIEnv *env, jobject, jstring directory) {
    std::string path = utf8(env, directory);
    return machine.call([path](kairo::MachineCore &core) {
        core.release_inputs();
        const int result = core.load_state(path);
        if (result == 0) machine.redraw_paused(core);
        return result;
    });
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
