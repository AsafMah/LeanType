### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 What's New in v4.2.0

### ✨ Highlights
- **Offline Camera OCR & Screenshot Extraction**: In-keyboard live camera viewfinder with top flash control and gallery picker, plus 1-tap screenshot text extraction. Includes advanced formatting (casing, join styles, punctuation, dehyphenation), auto-copy, and auto-insert.
- **Real-Time Inline Math Suggestions**: Live calculation on typing `=` (e.g. `25*4=`, `500-15%=`) with immediate suggestion strip answers and 1-tap replacement.
- **Sound Packs Suite & Plugins Hub**: Dedicated Sound Settings with 12+ built-in presets, remote GitHub catalog downloads, physical modeling packs, unbundled assets for a lighter APK footprint, and custom `.zip` imports.

### 🐛 Bug Fixes & Refinements
- **OCR Plugin Lifecycle**: Dynamic ClassLoader invalidation and timestamp validation to prevent native library lookup errors during plugin reload.
- **Typing & Auto-Capitalization**: Improved multiline auto-capitalization in chat apps, restored fast double-tap on Shift for Caps Lock, suppressed emojis during gesture typing, added case-matching autocorrect safeguards, and supported immediate text expansion when backspacing into shortcuts.
- **UI & Toolbar Polish**: Swipe-down gesture anywhere on the toolbar to close keyboard, centered micro-pill indicators, and improved dynamic contrast on Material You light themes.
- **Audio, Haptics & AI Safeguards**: Added haptic feedback on key repeat for Backspace/arrow keys, handled missing microphone permissions gracefully, added toast notifications when AI output is truncated, and auto-cleaned legacy sound packs.

## 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Self-Updater |
|:---|:---|:---|:---|:---|:---|
| **`1-LeanType_4.2.0-standardfull-release.apk`** | **Convenience (Recommended)** | Cloud AI | In-app download or File import | Optional (AI/Updates/plugins) | ✅ In-App Auto Update |
| **`1-LeanType_4.2.0-standard-release.apk`** | **F-Droid** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ❌ None |
| **`2-LeanType_4.2.0-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 5.0+), **Offline Translation** (Android 7.0+), **Offline Handwriting Recognition** (Android 8.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+) via modular plugins, and work 100% offline.
