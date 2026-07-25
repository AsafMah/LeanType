### 💖 Support Our Work
*   We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New

### 🛠️ Bug Fixes & Stability
- **Battery & Threading Fix**: Resolved a critical battery drain issue during fallback gesture typing by preventing redundant background threads from building the gesture index simultaneously.
- **Regional Dictionary Fallback**: Fixed an issue where regional/variant main dictionaries (like Persian `fa_IR`) were downloaded but not detected by language-only keyboard layouts (like `fa`).
- **Floating Mode Selection Fix**: Fixed text selection and deletion issues (including selection retrieval via `getSelectedText`) when the IME window is hidden in floating mode.
- **Backspace Selection Priority**: Corrected the deletion priority of selected text when pressing backspace.

### 🌟 Features & Improvements
- **First-Word Suggestion Toggle**: Added a user setting toggle to enable or disable suggestions for the first word in a text field.
- **Thai Word Segmentation**: Preserved Thai word boundaries and segmentation behavior when using text expansion.
- **Toolbar Pref Update**: Quick pin toolbar is now disabled by default to keep the interface clean out-of-the-box.
- **Documentation & Flavor Clarifications**: Added explicit handwriting support notes for the `standardfull` flavor.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.0.0-beta1-standardfull-debug.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.0.0-beta1-standard-debug.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.0.0-beta1-offline-debug.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.0.0-beta1-offlinelite-debug.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
