#pragma once

// The frame host's GPU presenter: the Slang shader presenter, or, in an app built without the
// shader runtime (KAIRO_NO_SHADERS, an app whose emulator cannot be combined with its license),
// one that never takes a frame, so every frame is drawn in software.

#ifdef KAIRO_NO_SHADERS

#include <android/log.h>
#include <android/native_window.h>
#include <cstdint>
#include <string>
#include "frame_shape.h"

namespace kairo {

class SurfacePresenter {
public:
    void setPreset(const std::string &path) {
        // The app offers no shaders, so the frontend never selects one.
        if (!path.empty())
            __android_log_assert("preset", "KairoShaders", "Shader preset %s in a build without shaders", path.c_str());
    }
    bool render(ANativeWindow *, const uint8_t *, const FrameShape &) { return false; }
    void destroy() {}
};

} // namespace kairo

#else

#include "slang_surface_presenter.h"

namespace kairo {
using SurfacePresenter = SlangSurfacePresenter;
} // namespace kairo

#endif
