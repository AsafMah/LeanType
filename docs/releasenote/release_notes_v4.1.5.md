### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget and have little time for marketing. If LeanType helps you daily, please consider becoming a sponsor on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leantype). Even if you can't contribute financially, sharing LeanType with your friends, family, or on social media makes a world of difference to help our project grow. Thank you for your support!

## 🚀 What's New in v4.1.5

### ✨ New Features & Enhancements

- **Ergonomic Default Text Edit Layout**: Redesigned default text edit layout featuring a central Select key surrounded by directional navigation arrows for intuitive one-handed editing.
- **Classic Text Edit Option**: Retained the original editing layout as `editing_classic` for users who prefer the legacy layout.
- **Plugin ClassLoader & Path Resolution**: Refined plugin ClassLoader lifecycle management and native model directory resolution for seamless offline translation and companion plugin integration.

### 🐛 Bug Fixes

- **Text Edit Key Border Rendering**: Fixed Enter and Action keys in text editing mode to consistently fill rectangular tiles when key borders are turned off.
- **ProGuard & WorkManager Runtime**: Ensured `androidx.work` and `ListenableWorker` classes are preserved under R8 minification for reliable background plugin tasks.

## 📦 Choose Your Flavor

| Flavor                                          | Primary Focus    | AI Engine        | Plugins Setup                  | Internet                         | Self-Updater         |
|:----------------------------------------------- |:---------------- |:---------------- |:------------------------------ |:-------------------------------- |:-------------------- |
| **`1-LeanType_4.1.5-standardfull-release.apk`** | **Recommended**  | Cloud AI         | In-app download or File import | Optional ( AI/Updates/plugins)   | ✅ In-App Auto Update |
| **`1-LeanType_4.1.5-standard-release.apk`**     | **F-Droid**      | Cloud AI         | In-app download or File import | Optional ( AI/plugins)           | ❌ None               |
| **`2-LeanType_4.1.5-offline-release.apk`**      | **Offline AI**   | Local LLM (GGUF) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None               |
| **`3-LeanType_4.1.5-offlinelite-release.apk`**  | **Offline Lite** | None             | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None               |

> 💡 **Plugin Compatibility**: All 4 flavors support **Offline Handwriting Recognition**, **Offline Translation**, and **Offline Voice Dictation** via plugins, and work 100% offline.
