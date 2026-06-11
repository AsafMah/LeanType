// SPDX-License-Identifier: Apache-2.0
//
// Fixture loader for native gesture-replay tests (issue #78, deliverable 2).
//
// Parses the TraceRecorder JSON schema (version 1) into plain C++ structs whose
// raw int arrays can be forwarded to the latinime recognizer APIs once the JNI
// blocker is lifted (see gesture_replay_test.cpp).
//
// No external dependencies: header-only, C++17, standard library only.

#pragma once

#include <cctype>
#include <fstream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

namespace latinime {
namespace replay {

// ---- Data model -------------------------------------------------------------

struct KeyboardMeta {
    int         width      = 0;
    int         height     = 0;
    std::string mainLayout;
    std::string locale;
};

struct PointerSample {
    int id = 0;
    int x  = 0;
    int y  = 0;
    int t  = 0;
};

struct TraceFixture {
    int           version     = 0;
    long long     createdAt   = 0;
    KeyboardMeta  keyboard;
    std::string   committedWord;
    std::vector<PointerSample> pointers;

    // Convenience accessors — return plain int vectors suitable for
    // Dictionary::getSuggestions / Suggest::getSuggestions once those are
    // reachable in the host build (see DISABLED_ tests).
    std::vector<int> xCoordinates() const {
        std::vector<int> v(pointers.size());
        for (std::size_t i = 0; i < pointers.size(); ++i) v[i] = pointers[i].x;
        return v;
    }
    std::vector<int> yCoordinates() const {
        std::vector<int> v(pointers.size());
        for (std::size_t i = 0; i < pointers.size(); ++i) v[i] = pointers[i].y;
        return v;
    }
    std::vector<int> times() const {
        std::vector<int> v(pointers.size());
        for (std::size_t i = 0; i < pointers.size(); ++i) v[i] = pointers[i].t;
        return v;
    }
    std::vector<int> pointerIds() const {
        std::vector<int> v(pointers.size());
        for (std::size_t i = 0; i < pointers.size(); ++i) v[i] = pointers[i].id;
        return v;
    }
    int inputSize() const { return static_cast<int>(pointers.size()); }
};

// ---- Minimal JSON parser ----------------------------------------------------
// Only handles the exact TraceRecorder schema; not a general-purpose parser.

namespace detail {

struct Parser {
    const char *p;
    const char *end;

    explicit Parser(const std::string &s)
        : p(s.data()), end(s.data() + s.size()) {}

    void skipWs() {
        while (p < end && std::isspace(static_cast<unsigned char>(*p))) ++p;
    }

    bool peek(char c) { skipWs(); return p < end && *p == c; }

    bool consume(char c) {
        if (!peek(c)) return false;
        ++p;
        return true;
    }

    void expect(char c) {
        if (!consume(c)) {
            throw std::runtime_error(
                std::string("TraceFixture JSON: expected '") + c +
                "', got '" + (p < end ? std::string(1, *p) : "EOF") + "'");
        }
    }

    std::string parseString() {
        expect('"');
        std::string out;
        while (p < end && *p != '"') {
            if (*p == '\\') {
                ++p;
                if (p < end) {
                    switch (*p) {
                        case '"':  out += '"';  break;
                        case '\\': out += '\\'; break;
                        case 'n':  out += '\n'; break;
                        case 'r':  out += '\r'; break;
                        case 't':  out += '\t'; break;
                        default:   out += *p;   break;
                    }
                    ++p;
                }
            } else {
                out += *p++;
            }
        }
        expect('"');
        return out;
    }

    long long parseInt() {
        skipWs();
        bool neg = false;
        if (p < end && *p == '-') { neg = true; ++p; }
        if (p >= end || !std::isdigit(static_cast<unsigned char>(*p)))
            throw std::runtime_error("TraceFixture JSON: expected digit");
        long long v = 0;
        while (p < end && std::isdigit(static_cast<unsigned char>(*p)))
            v = v * 10 + (*p++ - '0');
        return neg ? -v : v;
    }

    // parseObject: for each key, calls f(key) and f must consume the value.
    template<typename F>
    void parseObject(F f) {
        expect('{');
        skipWs();
        if (peek('}')) { ++p; return; }
        do {
            skipWs();
            std::string key = parseString();
            skipWs();
            expect(':');
            f(key);
            skipWs();
        } while (consume(','));
        expect('}');
    }

    // parseArray: calls f() for each element; f must consume the element.
    template<typename F>
    void parseArray(F f) {
        expect('[');
        skipWs();
        if (peek(']')) { ++p; return; }
        do {
            skipWs();
            f();
            skipWs();
        } while (consume(','));
        expect(']');
    }

    void skipValue() {
        skipWs();
        if (peek('"')) { parseString(); return; }
        if (peek('{')) {
            parseObject([this](const std::string &) { skipValue(); });
            return;
        }
        if (peek('[')) {
            parseArray([this]() { skipValue(); });
            return;
        }
        // number, true, false, null
        while (p < end && *p != ',' && *p != '}' && *p != ']' &&
               !std::isspace(static_cast<unsigned char>(*p)))
            ++p;
    }
};

} // namespace detail

// Parse a JSON string conforming to the TraceRecorder schema (version 1).
// Throws std::runtime_error on malformed input.
inline TraceFixture parseFixture(const std::string &json) {
    detail::Parser par(json);
    TraceFixture fix;

    par.parseObject([&](const std::string &key) {
        if (key == "version") {
            fix.version = static_cast<int>(par.parseInt());
        } else if (key == "createdAt") {
            fix.createdAt = par.parseInt();
        } else if (key == "committedWord") {
            fix.committedWord = par.parseString();
        } else if (key == "keyboard") {
            par.parseObject([&](const std::string &kk) {
                if      (kk == "width")      fix.keyboard.width      = static_cast<int>(par.parseInt());
                else if (kk == "height")     fix.keyboard.height     = static_cast<int>(par.parseInt());
                else if (kk == "mainLayout") fix.keyboard.mainLayout = par.parseString();
                else if (kk == "locale")     fix.keyboard.locale     = par.parseString();
                else                         par.skipValue();
            });
        } else if (key == "pointers") {
            par.parseArray([&]() {
                PointerSample ps;
                par.parseObject([&](const std::string &pk) {
                    if      (pk == "id") ps.id = static_cast<int>(par.parseInt());
                    else if (pk == "x")  ps.x  = static_cast<int>(par.parseInt());
                    else if (pk == "y")  ps.y  = static_cast<int>(par.parseInt());
                    else if (pk == "t")  ps.t  = static_cast<int>(par.parseInt());
                    else                 par.skipValue();
                });
                fix.pointers.push_back(ps);
            });
        } else {
            par.skipValue();
        }
    });

    return fix;
}

// Load and parse a fixture from a JSON file.
// Throws std::runtime_error if the file cannot be opened or is malformed.
inline TraceFixture loadFixture(const std::string &path) {
    std::ifstream f(path);
    if (!f) throw std::runtime_error("Cannot open fixture file: " + path);
    std::ostringstream ss;
    ss << f.rdbuf();
    return parseFixture(ss.str());
}

} // namespace replay
} // namespace latinime
