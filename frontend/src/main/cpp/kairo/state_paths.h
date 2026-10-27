#pragma once

/*
 * Where each disk a save state names is while that state loads. Several emulators record their
 * disks by path in a state and reopen them by those paths deep in their own load (np2's
 * statsave.c, quasi88's stateload, Tsugaru's devices, openMSX's DiskName, Hatari's floppies).
 * A game's working copy can have another name since the state was saved (Kairo#70), so the
 * frontend works out every path its game's disks had and where each is now
 * (MachineBridge.loadState), and each emulator's reopen asks kairo_state_path: a path the
 * frontend moved comes back as where it is now, any other path as it is.
 */
#ifdef __cplusplus
extern "C" {
#endif

const char *kairo_state_path(const char *recorded);

#ifdef __cplusplus
}

#include <map>
#include <string>

namespace kairo {

// The frontend's paths for the state that loads while this lives; one at a time, on the
// emulation thread.
class StatePaths {
public:
    explicit StatePaths(std::map<std::string, std::string> moved);
    ~StatePaths();
    StatePaths(const StatePaths &) = delete;
    StatePaths &operator=(const StatePaths &) = delete;

    const char *resolve(const char *recorded) const;

private:
    std::map<std::string, std::string> moved_;
};

} // namespace kairo
#endif
