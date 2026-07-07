# Vertical Shortcut Rows

## Goal

Add an upstreamable, optional vertical shortcut-row gesture to LeanType: moving upward from an eligible top-row source key opens a top shortcut row; moving downward from an eligible bottom-row source key opens a bottom shortcut row.

The feature gives users access to compact shortcut/action rows without changing normal tap input, long-press popups, or swipe/gesture typing semantics.

## Non-goals

- No arbitrary all-direction per-key swipe action system.
- No two-thumb typing changes.
- No toolbar, clipboard, dictionary, spacing, or autocorrect changes.
- No local test identity changes (`applicationId`, package name, app label, version).
- No Java/Kotlin package or namespace refactor.

## User-visible behavior

- A setting enables/disables shortcut rows globally.
- Separate settings allow the top and bottom shortcut rows to be independently enabled.
- When enabled, a pointer that starts on an eligible top-row normal key can move vertically upward to show the top shortcut row.
- A pointer that starts on an eligible bottom-row normal key can move vertically downward to show the bottom shortcut row.
- The gesture must be vertical-dominant (`abs(dY) > abs(dX)`) and exceed the normal pointer step threshold so regular taps and horizontal gesture typing are not hijacked.
- Once the shortcut row is shown, subsequent pointer movement is interpreted against that temporary row until release/cancel.

## Design

### Layouts

Add two layout types:

- `shortcut_top`
- `shortcut_bottom`

Each type has a default JSON layout asset under `app/src/main/assets/layouts/`.

### Pointer tracking

`PointerTracker` records whether the original down key is eligible for the top and/or bottom shortcut row. Eligibility requires:

- shortcut rows are globally enabled;
- the specific row setting is enabled;
- the current keyboard is alphabetic;
- the source key is a normal enabled non-modifier, non-space key;
- the source key belongs to the top-most or bottom-most eligible letter row for the requested direction.

During move handling, before regular gesture movement consumes the stroke, the tracker checks whether the movement is vertical-dominant and crosses the pointer step threshold. If so, it asks the drawing proxy to show the matching shortcut-row keyboard, marks the shortcut-row swipe active, and prevents gesture typing from continuing for that pointer.

### Keyboard view/switcher

`MainKeyboardView` exposes a narrow method to temporarily show a keyboard for `LayoutType.SHORTCUT_TOP` or `LayoutType.SHORTCUT_BOTTOM` and translate it above/below the source key.

The temporary keyboard is hidden on cancel/release via existing popup-key dismissal paths.

### Settings

Use the repository settings wiring pattern:

1. `Settings.java`
2. `Defaults.kt`
3. `SettingsValues.java`
4. `res/values/strings.xml`
5. existing settings screen entry

The settings should default off for upstream safety.

### Tests

Add focused JVM tests for:

- shortcut-row layout assets parse to popup key specs;
- settings container wiring includes the new preferences if covered by existing settings tests.

## Verification

Run targeted tests only:

- `:app:testOfflineDebugUnitTest --tests "*KeyboardParserTest*"`
- settings wiring test if modified/available

## Commit plan

1. Commit this `SPEC.md` design.
2. Commit the upstreamable implementation.
3. Add a separate local-only test identity commit after the upstreamable implementation is complete and verified.
