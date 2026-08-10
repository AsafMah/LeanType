### 💖 Support Our Work
* We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New in v4.1.0

### 🐛 Bug Fixes & Stability Improvements
- **Numeric Sequence Single-Click Backspace Fix**:
  - Resolved an issue where pressing backspace once deleted an entire typed numeric sequence (e.g., `12345`) instead of deleting a single digit.
  - Fixed emoji sequence boundary detection in `StringUtils.kt` (`isEmojiSequenceEnd`) so plain ASCII digits (`'0'..'9'`), `'#'`, and `'*'` are not misclassified as emoji sequence ends, preventing `getFullEmojiAtEnd` from calculating the entire numeric sequence as an emoji string.
  - Decoupled batch mode gesture typing deletion from single-character deletion in `InputLogic.java`, preserving block deletion for swiped words while enforcing character-by-character backspace for manual typing.
  - Added numeric string guards (`!TextUtils.isDigitsOnly`) to prevent autocorrect revert (`revertCommit`) and composing region setting (`setComposingRegion`) from applying to numeric sequences.
- **Unit Test Suite Verification**:
  - All 207 unit tests in `:app:testStandardfullDebugUnitTest` pass cleanly with zero regressions.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.1.0-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.1.0-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.1.0-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.1.0-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
