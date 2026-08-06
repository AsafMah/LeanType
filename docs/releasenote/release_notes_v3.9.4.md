### 💖 Support Our Work
*   We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New

### 👆 Gesture & Swipe Engine (Pure-Java)
- **OutOfMemoryError Fixes**: Fixed OOM crashes during gesture index construction. Dictionary words are now streamed directly to avoid massive intermediate maps, and gesture path coordinates are packed into primitive variables to drastically reduce object allocations and GC pressure.
- **Blacklist/Blocked Words Isolation**: Prevented blocked words from leaking into user history, next-word suggestions cache, and gesture recognition indexes. Settings changes/removals now trigger immediate cache and gesture index reloads.
- **Cursor Selection Fix**: Fixed a bug where moving the cursor under automatic shift mode (such as auto-capitalization at the start of a sentence) would cause text to be unintentionally selected.

### 📝 Text Expander & Placeholders
- **Sequential Placeholders**: Added support for sequential template placeholders in text expander macros.
- **Synchronous Placeholder Deletion**: Rewrote placeholder navigation/deletion to execute synchronously via `deleteSurroundingText` and `setSelection` to prevent IPC selection desync.
- **Data Backup**: Added text expander data backup and restore capabilities, linking preference keys directly to the database backup category.

### 🎨 Keyboards & Custom Layouts
- **Dynamic Layout Slots**: Added support for up to five dynamic custom secondary layouts (`custom1` to `custom5`) with a direct deletion option in layout settings.
- **Layout Compatibility**: Resolved issues involving blocked words, custom fonts, and the symbols number row.

### 🛠️ Suggestions & Settings
- **Long-Press Suggestion Deletion**: Enabled long-press on suggestions in `MoreSuggestionsView` to directly delete/block suggestions. This dialog is wrapped in the platform dialog theme and resolves the `BadTokenException` by binding to the correct window token.
- **Auto-Correction Triggers**: Added a new settings preference to configure whether auto-correction is triggered by the Spacebar, Punctuation, or both.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_3.9.4-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet |
| **`1-LeanType_3.9.4-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_3.9.4-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_3.9.4-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
