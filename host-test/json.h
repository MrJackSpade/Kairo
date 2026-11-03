// A JSON reader for the shared PC boot test's tables (core/test/boot-tests.json, Kairo#115):
// objects, arrays, strings (with \uXXXX escapes as UTF-8), numbers, true, false and null. A
// malformed table, or a value of another type than a test asks for, throws with where it is.

#pragma once

#include <cstdint>
#include <cstdlib>
#include <map>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace kairo::json {

class Value {
public:
    enum class Kind { Null, Bool, Number, String, Array, Object };

    Value() = default;
    static Value parse(const std::string &text, const std::string &source);

    Kind kind() const { return kind_; }
    bool is_null() const { return kind_ == Kind::Null; }
    // Where the value is in its table, for messages ("table.json › tests[2] › media").
    const std::string &path() const { return path_; }

    bool boolean() const { expect(Kind::Bool, "true or false"); return bool_; }
    double number() const { expect(Kind::Number, "a number"); return number_; }
    int64_t integer() const {
        const double value = number();
        if (value != static_cast<double>(static_cast<int64_t>(value))) fail("a whole number");
        return static_cast<int64_t>(value);
    }
    const std::string &string() const { expect(Kind::String, "a string"); return string_; }
    const std::vector<Value> &array() const { expect(Kind::Array, "an array"); return *array_; }
    const std::map<std::string, Value> &object() const { expect(Kind::Object, "an object"); return *object_; }

    bool has(const std::string &key) const { return object().count(key) != 0; }
    // The member [key]; a missing one is a mistake in the table.
    const Value &at(const std::string &key) const {
        auto found = object().find(key);
        if (found == object().end()) throw std::runtime_error(path_ + ": no \"" + key + "\"");
        return found->second;
    }
    // The member [key], or null when the table leaves it out (an optional part).
    const Value *find(const std::string &key) const {
        auto found = object().find(key);
        return found == object().end() ? nullptr : &found->second;
    }

private:
    friend class Parser;
    void expect(Kind kind, const char *what) const { if (kind_ != kind) fail(what); }
    [[noreturn]] void fail(const char *what) const { throw std::runtime_error(path_ + ": expected " + what); }

    Kind kind_ = Kind::Null;
    std::string path_;
    bool bool_ = false;
    double number_ = 0;
    std::string string_;
    std::shared_ptr<std::vector<Value>> array_;
    std::shared_ptr<std::map<std::string, Value>> object_;
};

class Parser {
public:
    Parser(const std::string &text, const std::string &source) : text_(text), source_(source) {}

    Value document() {
        Value value = parse(source_);
        skip();
        if (at_ < text_.size()) error("text after the table");
        return value;
    }

private:
    Value parse(const std::string &path) {
        skip();
        if (at_ >= text_.size()) error("the table ends early");
        Value value;
        value.path_ = path;
        const char c = text_[at_];
        if (c == '{') {
            ++at_;
            value.kind_ = Value::Kind::Object;
            value.object_ = std::make_shared<std::map<std::string, Value>>();
            skip();
            if (peek('}')) { ++at_; return value; }
            while (true) {
                skip();
                const std::string key = string();
                skip();
                take(':');
                if (value.object_->count(key)) error("\"" + key + "\" twice");
                (*value.object_)[key] = parse(path + " › " + key);
                skip();
                if (peek(',')) { ++at_; continue; }
                take('}');
                return value;
            }
        }
        if (c == '[') {
            ++at_;
            value.kind_ = Value::Kind::Array;
            value.array_ = std::make_shared<std::vector<Value>>();
            skip();
            if (peek(']')) { ++at_; return value; }
            while (true) {
                value.array_->push_back(parse(path + "[" + std::to_string(value.array_->size()) + "]"));
                skip();
                if (peek(',')) { ++at_; continue; }
                take(']');
                return value;
            }
        }
        if (c == '"') { value.kind_ = Value::Kind::String; value.string_ = string(); return value; }
        if (word("true")) { value.kind_ = Value::Kind::Bool; value.bool_ = true; return value; }
        if (word("false")) { value.kind_ = Value::Kind::Bool; return value; }
        if (word("null")) return value;
        if (c == '-' || (c >= '0' && c <= '9')) {
            const char *start = text_.c_str() + at_;
            char *end = nullptr;
            value.number_ = std::strtod(start, &end);
            if (end == start) error("a number");
            at_ += static_cast<size_t>(end - start);
            value.kind_ = Value::Kind::Number;
            return value;
        }
        error(std::string("unexpected '") + c + "'");
    }

