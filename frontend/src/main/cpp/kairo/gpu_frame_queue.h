// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once
#include "egl_context.h"
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <functional>
#include <mutex>

namespace kairo {
// Offscreen producer and independent window presenter. GPU copies and fences
// protect a three-texture mailbox; a slow panel drops stale frames, never
// makes the producer wait for eglSwapBuffers. No CPU pixel readback is used.
class GpuFrameQueue {
public:
    bool create(int major, int minor) {
        stopping_ = false; software_ = false; failed_ = false;
        if (!producer_.create(major, minor)) return false;
        glGenFramebuffers(1, &framebuffer_);
        glGenTextures(1, &texture_);
        glGenFramebuffers(1, &copyFramebuffer_);
        if (!resize(1024, 1024) ||
            !presenter_.create(major, minor, producer_.context()) || !buildProgram()) {
            destroy(); return false;
        }
        presenter_.releaseCurrent();
        return producer_.makeCurrent();
    }
    bool ready() const { return producer_.ready(); }
    bool makeCurrent() { return producer_.makeCurrent(); }
    void releaseCurrent() { producer_.releaseCurrent(); }
    bool failed() const { return failed_; }
    GLuint framebuffer() const { return framebuffer_; }
    bool resize(unsigned width, unsigned height) {
        if (width <= width_ && height <= height_) return true;
        GLint max = 0; glGetIntegerv(GL_MAX_TEXTURE_SIZE, &max);
        if (!width || !height || width > static_cast<unsigned>(max) || height > static_cast<unsigned>(max))
            return false;
        width = std::max(width, width_); height = std::max(height, height_);
        GLint oldTexture = 0, oldRead = 0, oldDraw = 0;
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &oldTexture);
        glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &oldRead);
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &oldDraw);
        glBindTexture(GL_TEXTURE_2D, texture_);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer_);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture_, 0);
        const bool ok = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
        glBindTexture(GL_TEXTURE_2D, oldTexture);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, oldRead);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldDraw);
        if (ok) { width_ = width; height_ = height; }
        return ok;
    }
    void publish(unsigned width, unsigned height, bool bottomLeft) {
        if (!width || !height || width > width_ || height > height_) { failed_ = true; return; }
        Slot* slot = nullptr;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            // Prefer an unused slot; otherwise replace the oldest queued frame.
            for (auto& s : slots_) if (s.state == Free) { slot = &s; break; }
            if (!slot) for (auto& s : slots_) if (s.state == Queued && (!slot || s.serial < slot->serial)) slot = &s;
            if (!slot) return;
            slot->state = Writing;
        }
        if (slot->done) { glWaitSync(slot->done, 0, GL_TIMEOUT_IGNORED); glDeleteSync(slot->done); slot->done = nullptr; }
        if (slot->ready) { glDeleteSync(slot->ready); slot->ready = nullptr; }
        GLint oldTexture = 0, oldRead = 0, oldDraw = 0;
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &oldTexture);
        glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &oldRead);
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &oldDraw);
        const bool scissor = glIsEnabled(GL_SCISSOR_TEST);
        glDisable(GL_SCISSOR_TEST);
        if (!slot->texture) glGenTextures(1, &slot->texture);
        glBindTexture(GL_TEXTURE_2D, slot->texture);
        if (slot->width != width || slot->height != height) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer_);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, copyFramebuffer_);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, slot->texture, 0);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        slot->ready = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        glFlush();
        glBindTexture(GL_TEXTURE_2D, oldTexture);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, oldRead);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldDraw);
        if (scissor) glEnable(GL_SCISSOR_TEST);
        {
            std::lock_guard<std::mutex> lock(mutex_);
            slot->width = width; slot->height = height; slot->bottomLeft = bottomLeft;
            slot->serial = ++serial_; slot->state = Queued;
        }
        changed_.notify_one();
    }
    // acquireWindow returns one acquired ANativeWindow reference or nullptr.
    // The caller's surface mutex is not held during GPU work or panel waits.
    void render(const std::function<ANativeWindow*()>& acquireWindow) {
        if (!presenter_.makeCurrent()) { failed_ = true; return; }
        Slot* current = nullptr;
        EGLint lastWidth = 0, lastHeight = 0;
        while (!stopping_ && !software_) {
            Slot* next = nullptr;
            {
                std::unique_lock<std::mutex> lock(mutex_);
                changed_.wait_for(lock, std::chrono::milliseconds(30), [&] {
                    return stopping_ || software_ || std::any_of(slots_.begin(), slots_.end(),
                        [](const Slot& s) { return s.state == Queued; });
                });
                if (stopping_ || software_) break;
                for (auto& s : slots_) if (s.state == Queued && (!next || s.serial > next->serial)) next = &s;
                if (next) {
                    if (current) current->state = Free;
                    next->state = Reading;
                    current = next;
                }
            }
            ANativeWindow* window = acquireWindow();
            const bool surfaceChanged = window != presenter_.window();
            const bool attached = presenter_.attach(window);
            if (window) ANativeWindow_release(window);
            if (!attached) { presenter_.detach(); continue; }
            if (!current || !presenter_.window()) continue;
            EGLint w = 0, h = 0;
            if (!eglQuerySurface(presenter_.display(), presenter_.surface(), EGL_WIDTH, &w) ||
                !eglQuerySurface(presenter_.display(), presenter_.surface(), EGL_HEIGHT, &h) || w <= 0 || h <= 0) continue;
            if (!next && !surfaceChanged && w == lastWidth && h == lastHeight) continue;
            if (current->ready) {
                glWaitSync(current->ready, 0, GL_TIMEOUT_IGNORED);
                glDeleteSync(current->ready); current->ready = nullptr;
            }
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, w, h);
            glDisable(GL_BLEND); glDisable(GL_DEPTH_TEST); glDisable(GL_SCISSOR_TEST);
            glUseProgram(program_);
            glUniform2i(glGetUniformLocation(program_, "screen"), w, h);
            glUniform2i(glGetUniformLocation(program_, "image"), current->width, current->height);
            glUniform1i(glGetUniformLocation(program_, "bottomLeft"), current->bottomLeft);
            glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, current->texture);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            if (current->done) glDeleteSync(current->done);
            current->done = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            glFlush();
            if (!eglSwapBuffers(presenter_.display(), presenter_.surface())) {
                if (eglGetError() == EGL_CONTEXT_LOST) { failed_ = true; break; }
                presenter_.detach(); // Surface can disappear during a display swap.
            } else { lastWidth = w; lastHeight = h; }
        }
        presenter_.detach();
        presenter_.releaseCurrent();
    }
    void stop() { stopping_ = true; changed_.notify_all(); }
    void useSoftware() { software_ = true; changed_.notify_all(); }
    // Call only after the presenter has joined and core context_destroy ran.
    void destroy() {
        if (presenter_.ready() && presenter_.makeCurrent()) {
            if (program_) glDeleteProgram(program_);
            presenter_.destroy();
        }
        if (producer_.ready() && producer_.makeCurrent()) {
            for (auto& s : slots_) {
                if (s.ready) glDeleteSync(s.ready);
                if (s.done) glDeleteSync(s.done);
                if (s.texture) glDeleteTextures(1, &s.texture);
                s = {};
            }
            glDeleteFramebuffers(1, &framebuffer_); glDeleteFramebuffers(1, &copyFramebuffer_);
            glDeleteTextures(1, &texture_);
        }
        producer_.destroy(); presenter_.destroy();
        program_ = framebuffer_ = copyFramebuffer_ = texture_ = 0;
        width_ = height_ = 0; serial_ = 0;
    }
