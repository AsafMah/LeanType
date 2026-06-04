# LeanType — Keyboard & Two-Thumb Improvement Plan (living doc)

> Working plan, evolved across design discussion. NOT yet GitHub issues — we convert to
> issues once converged. Status legend: ✅ greenlit · 🔬 needs design/chew · 🅿️ parked ·
> 🐞 bug to file · 💡 future/explore. Estimates are rough (S<1d, M 1–3d, L 4–6d, XL >1wk).
>
> Grounding note: file:line refs below come from read-only scouting of the current tree.
> Dependencies matter — the **test harness (A3)** and **spacing policy (B6)** are spines.
>
> **Guiding principle (feel-driven):** the two-thumb/spacing behaviors are judged by how they TYPE,
> not how they read. Ship feel-sensitive logic as *tunable experiments* (live settings, conservative
> defaults) — never hardcode a feel call. Build the structurally-correct part first (e.g. B6b), layer
> the feel knobs on top. The enabling trio for playtesting: **A3** (harness — measure regressions as
> you tweak), **A11** (typing-insight overlay — SEE why a word committed/stayed open), and a small
> **live tuning panel**. Prioritize these early; they're what make "play with it" possible.

---

## 0. Already filed
- **#12** — CI compiles tests but never runs them; 3 unit tests fail on `main` unnoticed
  (`testOfflineDebugUnitTest`). Root: CI only runs `compileOfflineRunTestsKotlin`.

---

## A. Greenlit items

| ID | Item | Difficulty | Time | Importance | Category | Depends on |
|---|---|---|---|---|---|---|
| A3 | Record/replay pointer-trace test harness | Med–High | L | **High** | Testing/Tooling | — |
| A7 | Unify backspace into one revertible input-unit stack | High | L | Med–High | Refactor/Reliability | A3 |
| A8 | Make live-converge recognition async | Medium | M | Medium | Perf/Reliability | — |
| A10 | Mis-commit "undo word" as toolbar key + swipe target | Low–Med | S–M | Medium | UX | C2 swipe plumbing |
| A11 | Debug-trace → built-in "typing insight" | Medium | M | Med (differentiator) | UX/Onboarding | — |

**A3 — Record/replay harness** (do first; unblocks A7, B4, B5, combos)
- A3a: Capture format + recorder (extend gesture-data collection) → dump `InputPointers`
  (x/y/time/pointerId) + committed result.
- A3b: JUnit replay harness through `InputLogic`/`WordComposer`, assert recognized words;
  seed from `TWO_THUMB_TYPING_INTERNALS.md §5` matrix.
- A3c: CI runs `:app:testOfflineDebugUnitTest` + corpus (non-blocking first). Closes #12.

**A7 — Unify backspace** (depends on A3)
- A7a: `InputUnit` abstraction (tap/fragment/gesture-word) with revert metadata; push per input.
- A7b: backspace pops one unit, restoring composing/committed state.
- A7c: migrate `tryFragmentBackspace` + combining whole-word delete onto stack; delete the
  two parallel mechanisms (the area that produced the corruption PR #11 hand-fixed).

**A8 — Async live-converge**
- Move `getBatchSuggestionsSync` off the input thread; update composing word on callback with a
  stale-generation token; fall back to literal if next input arrives first. (Pairs with B2.)

**A10 — Mis-commit undo** (toolbar key + swipe target)
- A10a: `UNDO_WORD` action — revert last committed gesture word to its alternatives strip.
- A10b: expose as toolbar key (model on existing `JOIN_NEXT` key, `ToolbarUtils.kt`) AND as an
  assignable swipe-up/down shortcut target (`tryStartShortcutRowSwipe`, PointerTracker.java:1366 /
  `ShortcutRowKeys.kt`). Shares plumbing with C2.

**A11 — Typing insight** (productize the always-on debug trace)
- A11a: promote `GestureDebugPointsDrawingPreview` from debug-flag to a real polished setting.
- A11b: join-feedback (visual/haptic) when a tap *extends* a swiped word vs *starts* a new one.
- A11c: optional post-commit "what happened" glance (which fragments merged, why this word).

---

## B. Reconsidered / design items

**B1 — Join key (CORRECTION: it already works).** `JOIN_NEXT` is wired: toolbar key / keyCode
`-249` → `enterJoinNextMode` (InputLogic.java:975) → `OneShotSpaceAction.armJoinNext()` →
consumed on next tap (InputLogic.java:731) and space (3530). Only the *config* pref
`PREF_MULTIPART_JOIN_KEY_MODE` is dead scaffolding (read into `mMultipartJoinKeyMode`, never used).
- **Action:** delete the dead pref. Make `JOIN_NEXT` an assignable swipe-shortcut target (reuses
  A10/C2 plumbing) instead of building the long-press-space/dedicated-key surfaces. Difficulty S.

**B2 — Live-converge: offer, don't replace (UX fix).** Concern raised: confidence-gating could feel
like input isn't registering. Resolution: the tap is *never* dropped (literal fallback already
exists). Don't auto-replace the composing word with a re-recognition — **keep the literal taps in
the field, surface the merged-trail word as the #1 suggestion** (non-destructive). User sees real
input + the smart guess one tap away. Difficulty M. Pairs with A8 + A11.

