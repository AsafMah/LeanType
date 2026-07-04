# Spacing Policy Playtest Plan

Use this when tuning the two-thumb spacing policy. The goal is practical feel, not reading raw telemetry.

The most important distinction:

- **Auto-finish** = when the word commits.
- **Auto-space** = when/if a trailing space appears after that commit.

Those are separate. Several settings sound similar but affect different stages.

---

## 1. Timing model cheat sheet

| Setting | Affects | Tap-only words | Swiped words | Tap-then-swipe words |
|---|---|---|---|---|
| **Grace timer** | base auto-finish delay | yes, unless swipe-only finish is ON | yes | yes |
| **Tap extra grace** | extra delay for tap-started combining | yes, unless swipe-only finish is ON | no | yes before the swipe extends |
| **Only auto-finish swiped words** | whether tap-only words can auto-commit | **blocks tap-only auto-finish** | no effect | still allows auto-finish after the swipe fragment |
| **Adapt pause to the word** | modifies the grace timer by word signals | only if the word is allowed to auto-finish | yes | yes after the swipe fragment |
| **Only auto-space after swipes** | trailing space only | tap word may still commit, just no space | allows space | allows space after swiped fragment |
| **Defer grace space** | when the space materializes | no change to commit timing | no change to commit timing | no change to commit timing |

Recommended safety defaults:

```text
Only auto-finish swiped words: ON
Only auto-space after swipes: ON if you dislike tap-created spaces
Adapt pause to the word: ON only while tuning / testing
Defer grace space: ON only while testing deferred-space feel
```

---

## 2. Presets to try

Do not tune one slider at a time first. Try a complete profile.

### Profile A — Conservative / safe

```text
Base grace timer: 650 ms
Tap extra grace: 250 ms
Finished-word speed-up: 150 ms
Extendable-stem patience: 700 ms
Only auto-finish swiped words: ON
Adapt pause to the word: ON
```

Expected:
- swiped complete words: about 500 ms
- swiped prefix-rich stems: roughly 900–1200 ms
- tap-only words: no auto-finish

### Profile B — Balanced

```text
Base grace timer: 550 ms
Tap extra grace: 250 ms
Finished-word speed-up: 250 ms
Extendable-stem patience: 600 ms
Only auto-finish swiped words: ON
Adapt pause to the word: ON
```

Expected:
- swiped complete words: about 300 ms
- medium stems: about 700–900 ms
- prefix-heavy stems: about 1000 ms

### Profile C — Fast / assisted

```text
Base grace timer: 450 ms
Tap extra grace: 250 ms
Finished-word speed-up: 300 ms
Extendable-stem patience: 650 ms
Only auto-finish swiped words: ON
Adapt pause to the word: ON
```

Expected:
- swiped complete words: about 150–250 ms
- stems still protected by patience

### Profile D — Debug / exaggerated

```text
Base grace timer: 600 ms
Tap extra grace: 250 ms
Finished-word speed-up: 450 ms
Extendable-stem patience: 1000 ms
Only auto-finish swiped words: ON
Adapt pause to the word: ON
```

Expected:
- swiped complete words commit very fast
- `ba`, `pre`, `con` wait a long time

Use this only to understand the system.

---

## 3. HUD labels

Enable **Experimental → Draw gesture debug points**.

HUD labels:

- `FAST Nms · finished word` — complete dictionary word; timer shortened.
- `WAIT Nms · many continuations` — prefix-rich stem; timer lengthened.
- `TIMER Nms · not complete` — normal timer; no complete-word signal yet.
- `INSTANT` / `PAUSE` — Assisted-tier gate decision once enabled.

If the HUD says `WAIT` for a word you expected to be finished, the dictionary/suggestion signal thinks many continuations are plausible.

---

## 4. Practical tests

### Test A — tap-only safety

Input method: **tap only**.

Use:
- `ba` (saved shortcut if available)
- any short typed word

Settings:
- **Only auto-finish swiped words: ON**

Expected:
- no auto-commit
- no Text Expander expansion
- no correction append behavior
- word stays composing until you press space or pick a suggestion

If this fails, the swipe-only finish gate is broken.

### Test B — swiped complete words should finish faster

Input method: **swipe**.

Try:
- `the`
- `and`
- `I`
- `hello`

Expected with **Adapt pause to the word ON**:
- HUD says **FAST**
- word commits sooner than with Adapt OFF
- no need to tap space if the grace auto-space settings allow it

Tuning:
- Too eager → lower **Finished-word speed-up** or raise base grace.
- Too slow → raise **Finished-word speed-up** or lower base grace.

### Test C — swiped extendable stems should stay open longer

Input method: **swipe or partial swipe**.

Try stems:
- `ba` (bad / bar / bat / ball / back / bank)
- `ca` (can / car / cat / call / came)
- `pre` (pretty / press / prefer / previous)
- `con` (continue / control / content / consider)

Expected:
- HUD says **WAIT**
- word does not auto-finish immediately
- you can keep extending without fighting the timer

Tuning:
- Still commits too soon → raise **Extendable-stem patience** or base grace.
- Too sticky / never finishes → lower **Extendable-stem patience**.

### Test D — tap-then-swipe still extends

Input method: **tap prefix, then swipe rest**.

Example:
- tap `fire`
- swipe `truck`

Expected:
- tap prefix does not auto-finish by itself
- after the swipe fragment, the combined word still auto-finishes normally
- it should not become `fire firetruck`

This verifies that **Only auto-finish swiped words** suppresses only the tap timer, not combining-mode entry.

### Test E — Adapt ON vs OFF comparison

Input methods: **swipe**, plus tap-only safety check.

Words:
- `the`
- `ba`
- `pre`

Expected:
- Adapt OFF: same pause for all swiped words/stems.
- Adapt ON: complete words faster, prefix-rich stems slower.
- Tap-only words still do not auto-finish when swipe-only finish is ON.

If you cannot feel a difference:

```text
Finished-word speed-up = 350 ms
Extendable-stem patience = 700 ms
```

### Test F — corrections replace, not append

Input method: **tap only**.

1. Misspell a word by tapping.
2. Wait briefly.
3. Pick the correction from the suggestion strip.

Expected:
- correction replaces the misspelled word
- it does not append a second word

If this fails, first confirm **Only auto-finish swiped words** is ON. The historical append bug came from the grace timer auto-committing tap words before the pick.

### Test G — punctuation and deferred spacing

Input method: **swipe**.

With **Defer grace space** ON, try:
- `hello.`
- `the,`
- `word?`

Expected:
- no double spaces
- no space before punctuation
- backspace after a grace commit still removes the right thing

Remember: this tests **space materialization**, not auto-finish timing.

---

## 5. Recording results

For each run, note:

```text
base grace:
tap extra grace:
finished-word speed-up:
extendable-stem patience:
Adapt pause to the word: on/off
Only auto-finish swiped words: on/off
Only auto-space after swipes: on/off
Defer grace space: on/off
input method: tap / swipe / tap-then-swipe
word:
HUD:
result:
```

Good tuning notes look like:

```text
base 550, tap extra 250, speed-up 250, patience 600
swipe "the" -> FAST 300ms, felt right
swipe "ba" -> WAIT 910ms, still too fast, raised patience
TAP shortcut "ba" -> stayed composing, good
TAP then SWIPE fire+truck -> combined and committed, good
```
