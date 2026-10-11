// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#define LIBRA_RUNTIME_OPENGL
#include "librashader.h"

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cstdint>
#include <mutex>
#include <string>

namespace kairo {

// The GL context belongs to the caller's presentation thread. Keep the
// filter chain in that context and recreate it after EGL context loss.
class SlangFilter {
public:
    SlangFilter() = default;
    SlangFilter(const SlangFilter&) = delete;
    SlangFilter& operator=(const SlangFilter&) = delete;
    ~SlangFilter() { reset(); }

    bool load(const std::string& presetPath) {
        reset();
        if (presetPath.empty()) return false;
        Api& api = functions();
        if (!api.available) return false;
        libra_shader_preset_t preset = nullptr;
        if (!check(api, api.presetCreate(presetPath.c_str(), &preset), "parse preset"))
            return false;
        filter_chain_gl_opt_t options{};
        options.version = LIBRASHADER_CURRENT_VERSION;
        options.disable_cache = true;
        if (!check(api, api.chainCreate(&preset, glLoader, &options, &chain_),
                   "create filter chain")) return false;
        frame_ = 0;
        return chain_ != nullptr;
    }

    bool active() const { return chain_ != nullptr; }

    bool draw(GLuint texture, uint32_t sourceWidth, uint32_t sourceHeight,
              uint32_t outputWidth, uint32_t outputHeight) {
        if (!chain_) return false;
        if (!prepareOutput(outputWidth, outputHeight)) return false;
        // librashader's GL samplers always request mipmap minification. The
        // external guest frame has one level; cap it at level zero so the
        // texture is complete and coordinate-dependent LOD stays at zero.
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
        if (glGetError() != GL_NO_ERROR) return false;
        Api& api = functions();
        const libra_image_gl_t source{texture, GL_RGBA8, sourceWidth, sourceHeight};
        const libra_image_gl_t output{outputTexture_, GL_RGBA8, outputWidth, outputHeight};
        if (check(api, api.chainFrame(&chain_, frame_++, source, output, nullptr,
                                      nullptr, nullptr), "render filter chain")) {
            glDisable(GL_SCISSOR_TEST);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, outputFramebuffer_);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
            glBlitFramebuffer(0, 0, outputWidth, outputHeight,
                              0, 0, outputWidth, outputHeight, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
            if (glGetError() != GL_NO_ERROR) return false;
            return true;
        }
        reset();
        return false;
    }

    void reset() {
        if (chain_) {
            Api& api = functions();
            if (api.available) check(api, api.chainFree(&chain_), "free filter chain");
            chain_ = nullptr;
        }
        if (outputFramebuffer_) glDeleteFramebuffers(1, &outputFramebuffer_);
        if (outputTexture_) glDeleteTextures(1, &outputTexture_);
        outputFramebuffer_ = outputTexture_ = 0;
        outputWidth_ = outputHeight_ = 0;
        frame_ = 0;
    }

private:
    bool prepareOutput(uint32_t width, uint32_t height) {
        if (!width || !height) return false;
        if (outputFramebuffer_ && outputWidth_ == width && outputHeight_ == height)
            return true;
        if (outputFramebuffer_) glDeleteFramebuffers(1, &outputFramebuffer_);
        if (outputTexture_) glDeleteTextures(1, &outputTexture_);
        outputFramebuffer_ = outputTexture_ = 0;
        glGenTextures(1, &outputTexture_);
        glBindTexture(GL_TEXTURE_2D, outputTexture_);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenFramebuffers(1, &outputFramebuffer_);
        glBindFramebuffer(GL_FRAMEBUFFER, outputFramebuffer_);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                               outputTexture_, 0);
        const bool complete = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        outputWidth_ = width;
        outputHeight_ = height;
        return complete;
    }

    struct Api {
        void* library = nullptr;
        bool available = false;
        PFN_libra_preset_create presetCreate = nullptr;
        PFN_libra_gl_filter_chain_create chainCreate = nullptr;
        PFN_libra_gl_filter_chain_frame chainFrame = nullptr;
        PFN_libra_gl_filter_chain_free chainFree = nullptr;
        PFN_libra_error_write errorWrite = nullptr;
        PFN_libra_error_free_string errorFreeString = nullptr;
        PFN_libra_error_free errorFree = nullptr;
    };

    static Api& functions() {
        static Api api;
        static std::once_flag once;
        std::call_once(once, [&] {
            api.library = dlopen("liblibrashader_capi.so", RTLD_NOW | RTLD_LOCAL);
            if (!api.library) {
                __android_log_print(ANDROID_LOG_WARN, "KairoShader",
                                    "Cannot load librashader: %s", dlerror());
                return;
            }
#define KAIRO_LOAD(member, symbol) \
            api.member = reinterpret_cast<decltype(api.member)>(dlsym(api.library, symbol));
            KAIRO_LOAD(presetCreate, "libra_preset_create")
            KAIRO_LOAD(chainCreate, "libra_gl_filter_chain_create")
            KAIRO_LOAD(chainFrame, "libra_gl_filter_chain_frame")
            KAIRO_LOAD(chainFree, "libra_gl_filter_chain_free")
            KAIRO_LOAD(errorWrite, "libra_error_write")
            KAIRO_LOAD(errorFreeString, "libra_error_free_string")
            KAIRO_LOAD(errorFree, "libra_error_free")
#undef KAIRO_LOAD
            api.available = api.presetCreate && api.chainCreate && api.chainFrame &&
                api.chainFree && api.errorWrite && api.errorFreeString && api.errorFree;
            if (!api.available)
                __android_log_print(ANDROID_LOG_WARN, "KairoShader",
                                    "librashader has an incompatible C API");
        });
        return api;
    }

    static const void* glLoader(const char* name) {
        if (auto address = eglGetProcAddress(name))
            return reinterpret_cast<const void*>(address);
        static void* gles = dlopen("libGLESv3.so", RTLD_NOW | RTLD_LOCAL);
        if (!gles) gles = dlopen("libGLESv2.so", RTLD_NOW | RTLD_LOCAL);
        return gles ? dlsym(gles, name) : nullptr;
    }

    static bool check(Api& api, libra_error_t error, const char* operation) {
        if (!error) return true;
        char* message = nullptr;
        if (api.errorWrite(error, &message) == 0 && message) {
            __android_log_print(ANDROID_LOG_WARN, "KairoShader", "%s: %s", operation,
                                message);
            api.errorFreeString(&message);
        } else {
            __android_log_print(ANDROID_LOG_WARN, "KairoShader", "%s failed", operation);
        }
        api.errorFree(&error);
        return false;
    }

    libra_gl_filter_chain_t chain_ = nullptr;
    size_t frame_ = 0;
    GLuint outputTexture_ = 0;
    GLuint outputFramebuffer_ = 0;
    uint32_t outputWidth_ = 0;
    uint32_t outputHeight_ = 0;
};
} // namespace kairo
