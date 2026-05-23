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

The PR landed in four architectural waves. The current state reflects wave 4 — earlier waves are listed for context because their constants and helpers remain in the codebase (some dormant for backwards-compat).

| Wave | Commits | What it built | Status |
| ---: | :--- | :--- | :--- |
| 1 | `ea078058`–`13db794b` | Scaffolding + per-feature prefs (manual spacing, autospace grace, tap-promotion, fragment backspace, apostrophe key, dual-thumb hinting, debug overlay) | Foundation; mostly still used |
| 2 | `72acb111`, `37f45e67` | Tap-promotion refactor (drop deferred multi-tap chain in `PointerTracker`, move "extend" decision to `InputLogic`) + visual flash on autospace | Flash later replaced by progress bar |
| 3 | `b921c58f`–`f53a8e97` | **Unified combining-mode state machine** (replaces wave-1/2's split grace + tap-promotion + flash) | Current production design |
| 4 | `3330dfd4`–`f2d66c3d` | Toolbar toggles (AUTOSPACE / AUTO_CAP / FORCE_AUTO_CAP), forward-delete keycode, daily-driver fixes (PHANTOM after auto-commit, auto-cap gesture results) | Current |
| 5 | `fedd3932`–`4040e755` | **Multi-part word composition** (swipe+swipe, tap+swipe, swipe+tap → one composing word) on `copilot/multi-part-words`, merged into `main` as `791344b3` | Current |

The rest of this guide walks through waves 3 and 4 in detail, calling out the wave 1/2 mechanisms they superseded so a reviewer can match what's in the code to what the design ended up wanting.

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

### 3.11 Wave-4 daily-driver fixes

Two regressions surfaced once the user started typing real prose with combining mode on:

**PHANTOM after timer-driven autospace.** The timer-driven commit used to set
`mSpaceState = SpaceState.NONE` after inserting the autospace. That's wrong:
HeliBoard's normal flow uses `SpaceState.PHANTOM` to mark "this trailing space
is soft — strip it if a punctuation character follows so the user gets
`hello, world` not `hello , world`". With `NONE`, the existing
`handleSeparatorEvent` PHANTOM-aware strip path was never taken. Fix is one
line in `onCombiningGraceExpired`:

```java
mSpaceState = autospaceInserted ? SpaceState.PHANTOM : SpaceState.NONE;
```

`autospaceInserted` was already being computed (cursor delta around the
`insertAutomaticSpaceIfOptionsAndTextAllow` call) for the `revertCommit`
fix-up; it doubles here. URL/email contexts (where the helper skipped the
space) stay on `NONE`.

**Gesture results honour shift state.** The recognizer always emits lowercase,
so swiping `"Hello"` at the start of a sentence produced `"hello"`. The fix
sits at the top of `onUpdateTailBatchInputCompleted`, right after the seed
strip and before `prevTypedWord` concat, and is gated on
`!extendExistingCompose` so continuation gestures keep the casing the user set
for the head of the word:

```java
if (!extendExistingCompose && !batchInputText.isEmpty()) {
    final int shiftMode = keyboardSwitcher.getKeyboardShiftMode();
    if (shiftMode == CAPS_MODE_MANUAL_SHIFTED
            || shiftMode == CAPS_MODE_AUTO_SHIFTED) {
        batchInputText = StringUtils.capitalizeFirstCodePoint(batchInputText, locale);
    } else if (shiftMode == CAPS_MODE_AUTO_SHIFT_LOCKED
            || shiftMode == CAPS_MODE_MANUAL_SHIFT_LOCKED) {
        batchInputText = batchInputText.toUpperCase(locale);
    }
}
```

The shift mode comes from `keyboardSwitcher.getKeyboardShiftMode()` which is
already kept in sync by `requestUpdatingShiftState` calls scattered through
the codebase. We don't need to recompute auto-caps state here; the keyboard's
visible shift indicator IS the source of truth.

---

## 3a. Toolbar toggles (wave 4)

A small but visible addition: three new toolbar keys, each modelled on the
existing `AUTOCORRECT` toolbar key. The same 7-touchpoint pattern repeats for
each: KeyCode + checkAndConvertCode allow-list, `ToolbarKey` enum entry +
`getCodeForToolbarKey` mapping, three `KeyboardIconsSet` icon-set entries
(default / lxx / rounded), `KeyboardActionListenerImpl` handler with
`invalidateAllKeys` so the activated state refreshes immediately,
`Settings.toggle*` method, `setToolbarButtonActivatedState` clause,
`setToolbarButtonsActivatedStateOnPrefChange` pref-key trigger, and an
accessibility content-description string.

| ToolbarKey | KeyCode | Underlying pref | Notes |
| --- | :---: | --- | --- |
| `AUTOSPACE` | `-246` | New `PREF_AUTOSPACE_ENABLED` (default true) | Master switch ANDed into `SettingsValues.shouldInsertSpacesAutomatically()`. The input-type guard (password / email / URL) still applies on top, so the toolbar button shows as *inactive* in those fields even when the master is on — matching reality. |
| `AUTO_CAP` | `-247` | Existing `PREF_AUTO_CAP` | Plain wrapper. Activated-state reads `Settings.getValues().mAutoCap`. |
| `FORCE_AUTO_CAP` | `-248` | Existing `PREF_FORCE_AUTO_CAPS` | Plain wrapper. Activated-state reads `Settings.getValues().mForceAutoCaps`. |

Defaults for the toolbar itself are **unchanged** — neither key is in
`defaultToolbarPref` or `defaultPinnedToolbarPref`, so users opt in via the
toolbar customizer dialog. This is a separate decision from the prefs they
toggle.

`AUTO_CAP` / `FORCE_AUTO_CAP` were delegated to a sub-agent on a parallel
branch (`add-autocap-toolbar-toggles`) and merged into `main` first, then
forward-merged into `copilot/improve-two-thumb-typing` — the conflict
resolution was uniformly "both branches added an entry in the same enum / when
/ icon map", concatenated trivially.

---

## 4. Multi-part word composition (wave 5)

### 4.1 Goal

Compose ONE word from multiple input fragments — `tech` (swipe) + `nology`
(swipe) → `technology`, `tech` (swipe) + `y` (tap) → `Techy`, `te` (tap) +
`chnology` (swipe) → `technology`. Backspace pops the last fragment so
fixing a misjoined word is one keystroke. Triggered by combining mode: as
long as the grace timer is running, the next input extends the current
composing word.

Wave 3's combining-mode gave us the timing; the remaining work was making
the gesture lib actually *recognise* the joined word. Wave-1 manual-spacing
had the same architecture (concat `prevTypedWord` + new batch result) but
inherited the same bug — the recognizer only sees the new fragment and
returns nonsense for it (`biology` for the `nology` half of `technology`).

### 4.2 New prefs

| Pref key | Default | Purpose |
| --- | :---: | --- |
| `PREF_MULTIPART_AUTO_EXTEND_IN_COMBINING` | true | Combining mode implies extend for next input |
| `PREF_MULTIPART_TAP_SEED_GESTURE` | true | Seed the next gesture's pointer trail from the previous fragment |
| `PREF_MULTIPART_FULL_WORD_SUGGESTIONS` | true | Strip shows alternatives for the whole composing word (not the last fragment) |
| `PREF_MULTIPART_JOIN_KEY_MODE` | `"off"` | Explicit "join next" modifier — scaffolded, UX TBD |

`PREF_GESTURE_FRAGMENT_BACKSPACE` was bumped from default-off to default-on
so backspace-pops-fragment "just works" once multi-part composition is on.

### 4.3 Extending via combining mode (todo 1 — `combining-extend-swipes`)

The wave-3 gesture-extend decision had two gates (`mGestureManualSpacing` /
`mGestureExtendsByTapPromotion`). Wave 5 adds a third:

```java
final boolean combiningExtendsSwipe = sv.mMultipartAutoExtendInCombining
        && sv.mCombiningGraceMs > 0
        && mInCombiningMode;
final boolean extendExistingCompose = (sv.mGestureManualSpacing
                || mGestureExtendsByTapPromotion
                || combiningExtendsSwipe)
        && mWordComposer.isComposingWord()
        && !mWordComposer.isCursorFrontOrMiddleOfComposingWord();
```

That same condition is mirrored at `onStartBatchInput` (so the lib gets the
right snapshot at gesture-start) and at `onUpdateTailBatchInputCompleted`
(where the concat happens).

### 4.4 Full-word suggestions (todo 4 — `suggestion-strip-full-word`)

Wave 1's extend path called `setSuggestedWords(EMPTY)` after concat. That
felt safer ("the gesture lib's suggestions are for one fragment so we'd
mislead the user by showing them"). But it meant after every multi-part
gesture the strip went blank, and the user had no way to recover from a bad
recognition without a backspace dance. Wave 5 replaces the blank with a
normal suggestion run over the new full composing word:

```java
if (sv.mMultipartFullWordSuggestions) {
    performUpdateSuggestionStripSync(sv, SuggestedWords.INPUT_STYLE_TYPING);
} else {
    setSuggestedWords(SuggestedWords.getEmptyInstance());   // legacy
}
```

This is what made the recognizer-accuracy problem survivable while we
worked on the actual recogniser fix — even if `tech+nology` came out wrong,
the strip now showed `technology` as an option.

### 4.5 The merged-trail fix (todo 3 — `tap-then-swipe-seed`, the big one)

Multiple attempts:

1. **First try — concat only, trust top suggestion.** Got `techbiology`
   because the lib was fed only the `nology` swipe's points in isolation.
2. **Second try — prefer-seed-letter pick from the suggestion list.**
   Scanned the lib's top-5 for a candidate starting with the seed letter.
   Better but unreliable: the lib literally never produces continuation
   words like `hnology`, so for `te+chnology` it returned things like
   `[exhuming, echoing, ecology, ...]` — nothing actually scoreable as
   "this is the continuation".
3. **Third try — feed the lib both fragments' raw pointers via
   `InputPointers.appendAll`.** Regressed `silo` (the lib output `I` because
   the tap fragment's `time=0` synthetic coords created a huge time
   discontinuity at the merge boundary).
4. **Final fix — feed both fragments with *re-timed* pointers.** This is the
   committed solution (`4040e755`).

The mechanism lives in `WordComposer`:

```java
// Multi-part word composition (#1.6): snapshot of the prior fragment(s)
// gesture trail. When set, every subsequent setBatchInputPointers call
// PREPENDS this base with RE-TIMED coordinates so the merged stream looks
// like one continuous stroke to the native gesture lib.
private final InputPointers mExtendBatchInputBase = new InputPointers(MAX_WORD_LENGTH);
private boolean mExtendBatchInputBaseSet;
private static final int EXTEND_BASE_POINT_INTERVAL_MS = 25;
private static final int EXTEND_BASE_GAP_BEFORE_NEW_MS  = 60;
```

`setBatchInputPointers` is the merge point. The lib calls it once per
gesture-progress update; we transparently splice the prior trail in:

```java
public void setBatchInputPointers(final InputPointers batchPointers) {
    if (mExtendBatchInputBaseSet && mExtendBatchInputBase.getPointerSize() > 0
            && batchPointers.getPointerSize() > 0) {
        final int baseSize = mExtendBatchInputBase.getPointerSize();
        final int firstNewTime = batchPointers.getTimes()[0];
        final int baseLastTime = firstNewTime - EXTEND_BASE_GAP_BEFORE_NEW_MS;
        final int baseFirstTime = baseLastTime - (baseSize - 1) * EXTEND_BASE_POINT_INTERVAL_MS;
        mInputPointers.reset();
        for (int i = 0; i < baseSize; i++) {
            mInputPointers.addPointer(baseX[i], baseY[i], 0,
                    baseFirstTime + i * EXTEND_BASE_POINT_INTERVAL_MS);
        }
        mInputPointers.appendAll(batchPointers);
    } else {
        mInputPointers.set(batchPointers);
    }
    mIsBatchMode = true;
}
```

Re-timing is the whole game. The 25 ms inter-point interval looks like a
naturally hand-drawn swipe; the 60 ms gap before the new gesture's first
point is well within the lib's "single continuous stroke" tolerance. Both
constants were picked empirically — bigger gaps started looking like
distinct strokes; smaller ones made the recognizer pick up spurious
high-velocity glyphs at the merge boundary.

The "extend base" gets set at `onStartBatchInput` when extending, *before*
the lib runs, and cleared at `onUpdateTailBatchInputCompleted` after it's
been consumed:

```java
// onStartBatchInput
if (extendComposingWord
        && sv.mMultipartAutoExtendInCombining
        && sv.mCombiningGraceMs > 0) {
    mWordComposer.setExtendBatchInputBase(mWordComposer.getInputPointers());
}

// onUpdateTailBatchInputCompleted
final boolean usedMergedTrail = mWordComposer.isExtendBatchInputBaseSet();
mWordComposer.setExtendBatchInputBase(null);
final String prevTypedWord = (extendExistingCompose && !usedMergedTrail)
        ? mWordComposer.getTypedWord() : "";
```

The `!usedMergedTrail` gate is critical: when the merged-trail path
produces the *whole* word in the lib's output (e.g. `technology`),
concatenating `prevTypedWord` ("tech") again would give `techtechnology`.
The legacy wave-1 concat path still applies for manual-spacing mode (where
the lib still sees only one fragment).

### 4.6 Disabling the PointerTracker single-point seed under multi-part

Wave 3's "silo" fix planted a single synthetic point at the previous tap's
coordinate. With the wave-5 merged-trail approach that point would (a)
duplicate context already in the extend base and (b) be at the merge
boundary with a stale-time, exactly the kind of discontinuity that breaks
the recognizer. So the seed is suppressed when multi-part is on:

```java
final boolean multipartExtendActive = sv.mMultipartAutoExtendInCombining
        && sv.mCombiningGraceMs > 0;
if (!multipartExtendActive
        && sv.mCombiningGraceMs > 0
        && sLastLetterTapCodepoint > 0 …) {
    // wave-3 seed (now only used when multi-part is off)
}
```

Result: `silo` still works (the merged-trail path covers the same case more
robustly), and multi-part swipes don't fight the seed.

### 4.7 Prefer plain-letter suggestions when extend-base is active

The lib occasionally ranks obscure hyphenated/CamelCase dictionary entries
above the obvious answer — for `te+chnology` we saw
`[technon-U, technology, techMolly, techbiology, techmollify]`. When the
merged-trail path is active and the top suggestion looks "weird" (contains
non-letters or mid-word capitals), walk the list and prefer the
highest-ranked plain-letter candidate:

```java
private static boolean isPlainLetterWord(final String s) {
    for (int i = 0; i < s.length(); ) {
        final int cp = s.codePointAt(i);
        if (cp == '\'') {
            // apostrophes ok
        } else if (!Character.isLetter(cp)) {
            return false;
        } else if (i > 0 && Character.isUpperCase(cp)) {
            return false;   // mid-word capital
        }
        i += Character.charCount(cp);
    }
    return true;
}

if (mWordComposer.isExtendBatchInputBaseSet()
        && !TextUtils.isEmpty(batchInputText)
        && !isPlainLetterWord(batchInputText)) {
    for (int i = 1; i < suggestedWords.size(); i++) {
        final String cand = suggestedWords.getWord(i);
        if (cand != null && !cand.isEmpty() && isPlainLetterWord(cand)) {
            batchInputText = cand;
            break;
        }
    }
}
```

Only kicks in when the merged-trail path is active, so single-fragment
gestures aren't affected.

### 4.8 Fragment-boundary backspace under multi-part (todo 5)

Wave 1's `recordFragmentBoundaryIfTracking` and `tryFragmentBackspace`
required *both* `mGestureManualSpacing` and `mGestureFragmentBackspace`.
Wave 5 adds a second eligibility path so multi-part composition (no
manual-spacing required) also tracks + pops boundaries:

```java
final boolean legacyTracking = sv.mGestureManualSpacing
        && sv.mGestureFragmentBackspace;
final boolean multipartTracking = sv.mMultipartAutoExtendInCombining
        && sv.mCombiningGraceMs > 0
        && sv.mGestureFragmentBackspace;
if (!legacyTracking && !multipartTracking) return;
```

This wired through both `recordFragmentBoundaryIfTracking` (the producer,
already called from `handleNonSeparatorEvent` for tap-extensions and from
`onUpdateTailBatchInputCompleted` for gesture-extensions) and
`tryFragmentBackspace` (the consumer in `handleBackspaceEvent`). Backspace
now snaps the composing word back to the previous boundary in one tap, no
matter whether the fragment was a tap or a swipe.

### 4.9 The PHANTOM-race fix (related, from end of wave 4)

A subtle bug that the multi-part work exposed: with `mAutospaceAfterGestureTyping`
on, gesture-end set `mSpaceState = SpaceState.PHANTOM`. When the user
immediately tapped the next letter (e.g. `tech` → `y`),
`handleNonSpecialCharacterEvent`'s PHANTOM branch would commit the
composing word ("tech"), insert an autospace, and only THEN start a new
composing word for `y` — giving `tech y` instead of `Techy`.

The fix gates the PHANTOM-set on `mCombiningGraceMs <= 0`. When combining
mode is on, it owns post-gesture autospace timing via the grace timer —
there shouldn't be a parallel PHANTOM mechanism setting up a race:

```java
if (sv.mAutospaceAfterGestureTyping && !extendExistingCompose
        && sv.mCombiningGraceMs <= 0) {
    mSpaceState = SpaceState.PHANTOM;
}
```

Committed as `a63e9ea3` on `copilot/improve-two-thumb-typing` and carried
through into wave 5.

### 4.10 Settings UI

All four new prefs sit under the existing Two-Thumb Typing screen, grouped
with the existing combining-mode prefs. `PREF_MULTIPART_AUTO_EXTEND_IN_COMBINING`
gates visibility of the other three (no point showing
seed-gesture / full-word-suggestions / join-key options if extension itself
is off). `PREF_GESTURE_FRAGMENT_BACKSPACE` is surfaced in *both* the
multi-part group and the manual-spacing group; `SettingsContainer` dedupes
on key so we don't get duplicate entries.

### 4.11 What's deliberately not done

`PREF_MULTIPART_JOIN_KEY_MODE` is scaffolded (Settings/Defaults/SettingsValues/
strings.xml/UI entry) but no input handler is wired up yet. The intent was
an explicit "force-extend the next input" modifier (long-press space, or a
dedicated symbols-layer key) for cases where the grace window times out
but the user still wants to extend. Pending a UX decision; the pref slot
exists so we don't have to revisit the scaffolding later.

`indicator-armed-state` (a distinct visual indicator for "explicit join
armed" vs "grace window open") is similarly deferred — comes with the
join-key UX work.

---

## 5. Test matrix (manual, on-device)

The intended-behaviour matrix that wave 5 validated against, in case a
future refactor needs to re-check it:

| Input | Combining grace = 0 | Combining grace > 0 |
| --- | --- | --- |
| `silo` (tap `s` + swipe `ilo`) | `s ilo` (legacy) | `silo` ✓ |
| `tech` + `nology` (swipe + swipe) | `tech nology` | `technology` ✓ |
| `tech` + `y` (swipe + tap) | `tech y` (PHANTOM bug, fixed in `a63e9ea3`) | `Techy` ✓ |
| `te` + `chnology` (tap + swipe) | `te chnology` | `technology` ✓ |
| Multi-part word + backspace | char-delete | pops last fragment ✓ (with `PREF_GESTURE_FRAGMENT_BACKSPACE` on, now default) |


### Forward-delete keycode

Marginally related: `KeyCode.FORWARD_DELETE = -9` was previously commented out
in the codebase. Uncommented + allow-listed in `checkAndConvertCode` + mapped
to `KeyEvent.KEYCODE_FORWARD_DEL` in `keyCodeToKeyEventCode`. A new
`KeyLabel.DEL = "del"` constant exposes it for simple-format custom layouts.
No new handler is needed — the existing negative-keycode →
`sendDownUpKeyEventWithMetaState` fallback in `InputLogic` routes it through
the raw key-event path. Useful for the user-imported "power" symbol layout
(not shipped as an asset — see §7).

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
| `keyboard/PointerTracker.java` | Tap-seeding statics + decision at `onDownEvent`; `mayEndBatchInput` now always passes `graceMs = 0` so the wave-1 `BatchInputArbiter` grace path is dormant. |
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
- **Simultaneous tap-while-swiping recognition** now relies on combining/multi-part composition and the experimental point hinter rather than a separate suppression preference. Remaining odd recognizer outputs should be investigated in the gesture data / hinting path.
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