**B4 — Per-thumb pointer attribution** (true simultaneous two-thumb). Today all touch points from
both thumbs are aggregated into one stroke, so a tap mid-glide warps the recognized word. OS already
tags pointer IDs (chording uses `PointerTrackerQueue.hasModifierKeyOlderThan`). Keep the two thumbs'
streams separate: gliding thumb → stroke; tapping thumb → discrete taps via live-converge. Difficulty
High/XL. **Gate on A3** (validate against corpus). Category: Recognition.

**B5 — Tap geometry as weighted hints.** A tap is fed as a single point at key center. Test (via A3)
"single point" vs "small footprint / 2-point micro-arc / key-proximity prior" for recognition
accuracy. Data-driven; don't ship blind. Difficulty M. Depends on A3.

**B6 — Spacing policy (THE SPINE — autospace/grace, now grounded).** 🔬 Today there are three
disjoint regimes: (a) default autospace-after-gesture — word committed, space DEFERRED via
`SpaceState.PHANTOM`, materialized on next input (and suppressed for connectors/URLs/punct);
(b) combining-grace timer — fixed `postDelayed` (InputLogic.java:962) → `onCombiningGraceExpired`
(1106) commits AND writes the space EAGERLY (1139); (c) manual spacing — no timer, word stays open
until you tap space.

**Reframe:** stop thinking "fixed grace timer." Instead: *while composing, decide "is this word
done?" from word-state signals; when done, commit-and-defer-space.* The timer stays as the
mechanism, but its duration — and whether it fires at all — is signal-driven.

**The signals are FREE** — already computed every keystroke and held in `InputLogic.mSuggestedWords`
(updated at :1331), readable at grace-arm time with zero JNI/locks:
- `complete` = `mSuggestedWords.mTypedWordValid && mTypedWordInfo.mSourceDict != DICTIONARY_USER_TYPED`
  (real dictionary word, not just something you typed). Set at `Suggest.kt:154`.
- `prefixRich` = count of `KIND_COMPLETION` items (`SuggestedWordInfo.isKindOf(KIND_COMPLETION)`,
  completions longer than typed) ≥ threshold — many words extend this stem.
- `pause` = time since last input vs learned per-posture cadence (B6c).
- `inputKind` = gesture (word-intent) vs tap (ambiguous).

**B6a — signal-driven grace + the "Assisted" tier (the useful-for-manual-typists bit).**
`graceMs = clamp(base − completeBonus·complete + prefixPenalty·prefixRich, min, max)`.
"I"/"לא" (complete, not rich prefix) → ~min → commits fast; "th"/"te" (incomplete, rich) → ~max →
waits. Subsumes old C3 with zero config (dictionary-driven); optional user override list on top.
User-facing model = three spacing **tiers** (replaces scattered toggles):
  - *Manual* (today): word stays open until you tap space. Unchanged.
  - ***Assisted* (new): like manual, but a *confident complete word* (real dict word, low
    prefix-richness) auto-commits after a brief learned pause with the space DEFERRED (PHANTOM) —
    so "I", "לא", finished gesture words commit without a space tap, while "th" / anything
    extendable stays open and under your control. This is the "type I, no space needed" you wanted,
    generalized and dictionary-driven.**
  - *Auto* (today): always autospace after gesture/word.
