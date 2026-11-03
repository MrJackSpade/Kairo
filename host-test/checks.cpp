// The shared PC boot test's checks (Kairo#115-#124), each written once against MachineCore. A
// table entry names the checks it runs in "checks", each with its own spec.

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <map>

#include "harness.h"

namespace kairo::host {
namespace {

std::string hex(uint64_t value) {
    char text[17];
    std::snprintf(text, sizeof text, "%016llx", static_cast<unsigned long long>(value));
    return text;
}

// Where two runs of frames first differ, or -1 when they match.
int64_t first_difference(const std::vector<uint64_t> &a, const std::vector<uint64_t> &b) {
    const size_t n = std::min(a.size(), b.size());
    for (size_t i = 0; i < n; ++i) if (a[i] != b[i]) return static_cast<int64_t>(i);
    return a.size() == b.size() ? -1 : static_cast<int64_t>(n);
}

// boot: the game starts and runs for "frames" frames; its picture is kept as boot.ppm.
void boot(Test &test, const json::Value &spec) {
    Session session(test.launch(), test.folder, test.name);
    session.run(test.frames(spec, "frames"));
    session.picture("boot");
    session.close();
}

// state (Kairo#115): at frame "at" (during a load, where states went wrong), save; run "frames"
// frames; load; run them again. The two runs must be the same frames, and so the same machine.
void state(Test &test, const json::Value &spec) {
    Session session(test.launch(), test.folder, test.name);
    session.run(test.frames(spec, "at"));
    const fs::path saved = test.folder / "state";
    fs::create_directories(saved);
    if (const int result = session.core().save_state(saved.u8string()))
        session.fail("save_state returned " + std::to_string(result) + ": " + session.core().state_error());
    const uint64_t frames = test.frames(spec, "frames");
    session.mark();
    session.run(frames);
    const std::vector<uint64_t> after_save = session.signatures();
    if (const int result = session.core().load_state(saved.u8string()))
        session.fail("load_state returned " + std::to_string(result) + ": " + session.core().state_error());
    session.mark();
    session.run(frames);
    const std::vector<uint64_t> &after_load = session.signatures();
    const int64_t at = first_difference(after_save, after_load);
    if (at >= 0) {
        size_t differ = 0;
        for (size_t i = 0; i < after_save.size(); ++i) differ += after_save[i] != after_load[i];
        session.fail("frame " + std::to_string(at) + " after the load differs from the same frame after the save (" +
            hex(after_save[static_cast<size_t>(at)]) + " then " + hex(after_load[static_cast<size_t>(at)]) + "); " +
            std::to_string(differ) + " of " + std::to_string(after_save.size()) + " frames differ, the last " +
            (after_save.back() == after_load.back() ? "matching" : "differing"));
    }
    session.close();
}

// sessions (Kairo#118): in one process, start the game, end it, start it again: the second
// session's first "frames" frames must be the first's. Then a start at the prompt ("prompt": the
// launch the app makes for it, as media and settings over the table's) must run as long.
void sessions(Test &test, const json::Value &spec) {
    const uint64_t frames = test.frames(spec, "frames");
    std::vector<uint64_t> first;
    for (int round = 0; round < 2; ++round) {
        Session session(test.launch(), test.folder, test.name + " session " + std::to_string(round + 1));
        session.run(frames);
        if (round == 0) first = session.signatures();
        else if (const int64_t at = first_difference(first, session.signatures()); at >= 0)
            session.fail("its frame " + std::to_string(at) + " differs from the first session's");
        session.close();
    }
    if (const json::Value *prompt = spec.find("prompt")) {
        Session session(test.launch(prompt), test.folder, test.name + " at the prompt");
        session.run(frames);
        session.close();
    }
}

// locks (Kairo#124): for each lock key in "keys" ({"code": the key's code, "bit": its bit in
// locks()}), after "at" frames: pressing it turns it on in locks(); a state saved then, with the
// key turned off again after it, loads with the key on.
void locks(Test &test, const json::Value &spec) {
    Session session(test.launch(), test.folder, test.name);
    session.run(test.frames(spec, "at"));
    for (const json::Value &key : spec.at("keys").array()) {
        const int code = static_cast<int>(key.at("code").integer());
        const int bit = 1 << key.at("bit").integer();
        const std::string name = "lock key " + std::to_string(code);
        if (session.core().locks() & bit) session.fail(name + " is on before it was pressed");
        session.press(code);
        if (!(session.core().locks() & bit)) session.fail(name + " is not on in locks() after it was pressed");
        const fs::path saved = test.folder / ("locks-" + std::to_string(code));
        fs::create_directories(saved);
        if (const int result = session.core().save_state(saved.u8string()))
            session.fail("save_state returned " + std::to_string(result));
        session.press(code);
        if (session.core().locks() & bit) session.fail(name + " is still on after a second press");
        if (const int result = session.core().load_state(saved.u8string()))
            session.fail("load_state returned " + std::to_string(result));
        session.run(1);
        if (!(session.core().locks() & bit)) session.fail(name + " is not on again after loading the state saved with it on");
        // As it was, for the next key.
        session.press(code);
    }
    session.close();
}

// loading (Kairo#120): loading() turns on within "startsBy" frames as the game reads its media, is
// off for good from "doneBy" for "settled" frames, and is never on in the "idle" launch (an empty
// drive at a prompt or desktop, as media and settings over the table's) after its "settled" frames.
void loading(Test &test, const json::Value &spec) {
    const uint64_t settled = test.frames(spec, "settled");
    {
        Session session(test.launch(), test.folder, test.name);
        session.run_until("loading() to turn on", test.frames(spec, "startsBy"), [&] { return session.core().loading(); });
        const uint64_t done = test.frames(spec, "doneBy");
        if (session.frames() < done) session.run(done - session.frames());
        for (uint64_t i = 0; i < settled; ++i) {
            session.run(1);
            if (session.core().loading()) session.fail("loading() is still on after the game's load");
        }
        session.close();
    }
    if (const json::Value *idle = spec.find("idle")) {
        Session session(test.launch(idle), test.folder, test.name + " idle");
        session.run(test.frames(*idle, "frames"));
        for (uint64_t i = 0; i < settled; ++i) {
            session.run(1);
            if (session.core().loading()) session.fail("loading() is on with nothing to load");
        }
        session.close();
    }
}

// Presses the spec's "keys" ({"frame": when, "code": the key}) as the session reaches each frame,
// for a game that needs keys to reach what a check looks at; runs to [until] frames.
void run_with_keys(Session &session, const json::Value &spec, uint64_t until) {
    std::vector<std::pair<uint64_t, int>> keys;
    if (const json::Value *list = spec.find("keys"))
        for (const json::Value &key : list->array())
            keys.emplace_back(static_cast<uint64_t>(key.at("frame").integer()), static_cast<int>(key.at("code").integer()));
    std::sort(keys.begin(), keys.end());
    size_t next = 0;
    while (session.frames() < until) {
        if (next < keys.size() && session.frames() >= keys[next].first) session.press(keys[next++].second);
        else session.run(1);
    }
}

// The screens a spec names ("screens": signatures as 16 hex digits), which a check waits for.
std::vector<uint64_t> screens(const json::Value &spec) {
    std::vector<uint64_t> out;
    for (const json::Value &screen : spec.at("screens").array()) out.push_back(std::stoull(screen.string(), nullptr, 16));
    return out;
}

// Runs until the guest shows one of [wanted], within [limit] frames; the failure names the screen it
// shows instead, which is how a table learns a screen's signature.
void wait_for_screen(Session &session, const std::vector<uint64_t> &wanted, uint64_t limit, const std::string &what) {
    for (uint64_t i = 0; i < limit; ++i) {
        if (std::find(wanted.begin(), wanted.end(), session.core().screen_signature()) != wanted.end()) return;
        session.run(1);
    }
    session.fail("still waiting after " + std::to_string(limit) + " frames for " + what + "; the screen now is " +
        hex(session.core().screen_signature()));
}

// Types "commands" (newline-separated lines) at the guest's prompts through the shared startup
// typer, as the app does (frame_machine.h), within "typedBy" frames.
void type_commands(Session &session, const Test &test, const json::Value &spec) {
    StartupTyper typer;
    typer.start(spec.at("commands").string());
    const uint64_t limit = test.frames(spec, "typedBy");
    std::string error;
    for (uint64_t i = 0; typer.active(); ++i) {
        if (i >= limit) session.fail("the startup commands were still being typed after " + std::to_string(limit) + " frames");
        session.run(1);
        if (!typer.frame(session.core(), error)) session.fail("typing the startup commands failed: " + error);
    }
}

// stateDisks (Kairo#116): with "drive"'s disk in at frame "at", swap to "swap" (or eject, ""), save,
// swap back and load: the load puts the state's disk back, as media_paths() reports. And an eject
// right before a save, as the Disks page does while the menu holds the machine (Kairo68#73), leaves
// the save working.
void state_disks(Test &test, const json::Value &spec) {
    const int drive = static_cast<int>(spec.at("drive").integer());
    Session session(test.launch(), test.folder, test.name);
    session.run(test.frames(spec, "at"));
    MachineCore &core = session.core();
    const std::vector<std::string> before = core.media_paths();
    if (static_cast<int>(before.size()) <= drive || before[static_cast<size_t>(drive)].empty())
        session.fail("drive " + std::to_string(drive) + " holds nothing to start with");
    const std::string original = before[static_cast<size_t>(drive)];
    const std::string swap = spec.at("swap").string().empty() ? "" : test.working_copy(spec.at("swap").string()).u8string();
    if (const int result = core.media(drive, swap)) session.fail("swapping drive " + std::to_string(drive) + " returned " + std::to_string(result));
    session.run(10);
    const fs::path saved = test.folder / "state-disks";
    fs::create_directories(saved);
    if (const int result = core.save_state(saved.u8string())) session.fail("save_state after the swap returned " + std::to_string(result));
    if (const int result = core.media(drive, original)) session.fail("swapping back returned " + std::to_string(result));
    session.run(10);
    if (const int result = core.load_state(saved.u8string())) session.fail("load_state returned " + std::to_string(result));
    session.run(1);
    const std::vector<std::string> loaded = core.media_paths();
    const std::string now = static_cast<int>(loaded.size()) > drive ? loaded[static_cast<size_t>(drive)] : "";
    if (now != swap) session.fail("after the load drive " + std::to_string(drive) + " holds \"" + now + "\", not the state's \"" + swap + "\"");

    if (const int result = core.media(drive, "")) session.fail("ejecting drive " + std::to_string(drive) + " returned " + std::to_string(result));
    const fs::path ejected = test.folder / "state-after-eject";
    fs::create_directories(ejected);
    if (const int result = core.save_state(ejected.u8string()))
        session.fail("a save right after an eject returned " + std::to_string(result) + ": " + core.state_error());
    session.close();
}

// What InputModeDecider (frontend InputModeDecider.kt) makes of the guest's input, sampled as the
// app samples it, every 250 ms. Ported line for line: the check must decide as the app does.
class InputMode {
public:
    void observe(const MachineCore::GuestInput &sample, double now_ms) {
        if (!have_ || sample.keyboard_waits < prior_.keyboard_waits || sample.keyboard_polls < prior_.keyboard_polls ||
            sample.mouse_reads < prior_.mouse_reads) {
            have_ = true;
            prior_ = sample;
            mouse_ = false;
            last_keyboard_ = now_ms;
            return;
        }
        const uint64_t waits = sample.keyboard_waits - prior_.keyboard_waits;
        const uint64_t polls = sample.keyboard_polls - prior_.keyboard_polls;
        const uint64_t mouse = sample.mouse_reads - prior_.mouse_reads;
        prior_ = sample;
        if (sample.keyboard_waiting || waits > 0 || polls >= 2) {
            mouse_ = false;
            last_keyboard_ = now_ms;
            return;
        }
        if ((mouse >= 4 || sample.mouse_enabled) && now_ms - last_keyboard_ >= 750) mouse_ = true;
    }
    const char *mode() const { return mouse_ ? "mouse" : "keyboard"; }

private:
    bool have_ = false;
    bool mouse_ = false;
    double last_keyboard_ = 0;
    MachineCore::GuestInput prior_;
};

// guestInput (Kairo#117): after "frames" frames (with the spec's "keys"), Auto touch mode, deciding
// from guest_input as the app does, is "expect" (mouse or keyboard).
void guest_input(Test &test, const json::Value &spec) {
    Session session(test.launch(), test.folder, test.name);
    const uint64_t frames = test.frames(spec, "frames");
    const double frame_ms = session.core().frame_seconds() * 1000.0;
    InputMode mode;
    double next_sample = 0;
    std::vector<std::pair<uint64_t, int>> keys;
    if (const json::Value *list = spec.find("keys"))
        for (const json::Value &key : list->array())
            keys.emplace_back(static_cast<uint64_t>(key.at("frame").integer()), static_cast<int>(key.at("code").integer()));
    std::sort(keys.begin(), keys.end());
    size_t next_key = 0;
    while (session.frames() < frames) {
        if (next_key < keys.size() && session.frames() >= keys[next_key].first) session.press(keys[next_key++].second);
        else session.run(1);
        const double now = static_cast<double>(session.frames()) * frame_ms;
        if (now < next_sample) continue;
        next_sample = now + 250;
        MachineCore::GuestInput input;
        if (!session.core().guest_input(input)) session.fail("the core reports no guest input (guest_input is false)");
        mode.observe(input, now);
    }
    const std::string expect = spec.at("expect").string();
    if (expect != "mouse" && expect != "keyboard") throw std::runtime_error(spec.at("expect").path() + ": expected mouse or keyboard");
    if (expect != mode.mode()) session.fail(std::string("Auto touch mode decides ") + mode.mode() + ", not " + expect);
    session.close();
}

// commands (Kairo#119): at the game's prompt, the startup typer types "commands", and the guest then
// shows one of "screens" (its answer) within "answerBy" frames.
void commands(Test &test, const json::Value &spec) {
    Session session(test.launch(), test.folder, test.name);
    type_commands(session, test, spec);
    wait_for_screen(session, screens(spec), test.frames(spec, "answerBy"), "the commands' answer");
    session.picture("commands");
    session.close();
}

// The guest's pointer as the core reports it in details() under "pointer" ("x,y" in frame pixels).
bool pointer(MachineCore &core, int &x, int &y) {
    const std::string details = core.details();
    const std::string key = "pointer=";
    size_t at = 0;
    while (at < details.size()) {
        const size_t end = details.find(';', at);
        const std::string pair = details.substr(at, end == std::string::npos ? std::string::npos : end - at);
        if (pair.compare(0, key.size(), key) == 0) return std::sscanf(pair.c_str() + key.size(), "%d,%d", &x, &y) == 2;
        if (end == std::string::npos) break;
        at = end + 1;
    }
    return false;
}

// directTap (Kairo#121): in the table's mouse program, after "frames" frames, each of "points"
// ({"x", "y"} in frame pixels): mouse_warp there, wait for mouse_warp_done within "warpBy" frames,
// and the guest's pointer is within "tolerance" pixels. At a point with "screens", a click then
// shows one of them (what was there is selected).
void direct_tap(Test &test, const json::Value &spec) {
    Session session(test.launch(), test.folder, test.name);
    run_with_keys(session, spec, test.frames(spec, "frames"));
    const int tolerance = static_cast<int>(spec.at("tolerance").integer());
    const uint64_t warp_by = test.frames(spec, "warpBy");
    for (const json::Value &point : spec.at("points").array()) {
        const int x = static_cast<int>(point.at("x").integer()), y = static_cast<int>(point.at("y").integer());
        session.core().mouse_warp(x, y);
        session.run_until("the warp to (" + std::to_string(x) + "," + std::to_string(y) + ") to finish", warp_by,
            [&] { return session.core().mouse_warp_done(); });
        int px = 0, py = 0;
        if (!pointer(session.core(), px, py)) session.fail("the core reports no pointer=x,y in details()");
        if (std::abs(px - x) > tolerance || std::abs(py - y) > tolerance)
            session.fail("the pointer is at (" + std::to_string(px) + "," + std::to_string(py) + "), not within " +
                std::to_string(tolerance) + " of (" + std::to_string(x) + "," + std::to_string(y) + ")");
        if (point.find("screens")) {
            session.core().mouse_button(1, true);
            session.run(4);
            session.core().mouse_button(1, false);
            wait_for_screen(session, screens(point), test.frames(point, "selectedBy"), "the click to select what is there");
        }
    }
    session.close();
}

// clock (Kairo#122): the table's timing program reaches one of "screens" (its end) in a number of
// frames at each of "values" of the setting "setting"; the second count over the first is "ratio",
// within "margin".
void clock(Test &test, const json::Value &spec) {
    const std::string setting = spec.at("setting").string();
    const std::vector<uint64_t> done = screens(spec);
    const uint64_t limit = test.frames(spec, "doneBy");
    std::vector<uint64_t> counts;
    for (const json::Value &value : spec.at("values").array()) {
        json::Value overrides = json::Value::parse("{\"settings\":{\"" + setting + "\":\"" + value.string() + "\"}}", spec.path());
        Session session(test.launch(&overrides), test.folder, test.name + " at " + setting + "=" + value.string());
        wait_for_screen(session, done, limit, "the timing program to finish");
        counts.push_back(session.frames());
        session.close();
    }
    if (counts.size() != 2) throw std::runtime_error(spec.at("values").path() + ": expected two values");
    const double ratio = static_cast<double>(counts[1]) / static_cast<double>(counts[0]);
    const double want = spec.at("ratio").number(), margin = spec.at("margin").number();
    if (std::abs(ratio - want) > margin)
        throw Failure(test.name + ": the timing program took " + std::to_string(counts[0]) + " then " + std::to_string(counts[1]) +
            " frames, a ratio of " + std::to_string(ratio) + ", not " + std::to_string(want) + " within " + std::to_string(margin));
}

// hardDisk (Kairo#123): the table's hard disk boots; the guest writes a file ("write": its startup
// commands, a spec of commands); the session ends; a new session on the same working copy reads it
// back ("read": commands whose answer screens show the file).
void hard_disk(Test &test, const json::Value &spec) {
    {
        Session session(test.launch(), test.folder, test.name + " writing");
        type_commands(session, test, spec.at("write"));
        session.run(test.frames(spec, "settle"));
        session.close();
    }
    Session session(test.launch(), test.folder, test.name + " reading");
    const json::Value &read = spec.at("read");
    type_commands(session, test, read);
    wait_for_screen(session, screens(read), test.frames(read, "answerBy"), "the file written in the first session");
    session.close();
}

const std::map<std::string, Check> &checks() {
    static const std::map<std::string, Check> all = {
        {"boot", boot},
        {"state", state},
        {"sessions", sessions},
        {"locks", locks},
        {"loading", loading},
        {"stateDisks", state_disks},
        {"guestInput", guest_input},
        {"commands", commands},
        {"directTap", direct_tap},
        {"clock", clock},
        {"hardDisk", hard_disk},
    };
    return all;
}

} // namespace

Check find_check(const std::string &name) {
    auto found = checks().find(name);
    return found == checks().end() ? nullptr : found->second;
}

std::vector<std::string> check_names() {
    std::vector<std::string> names;
    for (const auto &[name, check] : checks()) names.push_back(name);
    return names;
}

} // namespace kairo::host
