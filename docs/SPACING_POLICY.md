# Spacing Policy

**Status:** design spec. Epic [#14]; implementation tracked in [#23] (B6b), [#24] (B6a), [#25] (B6c/C1), [#26] (tuning panel).

Goal: rethink autospace/grace as a **per-word spacing *policy*** driven by word-state signals already computed every keystroke — not a fixed timer — with an opt-in "Assisted" tier, decoupled commit-from-space, and adaptive per-posture cadence. Feel-driven: every knob is a live, on-device-tunable experimental setting; **never hardcode a feel decision**.

---

## 1. The problem

Two different mechanisms do the same job inconsistently, and grace is blind to whether a word is *finished*:

- **Default gesture path** uses **`SpaceState.PHANTOM`**: the space is *deferred* and materialized on the next input, so it adapts to connectors / URLs / punctuation and stays backspace-reversible.
- **Combining-grace path** writes the space **eagerly** in `onCombiningGraceExpired` (`InputLogic.java` ~1139–1156: `insertAutomaticSpaceIfOptionsAndTextAllow(sv)` + `mAutospaceJustWritten`).
- Grace is a **fixed timer** (`mCombiningGraceMs`). It can't tell a finished word ("I") from an extendable stem, so finished words still need a space tap while stems get committed too eagerly.

## 2. Principles

- **Signals over timers.** Decisions key off per-keystroke word state, at **zero extra native cost**.
- **One deferred-space mechanism** (PHANTOM) for every commit path.
- **Opt-in / default-off**, conservative defaults.
- **Backspace-reversible**, and the policy **never fires mid multipart / live-converge fragment**.
- **Feel-driven**: knobs are live experimental settings (§7); no hardcoded feel calls; validate on-device.

## 3. Current state (the seam)

- `SpaceState` values: `NONE`, `DOUBLE`, `SWAP_PUNCTUATION`, `WEAK`, `PHANTOM` (`inputlogic/SpaceState.java`). PHANTOM = the deferred-space promise consumed on next input.
- `OneShotSpaceAction` (`inputlogic/OneShotSpaceAction.kt`): `JOIN_NEXT`, `FORCE_NEXT_SPACE`.
- PHANTOM consumers that materialize/suppress the space on next input: `handleNonSeparatorEvent` (~373 / ~2003), `handleSeparatorEvent` (~2207).
- The combining grace timer: scheduled in `enterCombiningMode`, cleared in `cancelCombiningMode`, fires `onCombiningGraceExpired` (~1139–1156), which currently commits **and eagerly spaces**.
- Backspace-revert of a committed word reads `mLastComposedWord.mSeparatorString` (patched ~1148–1156).

## 4. Signals (free — from `mSuggestedWords`, computed every keystroke)

```
complete        = mTypedWordValid && mTypedWordInfo.mSourceDict != DICTIONARY_USER_TYPED
prefixRichScore = (# of KIND_COMPLETION candidates) / (total candidates)   // normalized [0..1]
graceMs         = clamp(base − completeBonus·complete + prefixPenalty·prefixRichScore, min, max)
```

- `complete`: the typed word is a real dictionary word (not a user-typed-only string).
- **`prefixRichScore`** is a **normalized score** (completions ÷ total), not a raw count — more stable across dictionaries of different sizes. High = lots of longer words start with this stem (keep open); low = little left to extend to (safe to commit).

## 5. The "Assisted" tier — two gates

A confident, complete word auto-commits with the space **deferred** (PHANTOM). It fires through **either** gate, mapping onto the two signals:

| Gate | Condition | Behaviour |
|---|---|---|
| **A — instant** | `complete` **and** `prefixRichScore ≤ lowThreshold` | Commit immediately (nothing plausible left to extend to: "I", "the", a finished gesture). Space deferred. |
| **B — pause** | `complete` **and** `prefixRichScore > lowThreshold` | Hold; commit only after an inter-word **pause** (the adaptive threshold, §6) — the stem is extendable, give the user time. Space deferred. |
| _neither_ | not `complete` | Stay open; fall back to the signal-driven `graceMs` timer. |

So finished words feel instant; extendable-but-complete words wait until you stop. Subsumes the old "short-word auto-commit" idea, but dictionary-driven.

**Guards (hard):** only above the confidence floor; **never** while a multipart / live-converge fragment is mid-flight; the deferred space keeps the whole thing backspace-reversible.

## 6. Adaptive per-posture cadence ([#25])

The Gate-B pause threshold is **learned**, not fixed: a running percentile of the user's real inter-word pause distribution. **Separate baselines per posture** (one-handed / two-handed), keyed off the existing one-handed toggle (`KeyboardSwitcher.setOneHandedModeEnabled` ~535–547, today layout-only — timing is greenfield) so switching posture **loads the stored baseline instantly** instead of slowly re-adapting. Far-key reach stays handled by the existing layout shrink + swipe shortcuts (C2), not here.

## 7. Tuning & insight ([#26])

- **For users:** a small set of **discrete tiers** — `Off` / `Conservative` / `Assisted` / `Aggressive` — that pick coherent knob bundles.
- **Behind an "experimental" expander:** the raw **sliders** — `base` grace, `completeBonus`, `prefixPenalty`, `lowThreshold`, Gate-B pause percentile — for on-device tuning.
- **Live:** changes take effect with **no restart**; defaults conservative; the whole policy **default-off**.
- **Fuller A11 typing-insight overlay** (paired): per word, surface *why* it committed or stayed open — `complete`, `prefixRichScore`, the resolved `graceMs`, and **which gate fired** (A / B / none). This is the feedback loop that makes the sliders tunable by feel.

## 8. Sequencing

| Phase | Issue | What | Why here |
|---|---|---|---|
| 1 | [#23] | Route the grace commit through PHANTOM (remove the eager write) | Structurally correct regardless of feel knobs — the foundation. |
| 2 | [#26] | Live tuning-panel infra + the fuller A11 insight overlay | Force-multiplier: makes Phase 3 tunable on-device and shows *why*. |
| 3 | [#24] | Signal-driven `graceMs` + the two-gate Assisted tier | The meat; tuned via Phase 2 + on-device playtest. |
| 4 | [#25] | Adaptive per-posture cadence | The learning layer on top. |

(#23 is "first" per the epic; #26 is slotted **second** because Phase 3 is feel-critical and un-tunable without it.)

## 9. Phase 1 (#23) implementation notes — small seam, deliberate test rewrite

- **Seam:** in `onCombiningGraceExpired`, replace `insertAutomaticSpaceIfOptionsAndTextAllow(sv)` + `mAutospaceJustWritten` with `mSpaceState = SpaceState.PHANTOM`. Existing PHANTOM consumers then materialize/suppress on next input (already solved for the default path).
- **Move** the `mLastComposedWord.mSeparatorString` backspace-revert patch (~1148–1156) to **space-materialization time**.
- **Test-contract rewrite (not optional):** ~8 `InputLogicTest` cases lock in the *eager* space — `expireCombiningGrace(); assertEquals("hello ", textBeforeCursor)` at ~259/276/357/372/461. Each must be rewritten to assert **deferred-then-materialized** (`"hello"` after expiry, `"hello "` only after the next letter/separator). Add explicit commit-then-backspace and commit-then-punctuation coverage.
- **Risk:** this changes locked-in behaviour contracts. Build it **behind the experimental flag** and validate with **on-device playtesting** — not a blind autonomous change.

## 10. Settings (new keys, all experimental / default-off)

Follow the 5-file pattern (`Settings.java` / `Defaults.kt` / `SettingsValues.java` / `strings.xml` / a settings screen) + a `SettingsContainerTest` case each.

| Key (proposed) | Type | Meaning |
|---|---|---|
| `PREF_SPACING_POLICY_TIER` | enum | Off / Conservative / Assisted / Aggressive |
| `PREF_SPACING_GRACE_BASE_MS` | int | `base` in the graceMs formula |
| `PREF_SPACING_COMPLETE_BONUS_MS` | int | `completeBonus` |
| `PREF_SPACING_PREFIX_PENALTY_MS` | int | `prefixPenalty` |
| `PREF_SPACING_LOW_THRESHOLD` | float | Gate-A `lowThreshold` on `prefixRichScore` |
| `PREF_SPACING_PAUSE_PERCENTILE` | int | Gate-B adaptive-pause percentile |

(Tier selection writes a coherent bundle into the sliders; the experimental expander exposes the sliders directly. Reuse `MultiSliderPreference.kt`.)

## 11. Testing strategy

- **Unit-testable (JVM):** the `graceMs` formula and gate selection are deterministic given a `mSuggestedWords` snapshot — table-test them directly. The Phase 1 deferred-space contract is covered by the rewritten grace tests + new backspace-revert cases.
- **On-device only (feel):** the actual cadence, the tier defaults, and the adaptive percentile. These ride the #26 panel + the A11 overlay; do **not** assert specific timings in tests.
- The native gesture-replay harness ([#78]) and the trace recorder ([#20]) provide fixtures for regression-checking that the policy doesn't break recognition.

[#14]: https://github.com/AsafMah/LeanType/issues/14
[#20]: https://github.com/AsafMah/LeanType/issues/20
[#23]: https://github.com/AsafMah/LeanType/issues/23
[#24]: https://github.com/AsafMah/LeanType/issues/24
[#25]: https://github.com/AsafMah/LeanType/issues/25
[#26]: https://github.com/AsafMah/LeanType/issues/26
[#78]: https://github.com/AsafMah/LeanType/issues/78