- B6b: **decouple commit from space (small).** PHANTOM is ALREADY the deferred-space mechanism
  (regime a). Only the grace path writes eagerly. Fix: in `onCombiningGraceExpired` replace the
  eager `insertAutomaticSpaceIfOptionsAndTextAllow(sv)` + `mAutospaceJustWritten=…` (InputLogic.java
  1139–1156) with `mSpaceState = SpaceState.PHANTOM`; existing PHANTOM consumers
  (`handleNonSeparatorEvent` ~373/2003, `handleSeparatorEvent` ~2207) materialize/suppress the space
  on next input — consistent connector/URL/punct handling for free. One wrinkle: the
  `mLastComposedWord.mSeparatorString` patch (backspace-revert, 1148–1156) moves to
  space-materialization time — already solved for regime (a). Difficulty Low–Med.
- B6c: **learned cadence, per posture.** The "brief pause" that triggers an Assisted commit is a
  running percentile of the user's real inter-word pauses, stored separately per posture (one/two-
  handed), keyed off the existing one-handed toggle so switching loads the right baseline instantly
  (this is C1). Capture point: delta between commit and next-input-start.
- **Risks/guards:** wrong auto-commit (you meant to extend) → only commit on HIGH confidence
  (complete real word + low prefix-richness + pause), NEVER while a multipart/live-converge fragment
  is mid-flight; deferred PHANTOM space + word-as-last-composed keeps it cheaply backspace-reversible.
  Ship Assisted opt-in/default-off; expose one "eagerness" slider, not raw ms. **Gate tuning on A3.**
- **PRs:** B6b first (self-contained decouple) → B6a (signal grace + Assisted tier, needs A3 to
  tune) → B6c (learned cadence + per-posture, needs C1 posture key).
- **Playtest knobs (NOT paper decisions — these are feel calls, ship them adjustable):** (1)
  "Assisted" as a distinct tier vs folding the smarts into Manual; (2) prefix-richness = completion
  COUNT vs top-completion SCORE; (3) require a pause before assisted-commit vs fire instantly on a
  confident complete word; (4) discrete tiers vs one "eagerness" slider. Don't hardcode answers —
  expose each as a live experimental setting, conservative defaults, decide by typing on it.

**B9 — Split layout tuned for thumbs.** 🅿️ Parked, low priority.

---

## C. From-the-user ideas (concrete)

**C1 — One/two-handed adaptation.** 🔬 Ground truth: one-handed mode is *purely layout* today
(gravity/scale/reload; `KeyboardSwitcher.setOneHandedModeEnabled` 535–547) — touches no timing/gesture
code; all timings global. Approach (revised with user input):
- **Adaptive cadence (B6c) with separate stored baselines per posture**, keyed off the existing
  one-handed toggle, so switching loads the right learned baseline *instantly* instead of slowly
  re-adapting each way.
- Keep the existing manual layout-shrink for **reach** (the real one-handed pain is reaching far
  keys/corners — user agrees). Optionally add gesture access to far keys (ties to C2).
- Dropped: auto-posture-*detection* from touch geometry (the manual toggle already supplies the mode
  signal; detection unnecessary). Dropped: standalone manual "one-handed timing mode".

**C2 — Symbols in the flow.** Ground truth: vertical swipe-from-key already coexists with glide:
`tryStartShortcutRowSwipe` (PointerTracker.java:1366) is checked *first* in `onMoveEvent` (1099),
fires on **≥10dp vertical travel with `|dY|>|dX|`**, sets `mIsDetectingGesture=false` and consumes the
pointer. Horizontal falls through to glide. So direction + check-order already solve the conflict.
- **C2a (MVP, ✅ start here): swipe-up → key's popup/moreKey symbol.** Bind the symbol action to the
  same vertical-up gate (insertion ~PointerTracker.java:1374). Free on non-top rows (shortcut-top
  already disabled there); on top row choose symbol vs shortcut. Difficulty Low–Med. Solves "symbols
  in flow" without breaking swipe typing. Also fixes "123 is far left" (symbol access from any key).
