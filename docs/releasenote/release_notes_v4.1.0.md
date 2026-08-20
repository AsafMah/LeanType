### 💖 Support Our Work
As an open-source, community-funded project, we operate on a very limited budget and have little time for marketing. If LeanType helps you daily, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). Even if you can't contribute financially, sharing LeanType with your friends, family, or on social media makes a world of difference to help our project grow. Thank you for your support!

## 🚀 What's New in v4.1.0

### ✨ New Features & Enhancements
- **Inline Clipboard History Item Editing**: Right-swipe any clipboard item to edit text directly inside the toolbar strip (`[Text│] [✔] [✕]`), with precise tap-to-position cursor placement and spacebar/delete swipe gesture routing within the edit buffer.
- **Searchable Preference & Language Dialogs**: Added inline search bar filter to list preference dialogs and handwriting language selection.
- **Foldable Mode & Settings Performance**: Restored foldable screen profile toggle in Appearance settings and fixed `LazyColumn` key collision sluggishness.

### 🐛 Bug Fixes & Stability Improvements
- **Numeric Sequence Single-Click Backspace Fix**: Resolved single backspace deleting entire typed numeric sequences (e.g. `12345`) by fixing emoji sequence boundary detection (`StringUtils.kt`) for ASCII digits (`'0'..'9'`), decoupling batch-mode swipe deletion from single-character deletion in `InputLogic.java`, and adding numeric string guards against improper autocorrect reverts.
- **Toolbar & Clipboard Key Auto-Spanning**: Unified key auto-spanning (`mAutoSpanToolbarKeys`) across suggestion and clipboard toolbars; equal-weight spanning triggers when keys fit container width, falling back to 36dp x 36dp square keys starting at `Gravity.START` with smooth horizontal scrolling when keys exceed width.
- **Physical Keyboard Toolbar Exemption**: Exempted Emoji and Clipboard views from physical keyboard suppression so toolbar & emoji panels stay visible when a hardware keyboard is connected.
- **Handwriting Engine & Canvas Fixes**: Handled `CLEAR_HANDWRITING` keycodes in `InputLogic` to eliminate "Unknown event" crashes, and set transparent canvas background to eliminate duplicate background image shifting.
- **Unit Test Suite Verification**: All 207 unit tests pass cleanly with zero regressions.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.1.0-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.1.0-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.1.0-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.1.0-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |

