# Dev Log — LeanType Agent Sessions

This file records actions taken by AI coding agents in the LeanType repository. Each session is appended as a new entry. Agents are required to update this file at the end of every session (see `Agents.md §7.4`).

---

## 2026-05-22 — Create Agents.md and dev-log.md

### Context
The repository had no `Agents.md` or `dev-log.md`. The working branch is `copilot/create-or-update-agents-md`. The shallow clone contains three commits: the main branch tip and a merge of the `copilot/multi-part-words` branch that documented the wave-5 (multi-part word composition) two-thumb typing changes.

### Actions Taken
- Explored full repository structure: README, CONTRIBUTING, docs/, .github/, app/src/ layout, settings architecture, build files, test suite, CI workflow.
- Read `docs/FEATURES.md` (AI features, two-thumb typing, floating keyboard, clipboard features).
- Read `docs/TWO_THUMB_TYPING_INTERNALS.md` header to understand the combining-mode state machine.
- Read key source files: `Settings.java`, `Defaults.kt`, `SettingsValues.java`, `AIIntegrationScreen.kt`, `FloatingKeyboardManager.kt`, `InputLogic.java` (header), build configuration.
- Verified no `Agents.md` existed before this session.
- **Created `/Agents.md`** — comprehensive agent guide covering: project overview, repo layout, architecture (input pipeline, suggestions, settings 5-file pattern, AI integration, floating keyboard, build flavors), build/test commands, key source file reference, code conventions, agent workflow requirements (clarify first, mandatory tests, manual test plan format, dev log), feature-specific notes, upstream sync guidance, and useful links.
- **Created `/dev-log.md`** — this file.

### Decisions Made
- Named the file `Agents.md` (sentence-case) to match the problem statement's exact wording rather than the `AGENTS.md` all-caps convention — keeping consistent with the user's request.
- Included the dev log as a separate file (`dev-log.md`) rather than a section inside `Agents.md` so that the instructions file stays stable while the log grows over time.
- Captured the 5-file settings scaffolding pattern prominently (§3.3 and §6) because it is the most commonly missed convention for new contributors and agents.
- Included manual test plan format (§7.3) as a Markdown table template so agents can copy-paste it directly into PR descriptions.
- Noted the IME dialog / `EditText` limitation (§6) because it is a non-obvious gotcha that has burned previous sessions.

### Open Questions / Next Steps
- The problem statement mentions "everything from the fork" — if there are additional upstream branches or features not yet merged into this shallow clone, a full `git fetch --unshallow` would reveal them. A follow-up session could extend `Agents.md` with those details.
- If the user has specific preferences about which AI providers or model IDs to highlight (e.g., a recommended default), those should be clarified and added to §8.
- The `dev-log.md` format is a proposal; adjust the template in `Agents.md §7.4` if a different structure is preferred.

## 2026-05-22 — Replace clipboard inline editor with normal editor activity

### Context
The branch `copilot/improve-clipboard-editability` implemented clipboard clip editing inside the IME by embedding an edit panel and secondary keyboard in `ClipboardHistoryView`. The user clarified that edit mode is only worthwhile if it behaves like a normal text box using the user's regular selected keyboard.

### Actions Taken
- Removed the inline edit panel from `app/src/main/res/layout/clipboard_history_view.xml`.
- Removed edit-mode state, local text insertion, and local Shift/Symbol keyboard handling from `ClipboardHistoryView.kt`.
- Added `ClipboardClipEditActivity.kt`, a non-exported Activity with a normal focused `EditText`.
- Registered `ClipboardClipEditActivity` in `AndroidManifest.xml` with `stateAlwaysVisible|adjustResize`.
- Wired the clipboard long-press **Edit** action to launch the Activity with the clip id.
- Added `ClipboardDaoTest.kt` covering normal edit, empty-delete, duplicate merge, missing clip, and image-clip ignore behavior.
- Built `:app:assembleStandardDebug` and installed the standard debug APK on the connected phone.

### Decisions Made
- Chose an Activity instead of an IME-hosted view because Android only routes the selected user keyboard normally to a real focused app editor, not to a child editor inside the IME window.
- Passed only the clip id to the Activity and reloaded from `ClipboardDao`, so the Activity edits the latest stored clip content.
- Kept existing `ClipboardDao.updateText()` semantics: empty text deletes, duplicate text merges, image clips are ignored, and normal edits bump timestamp.

### Manual Tests — Clipboard Clip Editing

| # | Steps | Expected Result |
|---|---|---|
| 1 | Open the clipboard view, long-press a text clip, and tap **Edit**. | A normal edit screen opens with the clip text focused. |
| 2 | Type using the keyboard that appears, including Shift and Symbols. | The user's normal keyboard handles input and stays in edit mode. |
| 3 | Tap **Save** after changing the text. | The clipboard clip updates and returns to the prior flow. |
| 4 | Tap **Cancel** after changing the text. | The clipboard clip remains unchanged. |
| 5 | Save an empty clip. | The clip is deleted through existing clipboard storage semantics. |

### Open Questions / Next Steps
- User should confirm on-device whether launching a normal Activity is acceptable visually, since the editor no longer lives inside the clipboard panel.

## 2026-05-22 — Make clipboard editor dialog-sized

### Context
After the Activity-based clipboard editor was installed, the user asked whether it could avoid covering the entire screen.

### Actions Taken
- Added `ClipboardClipEditActivityTheme` in `platform-theme.xml` using a dialog-style Activity theme.
- Switched `ClipboardClipEditActivity` to the dialog theme in `AndroidManifest.xml`.
- Changed the editor content layout to wrap height with a fixed-height multi-line `EditText`, so the editor is a floating dialog instead of fullscreen.
- Rebuilt `:app:assembleStandardDebug` and installed the APK on the connected phone.

### Decisions Made
- Kept the editor as an Activity, not an IME child view, so it remains a normal focused text box and continues to use the user's selected keyboard.

### Open Questions / Next Steps
- User should verify whether the dialog height is comfortable on the target phone; it is currently a 280dp editor area.

## 2026-05-22 — Reset keyboard around clipboard clip editing

### Context
The dialog-sized editor still opened with LeanType's clipboard panel as the active keyboard. The user asked for the keyboard to return from clipboard to default when the editor appears, and to return to the clipboard menu after saving.

### Actions Taken
- Updated `ClipboardHistoryView.showEditActivity()` to call `KeyboardSwitcher.setAlphabetKeyboard()` before launching `ClipboardClipEditActivity`.
- Updated the editor Activity save action to persist the edit, call `KeyboardSwitcher.setClipboardKeyboard()`, and then finish.
- Rebuilt `:app:assembleStandardDebug` and installed the APK on the connected phone.

### Decisions Made
- Used `setAlphabetKeyboard()` / `setClipboardKeyboard()` directly instead of key-event simulation so the editor flow does not accidentally type into the target app or depend on current key layout state.

### Open Questions / Next Steps
- User should verify the visible transition: Edit opens with the alphabet keyboard; Save closes the editor and restores clipboard history.

## 2026-05-22 — Smooth clipboard editor transitions

### Context
The user reported the Activity-based clipboard edit flow still felt janky after switching between alphabet and clipboard modes.

### Actions Taken
- Disabled Activity open/close transitions for `ClipboardClipEditActivity`.
- Delayed returning to the clipboard keyboard until shortly after save starts closing the editor, rather than swapping keyboard content underneath the still-visible dialog.
- Rebuilt `:app:assembleStandardDebug` and installed the APK on the connected phone.

### Decisions Made
- Kept the alphabet-before-edit and clipboard-after-save behavior, but changed the timing to reduce visible flicker.

### Open Questions / Next Steps
- User should verify whether the transition now feels acceptable on-device.

## 2026-05-22 — Return to clipboard on cancel without delay

### Context
The user requested that Cancel also return to the clipboard page, and that the return-to-clipboard animation/flicker be skipped.

