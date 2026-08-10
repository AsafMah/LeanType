### 💖 Support Our Work
* We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New in v4.1.0

### ✨ New Features & Enhancements
- **Inline Clipboard Item Editing**:
  - Added right-swipe gesture on clipboard history items to trigger inline edit mode directly within the toolbar strip (`[Text│] [Save] [✕]`).
  - Added tap-to-position cursor placement on the editing buffer text.
  - Routed spacebar and delete key horizontal swipe gestures directly to the clipboard edit buffer for intuitive text manipulation.
- **Settings List & Handwriting Search**:
  - Added inline search bar option for list preference dialogs and handwriting recognition language selection dialogs.
- **Foldable Mode & Settings Performance**:
  - Restored foldable screen profile toggle in Appearance settings.
  - Fixed `LazyColumn` key collision sluggishness across settings screens.

### 🐛 Bug Fixes & Stability Improvements
- **Numeric Sequence Single-Click Backspace Fix**:
  - Resolved an issue where pressing backspace once deleted an entire typed numeric sequence (e.g., `12345`) instead of a single digit.
  - Fixed emoji sequence boundary detection in `StringUtils.kt` (`isEmojiSequenceEnd`) so ASCII digits (`'0'..'9'`), `'#'`, and `'*'` are not misclassified as emoji sequence ends.
  - Decoupled batch mode gesture typing deletion from single-character backspace in `InputLogic.java`.
  - Added numeric string guards (`!TextUtils.isDigitsOnly`) to prevent autocorrect revert (`revertCommit`) from applying to numeric sequences.
- **Toolbar & Clipboard Auto-Spanning**:
  - Aligned auto-spanning behavior across normal toolbar and clipboard toolbar (`mAutoSpanToolbarKeys`).
  - Equal-weight auto-spanning triggers when keys fit within available container width (`totalKeysWidth <= containerWidth`).
  - Automatically falls back to standard 36dp x 36dp square keys starting at `Gravity.START` with smooth horizontal scrolling when keys cover or exceed container width.
- **Suggestion Strip Word Truncation Fix**:
  - Resolved suggestion word cropping (e.g., `"physics"` truncated to `"physi"`) by setting `layout_width="0dp"` with `layout_weight="1"` on `suggestions_strip` so layout weight pass computes true container width prior to word measurement pass.
- **Physical Keyboard Toolbar Exemption**:
  - Exempted Emoji and Clipboard keyboards from physical keyboard suppression so toolbar and emoji panels remain fully visible and usable when a physical keyboard is attached.
- **Handwriting Engine & Canvas Fixes**:
  - Handled `CLEAR_HANDWRITING` and handwriting UI keycodes in `InputLogic.handleFunctionalEvent` to prevent "Unknown event" crashes.
  - Set transparent background on handwriting canvas to eliminate duplicate shifting background images.
- **Unit Test Suite Verification**:
  - All 207 unit tests pass cleanly with zero regressions.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.1.0-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.1.0-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.1.0-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.1.0-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |

