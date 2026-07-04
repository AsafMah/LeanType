// SPDX-License-Identifier: Apache-2.0
//
// Native gesture-replay harness — issue #78, deliverable 2.
//
// =============================================================================
// ENABLED tests  (TraceFixtureParserTest.*)
//   Validate the JSON fixture loader end-to-end: parse an embedded literal and,
//   when FIXTURE_DIR is defined, a file from the on-disk fixture directory.
//   These always pass in ctest without any runtime assets.
//
// DISABLED tests  (DISABLED_GestureReplayTest.*)
//   Compile-checked stubs that would feed a loaded trace through the latinime
//   gesture recognizer.  Disabled because the open-source tree does NOT contain
//   a gesture suggest policy implementation: it only has GestureSuggestPolicyFactory,
//   whose factory method is null in the host build. Dictionary::getSuggestions(...)
//   with IS_GESTURE therefore constructs Suggest with a null policy and would crash
//   at TRAVERSAL->getMaxSpatialDistance().
//
//   What is proven here:
//     a) TraceRecorder-style fixtures parse into the exact x/y/time/pointer arrays
//        expected by Dictionary::getSuggestions.
//     b) ProximityInfo can now be constructed on the host from raw arrays — no JNIEnv
//        required — and QWERTY key lookup works.
//
//   Next concrete step to get real replay assertions:
//     provide an open/host-buildable GestureSuggestPolicy implementation (e.g. the
//     future NLnet recognizer) or a test double with the same policy interface. Once
//     GestureSuggestPolicyFactory returns a real policy, this scaffold can wire the
//     fixture + ProximityInfo + Dictionary together and assert the suggestion.
// =============================================================================

#include <gtest/gtest.h>
#include <string>
#include <vector>

#include "replay/trace_fixture.h"
#include "suggest/core/layout/proximity_info.h"

