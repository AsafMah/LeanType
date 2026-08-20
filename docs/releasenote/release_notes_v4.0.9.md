### 💖 Support Our Work
* We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New in v4.0.9

### ✨ New Features & Enhancements
- **Full Localization & Translation Pipeline**:
  - Extracted all Text Expander UI components into string resources, enabling 100% translation support across all app languages.
  - Added complete (100% gap-free) localizations for 10 major languages: **Arabic (`ar`)**, **German (`de`)**, **Greek (`el`)**, **Spanish US (`es-rUS`)**, **French (`fr`)**, **Italian (`it`)**, **Malayalam (`ml`)**, **Portuguese (`pt`)**, **Russian (`ru`)**, **Turkish (`tr`)**, and **Urdu (`ur`)**.
  - Added reusable localization gap analysis and translation application tools in `Pdoc/scripts/`.
- **Foldable Screen Profile Toggle (#400)**: Added an opt-in "Foldable screen profile" toggle under Appearance settings to gate automatic screen profile detection and split keyboard scaling (off by default to prevent layout height scaling issues on extreme aspect ratio foldable devices).
- **Handwriting Recognition Language Selection**: Added option in settings to select a dedicated handwriting recognition language independent of active keyboard language, complete with thread-safe display name caching and spacebar text synchronization.
- **Personal Dictionary Blocklist Overrule**: Personal dictionary entries now correctly overrule blocklist/blacklist exclusions.
- **Text Expander UI Usability**: Added vertical scrolling support to the shortcut creation and edit dialogs on smaller displays.

### 🐛 Bug Fixes & Stability Improvements
- **Android 7 Onboarding Wizard Crash (#399)**: Added SDK version guards and defensive linkage exception boundaries in `HandwritingLoader.kt` and `SubtypeScreen.kt` to prevent ML Kit Digital Ink Recognition initialization from crashing the welcome setup wizard on Android 7 (API 24/25) or devices lacking Play Services.
- **App-Specific Autocorrect Override (#389)**: Fixed dictionary engine short-circuit in `SettingsValues.java` when apps (such as Instagram DMs, Google Keep, or YouTube comments) set `TYPE_TEXT_FLAG_NO_SUGGESTIONS`, enabling autocorrect when "More autocorrect" is enabled while preserving password, email, and URI field protections.
- **Custom Layout Symbol Navigation (#360)**: Fixed state machine in `KeyboardState.kt` to clear `lastCustomIndex` when toggling from Symbols layout back to Alphabet via `SYMBOL_ALPHA`, preventing users from being trapped in custom layouts while preserving custom layout state during auto-caps.
- **Suggestion Strip Delete Mode Leak (#382)**: Fixed unintended word blocking when selecting suggestions after a long-press by disarming delete mode listeners on click, pick, and strip refresh.
- **Action Key Navigate Flag Fallback (#369)**: Added AOSP-compliant fallback handling in `InputTypeUtils.java` for `IME_FLAG_NAVIGATE_NEXT` and `IME_FLAG_NAVIGATE_PREVIOUS` when `EditorInfo` action is unspecified.
- **Duplicate Action Entry on Composing Text (#380)**: Ensured composing text is explicitly committed (`finishComposingText()`) before performing editor actions to prevent duplicate linebreaks or character entries.
- **Physical Keyboard Shortcut Selection (#397)**: Fixed candidate selection via physical keyboard shortcuts when the suggestion strip is collapsed or hidden.
- **Unit Test Suite Reliability**: Resolved all unit test failures (`203/203` passing tests), ensuring zero regressions in emoji sequence boundaries, symbol-prefixed text expansions, and Hangul syllable composition.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.0.9-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.0.9-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.0.9-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.0.9-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
