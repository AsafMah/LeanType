### 💖 Support Our Work
As an open-source, community-funded project, we operate on a very limited budget and have little time for marketing. If LeanType helps you daily, please consider becoming a sponsor on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leantype). Even if you can't contribute financially, sharing LeanType with your friends, family, or on social media makes a world of difference to help our project grow. Thank you for your support!

## 🚀 What's New in v4.1.4

### ✨ New Features & Enhancements
- **Versatile Text Expander & Transformation Modifiers**: Introduced a modular modifier pipeline supporting composable filters on placeholders (`%clipboard:clean%`, `%clipboard:singleline%`, `%clipboard:title%`, `%clipboard:slug%`, `%clipboard:upper%`, `%clipboard:lower%`, `%clipboard:replace(a,b)%`).
- **Citation & Bracket Cleaner**: Added automatic citation stripping (`[1]`, `[1][2]`, `[note 1]`, `[citation needed]`) for Wikipedia and research paper text snippets in Text Expander.
- **Dedicated Handwriting Settings Screen**: Created a dedicated Handwriting settings dashboard featuring plugin status monitoring, stroke customization cards, and an in-app offline model manager with live progress downloads.
- **Voice Plugin Automated Update Checking**: Dynamic GitHub release checking and version comparison for LeanType Voice Plugin with one-tap update dialogs.
- **Reorganized Settings Structure**: Moved main dictionaries into Languages and layouts, and refactored the libraries section into a dedicated "Plugins" hub.

### 🐛 Bug Fixes & Stability Improvements
- **Emoticon & Colon Symbol Layout Switching**: Fixed symbols layout unexpectedly resetting to the alphabet keyboard when typing emoticons (`:)`, `:(`, `:/`) or colons followed by punctuation.
- **Inline Emoji Search Setting Guards**: Strictly enforced inline emoji search preferences so the search routine remains completely inactive when disabled in settings.
- **Personal Dictionary Auto-Learning**: Fixed dictionary type validation and connected session word count tracking to ensure typed words accurately learn to the personal dictionary according to the user's configured threshold.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.1.4-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwriting + In-App Updater | Internet | 
| **`1-LeanType_4.1.4-standard-release.apk`** | **F-Droid Build**. Standard - FOSS Only | Internet |
| **`2-LeanType_4.1.4-offline-release.apk`** | **Privacy Focused**. Offline AI (Local Models) | No Internet |
| **`3-LeanType_4.1.4-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. Zero AI integrations. | No Internet |