### Actions Taken
- Changed Cancel to use the same close-and-return-to-clipboard path as Save.
- Removed the delayed clipboard restore; clipboard mode is now posted immediately after the no-animation Activity finish.
- Rebuilt `:app:assembleStandardDebug` and installed the APK on the connected phone.

### Decisions Made
- Kept `overridePendingTransition(0, 0)` on open/close and removed the artificial delay so returning to clipboard does not visibly animate through the normal keyboard first.

### Open Questions / Next Steps
- User should verify the Save and Cancel transitions on-device.

## 2026-05-23 — Add layout-driven shortcut rows

### Context
The prior direct-action shortcut implementation was reverted because it did not match the requested Nintype-style behavior. The corrected approach is opt-in, layout-driven shortcut rows: a vertical swipe from an eligible source row opens a temporary one-row panel, and releasing on a panel key dispatches that key through the normal keyboard action path.

### Actions Taken
- Added `SHORTCUT_TOP` and `SHORTCUT_BOTTOM` layout types and default JSON row layouts under `app/src/main/assets/layouts/shortcut_top/` and `app/src/main/assets/layouts/shortcut_bottom/`.
- Added opt-in gesture settings for shortcut rows, top-row shortcut swipe, and bottom-row shortcut swipe.
- Added `ShortcutRowKeys.kt` to parse shortcut-row layouts into popup-key specs so the feature reuses existing slide-to-select popup panel behavior.
- Extended `DrawingProxy` and `MainKeyboardView` with `showShortcutRowKeyboard(...)`.
- Extended `PointerTracker` with shortcut-row swipe state, top/bottom source-row eligibility, vertical-dominant trigger thresholds, and panel open/commit/cancel handling.
- Added a parser test verifying shortcut row layouts produce expected functional key codes.

### Decisions Made
- Reused `PopupKeysPanel` / `PopupKeysKeyboardView` for the temporary row surface instead of adding a new full keyboard layer or hardcoded actions.
- Kept the feature off by default behind a master toggle and separate top/bottom toggles.
- Excluded modifier keys and existing key swipers (space/delete) from shortcut-row source eligibility so shift/symbol/numpad chording and existing swipe behaviors keep their current release paths.
- Used existing layout/default/per-subtype infrastructure for row contents, so future customization can happen through layout files instead of serialized action maps.

### Manual Tests — Shortcut Rows

| # | Steps | Expected Result |
|---|---|---|
| 1 | Open **Settings → Gesture typing**, enable **Shortcut rows**, then enable **Top shortcut row swipe**. | Toggles persist; no crash. |
| 2 | In a text field, swipe up from a top normal typing-row key. | A temporary shortcut row appears; releasing on **Undo**, **Redo**, **Copy**, **Paste**, **Select word**, **Select all**, or **Emoji** triggers that key/action. |
| 3 | With number row enabled, swipe up from the number row. | The number row is treated as the top source row and opens the top shortcut row. |
| 4 | Swipe up from shift, symbol/alpha, numpad, space, or delete. | Shortcut row does not open; existing modifier/key-swipe behavior remains unchanged. |
| 5 | Enable **Bottom shortcut row swipe** and swipe down from the bottom normal typing row. | The bottom shortcut row appears and selected keys dispatch normally. |
| 6 | Start a normal gesture-typing word from a letter with horizontal/diagonal motion. | Gesture typing still works; shortcut row only opens for clear vertical-dominant movement. |
| 7 | Long-press comma or period. | Existing popup menus still open. |
| 8 | Hold/slide from numpad or symbol keys to pick a symbol. | Existing momentary symbol/numpad behavior still returns to the correct keyboard state. |
| 9 | Disable **Shortcut rows**. | No shortcut rows open from top/bottom swipes. |

### Open Questions / Next Steps
- On-device testing should confirm the popup-row placement feels correct for top-row and bottom-row swipes; if full-width positioning is not comfortable, a dedicated `ShortcutRowPanel` can replace the popup surface without changing the layout model.
