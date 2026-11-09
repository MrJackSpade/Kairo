// A core of its own for emulation (KairoX86#407). Every frame the presenter hands a buffer to the
// system, and SurfaceFlinger, its timer and binder threads and the composer then run: real-time
// threads, which preempt whatever ordinary thread is on the CPU they last ran on, again and again the
// emulation thread. Keeping every other thread of the app off one CPU leaves that CPU to emulation:
// on the RG DS emulation then took 3.7% less time a frame (DOOM's demo, 24 interleaved pairs).
// The emulation threads (raise_emulation_priority) stay free; the rest of the process is kept to the
// other CPUs. Android resets a thread's CPUs when it moves the app between cpusets, and a core starts
// threads of its own whenever it likes, so the frame worker applies this again every so many frames.
#pragma once

#if defined(_WIN32)
namespace kairo {
// The shared PC boot test (Kairo#115), built for Windows: CPU placement is the device's concern.
inline void add_emulation_thread() {}
inline void keep_emulation_core() {}
} // namespace kairo
#else

#include <android/log.h>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <mutex>
#include <sched.h>
#include <set>
#include <unistd.h>

namespace kairo {

namespace emulation_core_detail {

inline std::mutex &mutex() {
    static std::mutex m;
    return m;
}

// The threads that run emulation (raise_emulation_priority), left free.
inline std::set<pid_t> &emulation_threads() {
    static std::set<pid_t> threads;
    return threads;
}

// A CPU's capacity as the kernel's scheduler rates it (1024 for the fastest); 0 when the kernel does
// not say, which leaves the CPUs alike.
inline long capacity(int cpu) {
    char path[64];
    std::snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpu_capacity", cpu);
    FILE *file = std::fopen(path, "r");
    if (!file) return 0;
    long value = 0;
    if (std::fscanf(file, "%ld", &value) != 1) value = 0;
    std::fclose(file);
    return value;
}

// The CPU emulation keeps: the one of [allowed] the scheduler rates fastest, the last of equals.
inline int choose(const cpu_set_t &allowed) {
    int chosen = -1;
    long best = -1;
    for (int cpu = 0; cpu < CPU_SETSIZE; ++cpu) {
        if (!CPU_ISSET(cpu, &allowed)) continue;
        const long rating = capacity(cpu);
        if (rating >= best) {
            best = rating;
            chosen = cpu;
        }
    }
    return chosen;
}

} // namespace emulation_core_detail

// Marks the calling thread as one that runs emulation: it may run on any CPU, the emulation core above all.
inline void add_emulation_thread() {
    std::lock_guard<std::mutex> guard(emulation_core_detail::mutex());
    emulation_core_detail::emulation_threads().insert(gettid());
}

// Keeps every thread of the process but the emulation threads off the emulation core. Called by an
// emulation thread, whose own CPUs (never narrowed here) are the ones the app may use now.
inline void keep_emulation_core() {
    using namespace emulation_core_detail;
    cpu_set_t allowed;
    CPU_ZERO(&allowed);
    if (sched_getaffinity(0, sizeof(allowed), &allowed) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, "KairoMachine", "emulation core: CPUs unreadable: %s", std::strerror(errno));
        return;
    }
    if (CPU_COUNT(&allowed) < 2) return;
    const int core = choose(allowed);
    cpu_set_t others = allowed;
    CPU_CLR(core, &others);

    std::lock_guard<std::mutex> guard(mutex());
    std::set<pid_t> &emulation = emulation_threads();
    std::set<pid_t> alive;
    DIR *tasks = opendir("/proc/self/task");
    if (!tasks) {
        __android_log_print(ANDROID_LOG_ERROR, "KairoMachine", "emulation core: threads unreadable: %s", std::strerror(errno));
        return;
    }
    while (const dirent *entry = readdir(tasks)) {
        if (entry->d_name[0] < '0' || entry->d_name[0] > '9') continue;
        const pid_t tid = static_cast<pid_t>(std::atoi(entry->d_name));
        if (emulation.count(tid)) {
            alive.insert(tid);
            continue;
        }
        cpu_set_t current;
        CPU_ZERO(&current);
        // A thread that ended since the listing (ESRCH) has nothing left to place.
        if (sched_getaffinity(tid, sizeof(current), &current) != 0) {
            if (errno != ESRCH)
                __android_log_print(ANDROID_LOG_ERROR, "KairoMachine", "emulation core: thread %d's CPUs unreadable: %s",
                                    tid, std::strerror(errno));
            continue;
        }
        if (CPU_EQUAL(&current, &others)) continue;
        if (sched_setaffinity(tid, sizeof(others), &others) != 0 && errno != ESRCH)
            __android_log_print(ANDROID_LOG_ERROR, "KairoMachine", "emulation core: thread %d kept on its CPUs: %s",
                                tid, std::strerror(errno));
    }
    closedir(tasks);
    emulation.swap(alive);
}

} // namespace kairo

#endif
