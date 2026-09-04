### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget and have little time for marketing. If LeanType helps you daily, please consider becoming a sponsor on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Even if you can't contribute financially, sharing LeanType with your friends, family, or on social media makes a world of difference to help our project grow. Thank you for your support!

## 🚀 What's New in v4.1.9

### ✨ New Features
- **Offline Camera OCR & Screenshot Text Extraction**: In-keyboard live camera viewfinder with top flash control and gallery picker, plus an automatic screenshot suggestion pill (`[OCR] [Screenshot] [X]`) for 1-tap text extraction. Includes advanced text formatting (casing transformations, single-line/paragraph joins, dehyphenation, punctuation normalization, bullet stripping, whitespace/noise filtering), auto-copy, and auto-insert.
- **Real-Time Inline Math Suggestions**: Live mathematical evaluation on typing `=` (e.g. `25*4=`, `500-15%=`, `(12+8)/4=`) with immediate suggestion strip answers and 1-tap in-place replacement. Configurable via `Settings -> Text correction -> Inline math calculation`.
- **Sound Packs Suite & Plugins Hub**: Dedicated Sound Settings screen under the Plugins Hub with 12+ built-in presets (iOS, Mechanical Cherry MX, Thocky, Typewriter, CRT, Bubble Pop, Velvet, Wood, Marimba, Modern Tick, Sci-Fi, 8-Bit Arcade), remote GitHub catalog downloads, physical modeling packs, unbundled assets for a lighter APK footprint, and custom `.zip` pack import.

### 🐛 Bug Fixes & Improvements
- **Typing & Auto-Capitalization**:
  - Improved auto-capitalization in chat apps and multiline fields after newlines ([#448](https://github.com/LeanBitLab/HeliboardL/issues/448)).
  - Restored fast double-tap on Shift to reliably lock Caps Lock.
  - Suppressed emoji suggestions during gesture/glide typing to avoid cluttering predictions ([#470](https://github.com/LeanBitLab/HeliboardL/issues/470)).
  - Explicitly finish composing text and commit words on IME editor actions (Enter / Next / Done).
  - Added case-matching autocorrect safeguards to prevent learned capitalized words from replacing regular words mid-sentence ([#477](https://github.com/LeanBitLab/HeliboardL/issues/477)).
  - Supported immediate text expansion when backspacing into shortcuts ([#476](https://github.com/LeanBitLab/HeliboardL/issues/476)).
- **UI & Toolbar Polish**:
  - Reliable swipe-down gesture anywhere on the toolbar to close the keyboard via `dispatchTouchEvent`.
  - Upgraded toolbar long-press indicators from asymmetric dots to centered micro-pills with 25% contrast.
  - Guaranteed high-contrast dynamic colors on Material You light themes ([#479](https://github.com/LeanBitLab/HeliboardL/issues/479)).
  - Unified screenshot and OCR suggestion chips into a compact pill layout with tight spacing.
- **Audio, Haptics & Voice**:
  - Enabled haptic feedback on key repeat for Backspace and navigation arrow keys ([#473](https://github.com/LeanBitLab/HeliboardL/issues/473)).
  - Prevented crashes and gracefully handled missing microphone permissions / audio initialization failures ([#466](https://github.com/LeanBitLab/HeliboardL/issues/466)).
  - Auto-cleanup legacy sound pack directories on duplicate re-import.
- **OCR & Stability**:
  - Cached and retained `PluginClassLoader` singleton in `OcrPluginLoader` to prevent `UnsatisfiedLinkError` on native `.so` reloads.
  - Enforced explicit `LayoutParams` height on OCR camera views to eliminate viewport expansion during keyboard resizing.
  - Scoped screenshot observers strictly to active keyboard sessions to optimize battery and thermal footprint.
  - Added truncation safeguards to AI prompts to prevent data loss and ensure clean prompt fallback ([#472](https://github.com/LeanBitLab/HeliboardL/issues/472)).

## 📦 Choose Your Flavor

| Flavor                                          | Primary Focus                 | AI Engine               | Plugins Setup                  | Internet                         | Self-Updater         |
|:----------------------------------------------- |:----------------------------- |:----------------------- |:------------------------------ |:-------------------------------- |:-------------------- |
| **`1-LeanType_4.1.9-standardfull-release.apk`** | **Convenience (Recommended)** | Cloud AI                | In-app download or File import | Optional (AI/Updates/plugins)    | ✅ In-App Auto Update |
| **`1-LeanType_4.1.9-standard-release.apk`**     | **F-Droid**                   | Cloud AI                | In-app download or File import | Optional (AI/plugins)            | ❌ None               |
| **`2-LeanType_4.1.9-offline-release.apk`**      | **Offline**                   | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None               |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 5.0+), **Offline Translation** (Android 7.0+), **Offline Handwriting Recognition** (Android 8.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+) via modular plugins, and work 100% offline.
