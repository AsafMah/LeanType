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

## Trigger model (what (re)composes, and what doesn't)

**Re-recognition is the core feature (the Nintype behavior) and must be preserved:**
combining taps and swipes — before, during, or after each other — into one word that
re-resolves against the context before the cursor. The rules below govern only *when* it
fires, so a deliberate tap is never silently reshaped:

- **A swipe always (re)composes, and always carries context.** A swipe at the cursor
  re-recognizes, pulling in the word at the cursor as context — whether that word was typed,
  swiped, or one the cursor was just moved into. *Example: move to the end of typed "Doc",
  swipe "ument" → re-recognizes with the "Doc" context → "Document".*
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
- **Swipe-on-word composition becomes (near) stateless.** When a *swipe* extends the word
  at the cursor: take that word's *text* → synthesize its key-center stroke
  (`getCoordinatesForCurrentKeyboard`) → append the swipe's raw points → re-recognize. No
  `mLiveStroke` accumulator to leak. Taps never enter this path (see Trigger model).
- **Fragment boundaries become derived,** not stored. "Delete last fragment" is computed
  from the text/recognition at delete time, so there is nothing to keep in sync.
- **Backspace, partial delete, and cursor moves stop being special cases.** Each just
  changes the text (a cursor move is otherwise inert — context-only); the *next swipe*
  rebuilds its base from whatever text is actually there.

### State inventory
- **Retire as STORED state — behavior preserved:** `mLiveStroke`. The accumulated raw stroke
  is replaced by deriving the prefix/context from the *current word's text* on demand. The
  re-recognition it powered (taps contributing to a swipe-involving word, and swipe-on-word)
  stays — only its source changes from a drift-prone buffer to the editor text.
- **Retire:** `mGestureFragmentBoundaries`; the bespoke `mInputPointers`-survives-reset
  contract and its clean-up patches.
- **Keep the feature:** the combine-taps-and-swipes re-recognition (today gated by
  `PREF_MULTIPART_RERECOGNIZE_TAPS`) — re-expressed statelessly. Whether it stays a toggle or
  becomes always-on core is a follow-up decision; the behavior does not go away.
- **Keep:** `mExtendBatchInputBase` as the *mechanism* for feeding "context prefix + new
  input" to the recognizer (the re-timing logic stays), but the prefix is sourced from
  text-derived key centers rather than a stored stroke.
- **Reuse:** `getWordRangeAtCursor`, `setComposingRegion`, `setComposingWord`,
  `getCoordinatesForCurrentKeyboard`, `setCursorPositionWithinWord`.

## Phases (each independently shippable + on-device validated)

1. **Stop the bleeding in fragment mode.** On a fragment-pop (`tryFragmentBackspace`),
   realign `mInputPointers` to the truncated word (rebuild from its key centers) so a
   following swipe-extend no longer merges with the pre-pop stroke. Keeps the current
   architecture; closes the fragment case the fresh-word reset doesn't cover. Small.
2. **Prove the model (re-recognition from text).** Re-express the existing re-recognition so
   its prefix/context comes from the *current word's text* via `getCoordinatesForCurrentKeyboard`
   instead of the stored `mLiveStroke` — for both a swipe extending the word and a tap
   contributing to a swipe-involving word. Behavior is unchanged; the stored stroke is gone.
   Validate on-device that re-recognition from synthesized key-center geometry holds up
   (vs. today's real-stroke accumulation). Medium.
3. **Generalize.** Route all multi-part composition through "composing region as source of
   truth": derive fragment boundaries from text, and support **swiping onto a word the cursor
   was moved into** (and after a partial delete), pulling in its text as context. Enforce the
   Trigger model exactly (swipe always re-recognizes with context; tap exact unless the word
   already has a swipe; cursor move = context-only). Retire `mGestureFragmentBoundaries`.
   Larger; the payoff phase.

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
