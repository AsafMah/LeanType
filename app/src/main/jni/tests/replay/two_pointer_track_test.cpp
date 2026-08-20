// SPDX-License-Identifier: Apache-2.0
//
// Two-pointer track experiment harness — "does the AOSP gesture pipeline actually ingest two
// simultaneous strokes as two tracks, and what does pointer-id assignment do to that?"
//
// WHY THIS EXISTS
// ---------------
// LeanType composes one word out of several thumb fragments (tap->swipe, swipe->swipe). Today
// that is done SPATIALLY: WordComposer.setBatchInputPointers prepends the prior fragment's trail,
// re-timed, and InputPointers.appendAll() forces EVERY point to pointer id 0, so the recognizer
// sees one long single-pointer glide with a synthetic connector.
//
// But AOSP models TWO pointer tracks for gesture input (defines.h MAX_POINTER_COUNT_G == 2):
// DicTraverseSession holds ProximityInfoState[2] and seeds state i with pointerId i
// (dic_traverse_session.cpp initializeProximityInfoStates), and updateTouchPoints() keeps only the
// points whose pointerIds[k] == pointerId (proximity_info_state_utils.cpp). So pointer id -- not
// timing -- is what splits strokes into tracks.
//
// These tests drive the REAL ProximityInfoState (the same open AOSP preprocessing that is compiled
// into libjni_latinimegoogle.so) so the claims are measured, not inferred. What they CANNOT do is
// produce a recognized word: this tree has no gesture suggest policy
// (gesture_suggest_policy_factory.cpp returns null), so decoding quality is still device-only.
//
// TUNABLE: see TrackParams below -- pointer-id assignment, time policy, overlap, gap/interval, and
// tap->micro-stroke promotion are all knobs, and `TwoPointerSweep` prints a table across them.

#include <gtest/gtest.h>

#include <cmath>
#include <cstdio>
#include <string>
#include <vector>

#include "defines.h"
#include "suggest/core/layout/proximity_info.h"
#include "suggest/core/layout/proximity_info_state.h"
#include "suggest/policyimpl/typing/scoring_params.h"

namespace latinime {
namespace replay {
namespace {

// ---------------------------------------------------------------------------
// Keyboard model: 1080x310 QWERTY, same geometry as GestureReplayHostSeamTest.
// ---------------------------------------------------------------------------

constexpr int kKeyboardWidth = 1080;
constexpr int kKeyboardHeight = 310;
constexpr int kGridWidth = 32;
constexpr int kGridHeight = 16;
constexpr int kKeyWidth = 108;
constexpr int kKeyHeight = 90;
constexpr int kKeyCount = 26;
constexpr const char *kLetters = "qwertyuiopasdfghjklzxcvbnm";

struct KeyGeometry {
    int xs[kKeyCount], ys[kKeyCount], widths[kKeyCount], heights[kKeyCount], codes[kKeyCount];
    float sweetXs[kKeyCount], sweetYs[kKeyCount], radii[kKeyCount];
};

KeyGeometry buildKeyGeometry() {
    KeyGeometry g{};
    for (int i = 0; i < kKeyCount; ++i) {
        const int row = i < 10 ? 0 : (i < 19 ? 1 : 2);
        const int col = i < 10 ? i : (i < 19 ? i - 10 : i - 19);
        const int rowOffset = row == 0 ? 0 : (row == 1 ? kKeyWidth / 2 : kKeyWidth);
        g.xs[i] = rowOffset + col * kKeyWidth;
        g.ys[i] = row * kKeyHeight;
        g.widths[i] = kKeyWidth;
        g.heights[i] = kKeyHeight;
        g.codes[i] = kLetters[i];
        g.sweetXs[i] = g.xs[i] + kKeyWidth / 2.0f;
        g.sweetYs[i] = g.ys[i] + kKeyHeight / 2.0f;
        g.radii[i] = kKeyWidth / 2.0f;
    }
    return g;
}

// Owns the arrays ProximityInfo borrows.
class Qwerty {
 public:
    Qwerty()
            : mGeom(buildKeyGeometry()),
              mProximityChars(kGridWidth * kGridHeight * MAX_PROXIMITY_CHARS_SIZE,
                      NOT_A_CODE_POINT),
              mInfo(kKeyboardWidth, kKeyboardHeight, kGridWidth, kGridHeight, kKeyWidth, kKeyHeight,
                      mProximityChars.data(), static_cast<int>(mProximityChars.size()), kKeyCount,
                      mGeom.xs, mGeom.ys, mGeom.widths, mGeom.heights, mGeom.codes,
                      mGeom.sweetXs, mGeom.sweetYs, mGeom.radii) {}

