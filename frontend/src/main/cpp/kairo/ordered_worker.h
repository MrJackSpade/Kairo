// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once
#include <array>
#include <condition_variable>
#include <cstddef>
#include <cstdint>
#include <exception>
#include <functional>
#include <mutex>
#include <stdexcept>
#include <thread>
#include <utility>

namespace kairo {
// One externally serialized caller, one worker. Both rings have fixed capacity.
// Results are consumed on the caller, never under the queue lock. submit() may
// consume earlier results to free output capacity while awaiting input space.
// The processor must not depend on locks held by the caller. A consumer must
// not reenter this object; a throwing consumer is fatal to its operation stream.
// drain() completes all submitted work. Destruction cancels pending work and
// joins the active operation; callers needing its results must drain first.
template<class Command, class Result, size_t Capacity = 64, bool Track = false>
class OrderedWorker {
    static_assert(Capacity > 0);
public:
    struct Statistics {
        size_t maxCommands = 0, maxResults = 0;
        size_t currentCommands = 0, currentResults = 0;
        uint64_t submitDrains = 0, submitWaits = 0;
    };
    Statistics statistics() {
        std::lock_guard lock(mutex_);
        auto result = statistics_;
        result.currentCommands = commandCount_;
        result.currentResults = resultCount_;
        return result;
    }
    explicit OrderedWorker(std::function<Result(const Command&)> process)
        : process_(std::move(process)) {
        thread_ = std::thread([this] { run(); });
    }
    ~OrderedWorker() {
        {
            std::lock_guard lock(mutex_);
            stopping_ = true;
        }
        changed_.notify_all();
        thread_.join();
    }
    OrderedWorker(const OrderedWorker&) = delete;
    OrderedWorker& operator=(const OrderedWorker&) = delete;

    template<class Consume>
    void submit(Command command, Consume&& consume) {
        for (;;) {
            std::unique_lock lock(mutex_);
            checkError();
            if (commandCount_ < Capacity) {
                commands_[(commandHead_ + commandCount_) % Capacity] = std::move(command);
                ++commandCount_;
                if constexpr (Track) {
                    if (commandCount_ > statistics_.maxCommands)
                        statistics_.maxCommands = commandCount_;
                }
                ++issued_;
                lock.unlock();
                changed_.notify_all();
                return;
            }
            if (resultCount_) {
                if constexpr (Track) ++statistics_.submitDrains;
                consumeOne(lock, consume);
            } else {
                if constexpr (Track) ++statistics_.submitWaits;
                changed_.wait(lock, [this] {
                    return error_ || commandCount_ < Capacity || resultCount_;
                });
            }
        }
    }

    template<class Consume>
    void drain(Consume&& consume) {
        while (consumed_ < issued_) {
            std::unique_lock lock(mutex_);
            checkError();
            if (resultCount_) {
                consumeOne(lock, consume);
            } else {
                changed_.wait(lock, [this] { return error_ || resultCount_; });
            }
        }
        std::lock_guard lock(mutex_);
        checkError();
    }

private:
    void checkError() {
        if (error_) std::rethrow_exception(error_);
    }
    template<class Consume>
    void consumeOne(std::unique_lock<std::mutex>& lock, Consume& consume) {
        Result result = std::move(results_[resultHead_]);
        resultHead_ = (resultHead_ + 1) % Capacity;
        --resultCount_;
        lock.unlock();
        changed_.notify_all();
        consume(std::move(result));
        ++consumed_;
    }
    void run() noexcept {
        try {
            for (;;) {
                std::unique_lock lock(mutex_);
                changed_.wait(lock, [this] {
                    return stopping_ || (commandCount_ && resultCount_ < Capacity);
                });
                if (stopping_) return;
                Command command = std::move(commands_[commandHead_]);
                commandHead_ = (commandHead_ + 1) % Capacity;
                --commandCount_;
                lock.unlock();
                changed_.notify_all();
                Result result = process_(command);
                lock.lock();
                if (stopping_) return;
                results_[(resultHead_ + resultCount_) % Capacity] = std::move(result);
                ++resultCount_;
                if constexpr (Track) {
                    if (resultCount_ > statistics_.maxResults)
                        statistics_.maxResults = resultCount_;
                }
                lock.unlock();
                changed_.notify_all();
            }
        } catch (...) {
            {
                std::lock_guard lock(mutex_);
                error_ = std::current_exception();
            }
            changed_.notify_all();
        }
    }
    std::function<Result(const Command&)> process_;
    std::array<Command, Capacity> commands_{};
    std::array<Result, Capacity> results_{};
    std::mutex mutex_;
    std::condition_variable changed_;
    size_t commandHead_ = 0, commandCount_ = 0;
    size_t resultHead_ = 0, resultCount_ = 0;
    // Only the externally serialized caller accesses these counters.
    uint64_t issued_ = 0, consumed_ = 0;
    bool stopping_ = false;
    std::exception_ptr error_;
    Statistics statistics_;
    std::thread thread_;
};
} // namespace kairo
