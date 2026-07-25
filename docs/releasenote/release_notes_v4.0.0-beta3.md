### 💖 Support Our Work
* We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New

### 🛠️ Bug Fixes & Stability
- **PointerTracker Crash Fix**: Fixed `NullPointerException` during rapid view switches and keyboard mode changes.
- **Settings Initialization Safety**: Made `Settings.getValues()` null-safe to auto-load settings during layout inflation, preventing `InflateException` crashes.
- **Emoji Dictionary Detection & Persistence**: Fixed issue where `emoji_*.dict` files failed to be recognized after downloading. Added preference tracking to prevent emoji and custom dictionaries from being wiped on app upgrade or cache cleanup.
- **Regional Locale Dictionary Aggregation**: Resolved issue where regional locales (e.g. `English (India)` / `en-IN`) only displayed the emoji dictionary tab by aggregating main and emoji dictionaries across variant and language fallback directories (`en-US`, `en`).

### ⚡ Performance & Battery Optimization
- **On-Demand Screenshot Scanning**: Replaced continuous background MediaStore `ContentObserver` callbacks with an on-demand background query when input starts, eliminating unnecessary CPU wakeups while typing.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.0.0-beta3-standardfull-debug.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.0.0-beta3-standard-debug.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.0.0-beta3-offline-debug.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.0.0-beta3-offlinelite-debug.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
