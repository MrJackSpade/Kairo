#include "harness.h"

#include "kairo/state_media.h"

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
    for (uint64_t i = 0; i < count; ++i) {
        if (!core_->run_frame()) fail("the machine stopped: " + core_->error());
        ++frames_;
        signatures_.push_back(core_->screen_signature());
        // Taken every frame, as the audio output does: a core keeps only what one frame makes.
        const uint32_t max = static_cast<uint32_t>(audio_.size() / 2);
        while (core_->read_audio(audio_.data(), max) == max) {}
    }
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
    if (!fs::is_regular_file(source)) throw std::runtime_error(entry->path() + ": no test resource " + source.string());
    // In the platform's own form, as a core reports the paths it holds (media_paths).
    const fs::path copy = (folder / "media" / fs::u8path(name)).make_preferred();
    if (!fs::exists(copy)) {
        fs::create_directories(copy.parent_path());
        fs::copy_file(source, copy);
    }
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
        const fs::path installed = folder / "firmware";
        for (const auto &[name, source] : firmware.object()) {
            const fs::path from = paths.resources / fs::u8path(source.string());
            if (!fs::is_regular_file(from)) throw std::runtime_error(source.path() + ": no test resource " + from.string());
            const fs::path to = installed / fs::u8path(name);
            fs::create_directories(to.parent_path());
            fs::copy_file(from, to, fs::copy_options::overwrite_existing);
        }
        launch.firmware = installed.u8string();
    }
    const fs::path home = folder / "home";
    fs::create_directories(home);
    launch.home = home.u8string();
    for (const json::Value &medium : entry->at("media").array())
        launch.media.push_back(working_copy(medium.string()).u8string());
    if (const json::Value *settings = entry->find("settings"))
        for (const auto &[name, value] : settings->object()) launch.settings[name] = value.string();
    if (overrides) {
        if (const json::Value *media = overrides->find("media")) {
            launch.media.clear();
            for (const json::Value &medium : media->array()) launch.media.push_back(working_copy(medium.string()).u8string());
        }
        if (const json::Value *settings = overrides->find("settings"))
            for (const auto &[name, value] : settings->object()) launch.settings[name] = value.string();
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
}

void Session::load_state(const fs::path &path) {
    // The working copies stay where the state found them: every path it names is where it is.
    const StatePaths paths({});
    if (const int result = load_state_with_media(*core_, path.u8string(), paths))
        fail("load_state returned " + std::to_string(result) + ": " + core_->state_error());
}

} // namespace kairo::host
