### 💖 Support Our Work
* We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New in v4.0.6

### 🛠️ Bug Fixes & Improvements
- **App Language Preference Relocation & Icon**: Moved the App Language preference from General Preferences to the **Languages and layout** screen and added a setting icon (`ic_settings_languages`) for visual consistency with the "Languages" option.
- **Process Locale Isolation**: Removed process-wide `Locale.setDefault()` mutation during locale wrapping, fixing text recapitalization and preventing next-word suggestion loss when non-default app languages are selected.
- **Native Window Context & Minimize Button Fix**: Preserved `InputMethodService` base context attachment and applied locale updates in-place, fixing bottom navigation bar chin padding, window inset listener breaks, and the minimize keyboard button across all app languages.
- **Instant In-Place Language Switching**: Replaced activity recreation (`recreate()`) with in-place resource configuration and settings container updates, eliminating the UI freeze and window flashing when changing app languages.
- **Number Hint Disappearance Fix**: Fixed an in-place list mutation bug in key layout parsing that caused number hints on key popups to vanish across field and keyboard switches.
- **Text Expander Performance & Safeguards**: Added in-memory shortcut caching and pre-compiled Regex evaluation to eliminate main-thread typing jank, added a 2,000 character limit for `%clipboard%` expansions, and protected battery service calls against exceptions.
- **Immediate Symbol & Special Character Expansion**: Extended "Expand immediately" support to non-composing symbols, math signs (e.g. `∆`), emojis, and special characters so they trigger instant shortcut expansion without requiring a space or extra keystroke.
- **Backspace Reverts Autocorrect Toggle Fix**: Fixed visibility conditions for the "Backspace reverts autocorrect" toggle so it correctly displays whenever either autocorrect or suggestion strip is enabled.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.0.6-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.0.6-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.0.6-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.0.6-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
