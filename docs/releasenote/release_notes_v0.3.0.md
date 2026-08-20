# LeanTypeDual 0.3.0

Brings the fork up to **LeanBitLab/LeanType v4.1.2**, covering 200 upstream commits from v4.0.9 onwards, plus fixes for two fork features the merge would otherwise have broken.

## Highlights

- **Clipboard edit mode** — edit a clipboard entry in place, with cursor movement driven from the keyboard.
- **Voice recognition language selector** — auto-detect plus 99+ Whisper languages, chosen independently of the typing language.
- **Personal dictionary learning threshold** — decide how many times a word has to be typed before it is added, instead of it being added immediately.
- **Physical keyboard suggestions** — pick a suggestion with a shortcut key when a hardware keyboard is attached.
- **Localized settings** — the Compose settings screens are translated.
- More reliable text-expander regex shortcuts, and a range of emoji and clipboard layout fixes.

## Fixed

- **Held backspace no longer mis-deletes emoji.** The accelerated second deletion measured the character from *before* the first deletion rather than the one it was about to remove, so it could cut a multi-code-point emoji in half. The same defect exists upstream and is reported as `LeanBitLab/LeanType#423`.
- **Whole-word backspace keeps working.** Upstream's fix for a single backspace bulk-deleting numeric sequences would have stopped the two-thumb whole-word delete from clearing the composing span, leaving partial words behind. Both behaviours now coexist.
- **Custom layouts still restore after symbol mode.** Upstream resets the remembered custom layout when switching back to the alphabet, which conflicts with this fork's persistent custom layout slots.

## Under the hood

- Both defects this fork had been carrying from upstream — subtype edit persistence and symbol-prefixed regex expansion — are fixed in v4.1.2, so their CI skip guards are gone and those tests genuinely run again.
- Test-result analysis is now gated by `tools/check_test_results.py`, which refuses to report on stale or self-inconsistent results and diffs failing test names against a checked-in baseline.

## Unchanged

LeanTypeDual keeps its own `applicationId`, its own version line, the privacy tiers (`standard` / `standardfull` / `offline` / `offlinelite`, with network access only in the two standard builds), the Java fallback gesture engine, two-thumb typing, and persistent custom layout slots.
