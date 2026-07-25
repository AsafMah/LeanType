### 💖 Support Our Work
*   We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New

### 🛠️ Kotlin Gesture Engine Clean Up
- **Removed Experimental Kotlin Engine**: Fully removed the experimental Kotlin gesture typing engine (`SwipeGestureEngineKotlin.kt`), its settings (advanced toggle), its keycode (`GESTURE_DEEP_SEARCH`), and associated icons/resource strings.

### 🎨 User Interface & Split Toolbar
- **Translation Selector Fix**: Fixed the target language list collapsing or showing only the close button in split/dual toolbar mode.
- **Top Toolbar Visibility**: Kept the top toolbar row fully visible when expanding the translation target language selector in split toolbar mode.

### ⚙️ Database & Reliability
- **Restore SQLite DB Fix**: Fixed database restore lockup and write crash (`SQLITE_READONLY_DBMOVED`) by closing helpers and active Room connections before deleting the database.
- **Native Dictionary SIGSEGV Fix**: Prevented a native SIGSEGV crash during dictionary traversal by holding the read lock for the entire traversal duration.

### ⚡ Welcome Wizard & Setup
- **Wizard Crash Fix**: Fixed a `ConcurrentModificationException` crash during step 3 of the Welcome Wizard when disabling/mutating enabled subtypes.
- **Default Gesture Engine**: Changed the default gesture typing engine to `"fallback"` (pure Java engine) consistently so that it works out of the box, rather than attempting to load `"native"` when not configured.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_3.9.8-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_3.9.8-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_3.9.8-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_3.9.8-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
