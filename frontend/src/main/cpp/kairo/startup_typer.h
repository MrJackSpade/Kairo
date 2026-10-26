// A game's startup commands, typed at the guest's prompt as a player would (KairoTowns#39): each
// line when the guest waits for one, then Return, one key change at a time with the frames each
// must last for the guest's keyboard scan to see it. The frame host runs it for every machine whose
// launch has commands (machine_jni.cpp); a product's core says when its guest waits at a prompt and
// which keys type a line.

#pragma once

#include <string>
#include <vector>

namespace kairo {

// What the guest waits for now, as a startup command could answer it.
enum class Prompt {
    None,    // Nothing: the typer waits.
    Line,    // A command line: the next one goes in.
    Return,  // Return alone, as for a question the commands do not answer (Kairo88's "How many files").
};

// The keys the typer needs besides a line's characters.
enum class SpecialKey { Return, Shift, Ctrl, F1, F2, F3, F4, F5 };

// Types newline-separated lines into a Machine with these members:
//   Prompt prompt();                                What the guest waits for now.
//   bool typed_key(char c, int &code, bool &shift); The key that types c, and whether Shift goes with it.
//   int special_key(SpecialKey key);                A special key's code, or -1 when it has none.
//   bool expand_command(std::string &line);         Changes a line before it is typed; false when it cannot be.
//   void key(int code, bool down);
// In a line, {CTRL-x} is Ctrl with x, and {F1} to {F5} a function key.
class StartupTyper {
public:
    // The lines to type; empty for none.
    void start(const std::string &script) {
        lines_.clear();
        events_.clear();
        next_line_ = next_event_ = 0;
        wait_ = settle_ = 0;
        std::string::size_type begin = 0;
        while (begin < script.size()) {
            const auto end = script.find('\n', begin);
            lines_.push_back(script.substr(begin, end == std::string::npos ? std::string::npos : end - begin));
            begin = end == std::string::npos ? script.size() : end + 1;
        }
        active_ = !lines_.empty();
    }

    // Whether lines are still to be typed.
    bool active() const { return active_; }
    // Whether a line is being typed now, rather than the typer waiting for the guest's prompt.
    bool typing() const { return active_ && (next_event_ < events_.size() || wait_ > 0); }

    // Advances the typer by one guest frame, after the machine ran it. False with error when a
    // line cannot be typed.
    template <class Machine>
    bool frame(Machine &machine, std::string &error) {
        if (!active_) return true;
        if (wait_ > 0) {
            --wait_;
            return true;
        }
        if (next_event_ < events_.size()) {
            const Event &event = events_[next_event_++];
            machine.key(event.key, event.down);
            wait_ = event.wait;
            // The last line is typed: the game is the machine's.
            if (next_event_ == events_.size() && next_line_ == lines_.size()) active_ = false;
            return true;
        }
        // Let the guest answer the last line before looking for its prompt.
        if (settle_ > 0) {
            --settle_;
            return true;
        }
        switch (machine.prompt()) {
        case Prompt::None:
            return true;
        case Prompt::Return:
            if (!queue(machine, "", error)) return fail(error);
            break;
        case Prompt::Line: {
            std::string line = lines_[next_line_++];
            if (!machine.expand_command(line) || !queue(machine, line, error)) {
                if (error.empty()) error = "The startup command \"" + lines_[next_line_ - 1] + "\" cannot be typed";
                return fail(error);
            }
            break;
        }
        }
        settle_ = kSettleFrames;
        return true;
    }

private:
    // A key change and the frames it lasts before the next.
    struct Event {
        int key;
        bool down;
        int wait;
    };
    // The frames the guest has to answer a line before the typer looks for its prompt again.
    static constexpr int kSettleFrames = 30;

    void push(int key, bool down, int wait) { events_.push_back({key, down, wait}); }

    void tap(int key, bool shifted, int shift) {
        if (shifted) push(shift, true, 2);
        push(key, true, 5);
        if (shifted) push(key, false, 2);
        push(shifted ? shift : key, false, 5);
    }

    // A line and Return, as key changes.
    template <class Machine>
    bool queue(Machine &machine, const std::string &line, std::string &error) {
        const int shift = machine.special_key(SpecialKey::Shift);
        const int ret = machine.special_key(SpecialKey::Return);
        if (shift < 0 || ret < 0) {
            error = "The machine has no Shift or Return key to type startup commands with";
            return false;
        }
        for (std::string::size_type i = 0; i < line.size(); ++i) {
            if (line[i] == '{') {
                const auto end = line.find('}', i);
                if (end == std::string::npos) return false;
                const std::string escape = line.substr(i + 1, end - i - 1);
                if (escape.size() == 6 && escape.compare(0, 5, "CTRL-") == 0) {
                    const int ctrl = machine.special_key(SpecialKey::Ctrl);
                    int code = 0;
                    bool shifted = false;
                    if (ctrl < 0 || !machine.typed_key(escape[5], code, shifted)) return false;
                    push(ctrl, true, 2);
                    tap(code, false, shift);
                    push(ctrl, false, 5);
                } else if (escape.size() == 2 && escape[0] == 'F' && escape[1] >= '1' && escape[1] <= '5') {
                    const int code = machine.special_key(static_cast<SpecialKey>(
                        static_cast<int>(SpecialKey::F1) + (escape[1] - '1')));
                    if (code < 0) return false;
                    tap(code, false, shift);
                } else {
                    return false;
                }
                i = end;
                continue;
            }
            int code = 0;
            bool shifted = false;
            if (!machine.typed_key(line[i], code, shifted)) return false;
            tap(code, shifted, shift);
        }
        tap(ret, false, shift);
        return true;
    }

    bool fail(const std::string &) {
        active_ = false;
        return false;
    }

    std::vector<std::string> lines_;
    std::vector<Event> events_;
    size_t next_line_ = 0, next_event_ = 0;
    int wait_ = 0, settle_ = 0;
    bool active_ = false;
};

} // namespace kairo
