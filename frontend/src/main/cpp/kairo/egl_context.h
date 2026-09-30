// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>

namespace kairo {
// Thread-owned EGL context. Surface replacement preserves GL resources; the
// pbuffer also allows offscreen rendering and temporary thread handoff.
class EglContext {
public:
    EglContext() = default;
    EglContext(const EglContext&) = delete;
    EglContext& operator=(const EglContext&) = delete;
    ~EglContext() { destroy(); }
    bool create(int major = 3, int minor = 0, EGLContext share = EGL_NO_CONTEXT,
                bool prefer565 = false) {
        destroy();
        display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (display_ == EGL_NO_DISPLAY || !eglInitialize(display_, nullptr, nullptr) ||
            !eglBindAPI(EGL_OPENGL_ES_API)) return fail();
        EGLint count = 0;
        const EGLint rgb565[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
            EGL_RED_SIZE, 5, EGL_GREEN_SIZE, 6, EGL_BLUE_SIZE, 5,
            EGL_ALPHA_SIZE, 0, EGL_DEPTH_SIZE, 0, EGL_NONE};
        const EGLint rgba[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
            EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 0, EGL_NONE};
        if (!prefer565 || !eglChooseConfig(display_, rgb565, &config_, 1, &count) || !count)
            if (!eglChooseConfig(display_, rgba, &config_, 1, &count) || !count) return fail();
        const EGLint attributes[] = {EGL_CONTEXT_CLIENT_VERSION, major,
            EGL_CONTEXT_MINOR_VERSION_KHR, minor, EGL_NONE};
        context_ = eglCreateContext(display_, config_, share, attributes);
        if (context_ == EGL_NO_CONTEXT) return fail();
        const EGLint size[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
        pbuffer_ = eglCreatePbufferSurface(display_, config_, size);
        if (pbuffer_ == EGL_NO_SURFACE || !makeCurrent()) return fail();
        GLint actualMajor = 0, actualMinor = 0;
        glGetIntegerv(GL_MAJOR_VERSION, &actualMajor);
        glGetIntegerv(GL_MINOR_VERSION, &actualMinor);
        if (actualMajor < major || (actualMajor == major && actualMinor < minor)) return fail();
        return true;
    }
    bool attach(ANativeWindow* next) {
        if (next == window_) return next ? makeCurrent() : true;
        detach();
        if (!next) return true;
        EGLint format = 0;
        if (!eglGetConfigAttrib(display_, config_, EGL_NATIVE_VISUAL_ID, &format) ||
            ANativeWindow_setBuffersGeometry(next, 0, 0, format) != 0) return false;
        ANativeWindow_acquire(next);
        window_ = next;
        surface_ = eglCreateWindowSurface(display_, config_, next, nullptr);
        if (surface_ == EGL_NO_SURFACE || !makeCurrent()) { detach(); return false; }
        eglSwapInterval(display_, 1);
        return true;
    }
    void detach() {
        if (display_ == EGL_NO_DISPLAY) return;
        if (context_ != EGL_NO_CONTEXT && pbuffer_ != EGL_NO_SURFACE)
            eglMakeCurrent(display_, pbuffer_, pbuffer_, context_);
        if (surface_ != EGL_NO_SURFACE) eglDestroySurface(display_, surface_);
        surface_ = EGL_NO_SURFACE;
        if (window_) ANativeWindow_release(window_);
        window_ = nullptr;
    }
    bool makeCurrent() const {
        const EGLSurface target = surface_ == EGL_NO_SURFACE ? pbuffer_ : surface_;
        return context_ != EGL_NO_CONTEXT && eglMakeCurrent(display_, target, target, context_);
    }
    void releaseCurrent() const {
        if (display_ != EGL_NO_DISPLAY)
            eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    }
    void destroy() {
        detach();
        releaseCurrent();
        if (display_ != EGL_NO_DISPLAY) {
            if (context_ != EGL_NO_CONTEXT) eglDestroyContext(display_, context_);
            if (pbuffer_ != EGL_NO_SURFACE) eglDestroySurface(display_, pbuffer_);
        }
        // EGL_DEFAULT_DISPLAY is process-wide, including Android's UI renderer.
        // Destroy our objects without terminating another renderer's display.
        display_ = EGL_NO_DISPLAY; context_ = EGL_NO_CONTEXT; pbuffer_ = EGL_NO_SURFACE;
    }
    bool ready() const { return context_ != EGL_NO_CONTEXT; }
    EGLDisplay display() const { return display_; }
    EGLSurface surface() const { return surface_; }
    EGLContext context() const { return context_; }
    ANativeWindow* window() const { return window_; }
private:
    bool fail() { destroy(); return false; }
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLConfig config_ = nullptr;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLSurface pbuffer_ = EGL_NO_SURFACE, surface_ = EGL_NO_SURFACE;
    ANativeWindow* window_ = nullptr;
};
}