namespace latinime {
namespace replay {
namespace {

// ---- Embedded fixture (TraceRecorder schema v1, word "hello") ---------------
//
// Recorded on a 1080×310 px QWERTY keyboard (en-US).
// Key-centre estimates (px):
//   h ≈ (648, 185)   e ≈ (270, 100)   l ≈ (864, 185)   o ≈ (918, 100)
//
static constexpr const char kHelloQwertyJson[] =
    R"json({"version":1,"createdAt":1720000000000,)json"
    R"json("keyboard":{"width":1080,"height":310,)json"
    R"json("mainLayout":"qwerty","locale":"en-US"},)json"
    R"json("committedWord":"hello",)json"
    R"json("pointers":[)json"
    R"json({"id":0,"x":648,"y":185,"t":0},)json"
    R"json({"id":0,"x":590,"y":168,"t":48},)json"
    R"json({"id":0,"x":450,"y":135,"t":98},)json"
    R"json({"id":0,"x":340,"y":110,"t":148},)json"
    R"json({"id":0,"x":270,"y":100,"t":200},)json"
    R"json({"id":0,"x":370,"y":120,"t":258},)json"
    R"json({"id":0,"x":540,"y":158,"t":308},)json"
    R"json({"id":0,"x":660,"y":185,"t":358},)json"
    R"json({"id":0,"x":756,"y":185,"t":408},)json"
    R"json({"id":0,"x":810,"y":185,"t":450},)json"
    R"json({"id":0,"x":864,"y":185,"t":500},)json"
    R"json({"id":0,"x":864,"y":184,"t":552},)json"
    R"json({"id":0,"x":891,"y":143,"t":600},)json"
    R"json({"id":0,"x":918,"y":100,"t":648})json"
    R"json(]})json";

// =============================================================================
// TraceFixtureParserTest — enabled, runs in ctest
// =============================================================================

TEST(TraceFixtureParserTest, ParsesVersionAndMetadata) {
    const TraceFixture fix = parseFixture(kHelloQwertyJson);
    EXPECT_EQ(1, fix.version);
    EXPECT_EQ(1720000000000LL, fix.createdAt);
    EXPECT_EQ("hello", fix.committedWord);
}

TEST(TraceFixtureParserTest, ParsesKeyboardGeometry) {
    const TraceFixture fix = parseFixture(kHelloQwertyJson);
    EXPECT_EQ(1080, fix.keyboard.width);
    EXPECT_EQ(310,  fix.keyboard.height);
    EXPECT_EQ("qwerty",  fix.keyboard.mainLayout);
    EXPECT_EQ("en-US",   fix.keyboard.locale);
}

TEST(TraceFixtureParserTest, ParsesPointerCount) {
    const TraceFixture fix = parseFixture(kHelloQwertyJson);
    EXPECT_EQ(14, fix.inputSize());
    ASSERT_EQ(14u, fix.pointers.size());
}

TEST(TraceFixtureParserTest, ParsesFirstAndLastPointerSample) {
    const TraceFixture fix = parseFixture(kHelloQwertyJson);

    // First sample — starts over 'h'
    EXPECT_EQ(0,   fix.pointers.front().id);
    EXPECT_EQ(648, fix.pointers.front().x);
    EXPECT_EQ(185, fix.pointers.front().y);
    EXPECT_EQ(0,   fix.pointers.front().t);

    // Last sample — ends over 'o'
    EXPECT_EQ(0,   fix.pointers.back().id);
    EXPECT_EQ(918, fix.pointers.back().x);
    EXPECT_EQ(100, fix.pointers.back().y);
    EXPECT_EQ(648, fix.pointers.back().t);
}

TEST(TraceFixtureParserTest, AccessorArraysMatchPointers) {
    const TraceFixture fix = parseFixture(kHelloQwertyJson);
    const auto xs  = fix.xCoordinates();
    const auto ys  = fix.yCoordinates();
    const auto ts  = fix.times();
    const auto ids = fix.pointerIds();

    ASSERT_EQ(fix.pointers.size(), xs.size());
    for (std::size_t i = 0; i < fix.pointers.size(); ++i) {
        EXPECT_EQ(fix.pointers[i].x,  xs[i])  << "xs mismatch at " << i;
        EXPECT_EQ(fix.pointers[i].y,  ys[i])  << "ys mismatch at " << i;
        EXPECT_EQ(fix.pointers[i].t,  ts[i])  << "ts mismatch at " << i;
        EXPECT_EQ(fix.pointers[i].id, ids[i]) << "ids mismatch at " << i;
    }
}

TEST(TraceFixtureParserTest, TimestampsAreMonotonicallyNonDecreasing) {
    const TraceFixture fix = parseFixture(kHelloQwertyJson);
    for (std::size_t i = 1; i < fix.pointers.size(); ++i) {
        EXPECT_GE(fix.pointers[i].t, fix.pointers[i - 1].t)
            << "timestamp regression at index " << i;
    }
}

TEST(TraceFixtureParserTest, AllPointersWithinKeyboardBounds) {
    const TraceFixture fix = parseFixture(kHelloQwertyJson);
    for (std::size_t i = 0; i < fix.pointers.size(); ++i) {
        EXPECT_GE(fix.pointers[i].x, 0)                  << "x < 0 at " << i;
        EXPECT_LE(fix.pointers[i].x, fix.keyboard.width)  << "x > width at " << i;
        EXPECT_GE(fix.pointers[i].y, 0)                  << "y < 0 at " << i;
        EXPECT_LE(fix.pointers[i].y, fix.keyboard.height) << "y > height at " << i;
    }
}

TEST(TraceFixtureParserTest, ParsesJsonEscapedString) {
    // Verify the parser handles escaped double-quotes and backslashes in strings.
    const std::string json =
        R"({"version":1,"createdAt":0,)"
        R"("keyboard":{"width":100,"height":100,"mainLayout":"a\"b","locale":"c\\d"},)"
        R"("committedWord":"w\"x","pointers":[]})";
    const TraceFixture fix = parseFixture(json);
    EXPECT_EQ("a\"b", fix.keyboard.mainLayout);
    EXPECT_EQ("c\\d", fix.keyboard.locale);
    EXPECT_EQ("w\"x", fix.committedWord);
}

TEST(TraceFixtureParserTest, ParsesEmptyPointerArray) {
    const std::string json =
        R"({"version":1,"createdAt":0,)"
        R"("keyboard":{"width":0,"height":0,"mainLayout":"","locale":""},)"
        R"("committedWord":"","pointers":[]})";
    const TraceFixture fix = parseFixture(json);
    EXPECT_EQ(0, fix.inputSize());
    EXPECT_TRUE(fix.pointers.empty());
}

TEST(TraceFixtureParserTest, ToleratesUnknownTopLevelKeys) {
    // Ensures forward-compatibility: extra keys are silently skipped.
    const std::string json =
        R"({"version":1,"createdAt":0,"newField":{"nested":42},)"
        R"("keyboard":{"width":0,"height":0,"mainLayout":"","locale":""},)"
        R"("committedWord":"hi","pointers":[]})";
    EXPECT_NO_THROW({
        const TraceFixture fix = parseFixture(json);
        EXPECT_EQ("hi", fix.committedWord);
    });
}