    std::string string() {
        take('"');
        std::string out;
        while (true) {
            if (at_ >= text_.size()) error("a string that never ends");
            const char c = text_[at_++];
            if (c == '"') return out;
            if (c != '\\') { out += c; continue; }
            if (at_ >= text_.size()) error("a string that never ends");
            const char e = text_[at_++];
            switch (e) {
                case '"': case '\\': case '/': out += e; break;
                case 'b': out += '\b'; break;
                case 'f': out += '\f'; break;
                case 'n': out += '\n'; break;
                case 'r': out += '\r'; break;
                case 't': out += '\t'; break;
                case 'u': utf8(out, hex4()); break;
                default: error(std::string("the escape \\") + e);
            }
        }
    }

    uint32_t hex4() {
        if (at_ + 4 > text_.size()) error("a \\u escape cut short");
        uint32_t code = 0;
        for (int i = 0; i < 4; ++i) {
            const char c = text_[at_++];
            code <<= 4;
            if (c >= '0' && c <= '9') code |= static_cast<uint32_t>(c - '0');
            else if (c >= 'a' && c <= 'f') code |= static_cast<uint32_t>(c - 'a' + 10);
            else if (c >= 'A' && c <= 'F') code |= static_cast<uint32_t>(c - 'A' + 10);
            else error("a \\u escape with a non-hex digit");
        }
        return code;
    }

    void utf8(std::string &out, uint32_t code) {
        // A surrogate pair is one character.
        if (code >= 0xD800 && code <= 0xDBFF) {
            if (at_ + 2 > text_.size() || text_[at_] != '\\' || text_[at_ + 1] != 'u') error("a lone surrogate");
            at_ += 2;
            const uint32_t low = hex4();
            if (low < 0xDC00 || low > 0xDFFF) error("a lone surrogate");
            code = 0x10000 + ((code - 0xD800) << 10) + (low - 0xDC00);
        }
        if (code < 0x80) out += static_cast<char>(code);
        else if (code < 0x800) { out += static_cast<char>(0xC0 | (code >> 6)); out += static_cast<char>(0x80 | (code & 0x3F)); }
        else if (code < 0x10000) {
            out += static_cast<char>(0xE0 | (code >> 12));
            out += static_cast<char>(0x80 | ((code >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (code & 0x3F));
        } else {
            out += static_cast<char>(0xF0 | (code >> 18));
            out += static_cast<char>(0x80 | ((code >> 12) & 0x3F));
            out += static_cast<char>(0x80 | ((code >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (code & 0x3F));
        }
    }

    void skip() { while (at_ < text_.size() && (text_[at_] == ' ' || text_[at_] == '\n' || text_[at_] == '\r' || text_[at_] == '\t')) ++at_; }
    bool peek(char c) const { return at_ < text_.size() && text_[at_] == c; }
    void take(char c) { if (!peek(c)) error(std::string("'") + c + "'"); ++at_; }
    bool word(const char *w) {
        const std::string s(w);
        if (text_.compare(at_, s.size(), s) != 0) return false;
        at_ += s.size();
        return true;
    }
    [[noreturn]] void error(const std::string &what) const {
        size_t line = 1;
        for (size_t i = 0; i < at_ && i < text_.size(); ++i) if (text_[i] == '\n') ++line;
        throw std::runtime_error(source_ + ":" + std::to_string(line) + ": " + what);
    }

    const std::string &text_;
    const std::string &source_;
    size_t at_ = 0;
};

inline Value Value::parse(const std::string &text, const std::string &source) { return Parser(text, source).document(); }

} // namespace kairo::json
