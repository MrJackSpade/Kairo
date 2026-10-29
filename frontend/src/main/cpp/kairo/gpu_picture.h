// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <GLES3/gl3.h>
#include <cstdint>
#include <memory>

namespace kairo {

// A core's picture composed on the GPU instead of in software (MachineCore::gpu_picture), such as
// palette-indexed planes a shader turns into colours (Kairo98#108). After each frame the frame host
// takes what it changed into a packet on the emulation thread and hands the newest packet to the
// presenter thread, folding one the presenter had no time for into the next. The presenter applies
// packets to the picture's own copy of the screen and has the picture drawn into a texture of its
// size, which it then shows as it shows a software frame: through the player's shader preset, or
// scaled to the window.
class GpuPicture {
public:
    // What frames changed; the picture's own type.
    struct Packet {
        virtual ~Packet() = default;
    };

    virtual ~GpuPicture() = default;

    // The picture's size, the same for the picture's whole life; any thread.
    virtual uint32_t width() const = 0;
    virtual uint32_t height() const = 0;

    // Emulation thread, between frames.
    virtual std::unique_ptr<Packet> new_packet() = 0;
    // Fills packet with what the frame that just ran changed; false, leaving it as it was, when the
    // frame changed nothing.
    virtual bool take(Packet &packet) = 0;
    // Whether packet, just taken, shows anything but black (every colour channel at most 16 of 255),
    // as a booting machine's screen does before it shows anything.
    virtual bool pictured(const Packet &packet) = 0;
    // Folds older, which the presenter will not see, into newer, taken after it.
    virtual void merge(Packet &newer, const Packet &older) = 0;

    // Presenter thread.
    // Brings the picture's copy of the screen up to packet; no GL calls (the picture may have no
    // context yet).
    virtual void apply(const Packet &packet) = 0;
    // A GL ES 3 context became current in which the picture has no GL objects: it makes them, and
    // its next draw uploads its whole copy of the screen.
    virtual void context_created() = 0;
    // The current context is about to go: the picture deletes its GL objects.
    virtual void context_ending() = 0;
    // Draws the picture into framebuffer, whose size is the picture's, with the picture's top row at
    // the framebuffer's first row when top_row_first (as a software frame is uploaded), else at its
    // last (OpenGL's own orientation, as a shader preset takes its source). The caller's GL state is
    // not kept.
    virtual void draw(GLuint framebuffer, bool top_row_first) = 0;
};

} // namespace kairo