// File-based test: only compiled when FIXTURE_DIR is passed by CMake.
#if defined(FIXTURE_DIR)
TEST(TraceFixtureParserTest, LoadsHelloQwertyFromFile) {
    const std::string path = std::string(FIXTURE_DIR) + "/hello_qwerty.json";
    TraceFixture fix;
    ASSERT_NO_THROW({ fix = loadFixture(path); }) << "path: " << path;
    EXPECT_EQ(1,       fix.version);
    EXPECT_EQ("hello", fix.committedWord);
    EXPECT_EQ(1080,    fix.keyboard.width);
    EXPECT_GT(fix.inputSize(), 0);
}
#endif

static std::vector<int> buildEmptyProximityChars(const int gridWidth, const int gridHeight) {
    return std::vector<int>(gridWidth * gridHeight * MAX_PROXIMITY_CHARS_SIZE, NOT_A_CODE_POINT);
}

TEST(GestureReplayHostSeamTest, BuildsProximityInfoWithoutJNI) {
    // Minimal QWERTY row geometry sufficient to prove the replay harness can construct
    // ProximityInfo from raw host arrays. The full recognizer assertion is still blocked on
    // a binary .dict asset; this removes the JNIEnv blocker.
    constexpr int keyboardWidth = 1080;
    constexpr int keyboardHeight = 310;
    constexpr int gridWidth = 10;
    constexpr int gridHeight = 5;
    constexpr int keyWidth = 108;
    constexpr int keyHeight = 90;
    const char *letters = "qwertyuiopasdfghjklzxcvbnm";
    constexpr int keyCount = 26;

    int xs[keyCount];
    int ys[keyCount];
    int widths[keyCount];
    int heights[keyCount];
    int codes[keyCount];
    float sweetXs[keyCount];
    float sweetYs[keyCount];
    float radii[keyCount];

    for (int i = 0; i < keyCount; ++i) {
        const int row = i < 10 ? 0 : (i < 19 ? 1 : 2);
        const int col = i < 10 ? i : (i < 19 ? i - 10 : i - 19);
        const int rowOffset = row == 0 ? 0 : (row == 1 ? keyWidth / 2 : keyWidth);
        xs[i] = rowOffset + col * keyWidth;
        ys[i] = row * keyHeight;
        widths[i] = keyWidth;
        heights[i] = keyHeight;
        codes[i] = letters[i];
        sweetXs[i] = xs[i] + keyWidth / 2.0f;
        sweetYs[i] = ys[i] + keyHeight / 2.0f;
        radii[i] = keyWidth / 2.0f;
    }
    const std::vector<int> proximityChars = buildEmptyProximityChars(gridWidth, gridHeight);
    ProximityInfo info(keyboardWidth, keyboardHeight, gridWidth, gridHeight, keyWidth, keyHeight,
            proximityChars.data(), static_cast<int>(proximityChars.size()), keyCount,
            xs, ys, widths, heights, codes, sweetXs, sweetYs, radii);

    EXPECT_EQ(keyCount, info.getKeyCount());
    EXPECT_TRUE(info.isCodePointOnKeyboard('h'));
    EXPECT_TRUE(info.isCodePointOnKeyboard('e'));
    EXPECT_TRUE(info.isCodePointOnKeyboard('l'));
    EXPECT_TRUE(info.isCodePointOnKeyboard('o'));
    EXPECT_FALSE(info.isCodePointOnKeyboard('#'));
    EXPECT_EQ('h', info.getCodePointOf(info.getKeyIndexOf('h')));
}

TEST(DISABLED_GestureReplayTest, ReplayHelloQwerty) {
    const TraceFixture fix = parseFixture(kHelloQwertyJson);
    ASSERT_EQ("hello", fix.committedWord);

    // The host replay harness can now parse the trace and construct ProximityInfo without JNI.
    // It still cannot call the actual gesture recognizer because this open-source tree has no
    // GestureSuggestPolicy implementation; GestureSuggestPolicyFactory::getGestureSuggestPolicy()
    // returns nullptr in host tests. Enabling this assertion requires an open/host-buildable
    // policy implementation (or a test policy) first.
}

} // namespace
} // namespace replay
} // namespace latinime
