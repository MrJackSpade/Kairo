#include "midi_out.h"

#include <condition_variable>
#include <cstring>
#include <mutex>
#include <vector>

namespace kairo::midi_out {
namespace {

// The queue: messages, each as its length (4 bytes) and its bytes, in a ring.
constexpr size_t kCapacity = kQueueBytes;

std::mutex lock;
std::condition_variable ready;
std::vector<uint8_t> ring(kCapacity);
size_t head = 0;  // where the next message is read
size_t used = 0;  // bytes queued
bool is_open = false;
bool overflowed = false;

void put(const uint8_t *bytes, size_t count) {
    size_t at = (head + used) % kCapacity;
    const size_t first = count < kCapacity - at ? count : kCapacity - at;
    std::memcpy(&ring[at], bytes, first);
    std::memcpy(&ring[0], bytes + first, count - first);
    used += count;
}

void get(uint8_t *bytes, size_t count) {
    const size_t first = count < kCapacity - head ? count : kCapacity - head;
    std::memcpy(bytes, &ring[head], first);
    std::memcpy(bytes + first, &ring[0], count - first);
    head = (head + count) % kCapacity;
    used -= count;
}

}  // namespace

void send(const uint8_t *message, size_t length) {
    if (length == 0) return;
    {
        std::lock_guard<std::mutex> guard(lock);
        if (!is_open) return;
        if (used + sizeof(uint32_t) + length > kCapacity) {
            // The port is behind by the whole queue: the message is lost, and the app is told.
            overflowed = true;
        } else {
            const auto size = static_cast<uint32_t>(length);
            put(reinterpret_cast<const uint8_t *>(&size), sizeof(size));
            put(message, length);
        }
    }
    ready.notify_one();
}

void open() {
    std::lock_guard<std::mutex> guard(lock);
    head = used = 0;
    overflowed = false;
    is_open = true;
}

void close() {
    {
        std::lock_guard<std::mutex> guard(lock);
        is_open = false;
        head = used = 0;
    }
    ready.notify_all();
}

int take(uint8_t *buffer) {
    std::unique_lock<std::mutex> guard(lock);
    ready.wait(guard, [] { return !is_open || overflowed || used > 0; });
    if (!is_open) return kClosed;
    if (overflowed) return kOverflowed;
    uint32_t size = 0;
    get(reinterpret_cast<uint8_t *>(&size), sizeof(size));
    get(buffer, size);
    return static_cast<int>(size);
}

}  // namespace kairo::midi_out
