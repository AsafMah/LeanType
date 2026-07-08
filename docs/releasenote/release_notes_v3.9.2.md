### 💖 Support Our Work
*   We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New

### 👆 Gesture & Swipe Engine
*   **Accuracy Fix (Hitbox Center)**: Updated mapping logic to use key hitbox center coordinates rather than visual bounds, resolving rightward touch offsets (misrecognitions like "just" registering as "jest").
*   **Performance Optimization**: Added dynamic threshold early-exit bounds to the L2 gesture matching loop, significantly reducing CPU usage by skipping poor candidates.

### 🛠️ Visual & Keyboard Settings
*   **Accent-colored direct deletion**: Added a direct delete button (trash bin icon) in the downloadable dictionary lists to allow quick deletion of other layout dictionaries.
*   **Separate Experimental Dictionaries**: Fixed a naming/path collision where installing a main dictionary caused the experimental version to show as installed.
*   **Missing Dictionary Toolbar Redirect**: Redirects missing dictionary button directly to settings rather than showing a placeholder.
*   **Reset Prediction Context**: Reset word prediction context to the beginning of the sentence on new lines.
*   **Backspace Emoji Grouping**: Prevented non-emoji symbols (e.g. mathematical operators, box drawings) from being grouped and deleted together under backspace.
*   **Custom Translation Target**: Added support for custom translation target language options.

### 📦 Build & Package Size
*   **Exclude English Assets**: Excluded all prepackaged English dictionary assets from the standard flavor APK builds to optimize package size.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_3.9.2-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet |
| **`1-LeanType_3.9.2-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_3.9.2-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_3.9.2-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
