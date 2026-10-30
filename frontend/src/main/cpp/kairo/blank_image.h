// A blank disk image (kairo::create_blank_disk) for a machine whose images are its sectors and
// nothing else: zero bytes of the drive's size, unformatted until the guest formats it, as a new
// disk was. A core whose images carry a header writes them with its emulator's own tool instead.
#pragma once

#include <cstdint>
#include <cstdio>
#include <string>

namespace kairo {

// Writes size zero bytes at path; "" when written, else why not.
inline std::string write_zeroed_image(const std::string &path, uint64_t size) {
    FILE *file = std::fopen(path.c_str(), "wb");
    if (!file) return "Could not create the blank disk " + path;
    static const unsigned char block[64 * 1024] = {};
    for (uint64_t left = size; left > 0;) {
        const size_t count = left < sizeof block ? size_t(left) : sizeof block;
        if (std::fwrite(block, 1, count, file) != count) {
            std::fclose(file);
            return "Could not write the blank disk " + path;
        }
        left -= count;
    }
    if (std::fclose(file) != 0) return "Could not finish the blank disk " + path;
    return "";
}

} // namespace kairo
