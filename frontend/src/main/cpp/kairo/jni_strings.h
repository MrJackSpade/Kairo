#pragma once

#include <jni.h>

#include <string>
#include <vector>

namespace kairo {

// [value] as UTF-8. JNI's GetStringUTFChars gives modified UTF-8, which writes a character
// outside the Basic Multilingual Plane as two three-byte surrogates: a path holding one would
// name a file that is not there. This encodes the UTF-16 code points instead.
inline std::string utf8(JNIEnv *env, jstring value) {
    if (!value) return "";
    const jsize length = env->GetStringLength(value);
    std::u16string units(static_cast<size_t>(length), u'\0');
    env->GetStringRegion(value, 0, length, reinterpret_cast<jchar *>(units.data()));
    std::string result;
    result.reserve(units.size());
    for (size_t i = 0; i < units.size(); ++i) {
        char32_t point = units[i];
        if (point >= 0xD800 && point <= 0xDBFF && i + 1 < units.size() &&
            units[i + 1] >= 0xDC00 && units[i + 1] <= 0xDFFF) {
            point = 0x10000 + ((point - 0xD800) << 10) + (units[++i] - 0xDC00);
        }
        if (point < 0x80) {
            result += static_cast<char>(point);
        } else if (point < 0x800) {
            result += static_cast<char>(0xC0 | (point >> 6));
            result += static_cast<char>(0x80 | (point & 0x3F));
        } else if (point < 0x10000) {
            result += static_cast<char>(0xE0 | (point >> 12));
            result += static_cast<char>(0x80 | ((point >> 6) & 0x3F));
            result += static_cast<char>(0x80 | (point & 0x3F));
        } else {
            result += static_cast<char>(0xF0 | (point >> 18));
            result += static_cast<char>(0x80 | ((point >> 12) & 0x3F));
            result += static_cast<char>(0x80 | ((point >> 6) & 0x3F));
            result += static_cast<char>(0x80 | (point & 0x3F));
        }
    }
    return result;
}

// Each string of [array] as UTF-8 ([utf8]).
inline std::vector<std::string> utf8_strings(JNIEnv *env, jobjectArray array) {
    std::vector<std::string> result;
    const jsize count = array ? env->GetArrayLength(array) : 0;
    for (jsize i = 0; i < count; ++i) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        result.push_back(utf8(env, item));
        env->DeleteLocalRef(item);
    }
    return result;
}

} // namespace kairo
