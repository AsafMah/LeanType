# Composing word: editor text as the single source of truth (design)

> Status: **partially shipped — see "Phases / outcome" below.** The drift-prone-stroke bug
> class this note set out to kill is fixed and on-device validated; the broader "retire all
> parallel state" refactor was *not* pursued in full (the targeted fix proved sufficient and
> the real captured stroke beat synthesized geometry on feel). This note is now the record of
> what shipped and why the plan changed, not a forward plan.
>
> Grounding: claims below were verified by read-only scouting of `InputLogic`,
> `WordComposer`, `RichInputConnection`. File/symbol refs are a snapshot — prefer the named
> symbols and re-confirm at implementation.

## Problem

Two-thumb / multi-part composition keeps several pieces of **gesture state in memory,
parallel to the editor text**, and they drift out of sync:

- `WordComposer.mInputPointers` — the raw stroke buffer. Intentionally *not* cleared by
  `reset()` / backspace (so a gesture commit can re-feed it via `setBatchInputWord`), and
  `ResizableIntArray.addAt` overwrites in place **without shrinking the length**.
- `InputLogic.mLiveStroke` — the live-converge accumulator.
- `WordComposer.mExtendBatchInputBase` — the merged-trail base prepended to each new gesture.
- `InputLogic.mGestureFragmentBoundaries` — char-index boundaries for "delete last fragment".

Because these are a *second* source of truth, anything that edits the text outside the
tracked flow — a backspace, a partial delete, a cursor move, autocorrect, an abnormal
gesture end — leaves them stale. That is the recurring bug class:

- **Stale-stroke accumulation:** mis-recognize a multi-part word, backspace, retry → the
  next swipe merges with leftover geometry and builds an ever-longer garbage word. (Partly
  fixed by resetting `mInputPointers` on the first tap of a fresh word; the **fragment-pop**
  path is still uncovered — see Phase 1.)
- **Fragment-backspace fragility:** `mGestureFragmentBoundaries` needs band-aid "filter
  stale boundaries past current length" logic, only works in specific spacing configs, and
  bails entirely when the cursor is in the middle of the word.
- **No mid-word editing:** you cannot move the cursor into the middle of a composing word,
  or delete part of it, and continue building with gestures — the in-memory state assumes
  append-at-end.

## Core principle

**The editor text + the composing region (and the cursor within it) are the single source
of truth for "the current word." Stroke geometry is derived from that text on demand, not
maintained as separate, drift-prone state.**

A word's plausible stroke is just the sequence of its keys' centers — recoverable from the
*text* alone. So we never need to remember a stroke across edits; we can always reconstruct
one when re-recognition is needed.

## Trigger model (what (re)composes, and what doesn't)

**Re-recognition is the core feature (the Nintype behavior) and must be preserved:**
combining taps and swipes — before, during, or after each other — into one word that
re-resolves against the context before the cursor. The rules below govern only *when* it
fires, so a deliberate tap is never silently reshaped:

- **A swipe always (re)composes, and always carries context.** A swipe at the cursor
  re-recognizes, pulling in the word at the cursor as context — whether that word was typed,
  swiped, one the cursor was just moved into, **or one already committed to the text box**
  (re-entered via a cursor move). *Example: move to the end of "Doc" — composing OR a
  committed word in the field — swipe "ument" → re-recognizes with the "Doc" context →
  "Document".* This swipe-onto-any-existing-word capability is the full payoff (Phase 3).
- **A tap is exact UNLESS the current word already contains a swipe.** A tap into a word with
  no swipe in it is literal — it never re-recognizes. *Example: move between "Do" and "c",
  tap "g" → "Dogc", exactly.* But once the current word's composition includes a swipe, taps
  before/during/after it **do** contribute and re-recognize — this is the combine-taps-and-
  swipes feature, kept (today's `PREF_MULTIPART_RERECOGNIZE_TAPS`), just reimplemented on the
  source-of-truth model.
- **Moving the cursor is context-only.** It makes the word at the cursor *known* (so a
  following swipe can build on it) but never re-composes on its own. The next swipe
  re-recognizes with that context; the next bare tap (no swipe in the word) stays exact.

Net: **swipes drive re-recognition and always carry the surrounding-word context; taps stay
exact until a swipe is in play, then they contribute; cursor moves are inert until you act.**
Space remains the word-submission point.

### Current behavior (pre-Phase 3) — how cursor moves actually behave today

The Trigger model above is the *target*. Until Phase 3 lands, moving the cursor onto an
existing word behaves differently for swipe vs. tap, and this is worth knowing:

- **Move the cursor into another word, or midway through a word, then SWIPE** → the keyboard
  commits/clears the re-entered word, inserts an autospace, and **starts a brand-new word**.
  It does *not* re-recognize the word the cursor is in. *Mechanism:* a cursor landing in the
  front/middle of the composing word makes `isCursorFrontOrMiddleOfComposingWord()` true, so
  `onStartBatchInput` takes the reset branch (`InputLogic.java` ~line 787); with no composing
  word left to extend and a letter before the cursor, the autospace (`SpaceState.PHANTOM`)
  branch fires (~line 846) and the gesture recognizes fresh.
- **Move the cursor into/onto a word, then TAP** → you **edit that existing word** with a
  literal character at the cursor. *Mechanism:* `tryLiveConvergeTap` returns early on the same
  front/middle guard (and on `!mCombiningWordHasGestureFragment` for a word with no swipe in
  it), so the tap falls through to normal literal insertion.

This is *not a bug* — it's a usable split: swipe = "start fresh here," tap = "edit what's
here." Phase 3 would have changed the swipe half (re-recognize the re-entered word with its
text as context — the "move to end of `Doc`, swipe `ument` → `Document`" payoff).

