// SPDX-License-Identifier: GPL-2.0-or-later
// Host test for kairo/startup_typer.h:
//   clang++ -std=c++17 -I../../main/cpp startup_typer_test.cpp -o startup_typer_test && ./startup_typer_test
#include "kairo/startup_typer.h"

using kairo::Prompt;
using kairo::SpecialKey;
using kairo::StartupStep;
using kairo::StartupTyper;

#include <cstdio>
#include <string>
#include <vector>

static int failures = 0;
static void check(bool condition, const char *message) {
    if (!condition) {
        std::fprintf(stderr, "%s\n", message);
        ++failures;
    }
}

// Letters type as their character code; Shift is 1, Return 2. Presses are recorded as the text they type.
struct Machine {
    Prompt waiting = Prompt::None;
    uint64_t screen = 0;
    std::string typed;
    bool shift = false;

    Prompt prompt() { return waiting; }
    bool typed_key(char c, int &code, bool &shifted) {
        if (c < 'a' || c > 'z') return false;
        code = c;
        shifted = false;
        return true;
    }
    int special_key(SpecialKey key) {
        if (key == SpecialKey::Shift) return 1;
        if (key == SpecialKey::Return) return 2;
        return -1;
    }
    bool expand_command(std::string &) { return true; }
    void key(int code, bool down) {
        if (!down) return;
        typed += code == 2 ? '\n' : static_cast<char>(code);
    }
    uint64_t screen_signature() { return screen; }
};

struct PromptOnly {
    Prompt waiting = Prompt::Line;
    std::string typed;
    Prompt prompt() { return waiting; }
    bool typed_key(char c, int &code, bool &shifted) { code = c; shifted = false; return true; }
    int special_key(SpecialKey key) { return key == SpecialKey::Shift ? 1 : key == SpecialKey::Return ? 2 : -1; }
    bool expand_command(std::string &) { return true; }
    void key(int code, bool down) { if (down) typed += code == 2 ? '\n' : static_cast<char>(code); }
};

template <class M>
static void run(StartupTyper &typer, M &machine, int frames) {
    std::string error;
    for (int i = 0; i < frames; ++i)
        if (!typer.frame(machine, error)) { std::fprintf(stderr, "frame failed: %s\n", error.c_str()); ++failures; return; }
}

int main() {
    {
        // A script types each line at the prompt.
        StartupTyper typer;
        Machine machine;
        typer.start("ab\ncd");
        run(typer, machine, 50);
        check(machine.typed.empty(), "a script waits for the prompt");
        machine.waiting = Prompt::Line;
        run(typer, machine, 400);
        check(machine.typed == "ab\ncd\n", "a script types its lines at the prompt");
        check(!typer.active(), "a typed script is done");
    }
    {
        // A step waits for its screen, then types without Return when asked.
        StartupTyper typer;
        Machine machine;
        machine.waiting = Prompt::Line;
        typer.start({{"x", false, {7, 9}}});
        run(typer, machine, 100);
        check(machine.typed.empty(), "a screen step ignores the prompt");
        machine.screen = 9;
        run(typer, machine, 100);
        check(machine.typed == "x", "a screen step types on its screen, without Return");
        check(!typer.active(), "the screen step is done");
    }
    {
        // A step that waits too long stalls; resume waits again.
        StartupTyper typer;
        Machine machine;
        typer.start({{"y", true, {5}, false, 10}});
        run(typer, machine, 9);
        check(typer.stalled() == -1, "a step does not stall before its timeout");
        run(typer, machine, 1);
        check(typer.stalled() == 0 && !typer.active(), "a step stalls at its timeout");
        machine.screen = 5;
        run(typer, machine, 20);
        check(machine.typed.empty(), "a stalled typer types nothing");
        typer.resume();
        run(typer, machine, 100);
        check(machine.typed == "y\n", "a resumed step types");
    }
    {
        // An added step typed now, after the typer finished.
        StartupTyper typer;
        Machine machine;
        typer.start(std::vector<StartupStep>{});
        StartupStep step;
        step.text = "z";
        step.now = true;
        step.enter = false;
        typer.add(step);
        run(typer, machine, 50);
        check(machine.typed == "z", "an added step typed now");
    }
    {
        // A machine without screen signatures still types scripts.
        StartupTyper typer;
        PromptOnly machine;
        typer.start("go");
        run(typer, machine, 100);
        check(machine.typed == "go\n", "a prompt-only machine types");
    }
    {
        // An untypeable line fails.
        StartupTyper typer;
        Machine machine;
        machine.waiting = Prompt::Line;
        typer.start("A");
        std::string error;
        bool ok = true;
        for (int i = 0; i < 5 && ok; ++i) ok = typer.frame(machine, error);
        check(!ok && !error.empty(), "an untypeable line fails with an error");
    }
    if (failures == 0) std::printf("startup_typer_test: all passed\n");
    return failures == 0 ? 0 : 1;
}
