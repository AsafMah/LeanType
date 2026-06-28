### 💖 Support Our Work
*   We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New

### 🖱️ Touchpad & Text Editing Mode
- **Gboard-style Text Editing Mode**: Added a dedicated cursor navigation and editing overlay. Activated from the toolbar, it offers precise DPAD navigation, custom selection mode (Shift + arrows), clipboard actions (Select All, Copy, Cut, Paste), and editing utilities (Backspace, Forward Delete).
- **Dedicated Touchpad settings section**: Extracted Touchpad configuration from Advanced Gestures into its own dedicated settings category page.
- **Full-screen Touchpad**: Added a preference to automatically hide the suggestion strip and toolbar when Touchpad mode is active, maximizing the touchpad surface area.
- **Touchpad Close Button**: Added an overlay close button at the bottom-right corner of the touchpad, styled dynamically according to the active keyboard theme, for quick exit.

### 📋 Clipboard & Screenshot Suggestions
- **Persistent Suggestions Dismissal**: The keyboard now remembers if you dismissed a clipboard text or screenshot suggestion, preventing it from showing up again even across keyboard restarts. The dismissed state automatically resets when a new item is copied.
- **Reduced Suggestion Timeouts**: Reduced clipboard and screenshot suggestion timeouts to 1 minute to keep suggestions fresh.

### 🔤 Text Expander Improvements
- **Shortcut Prefix Fix**: Resolved a prefix matching bug where custom prefixes (e.g. `*` for shortcut `g`) were prepended twice (matching `**g` instead of `*g`), restoring instant and spacebar text expansion.
- **Improved Dialog Layout**: Stacked the Prefix and Shortcut input fields vertically inside the Add/Edit Shortcut dialog for better legibility.

### 📚 Dictionary & Settings Refinement
- **Polished Dictionary Settings UI**: Re-designed the layout using modern Material 3 Card components, colorful badges/chips for dictionary types, and uniform utility icon sizes.
- **Smart Language Fallback**: Regional variants (e.g., `ml-IN`) will now fall back to the general parent language dictionary (e.g., `ml`) if country-specific files are missing, ensuring seamless next-word predictions.
- **Unified Dictionary Toggling**: Grouped all dictionary subtypes (including downloaded emoji dictionaries) under a single master toggle per language card, and removed redundant buttons.
- **Aesthetic Filtering**: Hidden non-enabled regional variant cards from the settings menu to reduce clutter, while keeping them configurable inside the active language dialog.
- **Download Path Collision Fix**: Extracted specific locales from download URLs to prevent file collision and duplicate settings cards.

### ⚙️ Engine & Compatibility
- **Build Flavor Split**: Split standard build into FOSS-compliant "standard" (no ML Kit, F-Droid ready) and "standardfull" (includes ML Kit/Handwriting).
- **Secure Signing Workflow**: Added secure CI/CD release build signing workflow for reproducible builds.
- **ABI & Theme Fixes**: Re-added `armeabi-v7a` support to ABI filters, fixed popup key contrast-aware colors on AMOLED black theme, and improved split mode suggestions.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_3.8.9-standardfull-release.apk`** | **Recommended**. Cloud AI | Internet |
| **`2-LeanType_3.8.9-standard-release.apk`** | **Fdroid Build**. Standard + No Handwrite | Internet |
| **`3-LeanType_3.8.9-offline-release.apk`** | **Privacy Focused**. No Internet. Offline AI Only. | No Internet |
| **`4-LeanType_3.8.9-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI code. | No Internet |
