### 💖 Support Our Work
* We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New in v4.0.7

### 🌐 Translation Plugin & Language Improvements
- **Standard Flavor Translation Plugin**: Enabled Translation Plugin UI support on the `standard` (FOSS) flavor as well as `standardfull`.
- **Translation Engine Settings**: Added a dedicated Translation Engine selector to the AI Integration settings page.
- **Target Language Management**: Improved target language selection with per-item delete (trash) icons and confirmation dialogs, resolving ISO language tags (e.g. `es`, `de`, `ml`) to localized display names.
- **Plugin API Key Bypass & Toast Indicators**: Allowed translation plugins to function without requiring a separate AI API key, showing toast feedback when loaded.

### 📐 UI, Theme & Navigation Bar Fixes
- **Persistent Custom Navigation Bar Color**: Fixed custom navigation bar background behavior by reapplying custom colors on every input focus start (`onStartInputViewInternal`), preventing background reversion or transparency issues in apps like Firefox.
- **Safe System Bar State Restoration**: Guarded original system navigation bar color state to prevent capturing host app transparent states during window hide/show cycles.
- **Landscape Toolbar Key Sizing**: Capped edge and pinned toolbar key dimensions in landscape mode, fixing stretched or oversized toolbar keys in landscape orientation.

### ⚡ UX & Input Connection Improvements
- **Direct Word Deletion from Suggestions**: Long-pressing a word in the expanded suggestions panel now removes the word immediately without a confirmation dialog and shows a brief toast (`"[word]" removed`).
- **Input Connection Safety**: Added null/connection guards in `RichInputConnection.reloadTextCache` to prevent connection warnings and IPC retries when host apps delay connection setup.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.0.7-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.0.7-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.0.7-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.0.7-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