> **Product decision (2026-06): keep this split.** The swipe = start-fresh / tap = edit
> behavior is the intended design, not a stepping stone. The swipe-onto-an-existing-word
> re-recognition described in the Trigger model is **descoped** — see Phase 3 below.

## The foundation already exists

HeliBoard already reconstructs a composing word from editor text when the cursor lands in a
word — `restartSuggestionsOnWordTouchedByCursor` → `getWordRangeAtCursor` →
`restartSuggestions`, whose core is:

```java
mWordComposer.setComposingWord(codePoints, getCoordinatesForCurrentKeyboard(codePoints));
mWordComposer.setCursorPositionWithinWord(...);
mConnection.setComposingRegion(start, end);
```

`getCoordinatesForCurrentKeyboard(text)` **synthesizes per-character key-center
coordinates from text alone** — exactly the "derive a stroke from the word" primitive this
design needs. Today it is used for **tap recorrection only**; it is never wired into the
gesture / live-converge path. The refactor is largely about routing two-thumb composition
through this existing machinery instead of around it.

## Target design (original aspiration — only partially realized)

> The bullets below are the *original* "remove all parallel state" vision. In practice only the
> stroke-from-text primitive was adopted, and only at the swipe-extend consumption point — see
> "Phases / outcome" above for what actually shipped. Kept here for context.

- **The word being built = the composing region in the editor.** It can be (re)established
  from any cursor position via `getWordRangeAtCursor` + `setComposingRegion`, which the
  editor already maintains.
- **Swipe-on-word composition becomes (near) stateless.** When a *swipe* extends the word
  at the cursor: take that word's *text* → synthesize its key-center stroke
  (`getCoordinatesForCurrentKeyboard`) → append the swipe's raw points → re-recognize. No
  `mLiveStroke` accumulator to leak. Taps never enter this path (see Trigger model).
- **Fragment boundaries become derived,** not stored. "Delete last fragment" is computed
  from the text/recognition at delete time, so there is nothing to keep in sync.
- **Backspace, partial delete, and cursor moves stop being special cases.** Each just
  changes the text (a cursor move is otherwise inert — context-only); the *next swipe*
  rebuilds its base from whatever text is actually there.

### State inventory — what actually happened
- **`mLiveStroke`: KEPT, UNCHANGED.** The plan was to retire it (derive the tap-re-recognition
  prefix from text). An attempt to do so — and a follow-up "real stroke primary, text-derived
  fallback" hybrid (`buildStrokeFromWordText`, `mComposingWordHasSwipeContent`) — was **reverted**:
  the fallback turned out to be effectively dead code (the guards that admit a re-recognizing tap
  are cleared on the same edits that empty the stroke, so it never fired), and the real captured
  stroke re-recognizes better than synthesized key-centers anyway. The live-converge tap path
  still uses `mLiveStroke` exactly as before.
- **`mInputPointers` drift: FIXED, not retired.** The "buffer survives reset / doesn't shrink on
  delete" contract stays, but the drift it caused is now corrected **at the point of consumption**
  rather than by removing the buffer (see Phases / outcome). `mGestureFragmentBoundaries` is also
  still in use (not retired).
- **`mExtendBatchInputBase`** remains the mechanism for feeding "context prefix + new input" to
  the recognizer. The prefix is the *real* stroke when it still matches the text, and is rebuilt
  from text-derived key centers only when an edit made it stale.
