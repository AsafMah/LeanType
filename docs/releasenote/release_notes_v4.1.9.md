### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget and have little time for marketing. If LeanType helps you daily, please consider becoming a sponsor on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Even if you can't contribute financially, sharing LeanType with your friends, family, or on social media makes a world of difference to help our project grow. Thank you for your support!

## 🚀 What's New in v4.1.9

- **Offline Camera OCR & Screenshot Text Extraction**: Integrated an in-keyboard camera viewfinder and automatic screenshot detection pill for instant text extraction. Features rich formatting options (casing, line join, dehyphenation, punctuation normalization, bullet stripping, noise filtering) and customizable auto-copy / auto-insert actions.
- **Inline Math Calculation Suggestions**: Type any mathematical expression followed by `=` (e.g. `25*4=`, `500-15%=`, `(12+8)/4=`) to instantly see the evaluated result in the suggestion strip and replace the expression in one tap. Configurable via Text Correction settings.
- **Sound Packs Suite & Plugins Integration**: Dedicated Sound Settings under the Plugins Hub with support for remote sound pack downloads, physical modeling and synthesized instrument packs, unbundled assets for a lighter app size, and custom `.zip` pack import.
- **Typing & Editing Enhancements**: Restored quick double-tap on Shift to lock Caps Lock, improved auto-capitalization in chat and multiline fields, suppressed emoji suggestions during gesture typing, enabled immediate text expansion when backspacing into shortcuts, and added key repeat haptics for backspace and arrow navigation.
- **Toolbar & Visual Refinements**: Redesigned long-press indicators into subtle centered micro-pills, fixed swipe-down gesture from the toolbar to dismiss the keyboard, and ensured high-contrast dynamic colors across all light Material You themes.
- **Performance & Thermal Optimization**: Scoped screenshot observers to active keyboard sessions, retained OCR plugin classloaders to prevent native library reload errors, and hardened AI prompt truncation.

## 📦 Choose Your Flavor

| Flavor                                          | Primary Focus                 | AI Engine               | Plugins Setup                  | Internet                         | Self-Updater         |
|:----------------------------------------------- |:----------------------------- |:----------------------- |:------------------------------ |:-------------------------------- |:-------------------- |
| **`1-LeanType_4.1.9-standardfull-release.apk`** | **Convenience (Recommended)** | Cloud AI                | In-app download or File import | Optional (AI/Updates/plugins)    | ✅ In-App Auto Update |
| **`1-LeanType_4.1.9-standard-release.apk`**     | **F-Droid**                   | Cloud AI                | In-app download or File import | Optional (AI/plugins)            | ❌ None               |
| **`2-LeanType_4.1.9-offline-release.apk`**      | **Offline**                   | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None               |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 5.0+), **Offline Translation** (Android 7.0+), **Offline Handwriting Recognition** (Android 8.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+) via modular plugins, and work 100% offline.
