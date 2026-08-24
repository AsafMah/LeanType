### 💖 Support Our Work
As an open-source, community-funded project, we operate on a very limited budget and have little time for marketing. If LeanType helps you daily, please consider becoming a sponsor on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leantype). Even if you can't contribute financially, sharing LeanType with your friends, family, or on social media makes a world of difference to help our project grow. Thank you for your support!

## 🚀 What's New in v4.1.4

### ✨ New Features & Enhancements
- **Dynamic Plugin Architecture**: Enabled standalone Translation and Handwriting plugins across all flavors (`standard`, `standardfull`, `offline`, `offlinelite`) with native `.so` library isolation, in-process reloading, and WorkManager task delegation.
- **Versatile Text Expander**: Added dynamic clipboard modifiers (`%clipboard:clean%`, `:singleline`, `:title`, `:slug`, `:upper`, `:replace`) and automatic citation cleaner (`[1]`, `[note 1]`) for Wikipedia and research text.
- **Comprehensive Offline Model Importer**: Added multi-pack handwriting imports (`recospec`, neural model, and dictionary FST), browser-assisted download dialogs for offline flavors, and instantaneous dialog loading.
- **Universal Toolbar Integration**: Handwriting, Translation, and Clipboard Search toolbar keys are now available across all builds (including OfflineLite) with auto-spanning calibration.
- **Refreshed Plugins Hub & Update Checking**: Unified status tags (`Active` / `Not installed`) across all plugins and automated GitHub release update checking for Voice and Handwriting plugins.

### 🐛 Bug Fixes & Improvements
- **Regional Handwriting Isolation**: Fixed language tag detection so regional models (e.g. `en-AU`, `hi-IN`) load in complete isolation without falsely marking other variants.
- **Emoticon Stability**: Fixed symbol keyboard resetting to the letters layout when typing emoticons (`:)`, `:-(`, `:(`) or colons followed by punctuation.
- **Personal Dictionary Learning**: Fixed auto-learning so unrecognized words accurately save after being typed the configured number of times.
- **Inline Emoji Search Guards**: Strictly enforced settings so emoji search stays completely dormant when turned off.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.1.4-standardfull-release.apk`** | **Recommended**. Cloud AI + Plugins + In-App Updater | Internet | 
| **`1-LeanType_4.1.4-standard-release.apk`** | **F-Droid Build**. Standard - FOSS Only | Internet |
| **`2-LeanType_4.1.4-offline-release.apk`** | **Privacy Focused**. Offline AI (Local Models) | No Internet |
| **`3-LeanType_4.1.4-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. Zero AI integrations. | No Internet |