- **Reused as planned:** `getCoordinatesForCurrentKeyboard` (text → key-center geometry) is the
  one primitive from this plan that did get wired into the gesture path, via
  `seedInputPointersFromKeyCenters` / `realignComposerStrokeToText`.

## Phases / outcome (what was planned vs what shipped)

1. **Stop the bleeding in fragment mode.** ✅ **SHIPPED & on-device validated.** On a
   fragment-pop (`tryFragmentBackspace`), realign `mInputPointers` to the truncated word
   (rebuild from its key centers via `seedInputPointersFromKeyCenters`) so a following
   swipe-extend no longer merges with the pre-pop stroke. Lowercases the word before the
   exact key lookup and skips `NOT_A_COORDINATE` keys.
2. **Re-express re-recognition from text (retire `mLiveStroke`).** ❌ **ATTEMPTED, then
   REVERTED.** Two variants were tried: (a) replace `mLiveStroke` with text-derived geometry —
   felt subpar, the real captured curves re-recognize better; (b) a "real primary + text-derived
   fallback" hybrid (`buildStrokeFromWordText`, `mComposingWordHasSwipeContent`) — the fallback
   was effectively unreachable (the guards that admit a re-recognizing tap are cleared by the
   same edits that empty the stroke), so it changed nothing. Both were rolled back; `mLiveStroke`
   stays as-is. **Lesson:** the parallel stroke state isn't worth removing for the tap path; it's
   only a problem for the *swipe-extend base*, which is fixed surgically below.
3. **Generalize / swipe-onto-committed-word.** 🚫 **Descoped (2026-06).** Today's split —
   **swipe at a moved cursor starts a fresh word, tap edits the existing word** — is the intended
   behavior (product decision), so the Trigger model's "a swipe always re-recognizes the word at
   the cursor" does *not* apply to a re-entered committed word; it governs only a word actively
   being built at the cursor end.

### What actually fixed the bug (the shipped general fix)

Instead of removing the parallel stroke buffer, the drift is corrected **at the single point it
is consumed** — when a swipe arms its merged-trail extend base in `onStartBatchInput`:

- `mComposingStrokeStale` is set on **any** backspace and cleared when a gesture rebuilds the
  buffer (or the word ends). It means "the composing text was edited since the stroke last
  matched it."
- At the extend-arm site, if stale, the base is rebuilt from the composing word's text via
  `realignComposerStrokeToText` (→ `getCoordinatesForCurrentKeyboard` →
  `seedInputPointersFromKeyCenters`); if not stale, the **real captured stroke** is used.
- This covers every edit path at once (single-char, fragment-pop, selection / multi-char delete,
  cursor re-compose) without per-path patches, and leaves continuous swipe+swipe and tap+swipe
  using their real stroke. `realignComposerStrokeToText` no-ops if the layout can't resolve any
  key, so it never disarms the extend. **Shipped & on-device validated** (the recurring
  "Thing→delete→Whining" failure is gone).

### Next: Nintype-style whole-word backspace (planned, not yet built)

A follow-up, independent of the above: in **"Whole word" backspace mode**, pop the last swipe
fragment while composing, then delete a **whole previous word** (any word, swiped or typed) once
past the composing word — gated by the existing `PREF_COMBINING_BACKSPACE_DELETES_COMPOSING_TEXT`
toggle (to be relabelled to describe this), with key-repeat deleting whole words. Requires
relaxing `tryFragmentBackspace`'s whole-word-mode bail and adding a word-boundary delete for
committed text (`getTextBeforeCursor` + `SpacingAndPunctuations`), gated to two-thumb mode so
plain typists are unaffected.

## Risks / validation

- **Synthesized strokes ≠ real strokes.** `getCoordinatesForCurrentKeyboard` yields key
  *centers*, not the user's actual curves. Re-recognition from synthesized geometry may
  differ from the captured stroke. **This is the primary thing to validate on-device** (the
  recognizer may be robust to clean key-center input, or need tuning). Gate each phase on a
  real-device check, conservative defaults, and the trace/replay harness where possible.
- **Performance:** re-synthesize + re-recognize per fragment is more work than appending to
  a buffer, but the same order as today's live-converge per-tap recognition.
- **Feel:** mid-word continuation and derived fragment behavior are feel calls — ship as
  tunable where it matters, decide by typing on it (per the project's feel-driven principle).

## Why this is the right direction

Every current bug here is the same disease — parallel gesture state drifting from the
editor. This refactor **removes** that parallel state rather than adding more patches, and
unlocks mid-word cursor editing and partial deletes for free. Each phase reduces state and
is shippable on its own, so the risk is bounded and the wins are incremental.
