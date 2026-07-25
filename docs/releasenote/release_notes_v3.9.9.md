### 💖 Support Our Work
*   We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New

### 🛠️ Crashes & ANR Fixes
- **Native JNI SIGABRT Fix**: Fixed a native crash in the LatinIME keyboard library (`libjni_latinime.so`) caused by thread-unsafe memory access during dictionary word iteration.
- **Gesture Indexer ANR Fix**: Prevented keyboard freezes and Application Not Responding (ANR) errors by running the fallback Java gesture indexer asynchronously on a background thread instead of blocking the main thread.
- **Asynchronous Dictionary Cleanup**: Moved dictionary closing and cleanup to a background coroutine to prevent the main thread from blocking on JNI write locks when switching languages.

### 📖 Dictionary Preservation on Upgrade
- **Manual Import Preservation**: Stopped the app from deleting manually imported or replaced custom dictionaries during version upgrades.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_3.9.9-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_3.9.9-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_3.9.9-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_3.9.9-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