private:
    enum State { Free, Writing, Queued, Reading };
    struct Slot {
        GLuint texture = 0;
        GLsync ready = nullptr, done = nullptr;
        unsigned width = 0, height = 0;
        bool bottomLeft = true;
        uint64_t serial = 0;
        State state = Free;
    };
    static GLuint compile(GLenum kind, const char* source) {
        GLuint shader = glCreateShader(kind);
        glShaderSource(shader, 1, &source, nullptr); glCompileShader(shader);
        GLint ok = 0; glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
        if (!ok) { glDeleteShader(shader); return 0; }
        return shader;
    }
    bool buildProgram() {
        const char* vertex = "#version 300 es\nvoid main(){vec2 p=vec2(gl_VertexID==1?3.:-1.,gl_VertexID==2?3.:-1.);gl_Position=vec4(p,0.,1.);}";
        const char* fragment = "#version 300 es\nprecision highp float;precision highp int;uniform sampler2D frame;uniform ivec2 screen,image;uniform bool bottomLeft;out vec4 color;void main(){ivec2 p=ivec2(gl_FragCoord.xy);p=p*image/screen;if(!bottomLeft)p.y=image.y-1-p.y;color=vec4(texelFetch(frame,p,0).rgb,1.);}";
        GLuint vs = compile(GL_VERTEX_SHADER, vertex), fs = compile(GL_FRAGMENT_SHADER, fragment);
        if (!vs || !fs) { if (vs) glDeleteShader(vs); if (fs) glDeleteShader(fs); return false; }
        program_ = glCreateProgram(); glAttachShader(program_, vs); glAttachShader(program_, fs);
        glLinkProgram(program_); glDeleteShader(vs); glDeleteShader(fs);
        GLint ok = 0; glGetProgramiv(program_, GL_LINK_STATUS, &ok);
        if (!ok) return false;
        glUseProgram(program_); glUniform1i(glGetUniformLocation(program_, "frame"), 0);
        return true;
    }
    EglContext producer_, presenter_;
    GLuint framebuffer_ = 0, copyFramebuffer_ = 0, texture_ = 0, program_ = 0;
    unsigned width_ = 0, height_ = 0;
    uint64_t serial_ = 0;
    std::array<Slot, 3> slots_{};
    std::mutex mutex_;
    std::condition_variable changed_;
    std::atomic<bool> stopping_{false}, software_{false}, failed_{false};
};
}
