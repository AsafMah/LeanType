# Agents.md — LeanType AI Agent Guide

> This file is the primary reference for AI coding agents (GitHub Copilot, Claude, Codex, etc.) working on the **LeanType** repository. Keep it up to date whenever you make structural changes to the codebase.

---

## 1. Project Overview

**LeanType** is a fork of [HeliBoard](https://github.com/Helium314/HeliBoard), which itself descends from OpenBoard and the AOSP `LatinIME` keyboard. The fork adds:

| Area | What was added |
|---|---|
| AI Proofreading & Translation | Groq, Google Gemini, and OpenAI-compatible cloud providers |
| Offline AI | ONNX Runtime with T5-based grammar-correction models (Offline build only) |
| Custom AI Keys | 10 toolbar keys with user-defined prompts and hashtag personas |
| Floating Keyboard | Detachable draggable overlay window |
| Two-thumb Typing | Nintype-style combining mode, manual spacing, autospace grace, point hinting |
| UI / UX | Squircle key backgrounds, Split toolbar, Incognito icon, Clipboard search & undo, Screenshot suggestion, Emoji search |
| Build flavors | `standard` (AI + internet), `offline` (ONNX, no internet), `offlinelite` (no AI at all) |

Upstream HeliBoard features (gestures, dictionaries, themes, layouts, one-handed mode, etc.) are inherited unchanged.

---

## 2. Repository Layout

```
LeanType/
├── app/
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/            # Keyboard layouts (.json), locale key texts
│       │   ├── java/helium314/keyboard/
│       │   │   ├── accessibility/
│       │   │   ├── compat/
│       │   │   ├── event/         # Event model (hardware keys, gestures)
│       │   │   ├── keyboard/      # View layer: MainKeyboardView, PointerTracker,
│       │   │   │                  #   KeyboardSwitcher, BatchInputArbiter
│       │   │   ├── latin/         # Core logic: LatinIME, InputLogic, Suggest,
│       │   │   │   │              #   RichInputConnection, DictionaryFacilitator,
│       │   │   │   │              #   FloatingKeyboardManager, ClipboardHistoryManager
│       │   │   │   └── settings/  # Settings.java, Defaults.kt, SettingsValues.java
│       │   │   └── settings/      # Compose UI: SettingsActivity, SettingsContainer,
│       │   │       └── screens/   #   per-screen Composables (AIIntegrationScreen,
│       │   │                      #   TwoThumbTypingScreen, etc.)
│       │   └── res/               # drawables, layouts, strings.xml, …
│       ├── standard/              # Standard-flavor-only sources (AI HTTP clients)
│       ├── offline/               # Offline-flavor-only sources (ONNX runner)
│       ├── offlinelite/           # Offline-Lite-flavor-only sources
│       ├── debug*/                # Debug/no-minify overlay flavors
│       └── test/                  # JVM unit tests (no Android runtime needed)
├── docs/
│   ├── FEATURES.md                # User-facing feature documentation
│   └── TWO_THUMB_TYPING_INTERNALS.md  # Deep-dive for contributors
├── layouts.md                     # How to add/edit keyboard layouts
├── CONTRIBUTING.md                # Contribution guidelines (from HeliBoard)
├── Agents.md                      # ← this file
└── dev-log.md                     # Chronological agent action log
```

---

## 3. Architecture — Key Subsystems

### 3.1 Input Pipeline

```
Screen touch
  └─ MainKeyboardView (View/Canvas)
       └─ PointerTracker (per-pointer state machine)
            ├─ BatchInputArbiter (aggregates multi-finger gesture points)
            └─ KeyboardActionListenerImpl
                 └─ LatinIME.onCodeInput / onEndBatchInput
                      └─ InputLogic
                           ├─ WordComposer    (live composing word)
                           ├─ LastComposedWord (revert info)
                           └─ RichInputConnection (writes to editor)
```

- `PointerTracker` holds per-finger state; `sInGesture` and `sAggregatedPointers` are **static** (shared across all trackers).
- `BatchInputArbiter.mayEndBatchInput` fires `onEndBatchInput` only when `activePointerCount == 1` (last finger up).
- `InputLogic` owns all text-manipulation logic: handling separators, backspace, composing words, auto-capitalization, and the two-thumb combining-mode state machine.

### 3.2 Suggestions Pipeline

```
User types
  └─ InputLogic
       └─ DictionaryFacilitatorImpl  (loads & queries dictionaries)
            └─ Suggest.kt            (scores, ranks candidates)
                 └─ SuggestionStripView  (renders the strip)
```

### 3.3 Settings Architecture — 5-File Pattern

Every new preference **must** touch exactly these five locations:

| File | What to add |
|---|---|
| `latin/settings/Settings.java` | `public static final String PREF_MY_FEATURE = "my_feature";` |
| `latin/settings/Defaults.kt` | `const val PREF_MY_FEATURE = <default value>` |
| `latin/settings/SettingsValues.java` | `public final <Type> mMyFeature;` + read in constructor |
| `res/values/strings.xml` | Title and summary strings |
| `settings/screens/<RelevantScreen>.kt` | `Setting{…}` entry in `create…Settings()` + `add()` in the items list |

`SettingsContainer.createSettings()` auto-aggregates all screen lists.

### 3.4 AI Integration

- **Standard flavor**: HTTP clients in `app/src/standard/` call Groq / Gemini / OpenAI-compatible APIs via `ProofreadService`.
- **Offline flavor**: ONNX Runtime loaded in `app/src/offline/`; encoder + decoder + tokenizer loaded from user-picked files.
- **Offlinelite flavor**: All AI code stripped at compile time; `AIIntegrationScreen` redirects back immediately.
- AI settings live under `Settings → AI Integration` (`AIIntegrationScreen.kt`, `CustomAIKeysScreen.kt`).

### 3.5 Floating Keyboard

`FloatingKeyboardManager` (in `latin/`) reparents `main_keyboard_frame` into a `TYPE_APPLICATION_OVERLAY` window. Requires `SYSTEM_ALERT_WINDOW` permission. Key widths are adjusted via `ResourceUtils.floatingWidthOverride` before reloading the keyboard.

### 3.6 Build Flavors

| Flavor | Internet | AI Type | Notes |
|---|---|---|---|
| `standard` | ✅ Optional | Cloud (Groq/Gemini/OpenAI) | Default release |
| `offline` | ❌ None | ONNX on-device | No `INTERNET` permission in manifest |
| `offlinelite` | ❌ None | None | Smallest size (~20 MB) |

---

## 4. Build & Test Commands

### Build
```bash
# Standard debug APK (requires JDK 17 or 21)
./gradlew :app:assembleStandardDebug --no-daemon

# All flavors release
./gradlew assembleRelease --no-daemon

# Fast compile-only check (no APK, single ABI) — same as CI
./gradlew compileOfflineRunTestsKotlin --no-daemon
```

On Windows PowerShell with a non-standard JDK path:
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
.\gradlew.bat :app:assembleStandardDebug --no-daemon
```

### Run Unit Tests
```bash
./gradlew :app:testOfflineDebugUnitTest --no-daemon
# or for all variants
./gradlew test --no-daemon
```

### CI Workflow
The GitHub Actions workflow (`.github/workflows/build-test-auto.yml`) runs `compileOfflineRunTestsKotlin` on every PR that touches `app/src/main/java**`.

---

## 5. Key Source Files Reference

| What you're looking for | File |
|---|---|
| Layout parsing | `assets/layouts/` + `keyboard/internal/keyboard_parser/KeyboardParser.kt` |
| Layout popup keys (per language) | `assets/locale_key_texts/` |
| Touch/swipe input | `keyboard/PointerTracker.java` |
| Key input handling | `latin/inputlogic/InputLogic.java` |
| Suggestions | `latin/DictionaryFacilitatorImpl.kt`, `latin/Suggest.kt`, `latin/inputlogic/InputLogic.java`, `latin/suggestions/SuggestionStripView.kt` |
| Text committed to editor | `latin/RichInputConnection.java` |
| Events from the editor | `latin/LatinIME.java` |
| Settings values (runtime) | `latin/settings/SettingsValues.java` |
| Settings constants/keys | `latin/settings/Settings.java` |
| Settings defaults | `latin/settings/Defaults.kt` |
| AI cloud proofreading | `app/src/standard/…/utils/ProofreadService.kt` |
| Offline ONNX proofreading | `app/src/offline/…` |
| Floating keyboard | `latin/FloatingKeyboardManager.kt` |
| Clipboard history | `latin/ClipboardHistoryManager.kt`, `keyboard/clipboard/ClipboardHistoryView.kt` |
| Two-thumb combining mode | `latin/inputlogic/InputLogic.java` (search `COMBINING`) |
| Settings UI screens | `settings/screens/*.kt` |

---

## 6. Code Conventions

- **Language mix**: Core engine is Java; newer/LeanType code is Kotlin. Follow the file's existing language.
- **Settings**: Always follow the 5-file pattern (§3.3). Do not skip any of the five files.
- **IME dialogs and text input**: Inside the IME process an `AlertDialog` `EditText` cannot reliably receive typed input. Use an `onCodeInput`/`onTextInput` intercept pattern writing into a `TextView` (see `ClipboardHistoryView.kt` search-mode and `EmojiPalettesView.java` search-mode for examples).
- **Flavor guards**: Use `BuildConfig.FLAVOR` checks (or source-set separation) to keep `standard`-only or `offline`-only code out of the shared `main` source set.
- **Performance**: Key event paths and suggestion pipelines run on the main thread and must remain fast. Avoid allocations in hot paths.
- **Privacy**: Never add `INTERNET` permission to the `offline` or `offlinelite` manifests. All network activity must be opt-in and only in the `standard` flavor.
- **Comments**: Match the style of surrounding code. Do not add comments that merely restate the code.
- **Commit hygiene**: One PR = one concern. Keep diffs small and reviewable.

---

## 7. Agent Workflow Requirements

These rules apply to **every agent session** in this repository:

### 7.1 Clarify Before Coding

Before starting implementation, **interview the user** about any part of the specification that is ambiguous or incomplete. Good questions to ask:

- Which build flavor(s) does this feature apply to?
- Should the feature be off by default (opt-in) or on by default?
- Does it need a new settings preference? If so, what is the default value and where should it appear in the settings tree?
- Are there related upstream HeliBoard issues or PRs to be aware of?
- What is the target Android API level / minimum API?

Do not make assumptions that change user-visible behavior. Ask first.

### 7.2 Tests Are Mandatory

Every implementation **must** be followed by tests. Structure your work as:

1. **Write the implementation.**
2. **Write or update JVM unit tests** in `app/src/test/java/helium314/keyboard/…`. Run with `./gradlew test --no-daemon`.
3. **Write a manual test plan** (see §7.3 below) and include it in the PR description or in the relevant section of `docs/FEATURES.md`.
4. If a change touches the settings architecture, add/update `SettingsContainerTest.kt`.

### 7.3 Manual Test Plan Format

Include a manual test table in your PR description (or in `dev-log.md`) for every user-visible change:

```markdown
### Manual Tests — <Feature Name>

| # | Steps | Expected Result |
|---|---|---|
| 1 | Open keyboard in any text field. Go to **Settings → <Path>** and enable **<Feature>**. | The toggle turns on; no crash. |
| 2 | Type a sentence with the feature active. | <describe expected behavior exactly> |
| 3 | Disable the feature. | Behavior reverts to default. |
| 4 | Rotate device / switch to split mode. | Feature still works; no layout breakage. |
| 5 | Test on both `standard` and `offline` flavors. | Flavor-specific behavior is correct. |
```

Add rows specific to your change. Tests must cover the happy path, edge cases, and regression scenarios.

### 7.4 Dev Log

Maintain `/dev-log.md` at the repository root. For each agent session, append a new entry:

```markdown
## YYYY-MM-DD — <Short description of session goal>

### Context
<What was the state of the repo at the start of the session.>

### Actions Taken
- <Bullet list of files created/modified and why>

### Decisions Made
- <Any non-obvious choices and the reasoning>

### Open Questions / Next Steps
- <Anything left to do or that needs human input>
```

---

## 8. Feature-Specific Notes

### AI Integration
- The `ProofreadService` class (standard flavor) abstracts all three cloud providers behind a single interface. New providers should implement this interface; do not add provider-specific code to `AIIntegrationScreen` or `InputLogic`.
- Custom AI Key hashtags (`#proofread`, `#editor`, `#append`, etc.) are processed client-side before the HTTP request is assembled. Add new keywords there.
- Offline ONNX proofreading supports T5-encoder + T5-decoder + tokenizer. The system instruction prefix is user-configurable.

### Two-thumb Typing
- The combining-mode state machine lives in `InputLogic.java`. Search for `COMBINING` to find all related fields and methods.
- New gesture preferences follow the same 5-file pattern as all other prefs.
- The debug overlay (`gesture_debug_draw_points`) renders in `MainKeyboardView`; keep drawing code separate from logic.
- Full architecture documentation is in `docs/TWO_THUMB_TYPING_INTERNALS.md`.

### Floating Keyboard
- Requires `SYSTEM_ALERT_WINDOW` permission; always check `Settings.canDrawOverlays(context)` before showing.
- Key width is set via `ResourceUtils.floatingWidthOverride`; trigger a keyboard reload after changing it.

### Layouts
- Layout JSON files live in `app/src/main/assets/layouts/`.
- See `layouts.md` for the full spec on adding new layouts.
- Popup keys for specific locales go in `app/src/main/assets/locale_key_texts/`.

---

## 9. Upstream HeliBoard Sync

LeanType periodically merges from upstream HeliBoard. When doing so:
- Resolve conflicts in favor of LeanType additions unless the upstream change is a critical bug fix.
- Update `docs/FEATURES.md` if upstream removes or renames anything LeanType documents.
- Run the full test suite after a merge: `./gradlew test --no-daemon`.
- Do **not** accept upstream changes that add `INTERNET` permission to non-standard flavors.

---

## 10. Useful Links

- [HeliBoard repository](https://github.com/Helium314/HeliBoard)
- [HeliBoard wiki](https://github.com/Helium314/HeliBoard/wiki)
- [HeliBoard issue #291 — two-thumb typing origin](https://github.com/Helium314/HeliBoard/issues/291)
- [LeanType GitHub Releases](https://github.com/LeanBitLab/HeliboardL/releases)
- [Google AI Studio (Gemini keys)](https://aistudio.google.com/apikey)
- [Groq Console (Groq keys)](https://console.groq.com/keys)
- [ONNX Runtime Android docs](https://onnxruntime.ai/docs/get-started/with-android.html)
