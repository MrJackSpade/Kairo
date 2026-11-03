#pragma once

// A save state with its media, as NativeMachine saves and loads one (machine_jni.cpp) and the shared
// PC boot test does (host-test): a core whose state holds no media (MachineCore::media_in_state)
// gets each slot's image when the state was saved recorded beside its own state file, and the state's
// media back in their slots after a load, where they are now (kairo_state_path).

#include <fstream>
#include <string>
#include <vector>

#include "kairo/machine_core.h"
#include "kairo/state_paths.h"

namespace kairo {

// One line per slot ("" for an empty one), beside the core's own state file.
constexpr const char *kStateMedia = "/media-v1.txt";

// Saves [core]'s state to the folder [path], with its media when the state does not hold them.
inline int save_state_with_media(MachineCore &core, const std::string &path) {
    const int result = core.save_state(path);
    if (result != 0 || core.media_in_state()) return result;
    std::ofstream media(path + kStateMedia, std::ios::binary);
    for (const std::string &image : core.media_paths()) media << image << '\n';
    return media.good() ? 0 : MachineCore::kFailed;
}

// After a core's load: the state's media back in their slots, where they are now ([paths]), for a
// core whose state holds none. A state saved before this record existed has none to restore.
inline int restore_state_media(MachineCore &core, const std::string &path, const StatePaths &paths) {
    std::ifstream media(path + kStateMedia, std::ios::binary);
    if (!media) return 0;
    const std::vector<std::string> now = core.media_paths();
    std::string recorded;
    for (size_t slot = 0; std::getline(media, recorded); ++slot) {
        if (slot >= now.size()) return MachineCore::kFailed;
        const std::string image = recorded.empty() ? "" : paths.resolve(recorded.c_str());
        if (image == now[slot]) continue;
        const int result = core.media(static_cast<int>(slot), image);
        if (result != 0) return result;
    }
    return 0;
}

// Loads [core]'s state from the folder [path], with its media for a core whose state holds none;
// [paths] is where each disk the state names is now, alive while the load runs.
inline int load_state_with_media(MachineCore &core, const std::string &path, const StatePaths &paths) {
    const int result = core.load_state(path);
    if (result != 0 || core.media_in_state()) return result;
    return restore_state_media(core, path, paths);
}

} // namespace kairo
