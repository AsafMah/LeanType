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

### Added
- **Side-by-side experimental build** — an `experimental` build type (`com.asafmah.leantypedual.exp`, shown as "LeanTypeDual EXP") that installs alongside the normal build instead of replacing it, so input experiments can be compared against a working daily driver. (#141)

### Fixed
- **Gesture typing no longer silently returns zero suggestions** when a stroke's touch points never carry pointer id 0 — reachable in two-thumb use (thumb A down, thumb B down, thumb A lifts, thumb B swipes on). Raw MotionEvent pointer ids are now renumbered in first-seen order. (#135)
- **The two-thumb recognition settings no longer appear when they cannot work.** They synthesise touch points for the native gesture decoder; the built-in fallback engine scores a single trail and ignores which thumb drew it, so applying them there corrupted the trail and produced nonsense words. The group is now gated on a loaded gesture library, and explains itself when the spacing mode leaves it inert, instead of showing controls that structurally cannot take effect. (#141)

### Changed
- Two experimental recognition modes exist behind settings — feeding the two thumbs as separate decoder tracks, and redrawing earlier word parts through key centres — but they are **off by default and not currently recommended**. On a device with a user-supplied gesture library they produce incorrect words: the decoder that actually runs is a closed third-party library, not the in-repo AOSP engine whose two-pointer-track behaviour the research measured. (#135, #144)
- Documented the two-thumb decoder research in `docs/TWO_THUMB_TEMPORAL_ALIGNMENT.md`, including the measurement that deliberately overlapping stroke timestamps corrupts the decoder's speed features rather than helping. (#135)

### Reliability & testing
- Added source-level and packaged-APK gates that fail upstream merges when LeanTypeDual's identity, privacy flavors, bundled offline dictionaries, fork integrations, or four-flavor release coverage are lost. (#148)
- Added a native gesture **two-pointer track harness** (`jni/tests/replay/two_pointer_track_test.cpp`) that drives the real AOSP `ProximityInfoState` on the host, with tunable knobs and a printed sweep table. Runs in CI alongside the existing native suite. Note that it exercises the in-repo engine, which is not the decoder used when a gesture library is loaded. (#135, #144)
- The multi-part trail merge moved behind a pure, unit-tested `StrokeAligner` seam whose defaults reproduce the previous behaviour exactly. (#135)

## [0.3.0] - 2026-08-20

### Upstream
- Merged **LeanBitLab/LeanType v4.1.2** (pinned at `8720abeb`, covering v4.0.9–v4.1.2, 200 commits) — adds a clipboard edit mode, a personal-dictionary learning threshold, physical-keyboard and suggestion fixes, and Compose localization. LeanTypeDual retains its distinct `applicationId`, fork version, privacy tiers, Java fallback gesture engine, two-thumb typing, and persistent custom layout slots. (#137)

### Fixed
- **Held backspace no longer mis-deletes emoji.** The accelerated second deletion measured the character from *before* the first deletion rather than the one it was about to remove, so it could cut a multi-code-point emoji in half. Present upstream too; reported as `LeanBitLab/LeanType#423`. (#133)
- **Whole-word backspace keeps working after the upstream merge.** Upstream's fix for single-click backspace bulk-deleting numeric sequences was auto-merged in a way that stopped the two-thumb whole-word delete from clearing the composing span, leaving partial words behind. Both behaviours now coexist. (#137)
- **Custom layouts still restore after leaving symbol mode.** Upstream resets the remembered custom layout when switching back to the alphabet, which conflicted with this fork's persistent custom layout slots. (#137)

### Reliability & testing
- **Dropped both `runTests` skip guards for defects inherited from upstream** — subtype edit persistence and symbol-prefixed regex expansion are both fixed in upstream v4.1.2. Verified twice: on a pristine upstream checkout at the tag, and in the merged tree. Those two tests now actually execute on CI instead of returning early. (#137)
- **Fixed a latent test-harness bug** where `setText` accepted a `requireIdle` parameter it never passed through, so `reset()` could not tolerate leftover delayed messages. Harmless until the merge shifted JUnit's hash-based test ordering, at which point it failed an unrelated backspace test. (#137)
- **Test results are now gated rather than eyeballed** — `tools/check_test_results.py` refuses to report when the results are untrustworthy (any file predating the run, so a Gradle `UP-TO-DATE` task can't pass off a stale green report; or its own enumeration disagreeing with the totals the suites declare). It then diffs failing test *names* against a checked-in baseline and quarantines network-dependent tests so they are never counted as a regression or as a fix. Runs as the authoritative gate in CI. (#139)

## [0.2.0] - 2026-08-06

### Upstream
- Merged **LeanBitLab/LeanType v4.0.8** (pinned at `dec87806`, including v4.0.3-v4.0.7) — adds foldable/screen-profile detection with split-keyboard defaults, physical-keyboard suggestion shortcuts, a translation plugin and engine selector, a unified settings registry with automatic search indexing, floating-keyboard drag-to-resize, key-gap narrowness controls, auto-spanning toolbar keys, and text-expander regex fixes. LeanTypeDual retains its Java fallback gesture engine, distinct `applicationId`, fork version, privacy tiers, and two-thumb typing (now registered for settings search). (#126)

### Reliability & testing
- Guarded two upstream tests that fail at upstream tag `v4.0.8` itself (subtype edit persistence and symbol-prefixed regex expansion) with the `runTests` CI skip, after reproducing both on a pristine upstream checkout. (#126)

## [0.1.0] - 2026-07-12

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
- **Unshifted typing and swiping preserve lowercase words** instead of promoting ordinary words such as `to`, `no`, and `meet` to title-case dictionary candidates. (#125)
- **Fallback gesture suggestions no longer leak dictionary capitalization** when Shift is off; the Java gesture engine now emits canonical lowercase candidates before the existing suggestion presentation-casing layer. (#118)
- **Dictionary and blacklist handling** prevents blocked words from leaking back into gesture and normal suggestions. (#118)
- **Text Expander settings no longer show a duplicate “Expand immediately” switch.** (#125)
- **Memory-pressure cleanup no longer crashes after the keyboard view releases its drawing proxy.** (#125)

### Reliability & testing
- Added regression coverage for KeyCode uniqueness, custom-layout state restoration, direct IME switch branches, multi-word filtering, and fallback gesture casing. (#118)
- Added tap, batch-commit, shift-mode, acronym, mixed-case, and Unicode regression coverage for suggestion casing. (#125)
- Fixed the Windows release tool to read dictionary metadata as UTF-8. (#119)
- Release CI verifies API 21–23 v1/JAR signature coverage for every flavor that supports pre-Android 7 devices. (#119)
- Added regression coverage for Text Expander control uniqueness and pointer cancellation after keyboard-view deallocation. (#125)

### Upstream
- Merged **LeanBitLab/LeanType v4.0.2** (pinned at `0477ef83`, including v4.0.0/v4.0.1) — adds JNI and lifecycle hardening, first-word and next-word controls, background-service controls, immediate autospace, translation-history improvements, and pointer/input-connection stability fixes. LeanTypeDual retains its Java fallback gesture engine, distinct `applicationId`, privacy tiers, two-thumb behavior, and fork-owned release metadata. (#123)

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
