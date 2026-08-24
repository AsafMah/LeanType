### 💖 Support Our Work
As an open-source, community-funded project, we operate on a very limited budget and have little time for marketing. If LeanType helps you daily, please consider becoming a sponsor on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leantype). Even if you can't contribute financially, sharing LeanType with your friends, family, or on social media makes a world of difference to help our project grow. Thank you for your support!

## 🚀 What's New in v4.1.4

### ✨ New Features & Enhancements
- **Offline Handwriting & Translation Everywhere**: Offline handwriting recognition and translation plugins are now fully supported across all app flavors (Standard, Standard Full, Offline, and Offline Lite).
- **Offline Model Downloader & Importer**: In-app model downloads for online flavors and direct browser download popups + multi-file import for offline flavors.
- **Unified Plugins Dashboard**: Reorganized settings with a single Plugins hub showing active status and automated update checks for Voice and Handwriting plugins.
- **Expanded Toolbar Keys**: Handwriting, Translation, and Clipboard Search toolbar keys are now available on all builds with improved auto-spacing.
- **Smart Text Expander**: Added handy clipboard modifiers (`%clipboard:clean%`, `:singleline`, `:title`, `:slug`, `:upper`, `:replace`) that automatically clean Wikipedia citations and format text.

### 🐛 Bug Fixes
- **Emoticon Fix**: Typing emoticons like `:)` and `:(` no longer resets the symbol keyboard back to letters.
- **Personal Dictionary**: Fixed word auto-learning so new words are properly saved based on your configured threshold.
- **Handwriting Tag Fix**: Fixed model loading so regional languages (like English Australia or Hindi) load without affecting other language variants.
- **Emoji Search Setting**: Strictly honored the inline emoji search toggle when turned off.

## 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins & Models | Internet Permission | Self-Updater |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`1-LeanType_4.1.4-standardfull-release.apk`** | **Recommended** | Cloud AI (Gemini, Groq, OpenAI) | In-app download or File import | Optional (Used for AI/Updates) | ✅ In-App Auto Update |
| **`1-LeanType_4.1.4-standard-release.apk`** | **F-Droid / FOSS** | Cloud AI (Gemini, Groq, OpenAI) | In-app download or File import | Optional (Used for AI) | ❌ None (F-Droid rules) |
| **`2-LeanType_4.1.4-offline-release.apk`** | **Offline AI** | On-Device Local LLM (GGUF) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None |
| **`3-LeanType_4.1.4-offlinelite-release.apk`** | **Offline Lite** | None (Lightest size) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None |

> 💡 **Plugin Compatibility**: All 4 flavors support **Handwriting Recognition**, **Offline Translation**, and **Voice Dictation** via our dedicated companion plugins, and work 100% offline.
