# Composing word: editor text as the single source of truth (design)

> Status: **design / not yet implemented**. Tracking: see the "Composing word source of
> truth" epic on the fork. This note is the plan of record for a multi-phase refactor of
> two-thumb / multi-part composition. Update it as phases land.
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

## Target design

- **The word being built = the composing region in the editor.** It can be (re)established
  from any cursor position via `getWordRangeAtCursor` + `setComposingRegion`, which the
  editor already maintains.
- **Live-converge becomes (near) stateless.** To fold a tap/gesture into the current word:
  take the current word's *text* → synthesize its key-center stroke
  (`getCoordinatesForCurrentKeyboard`) → append the new gesture's raw points → re-recognize.
  No `mLiveStroke` accumulator to leak.
- **Fragment boundaries become derived,** not stored. "Delete last fragment" is computed
  from the text/recognition at delete time, so there is nothing to keep in sync.
- **Backspace, partial delete, and cursor moves stop being special cases.** Each just
  changes the text; the next gesture rebuilds its base from whatever text is actually there.

### State inventory
- **Retire:** `mLiveStroke`, `mGestureFragmentBoundaries`, the bespoke
  `mInputPointers`-survives-reset contract and its clean-up patches.
- **Keep:** `mExtendBatchInputBase` as the *mechanism* for feeding "prefix + new gesture" to
  the recognizer (the re-timing logic stays), but the prefix is sourced from text-derived
  geometry rather than a stored stroke.
- **Reuse:** `getWordRangeAtCursor`, `setComposingRegion`, `setComposingWord`,
  `getCoordinatesForCurrentKeyboard`, `setCursorPositionWithinWord`.

## Phases (each independently shippable + on-device validated)

1. **Stop the bleeding in fragment mode.** On a fragment-pop (`tryFragmentBackspace`),
   realign `mInputPointers` to the truncated word (rebuild from its key centers) so a
   following swipe-extend no longer merges with the pre-pop stroke. Keeps the current
   architecture; closes the fragment case the fresh-word reset doesn't cover. Small.
2. **Prove the model.** Make live-converge derive its merge base from the *current word's
   text* via `getCoordinatesForCurrentKeyboard`, instead of `mLiveStroke`. Validate on-device
   that re-recognition from synthesized key-center geometry holds up. If so, delete
   `mLiveStroke`. Medium.
3. **Generalize.** Route all multi-part composition through "composing region as source of
   truth": derive fragment boundaries from text, support mid-word cursor + partial-delete
   continuation, retire `mGestureFragmentBoundaries`. Larger; the payoff phase.

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