    const ProximityInfo *info() const { return &mInfo; }

    void centerOf(const char c, int *outX, int *outY) const {
        for (int i = 0; i < kKeyCount; ++i) {
            if (kLetters[i] == c) {
                *outX = mGeom.xs[i] + kKeyWidth / 2;
                *outY = mGeom.ys[i] + kKeyHeight / 2;
                return;
            }
        }
        *outX = -1;
        *outY = -1;
    }

 private:
    KeyGeometry mGeom;
    std::vector<int> mProximityChars;
    ProximityInfo mInfo;
};

// ---------------------------------------------------------------------------
// TUNABLE PARAMETERS  <-- the knobs to play with
// ---------------------------------------------------------------------------

struct TrackParams {
    // How pointer ids are assigned to the two fragments.
    enum PointerMode {
        ALL_ZERO,   // today's behaviour: InputPointers.appendAll() forces id 0 for everything
        SPLIT_0_1,  // the proposal: fragment A -> id 0, fragment B -> id 1
        SPLIT_1_0,  // reversed, to test the "state 0 must be used" constraint
        ALL_ONE,    // pathological: nothing carries id 0
        ALL_TWO,    // pathological: a third finger (id >= 2)
    };
    // How the two fragments are laid out on the time axis.
    enum TimeMode {
        GLOBAL_MONOTONIC,     // B starts after A ends (today, via the re-timed extend base)
        PER_POINTER_RESTART,  // B's clock restarts at 0 (what raw per-stroke stamps look like)
        OVERLAPPED,           // B overlaps A by overlapPct of A's duration ("simultaneous")
    };

