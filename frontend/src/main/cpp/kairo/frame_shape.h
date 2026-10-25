#pragma once

#include <cstdint>

namespace kairo {

// A published frame's layout (MachineCore::frame, line_repeat, frame_bgra): width and height in
// pixels as handed over, how many picture rows each row stands for, and its byte order.
struct FrameShape {
    uint32_t width = 0;
    uint32_t height = 0;
    uint32_t repeat = 1;
    bool bgra = false;
};

} // namespace kairo
