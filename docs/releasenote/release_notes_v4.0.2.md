### 💖 Support Our Work
* We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

### 🙏 Special Thanks
* Special thanks to [@AZADAYAZ](https://github.com/AZADAYAZ) for thorough testing, bug reports, and detailed UX feedback for this release!

## 🚀 What's New in v4.0.2

### 👆 Native Gesture Engine Cleanup & Safety
- **Removed Experimental Java Gesture Engine**: Completely stripped the unstable Java gesture engine. Gesture typing now relies exclusively on the native C++ library (`libjni_latinimegoogle.so`).
- **SIGSEGV Crash Protection**: Added strict validation during JNI library loading so incompatible libraries fail gracefully instead of crashing.

### 🌐 Translation Toolbar & Custom Languages
- **Universal Long-Press Delete**: Enabled long-press removal for all target languages (default & custom) directly in the translation selector strip.
- **Persistent Custom Language History**: Created `TranslationUtils.kt` to accumulate custom target languages in history without replacing older entries.
- **Clean UI & Deduplication**: Removed redundant `"Custom..."` button from the translation selector strip and added case-insensitive language deduplication.
- **IME Window Token Crash Fix**: Fixed `BadTokenException` on translation dialogs by attaching `windowToken` via `showDialogForIme`.

### 🛠️ Bug Fixes & Stability Improvements
- **Always-On Suggestions**: Fixed suggestions on special / non-standard fields (like Google Translate and search inputs).
- **Emoji & Typeface**: Honored system emoji settings and applied custom emoji typefaces to suggestion strip emoji results.
- **Emoji Dict Detection**: Added support for detecting `emoji.dict` (without underscore suffix).
- **PointerTracker Stability**: Added defensive null checks for `sDrawingProxy` to prevent crashes.
- **IME Lifecycle & System Compatibility**: Cancelled gesture indexing tasks and non-blocking dictionary cleanup on `onDestroy`; exported ringer mode receiver for Android 14+.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.0.2-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.0.2-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.0.2-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.0.2-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