    PointerMode pointerMode = ALL_ZERO;
    TimeMode timeMode = GLOBAL_MONOTONIC;
    int overlapPct = 0;     // OVERLAPPED only: 0 = sequential, 100 = fully co-timed
    int gapMs = 60;         // WordComposer.EXTEND_BASE_GAP_BEFORE_NEW_MS
    int intervalMs = 25;    // WordComposer.EXTEND_BASE_POINT_INTERVAL_MS
    int samplesPerKeyHop = 4;   // densification along each inter-key segment
    // Tap -> micro-stroke promotion (IdealPrefixTrailBuilder, issue #99/B7b).
    bool promoteTaps = true;
    int tapArcRadiusDivisor = 6;    // radius = keyWidth / divisor
};

struct Trace {
    std::vector<int> xs, ys, times, ids;
    int fragmentASize = 0;
    int size() const { return static_cast<int>(xs.size()); }
};

// Trace a word's key centres, densified — mirrors IdealPrefixTrailBuilder.build().
void appendWordPath(const Qwerty &kb, const std::string &word, const TrackParams &params,
        std::vector<int> *xs, std::vector<int> *ys) {
    std::vector<int> cx, cy;
    for (const char c : word) {
        int x = 0, y = 0;
        kb.centerOf(c, &x, &y);
        if (x < 0) continue;
        cx.push_back(x);
        cy.push_back(y);
    }
    if (cx.empty()) return;
    if (cx.size() == 1) {
        if (params.promoteTaps) {
            // Out-and-back micro-stroke so the recognizer sees a vertex, not a lone point.
            const int r = std::max(1, kKeyWidth / std::max(1, params.tapArcRadiusDivisor));
            const int pxs[4] = {cx[0] - r, cx[0], cx[0] + r, cx[0]};
            for (int i = 0; i < 4; ++i) {
                xs->push_back(pxs[i]);
                ys->push_back(cy[0]);
            }
        } else {
            xs->push_back(cx[0]);
            ys->push_back(cy[0]);
        }
        return;
    }
    xs->push_back(cx[0]);
    ys->push_back(cy[0]);
    for (std::size_t i = 1; i < cx.size(); ++i) {
        const int steps = std::max(1, params.samplesPerKeyHop);
        for (int s = 1; s <= steps; ++s) {
            const float t = static_cast<float>(s) / steps;
            xs->push_back(static_cast<int>(std::lround(cx[i - 1] + (cx[i] - cx[i - 1]) * t)));
            ys->push_back(static_cast<int>(std::lround(cy[i - 1] + (cy[i] - cy[i - 1]) * t)));
        }
    }
}

Trace buildTwoFragmentTrace(const Qwerty &kb, const std::string &fragA, const std::string &fragB,
        const TrackParams &params) {
    Trace t;
    std::vector<int> ax, ay, bx, by;
    appendWordPath(kb, fragA, params, &ax, &ay);
    appendWordPath(kb, fragB, params, &bx, &by);

    const int nA = static_cast<int>(ax.size());
    const int nB = static_cast<int>(bx.size());
    t.fragmentASize = nA;

    // Fragment A always runs 0, interval, 2*interval, ...
    const int aDuration = std::max(0, (nA - 1) * params.intervalMs);
    int bStart = 0;
    switch (params.timeMode) {
        case TrackParams::GLOBAL_MONOTONIC:
            bStart = aDuration + params.gapMs;
            break;
        case TrackParams::PER_POINTER_RESTART:
            bStart = 0;
            break;
        case TrackParams::OVERLAPPED:
            bStart = aDuration - (aDuration * params.overlapPct) / 100;
            break;
    }

    int idA = 0, idB = 0;
    switch (params.pointerMode) {
        case TrackParams::ALL_ZERO:  idA = 0; idB = 0; break;
        case TrackParams::SPLIT_0_1: idA = 0; idB = 1; break;
        case TrackParams::SPLIT_1_0: idA = 1; idB = 0; break;
        case TrackParams::ALL_ONE:   idA = 1; idB = 1; break;
        case TrackParams::ALL_TWO:   idA = 2; idB = 2; break;
    }

    for (int i = 0; i < nA; ++i) {
        t.xs.push_back(ax[i]);
        t.ys.push_back(ay[i]);
        t.times.push_back(i * params.intervalMs);
        t.ids.push_back(idA);
    }
    for (int i = 0; i < nB; ++i) {
        t.xs.push_back(bx[i]);
        t.ys.push_back(by[i]);
        t.times.push_back(bStart + i * params.intervalMs);
        t.ids.push_back(idB);
    }
    return t;
}

struct TrackStats {
    bool used = false;
    int sampledSize = 0;
    int minRawIndex = -1;
    int maxRawIndex = -1;
    float minSpeedRate = 0.0f;
};

// Drive the REAL AOSP preprocessing for one pointer track.
TrackStats analyzeTrack(const Qwerty &kb, const Trace &trace, const int pointerId) {
    // Heap-allocated: ProximityInfoState is large.
    auto state = std::unique_ptr<ProximityInfoState>(new ProximityInfoState());
    const std::vector<int> locale;
    std::vector<int> inputCodes(trace.size(), NOT_A_CODE_POINT);

    state->initInputParams(pointerId, ScoringParams::MAX_SPATIAL_DISTANCE, kb.info(),
            inputCodes.data(), trace.size(), trace.xs.data(), trace.ys.data(), trace.times.data(),
            trace.ids.data(), true /* isGeometric */, &locale);

    TrackStats s;
    s.used = state->isUsed();
    s.sampledSize = state->size();
    for (int i = 0; i < s.sampledSize; ++i) {
        const int raw = state->getInputIndexOfSampledPoint(i);
        if (s.minRawIndex < 0 || raw < s.minRawIndex) s.minRawIndex = raw;
        if (raw > s.maxRawIndex) s.maxRawIndex = raw;
        const float rate = state->getSpeedRate(i);
        if (i == 0 || rate < s.minSpeedRate) s.minSpeedRate = rate;
    }
    return s;
}

// "technology" split the way LeanType composes it: swipe "tech", then swipe "nology".
const char *kFragA = "tech";
const char *kFragB = "nology";

// =============================================================================
// 1. Today's behaviour: everything is pointer 0, so the second track is dead.
// =============================================================================

TEST(TwoPointerTrackTest, AllPointsPointerZeroLeavesSecondTrackUnused) {
    const Qwerty kb;
    TrackParams params;
    params.pointerMode = TrackParams::ALL_ZERO;
    const Trace trace = buildTwoFragmentTrace(kb, kFragA, kFragB, params);

    const TrackStats t0 = analyzeTrack(kb, trace, 0);
    const TrackStats t1 = analyzeTrack(kb, trace, 1);

    EXPECT_TRUE(t0.used) << "track 0 must absorb the whole merged trail";
    EXPECT_GT(t0.sampledSize, 0);
    // Track 0 spans BOTH fragments — one long glide with a connector jump in the middle.
    EXPECT_LT(t0.minRawIndex, trace.fragmentASize);
    EXPECT_GE(t0.maxRawIndex, trace.fragmentASize);

    // This is the finding: InputPointers.appendAll()'s hardcoded id 0 makes the decoder's
    // second track permanently unused, so the whole multi-part problem has to be solved
    // spatially (connectors) instead.
    EXPECT_FALSE(t1.used) << "track 1 must be empty when every point carries id 0";
    EXPECT_EQ(0, t1.sampledSize);
}

// =============================================================================
// 2. The proposal: split ids 0/1 and BOTH native tracks light up.
// =============================================================================

TEST(TwoPointerTrackTest, SplitPointerIdsPopulateBothTracks) {
    const Qwerty kb;
    TrackParams params;
    params.pointerMode = TrackParams::SPLIT_0_1;
    const Trace trace = buildTwoFragmentTrace(kb, kFragA, kFragB, params);

    const TrackStats t0 = analyzeTrack(kb, trace, 0);
    const TrackStats t1 = analyzeTrack(kb, trace, 1);

    ASSERT_TRUE(t0.used);
    ASSERT_TRUE(t1.used) << "track 1 SHOULD be populated once fragment B carries pointer id 1";
    EXPECT_GT(t0.sampledSize, 0);
    EXPECT_GT(t1.sampledSize, 0);

    // Each track sees only its own fragment: no connector, no spatial jump.
    EXPECT_LT(t0.maxRawIndex, trace.fragmentASize) << "track 0 must not contain fragment B points";
    EXPECT_GE(t1.minRawIndex, trace.fragmentASize) << "track 1 must not contain fragment A points";
}

// =============================================================================
// 3. Pathological id assignments (why normalisation is mandatory).
// =============================================================================

// suggest.cpp: `if (!traverseSession->getProximityInfoState(0)->isUsed()) return;`
// If no point carries id 0 the whole search bails out and returns zero suggestions.
TEST(TwoPointerTrackTest, NoPointerZeroLeavesTrackZeroEmpty) {
    const Qwerty kb;
    TrackParams params;
    params.pointerMode = TrackParams::ALL_ONE;
    const Trace trace = buildTwoFragmentTrace(kb, kFragA, kFragB, params);

    EXPECT_FALSE(analyzeTrack(kb, trace, 0).used)
            << "track 0 empty => Suggest::initializeSearch early-returns => no suggestions";
    EXPECT_TRUE(analyzeTrack(kb, trace, 1).used);
}

// Only states 0 and 1 exist (MAX_POINTER_COUNT_G == 2), so a third finger is dropped silently.
TEST(TwoPointerTrackTest, PointerIdTwoIsSilentlyDropped) {
    const Qwerty kb;
    TrackParams params;
    params.pointerMode = TrackParams::ALL_TWO;
    const Trace trace = buildTwoFragmentTrace(kb, kFragA, kFragB, params);

    EXPECT_FALSE(analyzeTrack(kb, trace, 0).used);
    EXPECT_FALSE(analyzeTrack(kb, trace, 1).used) << "id >= 2 reaches no track at all";
}

// =============================================================================
// 4. Time policy: does it change track membership at all?
// =============================================================================

TEST(TwoPointerTrackTest, TimePolicyDoesNotChangeTrackMembership) {
    const Qwerty kb;
    const TrackParams::TimeMode modes[] = {TrackParams::GLOBAL_MONOTONIC,
            TrackParams::PER_POINTER_RESTART, TrackParams::OVERLAPPED};

    int baselineT0 = -1, baselineT1 = -1;
    for (const auto mode : modes) {
        TrackParams params;
        params.pointerMode = TrackParams::SPLIT_0_1;
        params.timeMode = mode;
        params.overlapPct = 100;
        const Trace trace = buildTwoFragmentTrace(kb, kFragA, kFragB, params);

        const TrackStats t0 = analyzeTrack(kb, trace, 0);
        const TrackStats t1 = analyzeTrack(kb, trace, 1);
        EXPECT_TRUE(t0.used);
        EXPECT_TRUE(t1.used);
        if (baselineT0 < 0) {
            baselineT0 = t0.sampledSize;
            baselineT1 = t1.sampledSize;
        }
        // Membership is decided purely by pointer id: shifting/overlapping the clocks cannot
        // move a point from one track to the other.
        EXPECT_EQ(baselineT0, t0.sampledSize) << "time mode changed track-0 membership";
        EXPECT_EQ(baselineT1, t1.sampledSize) << "time mode changed track-1 membership";
    }
}

// =============================================================================
// 5. Sweep: prints the parameter table so the knobs can be explored by hand.
//    Run with:  ctest --test-dir <build> -R TwoPointerSweep --output-on-failure
// =============================================================================

TEST(TwoPointerSweep, PrintTrackTable) {
    const Qwerty kb;
    struct Row { const char *name; TrackParams::PointerMode pm; TrackParams::TimeMode tm; int overlap; };
    const Row rows[] = {
        {"today  (all id 0, monotonic)", TrackParams::ALL_ZERO,  TrackParams::GLOBAL_MONOTONIC, 0},
        {"today  (all id 0, restart)  ", TrackParams::ALL_ZERO,  TrackParams::PER_POINTER_RESTART, 0},
        {"split  (0/1, monotonic)     ", TrackParams::SPLIT_0_1, TrackParams::GLOBAL_MONOTONIC, 0},
        {"split  (0/1, restart)       ", TrackParams::SPLIT_0_1, TrackParams::PER_POINTER_RESTART, 0},
        {"split  (0/1, overlap 50%)   ", TrackParams::SPLIT_0_1, TrackParams::OVERLAPPED, 50},
        {"split  (0/1, overlap 100%)  ", TrackParams::SPLIT_0_1, TrackParams::OVERLAPPED, 100},
        {"split  (1/0 reversed)       ", TrackParams::SPLIT_1_0, TrackParams::GLOBAL_MONOTONIC, 0},
        {"all id 1                    ", TrackParams::ALL_ONE,   TrackParams::GLOBAL_MONOTONIC, 0},
        {"all id 2                    ", TrackParams::ALL_TWO,   TrackParams::GLOBAL_MONOTONIC, 0},
    };
    std::printf("\n%-30s | t0.used t0.n [raw range] minSpeed | t1.used t1.n [raw range] minSpeed\n",
            "config");
    std::printf("%s\n", std::string(120, '-').c_str());
    for (const Row &r : rows) {
        TrackParams params;
        params.pointerMode = r.pm;
        params.timeMode = r.tm;
        params.overlapPct = r.overlap;
        const Trace trace = buildTwoFragmentTrace(kb, kFragA, kFragB, params);
        const TrackStats t0 = analyzeTrack(kb, trace, 0);
        const TrackStats t1 = analyzeTrack(kb, trace, 1);
        std::printf("%-30s |   %d    %3d [%3d..%3d] %8.3f |   %d    %3d [%3d..%3d] %8.3f\n",
                r.name, t0.used ? 1 : 0, t0.sampledSize, t0.minRawIndex, t0.maxRawIndex,
                t0.minSpeedRate, t1.used ? 1 : 0, t1.sampledSize, t1.minRawIndex, t1.maxRawIndex,
                t1.minSpeedRate);
    }
    std::printf("\n(raw index range shows which raw samples reached each track; fragment A is "
            "raw [0..%d))\n\n", buildTwoFragmentTrace(kb, kFragA, kFragB, TrackParams()).fragmentASize);
    SUCCEED();
}

} // namespace
} // namespace replay
} // namespace latinime
