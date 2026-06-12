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
//   gesture recognizer.  Disabled because two blockers must be resolved first:
//
//   BLOCKER 1 — JNI runtime required by ProximityInfo:
//     ProximityInfo::ProximityInfo(JNIEnv*, ...) calls env->GetArrayLength() and
//     env->GetIntArrayRegion() unconditionally on the very first line of the
//     constructor body (proximity_info.cpp:76).  There is no non-JNI constructor
//     overload.  A live JVM (JavaVM + JNI_CreateJavaVM, or an Android runtime)
//     must be available to create a ProximityInfo with real keyboard geometry.
//
//   BLOCKER 2 — binary dictionary asset required:
//     Dictionary wraps a DictionaryStructureWithBufferPolicy that is loaded from
//     a compiled binary .dict file (the Android asset pipeline provides these at
//     runtime; they are not part of the source tree and are not downloaded by the
//     CMake build).  Without a valid dict the gesture scorer has nothing to search.
//
//   Next concrete steps to get real replay assertions:
//     a) Either create a minimal fake JNIEnv shim (filling JNINativeInterface_
//        function pointers) so ProximityInfo can be constructed on the host, OR
//        add a non-JNI constructor that accepts raw int*/float* arrays directly.
//     b) Download / bundle a small compiled English .dict (e.g. the AOSP
//        "en_US" dict, ~1 MB) as a test asset via CMake FetchContent, OR
//        generate a tiny programmatic dict using the existing v4 writer classes.
//     c) Wire ProximityInfo + Dictionary + DicTraverseSession together and
//        uncomment the assertion in DISABLED_GestureReplayTest.ReplayHelloQwerty.
// =============================================================================

#include <gtest/gtest.h>

#include "replay/trace_fixture.h"

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

// =============================================================================
// DISABLED_GestureReplayTest — compile-checked scaffolding; skipped in ctest.
//
// Blocked by:
//   1. ProximityInfo requires JNIEnv* + jintArray/jfloatArray (JVM must be live).
//   2. Dictionary requires a binary .dict asset loaded from disk.
//
// To re-enable, prefix the test name with nothing (remove "DISABLED_") after
// both blockers are resolved.  See file header for the resolution path.
// =============================================================================

// Prevent "unused include" warnings while the DISABLED_ tests are inactive.
// The includes below are intentional — they document the API seam and will be
// used once the blockers are lifted.
//
// #include "suggest/core/layout/proximity_info.h"           // needs JNIEnv*
// #include "suggest/core/dictionary/dictionary.h"           // needs .dict asset
// #include "suggest/core/session/dic_traverse_session.h"    // needs JNIEnv*
// #include "suggest/core/result/suggestion_results.h"
// #include "suggest/core/suggest_options.h"
// #include "dictionary/property/ngram_context.h"

TEST(DISABLED_GestureReplayTest, ReplayHelloQwerty) {
    // --- Step 1: Load fixture ---
    const TraceFixture fix = parseFixture(kHelloQwertyJson);
    ASSERT_EQ("hello", fix.committedWord);

    // --- Step 2: Build ProximityInfo from fixture keyboard geometry ---
    //
    // BLOCKED: ProximityInfo constructor signature (proximity_info.h:31):
    //
    //   ProximityInfo(JNIEnv *env,
    //                 int keyboardWidth, int keyboardHeight,
    //                 int gridWidth, int gridHeight,
    //                 int mostCommonKeyWidth, int mostCommonKeyHeight,
    //                 jintArray proximityChars,   // JNI array — needs live JVM
    //                 int keyCount,
    //                 jintArray keyXCoordinates,  // JNI array
    //                 jintArray keyYCoordinates,  // JNI array
    //                 jintArray keyWidths,        // JNI array
    //                 jintArray keyHeights,       // JNI array
    //                 jintArray keyCharCodes,     // JNI array
    //                 jfloatArray sweetSpotCenterXs,  // JNI float array
    //                 jfloatArray sweetSpotCenterYs,  // JNI float array
    //                 jfloatArray sweetSpotRadii);    // JNI float array
    //
    // The body immediately calls env->GetArrayLength(proximityChars) and
    // env->GetIntArrayRegion(...), so even a null JNIEnv* would crash.
    // Resolution: add a non-JNI constructor that accepts raw int*/float* arrays.

    // ProximityInfo pInfo(env, fix.keyboard.width, fix.keyboard.height, ...);

    // --- Step 3: Load a binary dictionary ---
    //
    // BLOCKED: requires a compiled en_US .dict binary.  The latinime app loads
    // these from its APK assets folder at runtime; they are not in the source
    // tree.  For host tests, a small test dictionary must be bundled (e.g. via
    // CMake FetchContent) or generated programmatically using the v4 writer.

    // const char *dictPath = "/path/to/en_US.dict";
    // auto policy = DictionaryStructureWithBufferPolicyFactory::newPolicyForExistingDictFile(
    //     dictPath, 0, fileSize, false);
    // Dictionary dict(env, std::move(policy));

    // --- Step 4: Create DicTraverseSession ---
    //
    // DicTraverseSession also takes JNIEnv* + jstring locale (same blocker).

    // DicTraverseSession session(env, localeJstr, /*usesLargeCache=*/false);
    // session.init(&dict, nullptr, &suggestOptions);

    // --- Step 5: Call getSuggestions ---
    //
    // Suggest::getSuggestions(ProximityInfo*, void* traverseSession,
    //   int* inputXs, int* inputYs, int* times, int* pointerIds,
    //   int* inputCodePoints, int inputSize,
    //   float weightOfLangModelVsSpatialModel,
    //   SuggestionResults* outSuggestionResults)
    //
    // Once unblocked, wire like:
    //
    // auto xs  = fix.xCoordinates();
    // auto ys  = fix.yCoordinates();
    // auto ts  = fix.times();
    // auto ids = fix.pointerIds();
    // SuggestionResults results(MAX_RESULTS);
    // NgramContext ngramCtx;
    // SuggestOptions opts;
    // dict.getSuggestions(&pInfo, &session,
    //                     xs.data(), ys.data(), ts.data(), ids.data(),
    //                     /*inputCodePoints=*/nullptr, fix.inputSize(),
    //                     &ngramCtx, &opts,
    //                     NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL,
    //                     &results);
    //
    // Then assert the top suggestion matches committedWord:
    //
    // ASSERT_GT(results.getSuggestionsCount(), 0);
    // int topWordCodePoints[MAX_WORD_LENGTH];
    // results.getSortedScores();  // sort descending
    // // decode first result and compare to fix.committedWord
    // char topWord[MAX_WORD_LENGTH * 4 + 1];
    // intArrayToCharArray(topWordCodePoints, topWordLen, topWord, sizeof(topWord));
    // EXPECT_EQ(fix.committedWord, std::string(topWord));

    GTEST_SKIP() << "GestureReplayTest disabled: see file header for blocker details.";
}

} // namespace
} // namespace replay
} // namespace latinime
