// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include "egl_context.h"
#include <GLES3/gl3.h>
#include <android/log.h>
#include <android/native_window.h>
#include <cstdint>

namespace kairo {

// Draws a software-produced RGBA8 frame to the game's window on the GPU when no shader preset
// is chosen (Kairo#50): the frame is uploaded as it is and scaled to the whole window by a
// sharp-bilinear shader, which prescales by whole pixels and blends only across the edges of
// each source pixel. A core may therefore hand over its picture at the machine's own resolution,
// such as 640x200 for a 200-line mode the layout shows at 640x400: every source line becomes the
// same rows as the core's own line doubling would give, without the CPU writing them twice.
// Rows are flipped in the shader, not on the CPU. The window's layout, not the frame's size,
// decides the shape, so a frame's size may change with the guest's mode.
class GlFramePresenter {
public:
    GlFramePresenter() = default;
    GlFramePresenter(const GlFramePresenter &) = delete;
    GlFramePresenter &operator=(const GlFramePresenter &) = delete;
    ~GlFramePresenter() { destroy(); }

    // Draws [rgba] (top row first) to [window]. Fails fast: a device without OpenGL ES 3 cannot
    // show games at all, so a failure here stops the app with its reason.
    void render(ANativeWindow *window, const uint8_t *rgba, uint32_t width, uint32_t height) {
        if (!egl_.ready()) {
            require(egl_.create(3, 0), "create an OpenGL ES 3 context");
            createProgram();
        }
        require(egl_.attach(window), "attach the game window");
        GLint maxTexture = 0;
        glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxTexture);
        if (width > static_cast<uint32_t>(maxTexture) || height > static_cast<uint32_t>(maxTexture))
            __android_log_assert("size", "KairoPresent", "Frame %ux%u exceeds the GPU's %d texture size",
                                 width, height, maxTexture);
        glBindTexture(GL_TEXTURE_2D, texture_);
        if (width != width_ || height != height_) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
            width_ = width;
            height_ = height;
        }
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        EGLint screenWidth = 0, screenHeight = 0;
        require(eglQuerySurface(egl_.display(), egl_.surface(), EGL_WIDTH, &screenWidth) &&
                eglQuerySurface(egl_.display(), egl_.surface(), EGL_HEIGHT, &screenHeight) &&
                screenWidth > 0 && screenHeight > 0, "read the window's size");
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, screenWidth, screenHeight);
        glUseProgram(program_);
        glUniform2f(sourceSize_, static_cast<float>(width), static_cast<float>(height));
        glUniform2f(outputSize_, static_cast<float>(screenWidth), static_cast<float>(screenHeight));
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture_);
        glUniform1i(frame_, 0);
        glBindVertexArray(vertexArray_);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        require(glGetError() == GL_NO_ERROR, "draw the frame");
        // A window Android is taking away refuses the swap before the UI thread hands over its
        // replacement (set_window); the presenter lets go of it and attaches again with the next frame.
        if (!eglSwapBuffers(egl_.display(), egl_.surface())) {
            __android_log_print(ANDROID_LOG_ERROR, "KairoPresent", "eglSwapBuffers failed: 0x%x", eglGetError());
            destroy();
        }
    }

    // Releases the window, as when another presenter takes it or it goes away.
    void destroy() {
        if (egl_.ready() && egl_.makeCurrent()) {
            if (texture_) glDeleteTextures(1, &texture_);
            if (vertexArray_) glDeleteVertexArrays(1, &vertexArray_);
            if (program_) glDeleteProgram(program_);
        }
        egl_.destroy();
        texture_ = vertexArray_ = program_ = 0;
        width_ = height_ = 0;
    }

private:
    static void require(bool ok, const char *what) {
        if (!ok) __android_log_assert("present", "KairoPresent", "Could not %s (EGL 0x%x, GL 0x%x)", what,
                                      eglGetError(), glGetError());
    }

    static GLuint compile(GLenum type, const char *source) {
        const GLuint shader = glCreateShader(type);
        glShaderSource(shader, 1, &source, nullptr);
        glCompileShader(shader);
        GLint ok = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
        if (!ok) {
            char log[1024] = {};
            glGetShaderInfoLog(shader, sizeof(log), nullptr, log);
            __android_log_assert("compile", "KairoPresent", "Frame shader did not compile: %s", log);
        }
        return shader;
    }

    void createProgram() {
        // One triangle covering the window; the texture's first row (the frame's top) at the top.
        static const char *vertex = R"(#version 300 es
out vec2 uv;
void main() {
    vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    uv = vec2(corner.x, 1.0 - corner.y);
    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
})";
        // Sharp bilinear: the integer part of the scale is nearest-neighbour, and only a texel's
        // outermost fraction of an output pixel blends with its neighbour.
        static const char *fragment = R"(#version 300 es
precision highp float;
uniform sampler2D frame;
uniform vec2 sourceSize;
uniform vec2 outputSize;
in vec2 uv;
out vec4 color;
void main() {
    vec2 texel = uv * sourceSize;
    vec2 prescale = max(floor(outputSize / sourceSize), vec2(1.0));
    vec2 region = 0.5 - 0.5 / prescale;
    vec2 center = fract(texel) - 0.5;
    vec2 offset = (center - clamp(center, -region, region)) * prescale + 0.5;
    color = vec4(texture(frame, (floor(texel) + offset) / sourceSize).rgb, 1.0);
})";
        program_ = glCreateProgram();
        const GLuint vertexShader = compile(GL_VERTEX_SHADER, vertex);
        const GLuint fragmentShader = compile(GL_FRAGMENT_SHADER, fragment);
        glAttachShader(program_, vertexShader);
        glAttachShader(program_, fragmentShader);
        glLinkProgram(program_);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        GLint linked = 0;
        glGetProgramiv(program_, GL_LINK_STATUS, &linked);
        require(linked, "link the frame shader");
        frame_ = glGetUniformLocation(program_, "frame");
        sourceSize_ = glGetUniformLocation(program_, "sourceSize");
        outputSize_ = glGetUniformLocation(program_, "outputSize");
        glGenVertexArrays(1, &vertexArray_);
        glGenTextures(1, &texture_);
        glBindTexture(GL_TEXTURE_2D, texture_);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    EglContext egl_;
    GLuint program_ = 0, texture_ = 0, vertexArray_ = 0;
    GLint frame_ = -1, sourceSize_ = -1, outputSize_ = -1;
    uint32_t width_ = 0, height_ = 0;
};

} // namespace kairo
