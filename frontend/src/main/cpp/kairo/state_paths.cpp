#include "kairo/state_paths.h"

#include <stdexcept>
#include <utility>

namespace {
const kairo::StatePaths *loading = nullptr;
}

namespace kairo {

StatePaths::StatePaths(std::map<std::string, std::string> moved) : moved_(std::move(moved)) {
    if (loading) throw std::logic_error("A save state is already loading");
    loading = this;
}

StatePaths::~StatePaths() { loading = nullptr; }

const char *StatePaths::resolve(const char *recorded) const {
    auto found = moved_.find(recorded);
    return found == moved_.end() ? recorded : found->second.c_str();
}

} // namespace kairo

extern "C" const char *kairo_state_path(const char *recorded) {
    if (!loading || !recorded) return recorded;
    return loading->resolve(recorded);
}
