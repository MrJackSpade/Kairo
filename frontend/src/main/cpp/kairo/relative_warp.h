// Direct touch (MachineCore::mouse_warp) for a guest that keeps its own pointer from relative mouse
// motion, one pixel per count, and stops it at the screen's edges (kairo_touch_direct_explanation):
// a warp first pushes the pointer a whole screen past the top-left corner, where the guest stops
// it, then moves it from that corner to the point. Nothing has to know where the pointer was.
//
// The core feeds the motion step() gives it as it would the player's, and tells step() whether the
// guest has taken all it was given: the corner's push has to arrive before the counts that start
// from the corner, or the two would add up to one move that never reaches the corner.
#pragma once

namespace kairo {

class RelativeWarp {
public:
    // Starts a warp to (x, y) on a screen width by height, in the guest's own pixels.
    void start(int x, int y, int width, int height) {
        phase_ = Phase::kPush;
        x_ = x;
        y_ = y;
        width_ = width;
        height_ = height;
        settled_ = 0;
    }

    // Once a frame: the motion (dx, dy) to give the guest now, (0, 0) for none. delivered: whether
    // the guest has taken all the motion the core was given before.
    void step(bool delivered, int &dx, int &dy) {
        dx = dy = 0;
        // A frame more after the core has passed motion on, for the guest to take it in.
        settled_ = delivered ? settled_ + 1 : 0;
        switch (phase_) {
        case Phase::kNone:
            break;
        case Phase::kPush:
            dx = -2 * width_;
            dy = -2 * height_;
            phase_ = Phase::kPushing;
            settled_ = 0;
            break;
        case Phase::kPushing:
            if (settled_ < kSettle) break;
            dx = x_;
            dy = y_;
            phase_ = Phase::kMoving;
            settled_ = 0;
            break;
        case Phase::kMoving:
            if (settled_ >= kSettle) phase_ = Phase::kNone;
            break;
        }
    }

    // A drag after the tap. Until the warp has given its counts from the corner, the warp holds the
    // motion and gives it with them (true); after, the core gives it to the guest as usual (false).
    bool hold(int dx, int dy) {
        if (phase_ != Phase::kPush && phase_ != Phase::kPushing) return false;
        x_ += dx;
        y_ += dy;
        return true;
    }

    bool running() const { return phase_ != Phase::kNone; }
    // Whether the pointer is on the last warp's point, past its last motion.
    bool done() const { return phase_ == Phase::kNone; }

private:
    enum class Phase { kNone, kPush, kPushing, kMoving };
    static constexpr int kSettle = 2;
    Phase phase_ = Phase::kNone;
    int x_ = 0, y_ = 0, width_ = 0, height_ = 0;
    int settled_ = 0;
};

} // namespace kairo
