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
