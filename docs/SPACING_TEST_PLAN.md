# Spacing Policy Playtest Plan

Use this when tuning the two-thumb spacing policy. The goal is practical feel, not reading raw telemetry.

## Setup

Use a normal text field where suggestions work.

Enable:
- **Two-thumb typing** / combining mode
- a non-zero **grace timer**
- **Only auto-finish swiped words** (default on)
- **Adapt pause to the word** when testing signal-driven grace
- **Experimental → Draw gesture debug points** when you want the HUD

Start with:
- **Finished-word speed-up:** `200 ms`
- **Extendable-stem patience:** `400 ms`

HUD labels:
- `FAST Nms · finished word` — complete dictionary word; timer shortened.
- `WAIT Nms · many continuations` — prefix-rich stem; timer lengthened.
- `TIMER Nms · not complete` — normal timer; no complete-word signal yet.
- `INSTANT` / `PAUSE` — Assisted-tier gate decision once enabled.

## Test A — complete words should finish faster

Type or swipe:
- `I`
- `the`
- `and`
- `hello`

Expected:
- HUD says **FAST** or **INSTANT** (later Assisted tier).
- The word commits sooner than with **Adapt pause to the word** off.
- It should not feel like the keyboard is waiting for an extension.

Tune:
- Too eager / commits before you expected → lower **Finished-word speed-up**.
- Still too slow → raise **Finished-word speed-up**.

## Test B — extendable stems should stay open longer

Tap or partially swipe stems:
- `ba` (bad / bar / bat / ball / back / bank)
- `ca` (can / car / cat / call / came)
- `pre` (pretty / press / prefer / previous)
- `con` (continue / control / content / consider)

Expected:
- HUD says **WAIT**.
- The word does **not** auto-finish immediately.
- You can keep typing/swiping the rest without fighting the timer.

Tune:
- Still commits too soon → raise **Extendable-stem patience**.
- Feels sticky / never finishes → lower **Extendable-stem patience**.

## Test C — Adapt ON vs OFF comparison

Use the same words with **Adapt pause to the word** off and on:
- `the`
- `ba`
- `pre`

Expected:
- OFF: same pause for everything.
- ON: complete words faster, prefix-rich stems slower.

If you cannot feel a difference:
- try **Finished-word speed-up = 350 ms**
- try **Extendable-stem patience = 700 ms**

## Test D — shortcut safety

With **Only auto-finish swiped words** on:
- tap a saved Text Expander shortcut such as `ba`
- pause

Expected:
- no expansion yet
- no auto-commit
- the shortcut stays composing until you press space or pick it

This should remain true even when **Adapt pause to the word** is on.

## Test E — corrections replace, not append

1. Misspell a word by tapping.
2. Wait briefly.
3. Pick the correction from the suggestion strip.

Expected:
- The correction replaces the misspelled word.
- It does not append a second word.

If this fails, first confirm **Only auto-finish swiped words** is on. The historical append bug came from the grace timer auto-committing tap words before the pick.

## Test F — punctuation and deferred spacing

With **Defer grace space** on, try:
- `hello.`
- `the,`
- `word?`

Expected:
- no double spaces
- no space before punctuation
- backspace after a grace commit still removes the right thing

## Recording results

For each run, note:
- Base grace timer value
- Finished-word speed-up value
- Extendable-stem patience value
- Whether **Adapt pause to the word** was on
- Whether **Defer grace space** was on
- Example word / result / whether it felt too fast, too slow, or right

Good tuning notes look like:

```text
base 500, speed-up 250, patience 650
"the" FAST 250ms felt right
"ba" WAIT 760ms still too fast, raised patience
shortcut "ba" stayed composing, good
```
