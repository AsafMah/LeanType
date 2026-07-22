### 💖 Support Our Work
* We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New in v4.0.0

### 🛠️ Bug Fixes & Major Stability Hardening
- **Native JNI Protection**: Added `isValidDictionary()` guards and exception handling around `BinaryDictionary` JNI calls to prevent C++ native crashes from killing the keyboard process.
- **ANR & Thread Freeze Prevention**: Added non-blocking timeouts to `CountDownLatch.await()` in backup/restore, file copy, and secondary dictionary lookup paths.
- **Gesture Index Thread Storm Fix**: Switched gesture index building to managed `KEYBOARD` executor pool with atomic state tracking, eliminating CPU spikes and thread proliferation.
- **Memory & View Leak Fixes**: Cleared static proxy references in `PointerTracker.clearOldViewData()`, enabled `largeHeap`, and trimmed memory when UI is hidden to fix OOMs on high-DPI devices.
- **PointerTracker & Settings Safety**: Fixed NPEs during rapid view switches and made `Settings.getValues()` null-safe during layout inflation.
- **Screenshot Scanner Optimization**: Replaced active background `ContentObserver` with an on-demand check to eliminate background CPU wakeups while typing.
- **Regional Dictionary Fallback & Aggregation**: Aggregated main and emoji dictionaries across variant and language fallback directories (`en-IN` -> `en`) and fixed regional main dictionary variant detection.
- **Emoji Dictionary Persistence**: Fixed issue where `emoji_*.dict` files failed to be recognized after downloading, and added preference tracking to prevent dictionary deletion on upgrade.

### 🤖 AI Enhancements
- **Proofread Anti-Answering Guard**: Prevented models (Qwen, Llama) from expanding prompts into multi-paragraph answers during proofreading.
- **Clean Translation Output**: Automatically strips section headers (`Translated text:`) and trailing reasoning/thinking blocks from translation outputs.

### 🌟 UI & Keyboard Improvements
- **First-Word Prediction Toggle**: Added user setting to enable or disable suggestions for the first word in a text field.
- **Hardware Keyboard Mode**: Added option to show only the toolbar when a physical keyboard is connected.
- **Toolbar Key Spacing**: Added equal key distribution for unscrollable expanded and dual toolbars.
- **Thai Word Segmentation**: Preserved Thai word boundaries and segmentation behavior when using text expansion.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.0.0-standardfull-debug.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.0.0-standard-debug.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.0.0-offline-debug.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.0.0-offlinelite-debug.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
