// The shared PC boot test (Kairo#115): runs an app's MachineCore from its table.
//
//   <product> <table.json> <resources dir> <work dir> [test name ...]
//
// The table (the app's core/test/boot-tests.json) lists tests: each a game as the app launches it
// ("firmware", "media" and "settings", the media and firmware relative to the resources dir: the
// app's unpacked UI test resources) and the checks it runs, by name, each with its spec
// (checks.cpp). Every test runs in this one process, one after another, as the app runs one game
// after another. Each writes under <work dir>/<test name>: working copies of its media, its states
// and its pictures. The program exits with 0 only when every check of every test passed.

#include <cstdio>
#include <fstream>
#include <iterator>
#include <set>
#include <sstream>

#include "harness.h"

using namespace kairo::host;

int main(int argc, char **argv) {
    if (argc < 4) {
        std::fprintf(stderr, "usage: %s <table.json> <resources dir> <work dir> [test name ...]\n", argv[0]);
        return 2;
    }
    try {
        const fs::path table_file = fs::u8path(argv[1]);
        std::ifstream in(table_file, std::ios::binary);
        if (!in) throw std::runtime_error("Cannot read " + table_file.string());
        const std::string text((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
        const kairo::json::Value table = kairo::json::Value::parse(text, table_file.filename().string());

        Paths paths{fs::u8path(argv[2]), fs::u8path(argv[3])};
        if (!fs::is_directory(paths.resources)) throw std::runtime_error("No resources at " + paths.resources.string());
        std::set<std::string> wanted;
        for (int i = 4; i < argc; ++i) wanted.insert(argv[i]);

        int failed = 0, passed = 0;
        std::set<std::string> found;
        for (const kairo::json::Value &entry : table.at("tests").array()) {
            Test test;
            test.name = entry.at("name").string();
            if (!wanted.empty() && !wanted.count(test.name)) continue;
            found.insert(test.name);
            test.entry = &entry;
            test.paths = paths;
            test.folder = paths.work / fs::u8path(test.name);
            fs::remove_all(test.folder);
            fs::create_directories(test.folder);
            for (const auto &[name, spec] : entry.at("checks").object()) {
                const Check check = find_check(name);
                if (!check) {
                    std::string known;
                    for (const std::string &n : check_names()) known += " " + n;
                    throw std::runtime_error(spec.path() + ": no check named " + name + " (there are:" + known + ")");
                }
                std::printf("RUN  %s › %s\n", test.name.c_str(), name.c_str());
                std::fflush(stdout);
                try {
                    check(test, spec);
                    std::printf("PASS %s › %s\n", test.name.c_str(), name.c_str());
                    ++passed;
                } catch (const Failure &failure) {
                    std::printf("FAIL %s › %s: %s\n", test.name.c_str(), name.c_str(), failure.what());
                    ++failed;
                }
                std::fflush(stdout);
            }
        }
        for (const std::string &name : wanted)
            if (!found.count(name)) throw std::runtime_error("The table has no test named " + name);
        std::printf("%d passed, %d failed\n", passed, failed);
        return failed == 0 && passed > 0 ? 0 : 1;
    } catch (const std::exception &error) {
        // A broken table, a missing resource, or an error the harness itself hit: not a check result.
        std::fprintf(stderr, "ERROR: %s\n", error.what());
        return 2;
    }
}
