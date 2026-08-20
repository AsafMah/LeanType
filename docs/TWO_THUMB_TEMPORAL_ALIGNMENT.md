# Two-thumb typing & the native gesture decoder — temporal alignment vs. pointer attribution

Research findings for the hypothesis *"temporally shift the two thumbs' swipes so the AOSP gesture
library accepts them as simultaneous."*

**Status:** research complete, measured against the real AOSP preprocessing code.
**Harness:** `app/src/main/jni/tests/replay/two_pointer_track_test.cpp` (runs in CI via
`.github/workflows/native-tests.yml`).

---

## TL;DR — verdict

| Claim | Verdict |
| --- | --- |
| The AOSP library can handle two simultaneous strokes | ✅ **Confirmed.** It models exactly two pointer tracks and decodes a word by alternating between them. |
| We should *temporally shift* strokes so it accepts them as simultaneous | ❌ **Falsified.** Track membership is decided **purely by pointer id**. Shifting or overlapping timestamps moves **zero** points between tracks — and overlapping actively **corrupts** the decoder's speed features. |
| Taps should be promoted to micro-swipes | ✅ **Sound, and already implemented** — `IdealPrefixTrailBuilder` on branch `b7a-prefix-aware-stripping` (issue #99), never merged. |
| There is a better lever than the current connector hack | ✅ **Yes: pointer-id attribution.** It is *necessary* to reach the second track, and measurably cleaner than the merged trail — but *not sufficient* on its own (four constraints in §2.4). |

**The hypothesis is directionally right and mechanically wrong.** The goal — "make the library see
one genuine two-pointer gesture" — is achievable and natively supported. But the knob is
**`pointerIds[]`**, not the clock. Time still matters, in a *supporting* role: the concatenated
array must stay **globally monotonic**, and deliberately overlapping strokes is the one temporal
change that provably makes things worse.

---

## 1. Q1 — Ground truth about the native decoder

### 1.1 What is and isn't in this tree

The gesture **scoring policy** is absent: `GestureSuggestPolicyFactory::sGestureSuggestFactoryMethod`
is initialised to `0` (`jni/src/suggest/policyimpl/gesture/gesture_suggest_policy_factory.cpp:20`).
Glide typing therefore requires the closed `libjni_latinimegoogle.so`, loaded by
`JniUtils.java:88-107`; the built-in library sets `sHaveNativeGestureLib = false`.
`jni/tests/replay/gesture_replay_test.cpp:11-29` already documents this — its replay test is
`DISABLED_` for exactly this reason.

But "the decoder is closed source" is **imprecise**, and the distinction is what makes this research
possible:

| Component | Open in this tree? |
| --- | --- |
| Gesture **Traversal / Weighting / Scoring** policy | ❌ closed |
| Search core (`Suggest`, `DicNode`, `DicTraverseSession`) | ✅ open |
| **Input preprocessing** (`ProximityInfoState`, `ProximityInfoStateUtils`) | ✅ open |

The blob is *AOSP LatinIME + Google's private policy*, so the preprocessing that decides **what the
scorer is even allowed to see** is stock AOSP — readable, and (crucially) **host-executable**.

### 1.2 The decoder models exactly two pointer tracks

| Evidence | Location |
| --- | --- |
| `#define MAX_POINTER_COUNT 1` / `#define MAX_POINTER_COUNT_G 2` | `jni/src/defines.h:276-277` |
| `ProximityInfoState mProximityInfoStates[MAX_POINTER_COUNT_G]` — an array of **two** | `dic_traverse_session.h:178` |
| `for (i = 0; i < maxPointerCount; ++i) mProximityInfoStates[i].initInputParams(i, …)` — state *i* is seeded with pointer id *i* | `dic_traverse_session.cpp:69-79` |
| `updateTouchPoints` keeps **only** points where `pointerIds[i] == pointerId` | `proximity_info_state_utils.cpp:96-135` (esp. 98, 102) |
| `getProximityTypeG` loops over **both** used tracks, each at *its own* cursor `dicNode->getInputIndex(i)`, and returns MATCH if **either** matches | `dic_traverse_session.h:109-126` |
| The trie search node carries a **separate cursor per track** | `dic_node_state_input.h:89-91` |

So a word can be spelled by **alternating between the two thumbs' trails**. That is Nintype-style
two-thumb decoding built into AOSP — not something we need to synthesise.

### 1.3 Gesture input always runs with `maxPointerCount == 2`

`dic_traverse_session.cpp:69-77` passes `maxPointerCount == MAX_POINTER_COUNT_G` **as the
`isGeometric` flag**, with an AOSP comment admitting the trick is "hacky and incorrect". If the
gesture traversal returned 1, `isGeometric` would be false for gestures and the entire geometric
pipeline (speed rates, beeline rates, `updateAlignPointProbabilities`) would never run — glide
typing would be broken. It isn't. ⇒ **both tracks are initialised on every gesture.**

### 1.4 How time and pointer identity actually affect scoring

- **Pointer identity → track membership.** Binary and absolute (`…utils.cpp:102`).
- **Time → geometric features only**, computed *within* a track: `refreshSpeedRates`
  (`…utils.cpp:218-267`), `refreshBeelineSpeedRates` (`…:277-292`), and sampling decisions in
  `pushTouchPoint`. These feed the closed weighting via `ProximityInfoState`'s public getters
  (`proximity_info_state.h:156-174`).
- **There is no cross-track temporal comparator anywhere** — nothing in the open pipeline asks
  "did these two strokes overlap?".

---

## 2. Q2 — Assessing the temporal-shift idea

### 2.1 The experiment

`two_pointer_track_test.cpp` drives the **real** `ProximityInfoState::initInputParams` — the same
code compiled into the Google blob — with a two-fragment trace (`tech` + `nology`, the canonical
multi-part case from `TWO_THUMB_TYPING_INTERNALS.md` §5), sweeping pointer-id assignment and time
policy independently.

`TwoPointerSweep.PrintTrackTable` output (`minSpeed` = minimum `getSpeedRate()` across the track's
sampled points; fragment A is raw `[0..13)`):

```
config                         | t0.used t0.n [raw range] minSpeed | t1.used t1.n [raw range] minSpeed
------------------------------------------------------------------------------------------------------
today  (all id 0, monotonic)   |   1     28 [  0.. 33]    0.491 |   0      0 [ -1.. -1]    0.000
today  (all id 0, restart)     |   1     28 [  0.. 33]   -0.177 |   0      0 [ -1.. -1]    0.000
split  (0/1, monotonic)        |   1     13 [  0.. 12]    0.884 |   1     15 [ 13.. 33]    0.521
split  (0/1, restart)          |   1     13 [  0.. 12]   -0.415 |   1     15 [ 13.. 33]   -0.499
split  (0/1, overlap 50%)      |   1     13 [  0.. 12]   -1.037 |   1     15 [ 13.. 33]   -1.247
split  (0/1, overlap 100%)     |   1     13 [  0.. 12]   -0.415 |   1     15 [ 13.. 33]   -0.499
split  (1/0 reversed)          |   1     15 [ 13.. 33]    0.521 |   1     13 [  0.. 12]    0.884
all id 1                       |   0      0 [ -1.. -1]    0.000 |   1     28 [  0.. 33]    0.491
all id 2                       |   0      0 [ -1.. -1]    0.000 |   0      0 [ -1.. -1]    0.000
```

### 2.2 What it proves

1. **Today the second track is dead.** With every point on id 0 (what
   `InputPointers.appendAll` forces — see §2.3), track 0 absorbs raw `[0..33]` (both fragments,
   with a spatial jump in the middle) and **track 1 gets zero points**.
2. **Splitting ids engages both tracks, cleanly.** `split (0/1)` gives track 0 exactly fragment A
   (`raw [0..12]`) and track 1 exactly fragment B (`raw [13..33]`). No connector, no jump.
3. **Time cannot move a point between tracks.**
   `TwoPointerTrackTest.TimePolicyDoesNotChangeTrackMembership` asserts that
   `GLOBAL_MONOTONIC`, `PER_POINTER_RESTART` and `OVERLAPPED(100%)` all yield *identical* track
   membership. **This is the direct falsification of the temporal-shift hypothesis.**
4. **Overlapping timestamps actively harms the decoder.** Look at the `minSpeed` column: healthy
   configurations are positive; `overlap 50%` reaches **−1.037 / −1.247**. A negative speed rate is
   arithmetically impossible from real input (`speed = length / duration`, `length ≥ 0`) — it means
   `duration < 0`, i.e. the feature is garbage. So the one temporal change the hypothesis proposes
   is the one that measurably degrades the decoder's inputs.
5. **Global monotonicity is required.** `restart` (per-stroke clocks) goes negative even *with*
   correct ids (−0.415 / −0.499); `monotonic` is clean and in fact **better than today's merged
   trail** (0.884 / 0.521 vs 0.491).

### 2.3 Why time leaks across tracks at all (the F7 mechanism)

`refreshSpeedRates` (`…utils.cpp:231-259`) walks **raw** input indices `j`/`j+1`
(`duration += times[j+1] - times[j]`), guarded only by
`if (i < sampledInputSize - 1 && j >= (*sampledInputIndice)[i+1]) break;`. For raw blocks
`[p0 p0 | p1 p1]`:

- at track 0's **last** sampled point, `i < sampledInputSize - 1` is false ⇒ the forward guard is
  disabled ⇒ the boundary edge is consumed;
- at track 1's **first** sampled point, `i > 0` is false ⇒ the backward guard is disabled ⇒ the same
  edge is consumed again.

So the window straddles the pointer boundary and reads a cross-thumb distance and a possibly
negative duration. `calculateBeelineSpeedRate` (`…:475-560`) and the raw-neighbour **angle**
computation (`…:109-115`) leak the same way. AOSP's own debug assertion at `…:61-71` treats
decreasing raw times as invalid input, confirming this is out-of-contract.

**Consequence:** re-timestamping is still needed — but to enforce *monotonicity*, not to create
*overlap*.

### 2.4 Where it would hook in, and what breaks

The brief guessed `BatchInputArbiter`. That is the right seam for **truly simultaneous** input,
where ids are already correct (`GestureStrokeRecognitionPoints.java:314-320` appends with the
tracker's real MotionEvent id, `PointerTracker.java:434-437`). It is the **wrong** seam for the
fork's *sequential* fragments, which are merged much later in
`WordComposer.setBatchInputPointers` (`WordComposer.java:284-304`) — and that is where identity is
destroyed:

```java
// InputPointers.java:109-117
/** … Pointer ids are forced to 0 since multi-part gesture composition doesn't
 *  preserve pointer identity across separate strokes. */
public void appendAll(@NonNull final InputPointers other) {
    append(0, other.mTimes, other.mXCoordinates, other.mYCoordinates, 0, other.getPointerSize());
}
```

Four constraints make id-remapping *necessary but not sufficient*:

1. **Track 0 must anchor the word.** `suggest.cpp:81-84` early-returns when track 0 is unused ⇒
   **zero suggestions**. Measured: `NoPointerZeroLeavesTrackZeroEmpty`.
2. **Only ids 0 and 1 may be emitted.** Anything else reaches no track at all. Measured:
   `PointerIdTwoIsSilentlyDropped`. (Reachable today: a third finger, or thumb B keeping id 1 after
   thumb A lifts.)
3. **Ids must be stable across incremental recognition.**
   `checkAndReturnIsContinuousSuggestionPossible` (`…utils.cpp:904-929`) compares x/y/time but
   **not** pointer ids, so reassigning ids mid-gesture can silently reuse stale per-track state.
4. **Only two fragments fit.** The fork's combining mode routinely produces three or more. A third
   fragment must reuse an id, which re-creates the spatial-jump problem the connector exists to
   solve. **A hybrid is the likely endgame: ids for the first two fragments, connector beyond.**

Also note the two-pointer path, while real, is **under-exercised**: several methods are hard-coded
to pointer 0 (`dic_node.h:192-197`, `suggest.cpp:245-249`) and partial commit explicitly does not
support multiple pointers (`suggestions_output_utils.cpp:63-65`).

---

## 3. Q3 — Tap-to-micro-swipe promotion

**Already built, and stranded.** `IdealPrefixTrailBuilder` (branch `b7a-prefix-aware-stripping`,
commit `e4724109d`, issue #99/B7b) synthesises an ideal key-centre trail for the composing prefix
and turns a single-letter (tap) prefix into a small **out-and-back micro-stroke**:

- radius `keyWidth / 6`, **4 points** (`c−r`, `c`, `c+r`, `c`) — gives the recognizer a vertex
  instead of an isolated point;
- multi-letter prefixes: key centres densified to ~`keyWidth / 4` spacing.

It is gated behind `BuildConfig.FAKE_TRACK_V2` in a dedicated **`swipetest` build type** for
on-device A/B, and was never merged to `dev`. Its dev-log records the honest limitation: *"B7b
changes what the NATIVE recognizer returns, so it is not JVM-testable; verification is on-device
A/B only."*

That geometry is reproduced in this harness (`TrackParams::promoteTaps`,
`tapArcRadiusDivisor`) so it can be swept alongside the pointer/time knobs. **Note its final line
still writes `pointerId 0`** — even the "fake-track" work never touched pointer identity.

**Assessment:** sound and worth merging *independently* of the pointer-id question — it addresses a
different failure (sparse tap geometry), and 4 points at `keyWidth/6` is a reasonable starting
shape. It should be re-validated on device rather than assumed.

---

## 4. Q4 — Prior art

### 4.1 Inside this repo (the important part)

An entire epic already exists and is **open**:

| Issue | Title | State |
| --- | --- | --- |
| #97 | **[Epic] B7: Multi-part fake-track synthesis** | OPEN |
| #98 | B7a: Prefix-aware gesture result stripping | OPEN — built on `b7a-prefix-aware-stripping`, **not on `dev`** |
| #99 | B7b: Ideal prefix trail (tap → micro-stroke) | OPEN — built (`IdealPrefixTrailBuilder`), **not on `dev`** |
| #100 | **B7c: Adaptive connector bridge + distance-based re-timing** | OPEN — **never built** |
| #101 | B7d: Hybrid raw-vs-ideal prefix selection | OPEN — never built |
| #29 | B4: Per-thumb pointer attribution (true simultaneous) | OPEN — never built |
| #30 | B5: Tap geometry as weighted recognizer hints | OPEN — folded into #99 |

**#100 is the maintainer's hypothesis, already specified months ago**: *"replace fixed 25 ms/60 ms
with distance-aware timing… large gap ⇒ **teleport** — no intermediate points, short dt (12–25 ms)…
re-time by `dt = clamp(distance / velocity, 8, 28 ms)`."* This research says #100 is worth doing
**for monotonicity and connector-hallucination reasons** (`techcolony`), but it will not make the
decoder treat the strokes as two tracks — only ids do that.

**The key architectural finding:** epic #97 explicitly classifies #29 as
*"different problem (concurrent strokes), not composition"*. **That separation is wrong.** The
decoder's second track is exactly the "fake track" #97 is trying to synthesise — a real one, free.
Sequential composition and simultaneous two-thumb are the *same* mechanism at the decoder level.
No issue in this repo currently proposes using `mProximityInfoStates[1]`; even #29 proposes routing
the tapping thumb through Java-side live-converge instead.

### 4.2 Decoder replacement (rejected / pending)

- **SHARK²/statistical decoder**: a full FlorisBoard-derived `StatisticalSwipeDecoder` exists on
  `feat/statistical-swipe-decoder` (commit `6afc07850`) with 21 JVM tests — **ruled out on quality**
  per #97 ("we stay on Google's decoder and attack the track synthesis instead").
- **NLnet open gesture recognizer** (#75, NGI Mobifree grant 101135795): data-gathering phase only,
  library does not exist publicly; gathering ends "end of 2026 latest". Two-thumb work should be
  designed to sit on top of it eventually.
- The in-tree Java fallback `SwipeGestureEngine` **ignores `pointerIds` and `times` entirely** —
  `rankByIndex` flattens all points into one path (`SwipeGestureEngine.java:395-422`). It cannot
  validate anything in this document.

### 4.3 External

- **HeliBoard #291** "Improving simultaneous/two-finger swiping" is the upstream request this
  fork's two-thumb work answers (`TWO_THUMB_TYPING_INTERNALS.md` §intro).
- **Nintype** popularised two-thumb overlapping strokes; it uses its own decoder, so its design is
  inspirational rather than transferable.
- The AOSP two-pointer design (`MAX_POINTER_COUNT_G`) dates from the original Google gesture work
  and has never been publicly documented as a supported feature.

---

## 5. Q5 — The harness, and what it can and cannot prove

`app/src/main/jni/tests/replay/two_pointer_track_test.cpp`. Tunable knobs in `TrackParams`:

| Knob | Values | Meaning |
| --- | --- | --- |
| `pointerMode` | `ALL_ZERO`, `SPLIT_0_1`, `SPLIT_1_0`, `ALL_ONE`, `ALL_TWO` | pointer-id assignment |
| `timeMode` | `GLOBAL_MONOTONIC`, `PER_POINTER_RESTART`, `OVERLAPPED` | time-axis policy |
| `overlapPct` | 0–100 | overlap amount for `OVERLAPPED` |
| `gapMs` / `intervalMs` | default 60 / 25 | today's `EXTEND_BASE_*` constants |
| `samplesPerKeyHop` | default 4 | trail densification (`IdealPrefixTrailBuilder` uses `keyWidth/4`) |
| `promoteTaps` / `tapArcRadiusDivisor` | on, 6 | tap → micro-stroke (B7b geometry) |

Run:

```bash
cmake -S app/src/main/jni -B ~/lt-host -DCMAKE_BUILD_TYPE=Release
cmake --build ~/lt-host -j
~/lt-host/latinime_host_unittests --gtest_filter='TwoPointer*'      # sweep table
ctest --test-dir ~/lt-host -R TwoPointer                            # assertions
```

*(On Windows use WSL — the host build hits a MinGW `mkdir()` signature mismatch in an unrelated
v402 dictionary file.)*

### What each tier proves

| Tier | Proves | Does **not** prove |
| --- | --- | --- |
| **A. This harness** — real AOSP `ProximityInfoState`, no fidelity gap | Exactly which points reach each track; that time cannot change membership; that overlap corrupts speed features | The decoded **word** |
| **B. JVM unit tests** | Any Java-side transform (id assignment, monotonicity, micro-arc geometry) | Anything about recognition |
| **C. `SwipeGestureEngine` fallback** | *Spatial* path shape only | **Nothing** about ids or time — it ignores both |
| **D. On-device, with the blob** | Actual recognition quality | — |

**Stated plainly: nothing below tier D produces a recognized word.** The *research* question
("does the decoder ingest two strokes as two tracks, and is time the lever?") is fully answered at
tier A. The *product* question ("does it recognise better?") remains device-only, and the existing
`swipetest` build type (#99) is the right vehicle for that A/B.

---

## 6. Recommendation

**Do not pursue temporal alignment as the mechanism.** It is falsified: timestamps cannot move a
point between tracks, and deliberate overlap is the single change measured to degrade the decoder's
inputs.

Recommended order instead:

1. **Fix the pointer-id hazards regardless of any redesign** (cheap, independent, likely
   user-visible): guarantee at least one point carries id 0 and none carries id ≥ 2. Today a
   two-thumb sequence where thumb A lifts first leaves thumb B on id 1 ⇒ track 0 empty ⇒
   `suggest.cpp:81-84` returns **no suggestions at all**.
2. **Merge the stranded B7a + B7b work** (#98, #99). It is built, reviewed-by-use, and orthogonal —
   tap→micro-stroke fixes a real, separate weakness.
3. **Prototype `SPLIT_0_1` behind the existing `swipetest` build type**, for the *two-fragment*
   case only, with global-monotonic timestamps, subject to the four constraints in §2.4. This is
   the genuinely novel idea and it is a small change — but it must be judged on-device.
4. **Keep the connector** for three-or-more fragments, and take #100's distance-aware re-timing for
   its *own* merits (monotonicity, fewer `techcolony` hallucinations) — not as a simultaneity
   mechanism.
5. **Reclassify #29.** It is not a "different problem" from #97; it is the same lever. Consider
   folding them.

**Confidence:** high for §1–2 (measured against real code, in CI). Medium for the recommendation —
whether two tracks *score* better than one merged trail is decided by the closed weighting policy
and can only be settled on-device.

---

## 7. Appendix — the one-line summary of the bug behind it all

```java
// InputPointers.java:114 — this `0` is why the decoder's second track has never been used.
append(0, other.mTimes, other.mXCoordinates, other.mYCoordinates, 0, other.getPointerSize());
```
