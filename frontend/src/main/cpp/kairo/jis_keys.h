// The JIS keyboard's main block as the X68000 and the FM Towns number it (JisMainBlock.kt), for a
// core typing startup commands (MachineCore::typed_key): the key a character is on, and whether
// Shift goes with it. Backslash is the ¥ key, as a Japanese machine shows it.
#pragma once

#include <cstring>

namespace kairo::jis {

constexpr int kReturn = 0x1D;
constexpr int kSpace = 0x35;

// The key that types c, and whether Shift goes with it; false for a character the block has no key for.
inline bool typed_key(char c, int &code, bool &shift) {
    static const char *const rows[] = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    static const int bases[] = {0x11, 0x1E, 0x2A};
    // Unshifted and shifted symbols by code; ' ' where the key has none.
    struct Symbol { int code; char plain, shifted; };
    static const Symbol symbols[] = {
        {0x02, '1', '!'}, {0x03, '2', '"'}, {0x04, '3', '#'}, {0x05, '4', '$'}, {0x06, '5', '%'},
        {0x07, '6', '&'}, {0x08, '7', '\''}, {0x09, '8', '('}, {0x0A, '9', ')'}, {0x0B, '0', ' '},
        {0x0C, '-', '='}, {0x0D, '^', '~'}, {0x0E, '\\', '|'}, {0x1B, '@', '`'}, {0x1C, '[', '{'},
        {0x27, ';', '+'}, {0x28, ':', '*'}, {0x29, ']', '}'}, {0x31, ',', '<'}, {0x32, '.', '>'},
        {0x33, '/', '?'}, {0x34, '_', ' '}, {kSpace, ' ', ' '},
    };
    const char lower = c >= 'A' && c <= 'Z' ? char(c - 'A' + 'a') : c;
    for (int row = 0; row < 3; ++row)
        if (const char *at = std::strchr(rows[row], lower); at && lower != '\0') {
            code = bases[row] + int(at - rows[row]);
            shift = lower != c;
            return true;
        }
    for (const Symbol &symbol : symbols) {
        if (c == symbol.plain) {
            code = symbol.code;
            shift = false;
            return true;
        }
        if (c == symbol.shifted && c != ' ') {
            code = symbol.code;
            shift = true;
            return true;
        }
    }
    return false;
}

} // namespace kairo::jis