- **C2b (future 💡): swipe-down + sideways shortcuts + full customizability.** Down already exists
  (bottom row); sideways is addable. Scope customizable swipe-from-any-key → action later.
- **C2c (combos, ✅ follow-up): true-simultaneous, NOT hold-then-tap.** Combos = (control-anchor +
  key), anchor ∈ {enter, emoji, 123, shift, space}, never letter+letter. Fire when both go down within
  an overlap window (~50–80ms) AND neither pointer exceeds the glide movement threshold (movement →
  abort to gesture). Low false-positive (control keys at edges; movement-abort protects glide). Build
  after A3 so misfire rate is measurable. Infra exists: `PointerTrackerQueue` tracks active pointers.

**C3 — Short-word special handling.** ❌ Dropped → folded into **B6a** (it's the degenerate case).

**C4 — "Purge a typo forever" + the root scoring bug.** This is the highest user-satisfaction item.
Root cause traced (answers "why does it win even when swiping"):
- Gesture uses the SAME native engine + SAME UserHistoryDictionary as typing. Score formula
  `compoundDistance = spatialDistance + languageDistance × weight` (`dic_node_state_scoring.h:107`);
  `languageDistance` from frequency (`dic_node_utils.cpp:84`). **High learned frequency buys down bad
  geometry** → "לר" beats "לא" once learned.
- Garbage filter (`checkForGarbage`→`isInDictionary`, DictionaryFacilitatorImpl.kt:551) is useless
  once the junk word is in UHD.
- Batch results skip the autocorrect threshold (Suggest.kt:377–393); gesture commits feed the SAME
  learning hook (`performAdditionToUserHistoryDictionary`, InputLogic.java:3987); `isValid=false`
  does NOT stop frequency growth (DictionaryFacilitatorImpl.kt:408). Loop closes.

**Revised fix (user steer: DON'T block non-dict learning — people legitimately add new words.
Make the algorithm smarter + the UI clearer):**
- C4a–d (blacklist, still needed): blacklist on history-only removal; check blacklist on the learn
  path; stop un-blacklisting on commit; "block forever" UI distinct from transient delete.
- C4-smart (algorithm): **graduated trust** — a non-dictionary learned word needs *more* repetition
  before it's allowed to override a real dictionary word that the geometry matches better. New words
  still learn (slowly); a single misfire can't hijack a common word. (Replaces the heavier-handed
  "don't learn non-dict words" idea.)
- C4-ui (clarity): when a committed/suggested word is **not in the main dictionary, flag it**
  (distinct strip styling / indicator). User can: **Add** (promote to real dict), **Blacklist**
  (block), or **do nothing** → it isn't aggressively added, but enough repetition still learns it.
- Sequencing: ship C4a–d + C4-smart as the cure; C4-ui as the clarity layer.

---

## 🐞 Bugs to file
- **Down-swipe shortcut-row misalignment.** The bottom-row (down) shortcut popup doesn't align like
  the top (up) one — hypothesis: bottom row contains control keys and/or custom-width keys that throw
  off the popup anchor. Investigate `ShortcutRowKeys.kt` panel build + the anchor/positioning when
  the source row has non-uniform widths. (Separate from C2.)

---

## 💡 Future / to explore (Nintype-inspired UI — revisit later)
- **In-keyboard customization UI** — assign shortcuts/actions to keys *from within the keyboard*
  (Nintype-style), no trip to settings.
- **Thick info bar** — a persistent bar surfacing info + tools/actions in-context (could host the
  typing-insight from A11, quick toggles, etc.).
- These need their own design pass; captured so they're not lost.

---

## Dependency map (build order)
1. **A3 (harness)** — gates A7, B4, B5, C2c(combos), and de-risks B6.
2. **C4a–d + C4-smart** — independent, high value, ship early (the "לר" cure).
3. **C2a (swipe-up symbol)** — independent, low risk, immediate UX win; brings A10b/B1 swipe plumbing.
4. **B6 (spacing policy)** — spine; B6c feeds C1. Gate on A3.
5. Everything else hangs off the above.
