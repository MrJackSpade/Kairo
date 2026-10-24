// The priority emulation work runs at. FrameMachine's worker takes it, and so must any thread a
// core runs its own emulation on (Kairo68 runs MAME on a thread of its own, Kairo68#26): Linux
// priority belongs to a thread, not to the threads it starts later.
#pragma once

#include <android/log.h>
#include <pthread.h>
#include <sys/resource.h>

namespace kairo {

// Emulation competes with the UI and compositor for a few small cores; give the calling thread
// the same standing Android gives urgent display work. which names the thread in the log.
inline void raise_emulation_priority(const char *which) {
    if (setpriority(PRIO_PROCESS, 0, -16) != 0) {
        __android_log_print(ANDROID_LOG_INFO, "KairoMachine", "%s priority unchanged", which);
    }
}

// Names the calling thread (at most 15 characters), so per-thread CPU in /proc and profiles tells
// the emulation, frame, presenter and worker threads apart.
inline void name_thread(const char *name) { pthread_setname_np(pthread_self(), name); }

} // namespace kairo
