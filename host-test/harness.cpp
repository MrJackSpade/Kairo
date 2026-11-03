#include "harness.h"

#include "kairo/state_media.h"

#include <algorithm>
#include <cstdio>
#include <fstream>

namespace kairo::host {

Session::Session(const Launch &launch, fs::path pictures, std::string name)
    : pictures_(std::move(pictures)), name_(std::move(name)) {
    std::string error;
    core_ = create_core(launch, error);
    if (!core_) throw Failure(name_ + ": the core did not start: " + error);
    // Room for any core's frame of audio: a rate up to 96 kHz at 1/30 s, stereo.
    audio_.resize(8192 * 2);
}

Session::~Session() {
    // A session a check left without close() (it failed) still ends as the app ends one.
    if (core_ && !closed_) core_->close();
}

void Session::run(uint64_t count) {
    const uint32_t max = static_cast<uint32_t>(audio_.size() / 2);
    for (uint64_t i = 0; i < count; ++i) {
        if (!core_->run_frame()) fail("the machine stopped: " + core_->error());
        ++frames_;
        signatures_.push_back(core_->screen_signature());
        // Taken every frame, as the audio output does: a core keeps only what one frame makes.
        uint64_t sound = 1469598103934665603ull;
        for (;;) {
            const uint32_t got = core_->read_audio(audio_.data(), max);
            audio_frames_ += got;
            for (uint32_t s = 0; s < got * 2; ++s) sound = (sound ^ static_cast<uint16_t>(audio_[s])) * 1099511628211ull;
            if (got < max) break;
        }
        sounds_.push_back(sound);
    }
}

void Session::drain_audio() {
    const uint32_t max = static_cast<uint32_t>(audio_.size() / 2);
    while (core_->read_audio(audio_.data(), max) == max) {}
}

void Session::press(int code, uint64_t frames) {
    core_->key(code, true);
    run(frames);
    core_->key(code, false);
    run(frames);
}

fs::path Session::picture(const std::string &name) {
    fs::create_directories(pictures_);
    const fs::path file = pictures_ / (name + ".ppm");
    uint32_t width = 0, height = 0;
    const uint8_t *pixels = core_->frame(width, height);
    if (!pixels) return {};
    const uint32_t stride = core_->frame_stride() ? core_->frame_stride() : width * 4;
    const uint32_t repeat = core_->line_repeat();
    const bool bgra = core_->frame_bgra();
    std::ofstream out(file, std::ios::binary);
    out << "P6\n" << width << ' ' << height * repeat << "\n255\n";
    std::vector<char> row(static_cast<size_t>(width) * 3);
    for (uint32_t y = 0; y < height; ++y) {
        const uint8_t *line = pixels + static_cast<size_t>(y) * stride;
        for (uint32_t x = 0; x < width; ++x) {
            const uint8_t *p = line + x * 4;
            row[x * 3] = static_cast<char>(bgra ? p[2] : p[0]);
            row[x * 3 + 1] = static_cast<char>(p[1]);
            row[x * 3 + 2] = static_cast<char>(bgra ? p[0] : p[2]);
        }
        for (uint32_t r = 0; r < repeat; ++r) out.write(row.data(), static_cast<std::streamsize>(row.size()));
    }
    if (!out) throw std::runtime_error("Could not write " + file.string());
    return file;
}

void Session::fail(const std::string &why) {
    const fs::path shown = picture("failure");
    throw Failure(name_ + " at frame " + std::to_string(frames_) + ": " + why +
        (shown.empty() ? "" : " (last picture " + shown.string() + ")"));
}

void Session::close() {
    closed_ = true;
    const std::string error = core_->close();
    if (!error.empty()) throw Failure(name_ + ": closing the machine failed: " + error);
}

fs::path Test::working_copy(const std::string &name) const {
    if (name.empty()) return {};
    const fs::path source = paths.resources / fs::u8path(name);
    // In the platform's own form, as a core reports the paths it holds (media_paths).
    const fs::path copy = (folder / "media" / fs::u8path(name)).make_preferred();
    if (fs::exists(copy)) return copy;
    // A folder whole, for a core whose drive is a folder (KairoDos's C: drive, KairoDos#140).
    if (fs::is_directory(source)) {
        fs::create_directories(copy.parent_path());
        fs::copy(source, copy, fs::copy_options::recursive);
        return copy;
    }
    if (!fs::is_regular_file(source)) throw std::runtime_error(entry->path() + ": no test resource " + source.string());
    fs::create_directories(copy.parent_path());
    // With the files it reads beside it, as the app's working copy has them: a descriptor's track
    // data under its own name (a CUE's ISO, a CCD's IMG), and every file a CUE sheet names.
    std::vector<fs::path> files{source};
    for (const fs::directory_entry &sibling : fs::directory_iterator(source.parent_path()))
        if (sibling.is_regular_file() && sibling.path() != source && sibling.path().stem() == source.stem())
            files.push_back(sibling.path());
    if (source.extension() == ".cue" || source.extension() == ".CUE") {
        std::ifstream sheet(source);
        for (std::string line; std::getline(sheet, line);) {
            const size_t open = line.find('"');
            if (line.find("FILE") == std::string::npos || open == std::string::npos) continue;
            const size_t close = line.find('"', open + 1);
            if (close == std::string::npos) throw std::runtime_error(source.string() + ": a FILE line without its closing quote");
            const fs::path track = source.parent_path() / fs::u8path(line.substr(open + 1, close - open - 1));
            if (!fs::is_regular_file(track)) throw std::runtime_error(source.string() + " names " + track.string() + ", which is not there");
            if (std::find(files.begin(), files.end(), track) == files.end()) files.push_back(track);
        }
    }
    for (const fs::path &file : files) fs::copy_file(file, copy.parent_path() / file.filename());
    return copy;
}

Launch Test::launch(const json::Value *overrides) const {
    Launch launch;
    // A folder of the resources, or the files of the firmware folder the app installs (by their
    // names there, such as "x68000/cgrom.dat") with the resource each is.
    const json::Value &firmware = entry->at("firmware");
    if (firmware.kind() == json::Value::Kind::String) {
        launch.firmware = (paths.resources / fs::u8path(firmware.string())).u8string();
    } else {
        // Empty for a core with firmware of its own (Kairo98's built-in BIOS): the folder is still there.
        const fs::path installed = folder / "firmware";
        fs::create_directories(installed);
        for (const auto &[name, source] : firmware.object()) {
            const fs::path from = paths.resources / fs::u8path(source.string());
            if (!fs::is_regular_file(from)) throw std::runtime_error(source.path() + ": no test resource " + from.string());
            const fs::path to = installed / fs::u8path(name);
            fs::create_directories(to.parent_path());
            fs::copy_file(from, to, fs::copy_options::overwrite_existing);
        }
        launch.firmware = installed.u8string();
    }
    // The core's home, with what the app installs there ("home": {path in the home: source}), such as
    // KairoMSX's openMSX data and the player's firmware: a resource, the app's own files
    // ("app:<path in the repository>"), a file or a folder copied whole, or an empty folder the app
    // makes ("folder:", KairoST's hatari).
    const fs::path &home = paths.home;
    fs::create_directories(home);
    if (const json::Value *installs = entry->find("home")) {
        for (const auto &[name, source] : installs->object()) {
            const std::string from_name = source.string();
            if (from_name == "folder:") {
                fs::create_directories(home / fs::u8path(name));
                continue;
            }
            const fs::path from = from_name.rfind("app:", 0) == 0 ? paths.app / fs::u8path(from_name.substr(4))
                                                                 : paths.resources / fs::u8path(from_name);
            if (!fs::exists(from)) throw std::runtime_error(source.path() + ": no " + from.string());
            const fs::path to = home / fs::u8path(name);
            if (fs::exists(to)) continue;
            fs::create_directories(to.parent_path());
            fs::copy(from, to, fs::copy_options::recursive);
        }
    }
    launch.home = home.u8string();
    // The checks compare runs: of one session against itself, and of one session against the next.
    launch.repeatable = true;
    for (const json::Value &medium : entry->at("media").array())
        launch.media.push_back(working_copy(medium.string()).u8string());
    // A setting may name a file the app installs in the home ("home:<path in the home>", Kairo98's
    // font), as the app passes its path.
    const auto setting = [&home](const json::Value &value) {
        const std::string text = value.string();
        if (text.rfind("home:", 0) != 0) return text;
        const fs::path file = (home / fs::u8path(text.substr(5))).make_preferred();
        if (!fs::exists(file)) throw std::runtime_error(value.path() + ": no " + file.string() + " in the home");
        return file.u8string();
    };
    if (const json::Value *settings = entry->find("settings"))
        for (const auto &[name, value] : settings->object()) launch.settings[name] = setting(value);
    if (overrides) {
        if (const json::Value *media = overrides->find("media")) {
            launch.media.clear();
            for (const json::Value &medium : media->array()) launch.media.push_back(working_copy(medium.string()).u8string());
        }
        if (const json::Value *settings = overrides->find("settings"))
            for (const auto &[name, value] : settings->object()) launch.settings[name] = setting(value);
    }
    return launch;
}

uint64_t Test::frames(const json::Value &spec, const std::string &key) const {
    const int64_t value = spec.at(key).integer();
    if (value <= 0) throw std::runtime_error(spec.at(key).path() + ": expected a number of frames above 0");
    return static_cast<uint64_t>(value);
}

void Session::save_state(const fs::path &path, const std::string &what) {
    fs::create_directories(path);
    if (const int result = save_state_with_media(*core_, path.u8string()))
        fail(what + " returned " + std::to_string(result) + ": " + core_->state_error());
    drain_audio();
}

void Session::load_state(const fs::path &path) {
    // The working copies stay where the state found them: every path it names is where it is.
    const StatePaths paths({});
    if (const int result = load_state_with_media(*core_, path.u8string(), paths))
        fail("load_state returned " + std::to_string(result) + ": " + core_->state_error());
    drain_audio();
}

} // namespace kairo::host
