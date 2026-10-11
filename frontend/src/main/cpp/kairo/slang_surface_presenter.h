// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include "egl_context.h"
#include "slang_filter.h"
#include <android/native_window.h>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

namespace kairo {

// GPU presentation for a software-produced RGBA8 frame. A product may keep
// its existing software window path as the fallback when a preset cannot run.
class SlangSurfacePresenter {
public:
    ~SlangSurfacePresenter() { destroy(); }

    void setPreset(std::string path) {
        if (path == path_) return;
        destroy();
        path_ = std::move(path);
        preset_failed_ = false;
    }

    bool render(ANativeWindow* window, const uint8_t* rgba, uint32_t width,
                uint32_t height) {
        if (path_.empty() || preset_failed_ || !window || !rgba || !width || !height)
            return false;
        if (!egl_.ready()) {
            if (!egl_.create(3, 0)) return false;
            glGenTextures(1, &texture_);
        }
        if (!egl_.attach(window)) { destroy(); return false; }
        if (!filter_.active()) {
            if (!filter_.load(path_)) {
                preset_failed_ = true;
                destroy();
                return false;
            }
        }
        GLint maxTexture = 0;
        glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxTexture);
        if (width > static_cast<uint32_t>(maxTexture) ||
            height > static_cast<uint32_t>(maxTexture)) return false;
        if (source_width_ != width || source_height_ != height) {
            glBindTexture(GL_TEXTURE_2D, texture_);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                         GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            source_width_ = width;
            source_height_ = height;
        }
        // Software frames are top-down; OpenGL texture rows begin at the bottom.
        flipped_.resize(static_cast<size_t>(width) * height * 4);
        const size_t rowBytes = static_cast<size_t>(width) * 4;
        for (uint32_t row = 0; row < height; ++row)
            std::memcpy(flipped_.data() + row * rowBytes,
                        rgba + (height - 1 - row) * rowBytes, rowBytes);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glBindTexture(GL_TEXTURE_2D, texture_);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                        GL_RGBA, GL_UNSIGNED_BYTE, flipped_.data());
        EGLint screenWidth = 0, screenHeight = 0;
        if (!eglQuerySurface(egl_.display(), egl_.surface(), EGL_WIDTH, &screenWidth) ||
            !eglQuerySurface(egl_.display(), egl_.surface(), EGL_HEIGHT, &screenHeight) ||
            screenWidth <= 0 || screenHeight <= 0) return false;
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, screenWidth, screenHeight);
        if (!filter_.draw(texture_, width, height, screenWidth, screenHeight)) {
            preset_failed_ = true;
            destroy();
            return false;
        }
        if (!eglSwapBuffers(egl_.display(), egl_.surface())) {
            destroy();
            return false;
        }
        return true;
    }

    void destroy() {
        if (egl_.ready() && egl_.makeCurrent()) {
            filter_.reset();
            if (texture_) glDeleteTextures(1, &texture_);
        }
        egl_.destroy();
        texture_ = 0;
        source_width_ = source_height_ = 0;
        flipped_.clear();
    }

private:
    EglContext egl_;
    SlangFilter filter_;
    std::string path_;
    bool preset_failed_ = false;
    GLuint texture_ = 0;
    uint32_t source_width_ = 0, source_height_ = 0;
    std::vector<uint8_t> flipped_;
};
} // namespace kairo
