# Changelog

All notable changes to **LeanTypeDual** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> **Lineage & provenance.** LeanTypeDual is a fork of
> [LeanBitLab/LeanType](https://github.com/LeanBitLab) (the AI layer), which is itself a fork of
> [Helium314/HeliBoard](https://github.com/Helium314/HeliBoard) (the keyboard engine), based on
> AOSP/OpenBoard. Rather than re-list every inherited HeliBoard/LeanType version, this changelog
> records **LeanTypeDual's own releases**. Points where upstream code was merged in are noted as
> **`Upstream`** markers; everything else is original to this fork.

## [Unreleased]

## [3.11.0] - 2026-07-12

### Added
- **Direct IME switching** — configure a target keyboard/subtype and map keycode `-10076` to a toolbar action for immediate switching without the system picker. (#118)
- **Five persistent custom layout slots** — custom layouts now restore correctly across symbol mode, orientation changes, and keyboard reloads. (#118)
- **Suggestion controls** — configure auto-correct trigger characters and optionally suppress multi-word suggestions. (#118)

### Changed
- **Built-in Java gesture typing** uses less memory, streams dictionary entries, and improves path scoring/ranking performance. (#118)
- **Text Expander placeholder handling** now resolves and advances placeholders synchronously to avoid cursor/selection desynchronization. (#118)
- **Dictionary download catalog** is refreshed to the current repository inventory, removing stale unavailable entries and adding newly published dictionaries. (#119)

### Fixed
- **Direct IME switching on Android 6–8** now uses the legacy input-method manager API instead of calling an Android 9+ framework method. (#119)
- **Fallback gesture suggestions no longer leak dictionary capitalization** when Shift is off; the Java gesture engine now emits canonical lowercase candidates before the existing suggestion presentation-casing layer. (#118)
- **Dictionary and blacklist handling** prevents blocked words from leaking back into gesture and normal suggestions. (#118)

### Reliability & testing
- Added regression coverage for KeyCode uniqueness, custom-layout state restoration, direct IME switch branches, multi-word filtering, and fallback gesture casing. (#118)
- Fixed the Windows release tool to read dictionary metadata as UTF-8. (#119)

### Upstream
- Merged **LeanBitLab/LeanType v3.9.5** (pinned at `8cfe7f1fc`, including v3.9.3/v3.9.4). Fork identity (`LeanTypeDual`, distinct `applicationId`, privacy tiers, and fork-specific features) is preserved. (#118)

## [3.10.0] - 2026-06-20

### Added
- **Handwriting input** (Standard builds) — write characters on a recognition canvas using a
  downloadable plugin, with a dedicated bottom-row layout and a toolbar key.
- **Auto-read OTP from SMS** — a one-time code from an incoming SMS is offered in the suggestion
  strip while the keyboard is open; tap to insert. Uses a runtime, opt-in SMS permission.
- **Regex shortcuts in Text Expander** — expansion triggers can be matched by regular expression.
- **Dynamic dictionary/plugin downloader** — Standard builds can fetch layout dictionaries, emoji dictionaries, and handwriting plugins on demand.
- **Selective backup and restore** — backup/restore settings, dictionaries, and AI prompt configuration more granularly.

### Changed
- **Offline AI backend switched from ONNX Runtime to llama.cpp (GGUF).** The Offline build now
  loads compact quantized **GGUF** models on-device with configurable sampling
  (temperature / top-p / top-k / min-p); it now requires Android 8 (API 26).
- **Touchpad gestures reworked** into a fuller one-/two-finger suite (word select, word-by-word
  navigation, space, copy/paste, cut/select-all, undo/redo, hold-to-backspace). Single-finger
  double-tap now **selects the word** (previously deleted the selection).
- Release builds now target the **arm64-v8a** ABI only.
- Standard builds now exclude non-en-US dictionary assets and download optional dictionaries dynamically.

### Fixed
- **Text Edit mode no longer opens as a blank panel** when one-handed wrapper layout is active;
  the wrapper now lays out the visible Text Edit/touchpad overlay instead of the hidden keyboard view.
- **Sticky Shift from upstream handwriting cleanup** — upstream v3.8.6 stopped the hidden handwriting
  bottom row on every keyboard-frame switch, which globally cancelled the active Shift pointer before
  release. We keep the upstream handwriting feature but only stop handwriting when it is actually
  shown. (Upstream bug LeanBitLab/LeanType#186; upstream PR #194.)

### Upstream
- Merged **LeanBitLab/LeanType v3.8.9** (from v3.8.3, including v3.8.7/v3.8.8 and one post-tag docs/badge
  commit) — the source of the handwriting, llama.cpp/GGUF, dynamic downloader, text-editing mode, touchpad-gesture,
  SMS-OTP, selective-backup, and dictionary-downloader changes above. Fork identity (LeanTypeDual,
  distinct `applicationId`, two-thumb typing, the Gemini standard-AI layer, and the privacy tiers) is
  preserved.

## [3.9.1] - 2026-06-11

### Fixed
- **Erratic capitalization in two-thumb grace mode.** After the grace timer auto-committed a
  word, the shift/auto-caps state wasn't refreshed, so the next word's capitalization came out
  wrong (dropped sentence caps, or mid-word capitals). (#14)

### Changed
- **Tapped words no longer auto-finish by default** in two-thumb grace mode — the new
  "Only auto-finish swiped words" option defaults on, so a word you tap out stays open until you
  press space or pick a suggestion (fixes tapped shortcuts/corrections firing early). Only swiped
  words auto-commit on a pause. (#14)
- Reworded the two easily-confused spacing toggles: **"Only auto-space after swipes"** (the
  trailing space) vs **"Only auto-finish swiped words"** (whether the word commits at all). (#14)

### Added
- **Experimental: defer grace-mode space** (`PREF_SPACING_DEFER_GRACE_SPACE`, default off) — routes
  the two-thumb grace auto-commit space through the same deferred mechanism as the default swipe
  path. (#23)

## [3.9.0] - 2026-06-10

### Added
- **HCESAR keyboard layout** for Latin-script subtypes. (#74)
- **Touchpad edge-scroll** — holding a finger near the touchpad edge auto-repeats cursor movement
  with acceleration. (#74)
- **Toolbar: swipe down to hide the keyboard.** (#74)
- **Toolbar: show only the toolbar when a hardware keyboard is connected.** (#74)
- **Undo-word toolbar key** — reverts the last committed word back to its suggestion
  alternatives. (#35)
- **Pointer-trace recorder** (opt-in) — captures gesture traces + keyboard geometry to JSON for
  debugging/recognition work. (#20)

### Changed
- **Graduated trust for newly-learned words** — a just-learned word is held below real-dictionary
  suggestions until you've used it a few times, reducing premature autocorrect to half-typed
  words. (#39)
- Two-thumb down-swipe shortcut popup now tiles its icons proportionally across the usable
  row. (#36)
- README status badges switched to live shields.io badges (auto-updating; no CI). (#76)
- Backspace bookkeeping consolidated into a single, unit-tested `BackspaceUnitStack` (internal
  refactor, behaviour-preserving). (#31)

### Reliability & testing
- **Native C++ engine tests now run in CI.** A standalone host build (`app/src/main/jni/CMakeLists.txt`)
  compiles and runs the dictionary/suggest/geometry gtest suite on every change to the native
  engine — coverage the JVM/Robolectric tests cannot reach. (#78)
- Added a golden-master **backspace regression corpus** to the JVM suite, and the **unit-test gate
  is now blocking** on every PR. (#21, #12)

## [3.8.6] - 2026-06
### Added
- Flag learned/typed words that aren't in a dictionary; long-press to **Add** or **Block** them,
  plus a new **Blocklist** settings screen.
### Changed
- Two-thumb: the down-swipe shortcut popup now aligns to the letter row (swiping down on a key
  selects the icon above it).
### Fixed
- Two-thumb ghost-merge: a deleted or cancelled gesture trail no longer fuses into the next swipe.

## [3.8.5]
### Added
- Enable or disable individual dictionaries (built-in and custom) in settings.
### Fixed
- Toolbar key customization toggles not persisting.
- Emoji-search keyboard not splitting in landscape when split keyboard is enabled.

## [3.8.4]
### Added
- Double-tap touchpad gesture to delete selected words.
- Clipboard screenshot compression toggle; duplicate screenshots prevented.
- Text Expander: backspace-to-revert, and `%cursor%` / `%greeting%` / `%tomorrow%` / list
  placeholders (with optional custom count, e.g. `%list_5%`).
### Changed
- Gboard dictionary import performance; settings/editor performance, stability and memory.
### Fixed
- Missing words on import; corrupted imports (ZIP signatures pre-verified, streams closed).

## [3.8.3]
### Added
- Custom "Clear clipboard" toolbar key icon styles (bin, sweep, slanted, legacy).
- Custom drawable picker highlights in the Customize Icons grid; instant icon updates without
  restart; quick clipboard-item clear on long-press.
### Fixed
- Swipe-to-delete clipboard crash; pinned-section styling.
- Double-space-period countdown cancellation on Korean & combiner layouts.

## [3.8.2]
### Added
- **Text Expander** with placeholders (`%clipboard%`, `%day%`, `%time12%`, …) and a guide.
- Customizable tags for Custom AI Keys (themed capsules).
- Option to fold pinned clipboard items by default.
- Redesigned Sponsor dialog.
### Fixed
- F-Droid reproducible-build packaging discrepancy.
- Large clipboard-text truncation (native paste); clipboard suggestion in split-toolbar mode.

## [3.8.1]
### Added
- Fine-grained vibration strength (amplitude) control for keypress haptics.
- "Clear All" + confirmation in the personal-dictionary settings screen.
### Fixed
- `Resources$NotFoundException` crash from obsolete custom-icon overrides.
- Spacebar cursor-move and delete swipe in the emoji-search input field.
- Center-crop scaling for custom keyboard background images (no more squishing).

## Baseline

`Upstream` — Forked from **LeanBitLab/LeanType** (AI proofreading/translation, floating keyboard,
custom AI keys) on top of **HeliBoard 3.8.x** (the keyboard engine: dictionaries, layouts,
multilingual typing, glide typing, clipboard history, themes). LeanTypeDual ships as a distinct app
(`com.asafmah.leantypedual`) and adds, on top of that base, **two-thumb (dual-thumb) typing** and
the per-release changes above. See the [README](README.md) for the full feature set.
