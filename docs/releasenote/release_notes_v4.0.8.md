### 💖 Support Our Work
* We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New in v4.0.8

### ✨ New Features & Enhancements
- **Foldable & Large Screen Support (#281)**:
  - Automatic dynamic screen profile detection (`COMPACT` vs `LARGE`) based on window width (`widthDp >= 600`) and `smallestScreenWidthDp >= 600`.
  - Profile-aware preference keys (`_compact` / `_large`) with fallback to legacy settings.
  - Split keyboard default enabled for `LARGE` profile while preserving compact phone defaults.
  - Instant on-the-fly keyboard reload on fold, unfold, or window resize without closing the keyboard.
- **Physical Keyboard Suggestion Shortcuts**: Number hints (`¹`, `²`, `³`) on suggestion strip entries now correctly display only when a physical keyboard is actively in use (soft keyboard hidden), fixing false positives on devices (e.g. POCO/MIUI) where Android framework misreports hardware keyboard presence.

### 🐛 Bug Fixes & Stability Improvements
- **Text Expander Regex Expansion**: Fixed immediate text expansion failing for standalone non-letter symbols (e.g. `∆`, `£`) and symbol-prefixed regex patterns.
- **Translation Plugin Loading**: Fixed `AbstractMethodError` when importing dynamic translation plugin APKs on minified release builds by explicitly preserving `ITranslationProvider` interface methods in Proguard/R8 rules.
- **Suggestion Shortcut Hints**: Fixed `¹²³` digit hints appearing on suggestion strip even without a physical keyboard connected, caused by unreliable `Configuration.keyboard` / `hardKeyboardHidden` flags on certain OEM devices.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.0.8-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.0.8-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.0.8-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.0.8-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
