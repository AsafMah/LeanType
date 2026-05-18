# Two-thumb typing — internals guide

A code-level walkthrough of the changes introduced by the `copilot/improve-two-thumb-typing` PR. The user-facing reference lives in [`FEATURES.md`](FEATURES.md); this document is for reviewers / maintainers and explains the *systems* the PR touches, *what* was changed in each, and *why*.

The PR addresses [HeliBoard issue #291](https://github.com/Helium314/HeliBoard/issues/291) ("Improving simultaneous/two-finger swiping") and adds a unified Nintype-style "combining mode" on top of LeanType's existing gesture pipeline.

---

## 1. Surrounding architecture (what was already there)

Three subsystems carry every key/finger event from screen to text field:

```
┌───────────────────────────────────────────────────────────────────────┐
│  MainKeyboardView  (View / Canvas)                                    │
│  ├─ PointerTracker (per-pointer state machine)                        │
│  │   └─ BatchInputArbiter (aggregates multi-finger gesture points)    │
│  └─ KeyboardActionListenerImpl ───► LatinIME ───► InputLogic          │
│                                                    └─ RichInputConnection
│                                                       (talks to editor)
└───────────────────────────────────────────────────────────────────────┘
```

- **`PointerTracker`** owns the per-finger state and decides per-finger whether we are detecting a gesture. The global static `sInGesture` flag flips to `true` once *any* tracker passes `BatchInputArbiter.mayStartBatchInput`. All trackers append their points into one static `BatchInputArbiter.sAggregatedPointers`, so two-finger swipes already aggregate into one word out of the box. `BatchInputArbiter.mayEndBatchInput` fires the *real* `onEndBatchInput` only when `activePointerCount == 1` (i.e., the last finger lifted).
- **`InputLogic`** is the brain. Every keystroke / gesture / suggestion-pick arrives via `KeyboardActionListenerImpl → LatinIME.onCodeInput / .onEndBatchInput` and routes through `handleNonSeparatorEvent`, `handleSeparatorEvent`, `handleBackspaceEvent`, `onStartBatchInput`, `onUpdateTailBatchInputCompleted`, etc. It owns `mWordComposer` (the live composing word) and `mLastComposedWord` (revertibility info for the last commit).
- **`RichInputConnection`** is the cached InputConnection wrapper. Anything that has to land in the editor goes through `commitText`, `commitCodePoint`, `deleteTextBeforeCursor`, etc.

The HeliBoard 5-file pref pattern is preserved everywhere: each new pref shows up in `Settings.java` (key constant) → `Defaults.kt` (default) → `SettingsValues.java` (`public final` field + read in ctor) → `res/values/strings.xml` (title + summary) → `settings/screens/*.kt` (Setting{} entry + add() in the items list). `SettingsContainer.createSettings()` auto-aggregates the screen lists.

---

## 2. Phases of the PR

The PR landed in three architectural waves. The current state reflects wave 3 — earlier waves are listed for context because their constants and helpers remain in the codebase (some dormant for backwards-compat).

| Wave | Commits | What it built | Status |
| ---: | :--- | :--- | :--- |
| 1 | `ea078058`–`13db794b` | Scaffolding + per-feature prefs (manual spacing, autospace grace, tap-during-swipe, tap-promotion, fragment backspace, apostrophe key, dual-thumb hinting, debug overlay) | Foundation; mostly still used |
| 2 | `72acb111`, `37f45e67` | Tap-promotion refactor (drop deferred multi-tap chain in `PointerTracker`, move "extend" decision to `InputLogic`) + visual flash on autospace | Flash later replaced by progress bar |
| 3 | `b921c58f`–`f53a8e97` | **Unified combining-mode state machine** (replaces wave-1/2's split grace + tap-promotion + flash) | Current production design |

The rest of this guide walks through wave 3 in detail, calling out the wave 1/2 mechanisms it superseded so a reviewer can match what's in the code to what the design ended up wanting.

---

## 3. The combining-mode state machine

### 3.1 Goal

Replace three separate features that the user found unintuitive:

1. **Autospace grace** (`PREF_GESTURE_AUTOSPACE_GRACE_MS`): only applied between gestures.
2. **Tap-promotion window** (`PREF_GESTURE_TAP_PROMOTION_MS`): only let a single tap extend a follow-up gesture.
3. **Autospace flash** (`PREF_AUTOSPACE_VISUAL_HINT`): a per-event 220 ms space-bar flash decoupled from any state.

…with one unified state machine: *after any composing-word-extending event (tap **or** gesture), wait a configurable grace; any new input within the window extends the same composing word; on expiry, commit + autospace*. The user gets one mental model and one visual indicator.

### 3.2 State and data structures (`InputLogic.java`)

```java
private final Handler  mCombiningHandler = new Handler(Looper.getMainLooper());
@Nullable private Runnable mPendingCombiningCommit;
private boolean        mInCombiningMode;
private boolean        mAutospaceAlternativesPending;       // behavior #3 one-shot
private int            mAutoCommitRevertLength;             // for keep_alternatives mode
private boolean        mInsertTrailingSpaceAfterPick;       // re-inserts space after a revert+pick
private int            mLastGestureCommittedLength;         // for whole-word backspace
```

The Handler is anchored to the main looper (same thread as touch events + `LatinIME.onCodeInput`), so we don't need locking — every read/write happens on the UI thread.

### 3.3 The lifecycle (`enterCombiningMode` / `onCombiningGraceExpired` / `cancelCombiningMode`)

```
                  tap letter or gesture lifts
 ┌────────────┐  ─────────────────────────────►  ┌──────────────────┐
 │   IDLE     │                                  │   COMBINING       │
 │ normal kb  │   ◄─────────────────────────────  │   spacebar       │
 └────────────┘     timer expires:                │   progress bar    │
       ▲            commit (± autocorrect) +     │   running         │
       │            autospace + UI cleanup       └───────┬──────────┘
       │                                                 │ new tap/gesture
       │                                                 │ within grace
       │                                                 │ (cancels timer,
       │                                                 │ extends compose,
       │                                                 │ restarts timer)
       └─────────────────────────────────────────────────┘
   backspace / separator / cursor move / cancel → cancelCombiningMode
```

The grace itself is read from `SettingsValues`:

```java
final int graceMs = baseGraceMs
    + (fromTap ? Math.max(0, mCombiningTapExtraMs) : 0);
```

The `fromTap` flag is set at the two call sites — taps get `baseGraceMs + tapExtraMs`, gestures get just `baseGraceMs`. Rationale: peck-typists need more time between letters than swipers between gestures, but bundling that into one base grace would slow down the swipe→swipe case.

Cancel paths are wired into every input that meaningfully leaves the "extend current word" context: `handleBackspaceEvent`, `handleSeparatorEvent`, `resetEntireInputState` (which `resetComposingState` cancels via, then `handleBackspaceEvent`'s mid-recorrection branch), `onCancelBatchInput`. `enterCombiningMode` *also* cancels (then re-arms) so taps in quick succession cleanly refresh the timer instead of stacking.

### 3.4 Gesture-extend decision (`onStartBatchInput` + `onUpdateTailBatchInputCompleted`)

The crux of "this gesture extends the composing word" is a single boolean. Wave 1 had two separate gates (manual-spacing OR tap-promotion-window). Wave 3 simplifies to:

```java
final boolean wasInCombiningMode = mInCombiningMode;   // snapshot first
cancelCombiningMode();                                 // gesture will re-arm on completion
…
final boolean combiningExtend = wasInCombiningMode
        && cursorAtEndOfComposingWord;
final boolean extendComposingWord =
        manualSpacingExtend || combiningExtend;
mGestureExtendsByTapPromotion = combiningExtend;       // legacy field, still consumed below
```

The `mGestureExtendsByTapPromotion` field name is preserved from wave 1 because `onUpdateTailBatchInputCompleted` reads it to decide concat-vs-replace. The semantics it represents are now "this gesture should extend whatever the composing word currently holds", which is the same condition we want.

### 3.5 Tap seeding (PointerTracker → InputLogic) — the "silo" fix

**Problem.** With pure concat, the recognizer sees only the new gesture's points. For a short follow-up swipe like `i→l→o`, that's too few points to pin a word reliably, so `"s" + swipe-of-ilo` produced `"s older"` instead of `"silo"`.

**Solution.** When combining mode is active and the last input was a single-letter letter-tap within the grace window, seed `BatchInputArbiter.addDownEventPoint` with the tap's `(x, y, time)`. The recognizer then sees a continuous `s→i→l→o` stroke and produces `"silo"` reliably.

The seed letter is tracked statically in `PointerTracker`:

```java
private static int sLastLetterTapX, sLastLetterTapY;
private static long sLastLetterTapTime;
private static int sLastLetterTapCodepoint;          // 0 = none
private static int sCurrentGestureSeedCodepoint;     // 0 = no seed for current gesture

public static int consumeGestureSeedCodepoint() {
    final int seed = sCurrentGestureSeedCodepoint;
    sCurrentGestureSeedCodepoint = 0;
    return seed;
}
```

The decision lives at the top of the `if (mIsDetectingGesture)` block in `onDownEvent`. The seed only fires for *first*-finger downs (`!sInGesture`) so a two-thumb gesture doesn't get seeded twice.

**The catch — and how InputLogic handles it.** When the recognizer dutifully includes the seed letter in its output (`"silo"`, `"hnology"`, etc.), naive concat with the still-active composing word would double-count it (`"s" + "silo" = "ssilo"`). So `onUpdateTailBatchInputCompleted` consumes the seed and conditionally strips the leading letter:

```java
final int seedCp = PointerTracker.consumeGestureSeedCodepoint();
if (seedCp > 0 && batchInputText.length() > 0) {
    final int firstCp = batchInputText.codePointAt(0);
    if (Character.toLowerCase(firstCp) == Character.toLowerCase(seedCp)) {
        batchInputText = batchInputText.substring(Character.charCount(firstCp));
    }
}
```

The "if it matches" guard is what lets the same code path also handle `"tech" + 'h' tap + swipe-nology → "technology"` correctly: composing is `"tech"`, seed is `'h'`, batch returns `"hnology"`, leading `'h'` matches → strip → `"nology"` → concat with `"tech"` → `"technology"`. And `"s" + swipe-ilver` (no seed match in result): composing `"s"`, seed `'s'`, batch returns `"ilver"`, no leading-`'s'` match → no strip → concat → `"silver"`. Same code, no special cases.

### 3.6 Timer-driven commit (`onCombiningGraceExpired`)

When the grace timer fires the commit needs to:

1. Commit the composing word (with or without autocorrect per `PREF_COMBINING_AUTOCORRECT_ON_AUTOSPACE`).
2. Insert an autospace via the existing `insertAutomaticSpaceIfOptionsAndTextAllow` (URL/email guards still apply).
3. Update `mLastComposedWord` so `revertCommit` (triggered by backspace + `PREF_BACKSPACE_REVERTS_AUTOCORRECT`) knows about the trailing space.
4. Hand the suggestion strip off according to `PREF_COMBINING_AUTOSPACE_SUGGESTIONS` (next-word predictions / keep alternatives / hybrid).
5. Set the post-commit one-shots that drive backspace + suggestion-pick semantics later.

The mistake the PR went through twice and now handles correctly:

- **Mistake 1:** counted `cursorAfter - cursorBefore` (= 1, the autospace) as the chars to delete on a suggestion-pick revert. Bug: the composing text was already on-screen via `setComposingTextInternal` before the commit, so the commit itself doesn't move the cursor — only the autospace does. Fixed by reading `mLastComposedWord.mCommittedWord.length()` (which captures the post-autocorrect length) and adding autospace chars on top.
- **Mistake 2:** committed with `LastComposedWord.NOT_A_SEPARATOR` (since I inserted the autospace separately), which meant `mLastComposedWord.mSeparatorString` was empty. Backspace's `revertCommit` then computed `deleteLength = cancelLength + 0`, and its DEBUG-build assertion (`getTextBeforeCursor(deleteLength).subSequence(0, cancelLength) equals committedWord`) crashed because the slice ended up including the trailing space. Fixed by detecting whether autospace was actually inserted (cursor delta around the helper call) and, if so, rebuilding `mLastComposedWord` with `mSeparatorString = " "`. `LastComposedWord` is immutable in field-final-ness so the rebuild constructs a new instance with the same `mEvents` / `mInputPointers` / `mNgramContext` etc.

### 3.7 Post-autospace suggestion-strip behaviour

The user explicitly asked for three options:

| Mode (`PREF_COMBINING_AUTOSPACE_SUGGESTIONS`) | Behaviour |
| --- | --- |
| `next_word` (default) | Calls `handler.postUpdateSuggestionStrip(INPUT_STYLE_NONE)` — matches normal space-tap. |
| `keep_alternatives` | Leaves the gesture/typing strip alone. Sets `mAutoCommitRevertLength` so a follow-up pick reverts the auto-commit (see §3.8). |
| `alternatives_then_next_word` | Hybrid: leaves the strip alone *and* arms `mAutospaceAlternativesPending`. The next space tap is intercepted (BEFORE `cancelCombiningMode` clears the flag) and swaps the strip to next-word predictions without inserting a second space; the second space tap inserts a real space normally. |

Field-lifecycle is the subtle part: `mAutospaceAlternativesPending` and `mAutoCommitRevertLength` clear on `enterCombiningMode` (any new input means the user moved on), on `cancelCombiningMode` (cancel paths represent leaving the auto-committed context), and after their own consumers fire.

### 3.8 Revert-on-pick (`onPickSuggestionManually`)

Without this, picking an alternative from the kept-alternatives strip would *append* the picked text after the auto-committed word + space (`"teh " → "teh the"`), because the composing word is already gone — `commitChosenWord` lands at the post-autospace cursor.

The hook at the top of `onPickSuggestionManually`:

```java
if (mAutoCommitRevertLength > 0 && !mWordComposer.isComposingWord()) {
    mConnection.beginBatchEdit();
    mConnection.deleteTextBeforeCursor(mAutoCommitRevertLength);
    mConnection.endBatchEdit();
    mAutoCommitRevertLength = 0;
    mSpaceState = SpaceState.NONE;
    mInsertTrailingSpaceAfterPick = true;
}
```

`mInsertTrailingSpaceAfterPick` is consumed just after `commitChosenWord` later in the same method so the cursor lands at `"the |"` (not `"the|"`). Without that, only `SpaceState.PHANTOM` would be set, and the space would only materialize when the next character arrived — visually jarring.

### 3.9 Whole-word backspace after a gesture

After the timer auto-commits a gesture-mode word, the first backspace should wipe the whole word + space in one tap. Two ordering rules:

1. **Autocorrect-revert always wins.** If `mLastComposedWord.canRevertCommit() && mBackspaceRevertsAutocorrect`, that path runs first (existing behaviour). Once it does, the typed word is restored as composing and the gesture-word-delete flag becomes moot.
2. **Otherwise**, if `mLastGestureCommittedLength > 0 && PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD`, delete that many chars and return.

`handleBackspaceEvent` snapshots `mLastGestureCommittedLength` at the top of the method, BEFORE `cancelCombiningMode` wipes it. That's how the snapshot survives long enough to be consumed in the else-branch below.

The field is set in `onCombiningGraceExpired` only when `mWordComposer.isBatchMode()` was true at commit time — so tap-only words leave the field at 0 and keep their char-by-char delete behaviour.

### 3.10 Visual indicator (`MainKeyboardView.java`)

Wave 2 added a fading 220 ms `mSpaceFlashAlpha`-driven overlay on the space key. Wave 3 deleted that entirely and replaced it with a **countdown progress bar**:

```java
private boolean mCombiningModeActive;
private long    mCombiningStartTimeMs;
private int     mCombiningGraceMs;
@Nullable private ValueAnimator mCombiningAnimator;
```

`setCombiningMode(active, startTimeMs, graceMs)` cancels any in-flight animator, updates the state, and (when activating) starts a new `ValueAnimator` whose update listener invalidates the space key each frame. The actual draw is in `onDrawKeyTopVisuals` when `code == Constants.CODE_SPACE`:

```java
final float remainingFrac =
        Math.max(0f, 1f - (float)(now - mCombiningStartTimeMs) / mCombiningGraceMs);
final float barHeight = Math.max(2f, key.getHeight() * 0.10f);
final float barWidth  = key.getWidth() * remainingFrac;
canvas.drawRect(0f, key.getHeight() - barHeight, barWidth, key.getHeight(), paint);
```

An earlier iteration drew a translucent themed overlay across the whole keyboard too; the user found that too aggressive so it was removed. The spacebar bar alone is now the sole feedback for combining-mode activity.

---

## 4. Pref reference

| Constant | Default | Wired? | Notes |
| --- | :---: | :---: | --- |
| `PREF_COMBINING_GRACE_MS` | 0 | ✓ | Master switch — 0 disables every combining-mode behaviour below |
| `PREF_COMBINING_TAP_EXTRA_MS` | 250 | ✓ | Added on top of grace when the trigger was a letter tap (not a gesture) |
| `PREF_COMBINING_AUTOCORRECT_ON_AUTOSPACE` | true | ✓ | Whether the timer's commit runs `commitCurrentAutoCorrection` vs. `commitTyped` |
| `PREF_COMBINING_AUTOSPACE_SUGGESTIONS` | `next_word` | ✓ | Tri-state: `next_word` / `keep_alternatives` / `alternatives_then_next_word` |
| `PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD` | true | ✓ | First backspace after a gesture-auto-commit deletes the whole word + space |
| `PREF_GESTURE_MANUAL_SPACING` | false | ✓ | Original Nintype-style "never auto-commit" mode (orthogonal to combining grace) |
| `PREF_GESTURE_FRAGMENT_BACKSPACE` | false | ✓ | Sub-option of manual spacing — backspace pops one fragment instead of one char |
| `PREF_GESTURE_TAP_DURING_SWIPE` | false | ✓ | When a finger taps mid-swipe of another finger, fold the tap into the swipe |
| `PREF_GESTURE_TAP_AS_SWIPE_WINDOW_MS` | 60 | ✓ | Max tap duration before it stops counting as part of an ongoing swipe |
| `PREF_GESTURE_DUAL_THUMB_HINTING` | false | ✓ | Post-process the gesture's points before handing to the recognizer (`DualThumbHinter`) |
| `PREF_GESTURE_DUAL_THUMB_MIDLINE_PCT` | 50 | ✓ | Left/right hand split for the hinter (proximity guard) |
| `PREF_GESTURE_DEBUG_DRAW_POINTS` | false | ✓ | Overlay raw + synthetic gesture points (`GestureDebugPointsDrawingPreview`) |
| `PREF_GESTURE_APOSTROPHE_KEY` | false | ✓ (toggle) | Surfaces a swipeable apostrophe key when supported by the active layout |
| `PREF_GESTURE_AUTOSPACE_GRACE_MS` | 0 | dormant | Wave-1 grace; superseded by combining mode but kept for shared_prefs backwards-compat |
| `PREF_GESTURE_TAP_PROMOTION_MS` | 0 | dormant | Wave-1/2 promotion window; superseded |
| `PREF_AUTOSPACE_VISUAL_HINT` | true | dormant | Wave-2 flash; replaced by the progress bar |

"Dormant" means: still defined, still in `SettingsValues`, but no live read of the field affects behaviour. They're left in so previous shared_prefs aren't orphaned on upgrade. The UI no longer surfaces them in `TwoThumbTypingScreen`.

---

## 5. New files

| File | Why it was added |
| --- | --- |
| `keyboard/internal/DualThumbHinter.java` | Pre-recognizer hinter for `PREF_GESTURE_DUAL_THUMB_HINTING`. Reinforces tap-bursts that overlap an active stroke with synthetic on-stroke waypoints so the library doesn't under-weight them; proximity-guards against opposite-hand stray taps. Independent of the combining-mode work; lives in `BatchInputArbiter`'s commit path. |
| `keyboard/internal/GestureDebugPointsDrawingPreview.java` | Overlay rendering for `PREF_GESTURE_DEBUG_DRAW_POINTS`. Draws raw recognizer-input points and any synthetic points injected by the hinter. Used for empirical tuning when iterating on hinting. |
| `settings/screens/TwoThumbTypingScreen.kt` | Dedicated settings screen so the experimental options don't clutter the main gesture-typing screen. Conditional-visibility logic groups sub-options under their parents. |
| `docs/TWO_THUMB_TYPING_INTERNALS.md` | This document. |

`FEATURES.md` and `MainSettingsScreen.kt` got short routing additions so users find the new screen.

---

## 6. Files modified (summary)

| File | What changed |
| --- | --- |
| `latin/settings/Settings.java` | 6 new `PREF_COMBINING_*` keys + 9 wave-1 `PREF_GESTURE_*` keys |
| `latin/settings/Defaults.kt` | Matching defaults (off / 0 for behaviour-changing prefs; `true` for the autocorrect-on-autospace toggle; `250` for tap-extra) |
| `latin/settings/SettingsValues.java` | Matching `public final` fields + `prefs.get*` reads in the ctor |
| `latin/inputlogic/InputLogic.java` | The combining-mode state machine itself (§3.2–§3.9). Hooks on `handleNonSeparatorEvent`, `onStartBatchInput`, `onUpdateTailBatchInputCompleted`, `handleSeparatorEvent`, `handleBackspaceEvent`, `onPickSuggestionManually`, `resetComposingState`, `onCancelBatchInput`. |
| `keyboard/PointerTracker.java` | Tap-seeding statics + decision at `onDownEvent`; tap-during-swipe pointer marker + suppression at `onUpEventInternal`; `mayEndBatchInput` now always passes `graceMs = 0` so the wave-1 `BatchInputArbiter` grace path is dormant. |
| `keyboard/MainKeyboardView.java` | Removed the flash overlay; added `setCombiningMode` + the spacebar progress bar; new field-set + `ValueAnimator`. |
| `keyboard/internal/BatchInputArbiter.java` | Wave-1 added the static grace handler (`sPendingGraceRunnable`, `continuePendingGesture`, `flushGrace`, `isGracePending`). Wave 3 doesn't activate it (we pass `graceMs = 0`) but the API surface remains for the dual-thumb-hinter and dormant pref. |
| `keyboard/internal/DrawingProxy.java` | Two interface additions (`clearGestureDebugPoints`, `setGestureCommitPending`) used by the hinter and the wave-1 ellipsis preview. The ellipsis is dormant in wave 3 but the API is left so `PointerTracker`'s defensive-cleanup paths stay intact. |
| `keyboard/internal/GestureFloatingTextDrawingPreview.java` | Wave-1 ellipsis support (`mIsCommitPending`). Dormant in wave 3. |
| `settings/screens/TwoThumbTypingScreen.kt` | Visibility/ordering for the new prefs; dormant wave-1 prefs removed from the screen. |
| `settings/screens/GestureTypingScreen.kt`, `TextCorrectionScreen.kt`, `MainSettingsScreen.kt`, `SettingsContainer.kt`, `SettingsNavHost.kt` | Small routing / cross-ref additions so the new screen is discoverable and the parent screens stay uncluttered. |
| `res/values/strings.xml` | All new pref titles + summaries. Apostrophes in summaries are XML-escaped (`\'`) per the existing convention in this file. |
| `docs/FEATURES.md` | User-facing reference for the experimental section. |

---

## 7. Things intentionally left as-is

- **`BatchInputArbiter` static grace machinery** is still there but always passed `graceMs = 0`. Could be removed later, but the API is also exercised by the dual-thumb hinter path and the dormant `PREF_GESTURE_AUTOSPACE_GRACE_MS`. Low priority.
- **`mGestureExtendsByTapPromotion` field name** preserved despite the field now meaning "this gesture extends via combining mode". Renaming would touch a lot of comments without changing behaviour.
- **`mAutospaceVisualHint`**, **`mGestureTapPromotionMs`**, **`mGestureAutospaceGraceMs`** in `SettingsValues` are read but unused. Kept so shared_prefs files from previous LeanType installs continue to load without warnings.

---

## 8. Known caveats / future work

- **Gesture-recognition accuracy with two thumbs** is bounded by the native glide-typing library. The PR's seed + concat trick fixes the common single-thumb-tap-then-swipe case (`"silo"`, `"technology"`) but a simultaneous two-thumb gesture where one thumb taps mid-swipe of the other can still produce odd results — that's where `PREF_GESTURE_DUAL_THUMB_HINTING` and `PREF_GESTURE_DEBUG_DRAW_POINTS` come in, and they remain experimental.
- **Tap-as-swipe** (`PREF_GESTURE_TAP_DURING_SWIPE`) doesn't yet seed the parent stroke with the tap's coordinates — it just suppresses the stray keystroke. Future work could add seeding here too.
- **`alternatives_then_next_word` mode** eats the first space tap to swap the strip. The current implementation doesn't restart the combining-mode timer for that synthetic event (since no composing word exists at that point). Probably correct, but worth keeping an eye on.
- **`tryFragmentBackspace`** (manual-spacing sub-feature) was kept from wave 1. It is independent of the combining-mode timer and only fires under manual spacing — no conflict, but it does mean two backspace-pop mechanisms coexist (one for fragments under manual spacing, one for the gesture-committed-whole-word under combining mode).

---

## 9. Test plan (manual)

| Scenario | Expected | Pref state |
| --- | --- | --- |
| Tap `s`, wait | `s ` autospaced; cursor past the space | grace > 0, autocorrect-on-autospace as you like |
| Tap `s`, swipe `ilo` within grace | `silo ` (seeding kicks in) | grace > 0 |
| Tap `s`, wait beyond grace, swipe `ilo` | `s ilo ` (new word) | grace > 0 |
| Swipe `tech`, swipe `nology` within grace | `technology ` | grace > 0 |
| Swipe `helo`, wait | `hello ` if autocorrect is on; `helo ` otherwise | grace > 0, autocorrect-on-autospace true |
| Tap `I`, wait → backspace | `I` reverted to nothing (or undo autocorrect if applied) | grace > 0 |
| Swipe `hello`, wait → backspace | empty (whole-word delete) | gesture-word-backspace true |
| Swipe `helo` (autocorrected to `hello`), wait → backspace | `helo` reappears as composing (autocorrect-revert wins) | both revert-autocorrect and gesture-word-backspace on |
| Tap `teh`, wait → tap `the` in strip | `the ` (revert + replace) | mode = keep_alternatives |
| Tap `teh`, wait → tap space | strip switches to next-word; no second space | mode = alternatives_then_next_word |
| Tap `teh`, wait → tap space → tap space | first swap, second inserts a real space | mode = alternatives_then_next_word |
