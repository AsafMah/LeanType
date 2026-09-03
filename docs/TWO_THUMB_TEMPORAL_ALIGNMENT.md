# Two-thumb typing and gesture-decoder attribution

Historical record of the research and product experiment from issues #135, #141, #144, and #147.

**Current status:** the research established useful facts about the in-tree AOSP preprocessing code,
but device testing falsified the assumption that those facts predict recognition by the closed
gesture library that actually runs. The production experiment was removed in #147.

## Verdict

### Proven

1. The in-tree AOSP preprocessing code allocates two gesture tracks
   (`MAX_POINTER_COUNT_G == 2`).
2. Track membership in that code is selected by pointer id, not by whether two strokes overlap in
   time.
3. A gesture with no point carrying id 0 leaves track 0 unused. The open search path then returns
   no suggestions. Android can produce this sequence when thumb A (raw id 0) lifts while thumb B
   continues with raw id 1.
4. Deliberately overlapping or restarting timestamps does not move points between tracks and can
   produce invalid speed features. Gesture input should remain globally monotonic.
5. `PointerIdNormalizer` fixes the no-id-0 failure without changing the common one-finger id-0
   case. This fix and its production wiring remain.

### Falsified

1. **The measured in-tree decoder is the running recognizer.** It is not. The bundled native
   library has no gesture-scoring policy and sets `sHaveNativeGestureLib` false. Glide recognition
   is available only after loading a user-supplied or system closed library.
2. **Feeding sequential word fragments as two pointer tracks improves production recognition.**
   The `DUAL_POINTER` experiment produced nonsense on a real device, including for words such as
   `ambulance`.
3. **Redrawing a prefix through ideal key centers is a safe recognition improvement.**
   `IdealPrefixTrailBuilder` was actively harmful when no native gesture library was loaded and did
   not establish a product benefit with the closed recognizer.
4. **Default-off is enough protection for known-bad input machinery.** Even gated code increased
   complexity in the hot production path and made future behavior harder to reason about.

### Removed in #147

- `StrokeAligner`, including `DUAL_POINTER` and its connector indirection.
- `IdealPrefixTrailBuilder`.
- Pointer-track mode, ideal-prefix, interval, and gap preferences and UI.
- Settings cache fields, strings, search registration, and tests for those controls.
- The native two-pointer host harness. It exercised unused in-tree preprocessing and was too easy
  to interpret as evidence about the closed runtime recognizer.

### Retained in #147

- `PointerIdNormalizer`, its `BatchInputArbiter`/`GestureStrokeRecognitionPoints` wiring, and
  regression tests.
- The pre-experiment connector implementation directly in
  `WordComposer.setBatchInputPointers`.
- The `experimental` build type (`.exp`, label `LeanTypeDual EXP`) for future device A/B tests.

## Architecture boundary that invalidated the product claim

The original research treated "the decoder" as one component. There are two relevant boundaries:

| Component | In this repository? | Used for bundled glide recognition? |
| --- | --- | --- |
| Input preprocessing (`ProximityInfoState`, pointer filtering, speed features) | Yes | Compiled, but not a complete recognizer |
| Gesture traversal/weighting/scoring policy | No | Supplied by the loaded closed library |
| Java fallback `SwipeGestureEngine` | Yes | Separate fallback; ignores pointer ids and times |

`GestureSuggestPolicyFactory::sGestureSuggestFactoryMethod` is initialized to null in the in-tree
native code. `JniUtils` therefore reports no native gesture library for the bundled implementation.
The host harness could inspect the open preprocessing layer, but it could not produce a recognized
word or validate the closed scoring policy.

That distinction matters more than the fidelity of the harness: exact measurements of an unused
layer do not establish production recognition quality.

## What the removed host harness established

The harness called `ProximityInfoState::initInputParams` with a two-fragment trace and varied pointer
ids and timestamp policy independently. Its representative preprocessing output was:

```text
config                         | track 0 points | track 1 points | timing result
all id 0, monotonic            | both fragments | none           | valid
split id 0/1, monotonic        | fragment A     | fragment B     | valid
split id 0/1, clocks restart   | fragment A     | fragment B     | negative speed features
split id 0/1, overlap 50%      | fragment A     | fragment B     | negative speed features
all id 1                      | none           | both fragments | track 0 unused
all id 2                      | none           | none           | both tracks unused
```

This supported three narrow conclusions:

- ids determine preprocessing track membership;
- timestamp policy does not change membership;
- global monotonicity is required by the preprocessing feature calculations.

It did **not** show that the loaded gesture library would alternate between those tracks when
scoring a word, or that the word would be recognized correctly. The device experiment supplied
that missing product-level evidence and rejected the approach.

## The retained pointer-id fix

The no-id-0 failure is independent of the removed production experiment. Raw Android pointer ids
need not start at zero for the stroke that contributes gesture points:

1. thumb A goes down as raw id 0;
2. thumb B goes down as raw id 1;
3. thumb A lifts;
4. thumb B continues swiping with raw id 1.

`PointerIdNormalizer` assigns dense slots in first-seen order for each gesture. The first
contributing pointer is therefore emitted as id 0, allowing the native search path to initialize
instead of returning zero suggestions. The mapping is reset at the start of a fresh batch gesture
and remains stable across incremental and final aggregation.

This is deliberately a narrow fix:

- normal one-finger input remains 0 -> 0;
- a second contributing pointer maps to slot 1;
- additional pointers still fall outside the native two-track limit rather than being merged into
  an existing track.

## Restored production behavior

Multi-part composition again uses the connector that shipped before #141:

1. replay the saved base with pointer id 0;
2. space replayed base points by 25 ms;
3. end the base 60 ms before the current gesture;
4. append current coordinates and timestamps while forcing pointer id 0.

The implementation is direct in `WordComposer`, matching the v0.3.0 path. Tests pin the exact
coordinates, timestamps, and pointer ids so removing the experimental abstraction does not alter
the established connector behavior.

## Guidance for future experiments

- Treat host preprocessing tests as research about that layer only.
- Do not infer recognized words from point routing or geometric features.
- Put recognizer changes behind the side-by-side `experimental` build and decide them with device
  A/B evidence against the exact loaded library.
- Keep production transforms narrow and independently justified.
- Remove an experiment after falsification; preserve its conclusions here and in the linked issue
  rather than preserving dead machinery in the input path.
