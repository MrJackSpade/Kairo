/*
 * A CHD's disc image as a std::istream, for a C++ core that reads its disc images through streams
 * (kairo_chd.h). Every stream on one CHD shares one open reader, kept while a stream or a Holder
 * of it lives, so a core that opens its image for each read decodes from one handle.
 */
#ifndef KAIRO_CHD_STREAM_H
#define KAIRO_CHD_STREAM_H

#include <istream>
#include <map>
#include <memory>
#include <mutex>
#include <streambuf>
#include <string>
#include <vector>

#include "kairo_chd.h"

namespace kairo {

/** One open CHD, shared by everything reading it. */
class ChdSource {
public:
    explicit ChdSource(kairo_chd *chd) : chd(chd) {}
    ~ChdSource() { kairo_chd_close(chd); }
    ChdSource(const ChdSource &) = delete;
    ChdSource &operator=(const ChdSource &) = delete;

    kairo_chd *get() const { return chd; }

    /** The reader of the CHD at [path], opened the first time; null with the reason in [error] when it cannot be read. */
    static std::shared_ptr<ChdSource> Of(const std::string &path, std::string &error) {
        static std::mutex lock;
        static std::map<std::string, std::weak_ptr<ChdSource>> open;
        std::lock_guard<std::mutex> guard(lock);
        if (auto held = open[path].lock()) return held;
        char text[256] = "";
        kairo_chd *chd = kairo_chd_open(path.c_str(), text, sizeof(text));
        if (!chd) {
            error = text;
            return nullptr;
        }
        auto source = std::make_shared<ChdSource>(chd);
        open[path] = source;
        return source;
    }

private:
    kairo_chd *chd;
};

/** Reads a CHD's image through a buffer of whole sectors' worth of bytes, seeking anywhere. */
class ChdStreamBuffer : public std::streambuf {
public:
    explicit ChdStreamBuffer(std::shared_ptr<ChdSource> source) : source(std::move(source)), buffer(64 * 1024) {
        setg(buffer.data(), buffer.data(), buffer.data());
    }

protected:
    int_type underflow() override {
        if (gptr() < egptr()) return traits_type::to_int_type(*gptr());
        start += static_cast<uint64_t>(egptr() - eback());
        size_t read = kairo_chd_read(source->get(), start, buffer.data(), buffer.size());
        if (read == static_cast<size_t>(-1) || read == 0) {
            setg(buffer.data(), buffer.data(), buffer.data());
            return traits_type::eof();
        }
        setg(buffer.data(), buffer.data(), buffer.data() + read);
        return traits_type::to_int_type(*gptr());
    }

    pos_type seekoff(off_type offset, std::ios_base::seekdir direction, std::ios_base::openmode) override {
        uint64_t here = start + static_cast<uint64_t>(gptr() - eback());
        int64_t base = direction == std::ios_base::beg ? 0 : direction == std::ios_base::cur
            ? static_cast<int64_t>(here) : static_cast<int64_t>(kairo_chd_size(source->get()));
        int64_t target = base + offset;
        if (target < 0) return pos_type(off_type(-1));
        return seekpos(pos_type(target), std::ios_base::in);
    }

    pos_type seekpos(pos_type position, std::ios_base::openmode) override {
        uint64_t target = static_cast<uint64_t>(static_cast<off_type>(position));
        uint64_t end = start + static_cast<uint64_t>(egptr() - eback());
        if (target >= start && target < end) {
            setg(eback(), eback() + (target - start), egptr());
        } else {
            start = target;
            setg(buffer.data(), buffer.data(), buffer.data());
        }
        return position;
    }

private:
    std::shared_ptr<ChdSource> source;
    std::vector<char> buffer;
    /** The image offset of the buffer's first byte. */
    uint64_t start = 0;
};

/** An istream over a CHD's image. */
class ChdStream : public std::istream {
public:
    explicit ChdStream(std::shared_ptr<ChdSource> source) : std::istream(nullptr), buffer(std::move(source)) {
        rdbuf(&buffer);
    }

private:
    ChdStreamBuffer buffer;
};

}  // namespace kairo

#endif
