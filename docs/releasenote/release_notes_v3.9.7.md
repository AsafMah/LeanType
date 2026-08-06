### 💖 Support Our Work
*   We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New

### 🛠️ Compatibility & Reproducible Builds
- **F-Droid Reproducible Build Fix**: Forced `android.enableR8.fullMode` to `false` in `settings.gradle` to resolve compiler output differences between build environments, ensuring consistent build hashes.

### ⚡ Performance & Focus Latency
- **Android 17 Startup Optimization**: Resolved IME startup and focus latency on Android 17 by caching layouts and state settings, bypassing redundant settings reloads.

### 🎨 User Interface & Styling
- **Themed Translation Bar Layout**: Styled button layouts for the horizontal language selector bar and aligned text colors with standard key themes for higher contrast. Fixed horizontal width constraint that pushed the close button off-screen.
- **Toolbar Swipe-to-Dismiss**: Added support for swiping to close/dismiss the toolbar.
- **Sorted Translation Target Languages**: Sorted the translation language selector dynamically to show last used target languages first.

### 📖 Language & Corrective Dictionaries
- **Turkish Case-Folding Blacklist Fix**: Fixed Turkish word blacklist filtering by processing case-folding logic using the Turkish locale directly, correctly treating dotless `ı` and dotted `i` as independent characters.
- **Dictionary Upgrade & Protection**: Added support for in-app dictionary upgrades and protected user-downloaded dictionaries from accidental deletion.
- **Multilingual Settings Visibility**: Fixed the multilingual settings option to show when at least one secondary language/layout is enabled.
- **Immediate Download Status Refresh**: Ensured the dictionary installation status refreshes immediately after downloading from the missing dictionary dialog.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_3.9.7-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_3.9.7-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_3.9.7-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_3.9.7-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
