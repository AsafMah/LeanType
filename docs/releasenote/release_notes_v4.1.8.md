### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget and have little time for marketing. If LeanType helps you daily, please consider becoming a sponsor on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leantype). Even if you can't contribute financially, sharing LeanType with your friends, family, or on social media makes a world of difference to help our project grow. Thank you for your support!

## 🚀 What's New in v4.1.8

- **Custom Click Sounds & 12 Built-in Presets**: Introduced a zero-latency native audio feedback engine with 12 out-of-the-box sound styles (iOS Tap, Mechanical Cherry MX, Thocky Mechanical, Vintage Typewriter, Retro CRT Terminal, Bubble Pop, Soft Velvet/Pudding, Woodblock Minimal, Acoustic Marimba, Modern Crisp Tick, Sci-Fi Cyberpunk, and 8-Bit Chiptune Arcade), live sample audition (▶️), volume preview, and custom `.zip` sound pack import support.
- **Harmonized Import Dialogs & Language Selection**: Added language selection confirmation dialogs with auto-detection for Handwriting, Translation, and Dictionary model imports, and modernized the Dictionary "Add new" dialog with a clean `PreferenceDialog` layout.
- **AI Translation Hardening**: Enhanced translation prompts and output sanitizer with preamble and code-fence filtering to prevent conversational artifacts from LLM providers, and fixed active AI provider token checks in `translateAsync`.
- **Clean Notification & Toast Guardrails**: Suppressed redundant plugin-not-found toasts when the translation engine is explicitly configured to AI mode.

## 📦 Choose Your Flavor

| Flavor                                          | Primary Focus                  | AI Engine        | Plugins Setup                  | Internet                         | Self-Updater         |
|:----------------------------------------------- |:------------------------------ |:---------------- |:------------------------------ |:-------------------------------- |:-------------------- |
| **`1-LeanType_4.1.8-standardfull-release.apk`** | **Convenience (Recommended)**  | Cloud AI         | In-app download or File import | Optional (AI/Updates/plugins)    | ✅ In-App Auto Update |
| **`1-LeanType_4.1.8-standard-release.apk`**     | **F-Droid**                    | Cloud AI         | In-app download or File import | Optional (AI/plugins)            | ❌ None               |
| **`2-LeanType_4.1.8-offline-release.apk`**      | **Offline**                    | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None               |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 5.0+), **Offline Translation** (Android 7.0+), **Offline Handwriting Recognition** (Android 8.0+), and **Offline AI Proofreading** (Android 8.0+) via modular plugins, and work 100% offline.
