// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include "egl_context.h"
#include "frame_shape.h"
#include "gpu_picture.h"
#include "slang_filter.h"
#include <android/log.h>
#include <android/native_window.h>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

namespace kairo {

// GPU presentation for a software-produced RGBA8 frame, or a core's GPU picture (gpu_picture.h),
// through a RetroArch shader preset. A product may keep its existing software window path as the
// fallback when a preset cannot run.
class SlangSurfacePresenter {
public:
    ~SlangSurfacePresenter() { destroy(); }

    void setPreset(std::string path) {
        if (path == path_) return;
        destroy();
        path_ = std::move(path);
        preset_failed_ = false;
    }

    bool render(ANativeWindow* window, const uint8_t* rgba, const FrameShape& shape) {
        // The preset sees the picture as the core would have drawn it: repeated rows and RGBA.
        const uint32_t width = shape.width, height = shape.height * shape.repeat;
        if (!rgba || !width || !height || !begin(window, nullptr) || !size(width, height)) return false;
        // Software frames are top-down; OpenGL texture rows begin at the bottom.
        flipped_.resize(static_cast<size_t>(width) * height * 4);
        const size_t rowBytes = static_cast<size_t>(width) * 4;
        for (uint32_t row = 0; row < height; ++row) {
            uint8_t* target = flipped_.data() + row * rowBytes;
            const uint8_t* source = rgba + ((height - 1 - row) / shape.repeat) * rowBytes;
            if (!shape.bgra) { std::memcpy(target, source, rowBytes); continue; }
            for (size_t i = 0; i < rowBytes; i += 4) {
                target[i] = source[i + 2];
                target[i + 1] = source[i + 1];
                target[i + 2] = source[i];
                target[i + 3] = 0xff;  // The picture is opaque (MachineCore::frame_bgra).
            }
        }
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glBindTexture(GL_TEXTURE_2D, texture_);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                        GL_RGBA, GL_UNSIGNED_BYTE, flipped_.data());
        return filter(width, height);
    }

    // A core's GPU picture, drawn into the preset's source texture in OpenGL's orientation.
    bool render(ANativeWindow* window, GpuPicture& picture) {
        const uint32_t width = picture.width(), height = picture.height();
        if (!begin(window, &picture) || !size(width, height)) return false;
        if (!framebuffer_) {
            glGenFramebuffers(1, &framebuffer_);
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer_);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture_, 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                __android_log_assert("picture", "KairoShaders", "Could not make the picture's framebuffer (GL 0x%x)",
                                     glGetError());
        }
        picture.draw(framebuffer_, false);
        return filter(width, height);
    }

    void destroy() {
        if (egl_.ready() && egl_.makeCurrent()) {
            if (picture_) picture_->context_ending();
            filter_.reset();
            if (framebuffer_) glDeleteFramebuffers(1, &framebuffer_);
            if (texture_) glDeleteTextures(1, &texture_);
        }
        egl_.destroy();
        picture_ = nullptr;
        texture_ = framebuffer_ = 0;
        source_width_ = source_height_ = 0;
        flipped_.clear();
    }

private:
    // The context, the window, the preset, and the picture's GL objects in the context; false when
    // the preset cannot run.
    bool begin(ANativeWindow* window, GpuPicture* picture) {
        if (path_.empty() || preset_failed_ || !window) return false;
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
        if (picture != picture_) {
            if (picture_) picture_->context_ending();
            picture_ = picture;
            if (picture_) picture_->context_created();
        }
        return true;
    }

    // Gives the source texture [width] x [height] pixels; false when the GPU cannot hold it.
    bool size(uint32_t width, uint32_t height) {
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
        return true;
    }

    // Filters the source texture to the window and shows it.
    bool filter(uint32_t width, uint32_t height) {
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

    EglContext egl_;
    // The picture whose GL objects the context holds, or null.
    GpuPicture* picture_ = nullptr;
    GLuint framebuffer_ = 0;
    SlangFilter filter_;
    std::string path_;
    bool preset_failed_ = false;
    GLuint texture_ = 0;
    uint32_t source_width_ = 0, source_height_ = 0;
    std::vector<uint8_t> flipped_;
};
} // namespace kairo
