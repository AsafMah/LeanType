### 💖 Support Our Work
* We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New in v4.0.9

### ✨ New Features & Enhancements
- **Full Localization & Translation Pipeline**:
  - Extracted all Text Expander UI components into string resources, enabling 100% translation support across 50+ supported languages.
  - Added comprehensive Turkish (`values-tr`) translation coverage (281+ missing string resources localized).
  - Added reusable localization gap analysis and translation application tools in `Pdoc/scripts/`.
- **Text Expander UI Usability**: Added vertical scrolling support to the shortcut creation and edit dialogs on smaller displays.

### 🐛 Bug Fixes & Stability Improvements
- **Suggestion Strip Delete Mode Leak (#382)**: Resolved recycled view icon/click listener leaks during rapid suggestion strip updates by tracking and cancelling pending delete mode runnables.
- **Duplicate Action Entry on Composing Text (#380)**: Ensured composing text is explicitly committed (`finishComposingText()`) before performing editor actions to prevent duplicate linebreaks or character entries.
- **Physical Keyboard Shortcut Selection (#397)**: Fixed candidate selection via physical keyboard shortcuts when the suggestion strip is collapsed or hidden.
- **Unit Test Suite Reliability**: Resolved all pre-existing unit test failures (`202/202` passing tests), ensuring zero regressions in emoji sequence boundaries, symbol-prefixed text expansions, and Hangul syllable composition.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.0.9-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.0.9-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.0.9-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.0.9-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
