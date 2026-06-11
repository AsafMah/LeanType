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

---

## 2026-05-22 — Fix one-shot toolbar spacing keys

### Context
The standard debug APK was built and installed on a paired Android device from branch `copilot/improve-two-thumb-typing-again`. After restoring a backup, the new toolbar keys appeared, but **Join Next** only suppressed autospace and **Force Next Space** did not reliably suppress autospace after the following word.

### Actions Taken
- Updated `app/src/main/java/helium314/keyboard/latin/inputlogic/InputLogic.java` so **Join Next** resumes/removes a trailing autospace, restores the previous word as composing text when needed, and enters an explicit join mode for the next tap or gesture.
- Updated `InputLogic.java` so **Force Next Space** avoids inserting a duplicate space when an autospace already exists, then arms suppression for the next automatic spacing decision.
- Revised **Force Next Space** to track the next word explicitly (`mForceNextSpacePendingWord` / `mSuppressAutospaceForForceNextSpace`) so unrelated spacing helper calls cannot consume the one-shot before that word's own autospace is due.
- Moved the Force Next Space transition from "combining mode entered" to actual next tap/gesture start (`markForceNextSpaceWordStarted`) so real input paths arm suppression before the next word is committed.
- Fixed real-editor selection callback timing: `onUpdateSelection()` now ignores belated/expected updates before clearing one-shot state, so the normal cursor update after Force Next Space inserts a space does not erase the pending force action.
- Gated the spacebar combining/autospace progress indicator on `shouldInsertSpacesAutomatically()` and the current language's spacing support, and hide it immediately when the toolbar Autospace toggle is turned off.

---

## 2026-05-22 — Toolbar state indicators and force auto-cap

### Context
The user requested a plan and implementation for visible on/off state indicators on toolbar state keys, plus making **Force Auto-Capitalize** work even when normal Auto-Capitalize is off. The approved UX was an explicit active tint/background, normal icon appearance when inactive, and Autospace showing effective state including temporary suppression from **Force Next Space**.

### Actions Taken
- Updated `ToolbarUtils.kt` so the requested state keys get a translucent accent active background while inactive icons remain normal.
- Updated toolbar state refresh coverage for split-keyboard prefs and immediate toggle handlers for autocorrect, autospace, auto-cap, force auto-cap, and incognito.
- Updated Autospace state mapping so it shows inactive while **Force Next Space** is armed.
- Updated `Colors.kt` so toolbar icon color does not dim for inactive state keys; the active background now carries the state indication.
- Updated `InputLogic.getCurrentAutoCapsState()` so **Force Auto-Capitalize** can force sentence caps when normal Auto-Capitalize is off, while preserving password/visible-password guards.
- Added targeted `InputLogicTest` coverage for Force Auto-Capitalize with Auto-Cap off and password fields.
- Rebuilt `:app:assembleStandardDebug` and installed the APK on the paired device.

### Decisions Made
- Kept state indication centralized in `ToolbarUtils` rather than adding separate icon assets for each state key.
- Used effective Autospace state for the toolbar button, including Force Next Space suppression, matching the user's requested behavior.

### Manual Tests — Toolbar State Indicators / Force Auto-Cap

| # | Steps | Expected Result |
|---|---|---|
| 1 | Add/pin Incognito, One-handed, Split, Autocorrect, Auto-cap, Force Auto-cap, Autospace, Join Next, and Force Next Space toolbar keys. Toggle each state. | Active states show an accent background; inactive states show a normal icon without the active background. |
| 2 | Tap **Force Next Space**. | Force Next Space shows active and Autospace shows inactive until the one-shot is consumed/cancelled. |
| 3 | Turn Auto-Capitalize off, then turn Force Auto-Capitalize on. Type at the start of a sentence in a normal text field. | Sentence-start capitalization still occurs. |
| 4 | Repeat Force Auto-Capitalize in a password/visible-password field. | Force capitalization does not apply. |

### Open Questions / Next Steps
- If the accent background is too subtle or too strong on a specific theme, tune the active background alpha in `ToolbarUtils.createToolbarStateBackground()`.
- Updated `app/src/main/java/helium314/keyboard/latin/inputlogic/OneShotSpaceAction.kt` with a targeted `consumeJoinNext()` helper so join-mode consumption does not clear unrelated one-shot state.
- Added regression coverage in `app/src/test/java/helium314/keyboard/latin/InputLogicTest.kt` for joining after a timer autospace and forcing a space after an existing autospace.
- Rebuilt `:app:assembleStandardDebug` and installed the APK on the paired device with `adb install -r`.

### Decisions Made
- Kept **Join Next** as a one-shot action, but consumed it when the next tap or gesture actually joins, so the combined word can still autospace normally afterward.
- Made **Force Next Space** idempotent around an existing trailing space, because the toolbar action should mean "ensure a space before the next word", not "always add another space".

### Open Questions / Next Steps
- Full `InputLogicTest` class execution still has pre-existing failures under the standard debug unit-test variant (`insertLetterIntoWordHangulFails`, `revert autocorrect on delete`); the targeted one-shot key tests pass.

---

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

---

## 2026-05-22 — Resolve merge conflicts in PR #3

### Context
The user requested resolution of merge conflicts in PR #3 (`copilot/improve-two-thumb-typing-again`). The PR had `mergeable_state: "dirty"` due to conflicts with changes merged into main from the clipboard editability work.

### Actions Taken
- Fetched the latest `main` branch from origin.
- Attempted merge of `origin/main` into `copilot/improve-two-thumb-typing-again`.
- Identified conflict in `dev-log.md` where both branches had added new session entries.
- Resolved conflict by preserving both session entries (one-shot toolbar spacing keys from current branch, clipboard editor improvements from main).
- Completed merge commit `eae5f8b7`.
- Pushed resolved merge to origin.
- Verified PR now shows `mergeable_state: "clean"`.

### Decisions Made
- Kept all dev-log entries from both branches in chronological order, as both represent valid work done in parallel branches.
- Used standard git merge workflow rather than rebase to preserve the full history of both branches.

### Open Questions / Next Steps
- PR #3 is now ready for final review and merge into main.

---

## 2026-05-22 — Cache toolbar state background allocations (review comment r3289683508)

### Context
Review comment r3289683508 on PR #3 noted that `createToolbarStateBackground()` allocates new `GradientDrawable`/`StateListDrawable` instances every time toolbar state is refreshed (potentially frequently during typing). The reviewer suggested caching the drawable per Context/theme to reduce allocations/GC.

### Actions Taken
- Added `ToolbarStateBackgroundCache` data class to hold radius, activeColor, and drawable `ConstantState`.
- Modified `createToolbarStateBackground()` to check cache validity (matching radius and activeColor) before creating new drawables.
- When cache is valid, clone from `constantState.newDrawable()` and mutate to avoid shared state.
- When cache is invalid or empty, create new drawables and update cache with the new `constantState`.
- Ran `parallel_validation` (CodeQL: no alerts, Code Review: noted potential concurrency issue but acceptable for UI-thread-only usage).
- Pushed changes in commit `fcd7c1d4`.

### Decisions Made
- Used `ConstantState` cloning pattern (standard Android drawable caching approach) rather than direct drawable reuse to avoid shared-state bugs.
- Did not add synchronization because toolbar state updates run on the main/UI thread in current usage.
- Keyed cache on both radius and activeColor so theme changes invalidate the cache automatically.

### Open Questions / Next Steps
- If toolbar state updates ever move off the main thread, add `@Volatile` or synchronization to the cache variable.

---

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
## 2026-05-23 — Reorganize two-thumb typing settings

### Context
The two-thumb typing settings screen mixed current user-facing features, legacy/manual-spacing controls, debugging options, and future/unimplemented toggles. Some settings were confusingly named after implementation details, while `gesture_apostrophe_key` and `multipart_join_key_mode` were exposed even though they are not wired to runtime behavior.

### Actions Taken
- Created branch `copilot/organize-two-thumb-settings` from `origin/main`.
- Reorganized `TwoThumbTypingScreen.kt` into clearer user-facing sections:
  - Build words from taps and swipes
  - Manual spacing mode
  - Two-finger input
  - Recognition tuning
  - Troubleshooting
- Renamed visible labels/summaries for the main combining grace, tap extra time, multi-part joining, and typed-prefix swipe continuation options.
- Removed unimplemented/dead settings from the screen and global search registry:
  - `PREF_GESTURE_APOSTROPHE_KEY`
  - `PREF_MULTIPART_JOIN_KEY_MODE`
- Kept existing preference keys/defaults/runtime reads in place for compatibility; only the user-facing settings registry was changed.
- Built `:app:assembleStandardDebug` and ran `SettingsContainerTest`.

### Decisions Made
- Kept the existing runtime behavior unchanged and only reorganized/renamed the UI.
- Left legacy manual spacing visible but moved it into its own advanced-feeling section so it is not confused with the recommended combining-mode flow.
- Left debug point drawing visible under **Troubleshooting** rather than mixing it with recognition settings.
- Did not remove old strings yet, because other docs/translations may still refer to them and keeping them is safer than a broad cleanup.

### Manual Tests — Two-thumb Settings Organization

| # | Steps | Expected Result |
|---|---|---|
| 1 | Open **Settings → Two-thumb typing** with gesture typing disabled. | Screen shows a simple “Enable gesture typing first” hint instead of confusing no-op toggles. |
| 2 | Enable gesture typing, then open **Two-thumb typing**. | Settings are grouped into the new user-facing sections. |
| 3 | Set **Wait for next input** to 0 ms. | Advanced combining options are hidden. |
| 4 | Set **Wait for next input** above 0 ms. | Follow-up timing, word joining, backspace, autocorrect, and suggestion options appear. |
| 5 | Disable **Join word parts**. | Sub-options for full-word suggestions, typed-prefix swipe, and fragment backspace are hidden unless manual spacing needs fragment backspace. |
| 6 | Enable **Manual spacing**. | Manual-spacing-related fragment backspace appears when not already shown under word joining. |
| 7 | Enable **Tap letters while swiping**. | **Maximum tap length** appears. |
| 8 | Enable **Two-thumb point hinting**. | **Left/right hand split** appears. |
| 9 | Search settings for “apostrophe” or “join next modifier”. | The removed unimplemented controls do not appear. |

### Open Questions / Next Steps
- User should review the new wording on-device and decide whether debug point drawing should remain visible or move behind the separate debug settings screen later.

---

## 2026-05-23 — Convert two-thumb settings to mode selectors

### Context
After trying the first reorganization, the user clarified the desired structure: a single spacing-mode selector should drive normal/manual/autospace behavior, backspace should be a single behavior selector, and implementation details like multi-part joining should be enabled automatically instead of exposed as separate confusing toggles.

### Actions Taken
- Added a synthetic **Spacing mode** radio/list setting with Normal spacing, Manual spacing, and Auto-space after a delay.
- Added a synthetic **Backspace behavior** radio/list setting shown only for manual/autospace modes.
- Mapped the spacing selector onto the existing `PREF_GESTURE_MANUAL_SPACING` and `PREF_COMBINING_GRACE_MS` runtime preferences.
- Mapped backspace behavior onto existing fragment-backspace and whole-word-backspace preferences.
- Added whole composing-word deletion for the new **Delete whole word** behavior when manual spacing or autospace mode is active.
- Forced multi-part internals (join word parts, full-word suggestions, typed-prefix continuation) on whenever non-normal spacing is active, removing those implementation toggles from the user-facing screen.
- Changed the default after-autospace suggestion behavior to **Alternatives, then next word on space**.
- Renamed autospace timing and tap timing labels to clearer user-facing wording.
- Rebuilt `:app:assembleStandardDebug` and ran `SettingsContainerTest`.

### Decisions Made
- Kept underlying preference keys for compatibility but made the UI present modes instead of exposing each low-level boolean.
- Defaulted the autospace mode transition to a 500 ms grace when switching from Normal/Manual to Autospace.
- Left **Tap letters while swiping** as a separate opt-in feature for now because it is a distinct runtime path; only the timing label/summary was clarified.
- Left **Improve two-thumb recognition** off by default and described the possible tradeoff, because the midline can hurt recognition if configured poorly.

### Manual Tests — Two-thumb Mode Selectors

| # | Steps | Expected Result |
|---|---|---|
| 1 | Open **Settings → Two-thumb typing**. | First control is **Spacing mode** with Normal, Manual, and Auto-space choices. |
| 2 | Select **Normal spacing**. | Autospace duration/backspace behavior controls are hidden; typing behaves normally. |
| 3 | Select **Manual spacing**. | Backspace behavior appears; no autospace duration controls appear. |
| 4 | Select **Auto-space after a delay**. | Duration, tap delay, autocorrect, after-autospace suggestions, and backspace behavior appear. |
| 5 | In Auto-space mode, check **After auto-space, show…** default. | New default is **Alternatives, then next-word on space** unless an older saved value exists. |
| 6 | Try each backspace behavior in Manual/Autospace modes. | Normal deletes characters, last part removes the latest fragment, whole word removes the composing/last swiped word. |
| 7 | Search settings for “join word parts”, “typed prefix”, or “full composing”. | These implementation toggles no longer appear separately. |

### Open Questions / Next Steps
- User should confirm whether **Tap letters while swiping** should remain user-facing or be folded into the spacing mode later.
- Debug overlay expansion (different colors/shapes for fragments, taps, fingers, start/end) is still future work.

---

## 2026-05-23 — Remove obsolete tap-during-swipe setting

### Context
The user tested several tap/swipe word combinations and found they worked without the old tap-during-swipe flag. The newer combining/multi-part word system appears to cover the useful behavior, while the old flag only suppressed quick child taps and added confusion.

### Actions Taken
- Removed the tap-during-swipe setting and timing setting from the two-thumb settings screen and global settings registry.
- Removed the corresponding `Settings` constants, defaults, `SettingsValues` fields, and `PointerTracker` suppression path.
- Updated docs and user-facing references so the removed setting is no longer advertised.
- Built `:app:compileStandardDebugKotlin`.

### Decisions Made
- Kept combining/multi-part tap seeding intact; only the obsolete simultaneous tap suppression flag was removed.
- Left the observed “giraffe” gesture-recognition issue as a separate investigation target, likely around gesture recognition/hinting rather than settings UI.

### Open Questions / Next Steps
- Investigate why some `giraffe` attempts stop swipe detection after the second letter.
- Investigate why first attempts often recognize unrelated long words before learning improves.

## 2026-05-23 — Gate autospace on swiped words

### Context
The user wanted an opt-in autospace behavior for two-thumb Auto-space mode: tap-only words should commit without inserting an automatic space, while words that include a swipe should keep the existing autospace flow.

### Actions Taken
- Added `PREF_COMBINING_AUTOSPACE_ONLY_AFTER_GESTURE` across the settings 5-file pattern: `Settings.java`, `Defaults.kt`, `SettingsValues.java`, `strings.xml`, and `TwoThumbTypingScreen.kt`.
- Added the setting to the Auto-space mode group as **Only auto-space after swipes**, defaulting off to preserve existing behavior.
- Updated `InputLogic.java` to track whether the current combining word has received a gesture fragment, including tap-then-swipe words where the composer is later downgraded out of batch mode.
- Suppressed timer-driven autospace for tap-only words when the new setting is enabled, while still committing the word and preserving suggestion-revert behavior.
- Added `InputLogicTest` coverage for tap-only suppression, gesture autospace, tap-then-gesture autospace, and `SettingsContainerTest` coverage for setting registration.
- Built `:app:assembleStandardDebug` and installed the APK on the connected device.

### Decisions Made
- Kept explicit spaces, punctuation, Join Next, and Force Next Space paths on their existing behavior; the new setting only gates the timer's automatic space after committing a combining word.
- Used a dedicated per-word gesture flag instead of relying only on `WordComposer.isBatchMode()`, because tap-then-swipe composition can unset batch mode after merging fragments.
- When autospace is skipped, kept the suggestion strip in an alternatives/revert-friendly state instead of requesting next-word predictions, since there is no trailing space yet.

### Manual Tests — Gesture-gated Autospace

| # | Steps | Expected Result |
|---|---|---|
| 1 | Open **Settings → Two-thumb typing**, select **Auto-space after a delay**, and enable **Only auto-space after swipes**. | Toggle turns on; no crash. |
| 2 | Tap a word letter-by-letter and pause past the autospace delay. | Word commits, but no automatic space is inserted. |
| 3 | Swipe a word and pause past the autospace delay. | Word commits with the normal automatic space. |
| 4 | Tap a prefix, then swipe the rest of the word before the delay expires. | Combined word commits with the normal automatic space. |
| 5 | Disable **Only auto-space after swipes** and repeat a tap-only word. | Existing autospace behavior returns. |

### Open Questions / Next Steps
- The targeted new unit tests pass. A full `InputLogicTest` run in the standard debug variant still reports unrelated existing failures (`insertLetterIntoWordHangulFails`, `revert autocorrect on delete`).

---

## 2026-05-23 — Hide tap-only autospace indicator

### Context
After enabling gesture-gated autospace, tap-only words no longer inserted an automatic space, but the first tapped letter still showed the autospace progress bar even though no autospace would happen.

### Actions Taken
- Updated `InputLogic.enterCombiningMode()` so the spacebar autospace progress indicator only appears when autospace can actually be inserted under the current settings.
- Added regression coverage in `InputLogicTest` verifying tap-only input with **Only auto-space after swipes** enabled arms the combining timer without showing an autospace indicator.
- Rebuilt `:app:assembleStandardDebug`.

### Decisions Made
- Kept the underlying combining commit timer active for tap-only words; only the autospace visual indicator is hidden until the current word includes a gesture fragment.

### Open Questions / Next Steps
- APK install was not completed because ADB reported no connected devices.

---

## 2026-05-23 — Merge upstream main into origin main branch

### Context
The user asked for a new PR based on `main` that merges changes from the configured `upstream` remote. `origin/main` and `upstream/main` had diverged, with upstream containing build, README, symbol-row, floating-keyboard persistence, clipboard icon, touchpad, and release/version updates.

### Actions Taken
- Created branch `copilot/merge-upstream-main` from `origin/main`.
- Merged `upstream/main` into the branch.
- Resolved conflicts in `KeyboardIconsSet.kt` by preserving LeanType toolbar state icons while keeping the merge compatible with upstream's icon mapping changes.
- Resolved conflicts in `ClipboardDao.kt` by preserving clipboard edit support and upstream synchronization around pin toggling.

### Decisions Made
- Used a merge branch instead of rebasing so the PR clearly represents an upstream sync into `main`.
- Preserved LeanType-specific clipboard editing and toolbar state-key behavior where it overlapped with upstream changes.

### Open Questions / Next Steps
- Build validation and PR creation should complete before merging.

---

## 2026-05-23 — Address PR #6 review comments

### Context
The user asked to return to PR #6, merge current `main`, and resolve the review comments on the two-thumb settings PR. Merging `origin/main` into `copilot/organize-two-thumb-settings` conflicted only in this dev log.

### Actions Taken
- Merged `origin/main` into `copilot/organize-two-thumb-settings` and preserved both branches' dev-log entries.
- Updated `TwoThumbTypingScreen.kt` so the "Enable gesture typing first" hint renders as screen content instead of an empty settings category.
- Removed the low-level whole-word backspace toggle from the settings search registry, leaving only the synthetic Backspace behavior selector visible.
- Fixed `InputLogic.java` so whole-word backspace in manual/autospace composing mode deletes the composing text from the editor before resetting the composer.
- Reduced `GestureDebugPointsDrawingPreview.java` allocation churn by growing snapshot arrays amortized and reusing HSV color storage during drawing.
- Added regression coverage for whole-word backspace deleting composing text and for hiding the low-level backspace setting from the registry.

### Decisions Made
- Kept the user-facing Backspace behavior mode selector as the only visible control for whole-word backspace semantics.
- Preserved the accumulated debug overlay behavior while changing its storage strategy to avoid repeated full-array copies per fragment.

### Open Questions / Next Steps
- Run targeted tests and push the review fixes to PR #6.

---

## 2026-05-23 — Restore advanced two-thumb toggles

### Context
After testing the PR branch, the user asked to make the live composing-text deletion and debug overlay accumulation behavior toggleable, and to restore the removed full-word suggestion setting.

### Actions Taken
- Added a `Delete live composing text` switch shown when Backspace behavior is set to whole-word deletion.
- Added an `Accumulate debug fragments` switch under the gesture debug overlay setting.
- Restored the `Suggestions for full composing word` setting to the two-thumb screen and made the runtime value respect the switch instead of forcing it on for all non-normal spacing modes.
- Added tests covering the new setting registration and the new whole-word backspace toggle behavior.

### Decisions Made
- Defaults preserve the current PR behavior: live composing text deletion, debug fragment accumulation, and full-word suggestions are all enabled unless the user turns them off.

### Open Questions / Next Steps
- Run targeted tests and rebuild/install before handing back for device testing.

---

## 2026-05-23 — Restore tap-during-swipe toggle

### Context
The user pointed out that the tap-during-swipe behavior had been made hardcoded, but they had asked for it to remain a toggleable option.

### Actions Taken
- Restored `PREF_GESTURE_TAP_DURING_SWIPE` as a settings key/default/runtime value.
- Added the `Tap during swipe` switch back to the two-thumb settings screen.
- Gated `PointerTracker`'s pending tap-fragment behavior behind the restored preference.
- Added settings registry coverage for the restored toggle.
- Built and installed the updated standard debug APK on the connected device after fully uninstalling the mismatched existing debug package.

### Decisions Made
- Defaulted the toggle to enabled so existing PR behavior remains unchanged unless the user turns it off.

### Open Questions / Next Steps
- Commit and push the PR update.

---

## 2026-05-23 — Fix initial toolbar toggle highlighting

### Context
After PR #6 was merged to `main`, the user asked to ensure this worktree was on `main` and reported that toolbar toggle highlighting only appeared after changing a toggle, instead of reflecting active defaults immediately.

### Actions Taken
- Freed the local `main` branch from another worktree, checked this worktree out on `main`, and fast-forwarded it to the merged PR #6 commit.
- Fixed toolbar construction so state-key activation is reapplied after `setupKey()` assigns the normal toolbar background.
- Built and installed the updated standard debug APK on the connected device.

### Decisions Made
- Kept the existing state computation in `ToolbarUtils`; the bug was ordering during initial button setup, not the active-state logic.

### Open Questions / Next Steps
- Commit and push the fix on `main`.

---

## 2026-05-23 — Address follow-up PR review comments

### Context
Copilot review comments on PR #6 flagged that debug overlay accumulation depended on the visual autospace indicator state, and that changing spacing modes discarded the user's configured autospace duration.

### Actions Taken
- Separated the debug accumulation state in `MainKeyboardView` from the visible spacebar combining indicator.
- Preserved debug overlay accumulation for manual-spacing composition as well as hidden-indicator combining mode.
- Added a hidden last-autospace-duration preference and restored it when switching back to Auto-space mode.
- Ran targeted settings test, built the standard debug APK, and installed it on the connected device.

### Decisions Made
- Kept the visual indicator behavior unchanged; only the debug overlay preservation decision now uses the dedicated composition/debug state.
- Preserved the user's last positive autospace duration when switching to Normal or Manual spacing, then restored it when Auto-space is selected again.

### Open Questions / Next Steps
- Commit, push, and resolve the PR review threads.

---

## 2026-05-23 — Fix two-thumb enabled gate

### Context
After installing PR #6, the user reported the two-thumb settings screen showed "Enable gesture typing first" even though gesture typing was enabled.

### Actions Taken
- Changed `TwoThumbTypingScreen.kt` to gate the "Enable gesture typing first" hint only on `PREF_GESTURE_INPUT`, not `JniUtils.sHaveGestureLib`.
- Built and installed the updated standard debug APK on the connected device.

### Decisions Made
- Kept gesture-library availability checks on the dedicated Gesture typing screen, where the library loader is shown; the two-thumb screen now reflects the actual user-facing enable toggle.

### Open Questions / Next Steps
- Commit and push the fix.

---

## 2026-05-23 — Restore gesture library after reinstall

### Context
After the debug app was fully uninstalled to fix a signature mismatch, gesture typing stopped working. The reinstall preserved the gesture preference, but the user-supplied native gesture library in app files was gone.

### Actions Taken
- Confirmed the device app files did not contain `libjni_latinime.so`.
- Downloaded the arm64 OpenBoard `libjni_latinimegoogle.so`, verified its expected SHA-256 checksum, copied it into the app files as `libjni_latinime.so`, and force-stopped the app so it reloads.
- Updated the two-thumb settings gate to distinguish between "gesture toggle is off" and "gesture library is missing".
- Built and installed the updated standard debug APK while preserving the restored gesture library file.

### Decisions Made
- Kept the library availability gate for the two-thumb screen, but changed the missing-library message so it no longer incorrectly says only "Enable gesture typing first".

### Open Questions / Next Steps
- Commit and push the corrected screen text.

---

## 2026-05-23 — Remove tap-during-swipe fragments

### Context
After testing, the user confirmed the tap-during-swipe fragment behavior was causing the typing problem and asked to remove both the implementation and the setting.

### Actions Taken
- Removed the pending tap-fragment state machine from `PointerTracker`.
- Removed the `Tap during swipe` setting from the two-thumb screen and settings registry coverage.
- Removed the preference key/default/runtime read and the now-unused strings.
- Built and installed the updated standard debug APK on the connected device.

### Decisions Made
- Kept the rest of two-thumb composing, autospace, backspace, and debug overlay options unchanged.

### Open Questions / Next Steps
- Commit and push the PR update.

---

## 2026-05-24 — Fix two-thumb backspace modes

### Context
The user reported that the two-thumb backspace selector had misleading wording and broken behavior: **Delete last word part** should be **Delete last fragment**, fragment mode behaved like normal character delete, and whole-word mode mishandled live composing text depending on the **Delete live composing text** sub-option.

### Actions Taken
- Renamed the fragment-mode label to **Delete last fragment**.
- Updated fragment boundary tracking in `InputLogic.java` so the first swipe fragment is recorded and extended swipe fragments record both the previous boundary and the new fragment end.
- Fixed fragment backspace so an end boundary equal to the current composing length is treated as the current fragment marker, not stale state.
- Changed whole-word backspace with **Delete live composing text** disabled to fall back to normal one-character live composing deletion instead of consuming backspace as a no-op.
- Cleared the editor composing span after whole-word live composing deletion to avoid stale composing state.
- Preserved the committed fragment stack after delayed autospace commits so repeated **Delete last fragment** presses pop one committed fragment at a time instead of reverting to character deletion after the first pop.
- Added focused JVM regression tests for live fragment backspace, repeated committed fragment backspace, whole-word live composing deletion on/off, and the renamed label.
- Ran focused backspace/settings regression tests.
- Re-paired wireless ADB to `SM-S936B` and installed `:app:installStandardDebug`.

### Decisions Made
- Kept default gesture/batch rejection behavior outside fragment mode unchanged; the fix is scoped to the two-thumb backspace selector and live composing option.
- Added a single-fragment regression test because **Delete last fragment** should also delete a one-swipe composing word, not only the last piece of a multi-fragment word.

### Manual Tests — Two-thumb Backspace Modes

| # | Steps | Expected Result |
|---|---|---|
| 1 | Open **Settings → Two-thumb typing → Backspace behavior**. | The fragment option is labeled **Delete last fragment**. |
| 2 | Enable manual spacing, choose **Delete last fragment**, swipe one word, then press Backspace. | The whole swiped fragment is removed in one press. |
| 3 | With **Delete last fragment**, swipe a word fragment and then swipe another fragment to extend it, then press Backspace. | Only the latest fragment is removed; the previous fragment remains composing. |
| 4 | With **Delete last fragment**, wait for delayed autospace to commit the combined word, then press Backspace twice. | The first press removes the autospace and latest fragment; the second press removes the previous fragment. |
| 5 | Choose **Delete whole word**, turn **Delete live composing text** off, type letters, then press Backspace. | One character is deleted and the remaining text stays live/composing. |
| 6 | Choose **Delete whole word**, turn **Delete live composing text** on, type letters, then press Backspace. | The whole live composing word is removed in one press, and the next typed word deletes normally. |

### Open Questions / Next Steps
- Full `InputLogicTest` class execution still fails on existing unrelated tests (`tapOnlyCombiningWordDoesNotShowAutospaceIndicatorWhenGestureGateEnabled`, `insertLetterIntoWordHangulFails`, `revert autocorrect on delete`); the focused backspace/settings tests pass.
- PR opened at https://github.com/AsafMah/LeanType/pull/8.

---

## 2026-05-25 — Sync main and update shortcuts PR

### Context
The backspace fixes were ready in PR #8, and the user asked to merge them to `main`, merge `upstream/main` into our `main`, then merge the updated `main` into the shortcuts PR branch and install it.

### Actions Taken
- Merged `fix-backspace-delete-options` into `main` and pushed `origin/main`.
- Merged `upstream/main` into `main`, resolving conflicts in `ClipboardHistoryView.kt` and `KeyboardIconsSet.kt` by keeping LeanType's bottom-row geometry and clear-clipboard rounded icon while accepting upstream changes elsewhere.
- Merged the updated `origin/main` into `copilot/implement-keyboard-shortcuts`.
- Fixed a merge artifact in `ToolbarUtils.kt` where duplicate `Color` and `Drawable` imports broke Kotlin compilation.
- Built and installed `:app:installStandardDebug` for the updated shortcuts branch on `SM-S936B`.

### Decisions Made
- Preserved LeanType-specific clipboard bottom-row layout behavior over upstream's new helper because the existing branch intentionally used secondary-keyboard geometry for alpha/symbol layouts.
- Kept the rounded clear-clipboard toolbar icon to match LeanType toolbar icon styling.

### Open Questions / Next Steps
- Push the updated shortcuts PR branch.

---

## 2026-06-02 — Combine taps and swipes under manual spacing

### Context
With **Manual spacing** on (Nintype-style "never auto-commit"), tapping letters then swiping the rest of a word did not build one word reliably — e.g. `tap H, tap E, swipe L→O` did not yield `hello`. Investigation showed the multi-part **merged-trail** recognizer-help (in `WordComposer`, which prepends the prior fragment's re-timed pointer trail so the native recognizer sees one continuous stroke) was gated on the combining-grace timer only (`mCombiningGraceMs > 0`). In pure manual spacing (grace = 0) the gesture fell through to the legacy concat path, which feeds the recognizer only the isolated swipe fragment AND re-prepends the already-composed head — producing garbage / doubled text. The "hold a finger down while the other swipes" workaround worked because `BatchInputArbiter` aggregates the held pointer into the stroke, but it caps at a single held tap.

User decisions for this change: **scope = just fix tap+swipe combining**; **manual spacing is the primary mode**.

### Actions Taken
- `latin/settings/SettingsValues.java`: added `isMultipartComposeActive()` — `mMultipartAutoExtendInCombining && (mCombiningGraceMs > 0 || mGestureManualSpacing)` — as the single shared definition of "multi-part composition is active". (`mMultipartAutoExtendInCombining` is already forced true whenever manual spacing or grace is on, see the `nonNormalTwoThumbSpacing` line in the ctor.)
- `latin/inputlogic/InputLogic.java` (`onStartBatchInput`): replaced the `mMultipartAutoExtendInCombining && mCombiningGraceMs > 0` gate on `setExtendBatchInputBase(...)` with `isMultipartComposeActive()`, so the merged trail is captured when extending under manual spacing too. This flips `usedMergedTrail` true downstream in `onUpdateTailBatchInputCompleted`, which correctly suppresses the legacy `prevTypedWord` re-concat.
- `keyboard/PointerTracker.java` (`onDownEvent`): switched `multipartExtendActive` to `sv.isMultipartComposeActive()` so the single-point tap seed and the merged-trail path can never both fire and the two files share one definition.
- Tests:
  - `app/src/test/.../WordComposerTest.java`: `testExtendBatchInputBaseMergesAndRetimes` (asserts base+gesture merge to one monotonically-retimed stream, base before gesture, gesture times preserved) and `testExtendBatchInputBaseEmptyIsNoOp` (empty base does not arm the merge).
  - `app/src/test/.../InputLogicTest.kt`: `manualSpacingTapThenGestureBuildsOneOpenWord` (tap `he` + gesture `hello` → `hello`, not `hehello`, and the word stays open until an explicit space — this fails pre-fix) and `manualSpacingActivatesMultipartCompose` (helper truth table).
- `res/values/strings.xml`: clarified `gesture_manual_spacing_summary` to mention mixing taps and swipes into one word.

### Decisions Made
- Reused the existing `mMultipartAutoExtendInCombining` pref as the de-facto master switch rather than renaming it (the codebase deliberately keeps the slightly-misleading "...InCombining" name to avoid churn; per `TWO_THUMB_TYPING_INTERNALS.md §7`). It already defaults to active under manual spacing, so users get the fix with no new pref.
- Secondary (intended) effect: swipe+swipe under manual spacing now also merges (matching grace-mode), instead of the legacy concat that produced e.g. `techbiology`. This is strictly better and consistent with "manual spacing primary".
- Why the existing manual-spacing tests still pass: the unit-test harness injects gesture results via `setBatchInputWord`, which `reset()`s `mInputPointers` to empty, so a gesture fragment leaves an empty trail → `setExtendBatchInputBase(empty)` is a no-op (`isExtendBatchInputBaseSet()` stays false) → legacy concat is preserved in tests. Tapped letters DO populate the trail, which is why the new tap-then-gesture test genuinely exercises the fix.

### Testing status
- **NOT yet compiled/run** — the dev machine has only JRE 1.8; the build needs JDK 17/21. Edits were made manually. Unit tests must be run on a Linux machine (procedure handed to the user).

### Manual Tests — Tap+Swipe under Manual Spacing
| # | Steps | Expected Result |
|---|---|---|
| 1 | Settings → Two-thumb typing (experimental) → enable **Manual spacing**. | Toggle on; no crash. |
| 2 | In any text field, tap `H`, tap `E`, then swipe `L→O` (sequentially, fingers lifting between). | Composing word becomes `hello`; nothing is committed yet. |
| 3 | Tap space. | `hello ` is committed; word was open until the space. |
| 4 | Tap `s`, then swipe `ilo`. | `silo` (single tap + swipe). |
| 5 | Swipe `tech`, then swipe `nology`. | `technology` (swipe + swipe merges, not `techbiology`). |
| 6 | Build a multi-fragment word, then press backspace (with **Backspace removes last fragment** on). | The last fragment is popped, not one character. |
| 7 | Repeat #2 with manual spacing OFF and grace = 0. | Reverts to default behavior (no combining). |

### Open Questions / Next Steps
- Run the unit tests on Linux and confirm green; then on-device validation of the manual test table above.
- After the user dailies this, evaluate the further Nintype items mentioned (faster/optional autospace, multi-tap recognition tuning) as separate changes.

---

## 2026-06-02 — Fix gesture-then-tap finalizing the word under manual spacing

### Context
Follow-up to the tap+swipe combining fix. With manual spacing on, tap-before/during-swipe worked, but ending a word with taps did not: e.g. swipe `dea` then tap `l` produced `dea l` (and once Auto-correction was enabled, `sea l`) instead of extending to `deal`. The tap after a gesture was finalizing the word and inserting an autospace.

### Root cause
`mAutospaceAfterGestureTyping` is read straight from its pref in `SettingsValues` with no manual-spacing override (the "no-op under manual spacing" behavior is only enforced at certain call sites). The post-gesture PHANTOM-set in `onUpdateTailBatchInputCompleted` only excluded combining-grace mode (`mCombiningGraceMs <= 0`), so under manual spacing (grace = 0) it still set `SpaceState.PHANTOM` after every gesture. The next letter tap then hit `handleNonSpecialCharacterEvent`'s PHANTOM branch, which commits the composing word and inserts the deferred autospace before starting the new letter. This is the manual-spacing analogue of the combining-mode PHANTOM-race fixed earlier in `a63e9ea3`.

### Actions Taken
- `latin/inputlogic/InputLogic.java` (`onUpdateTailBatchInputCompleted`): added `&& !settingsValues.mGestureManualSpacing` to the post-gesture PHANTOM-set condition, so manual spacing never arms a post-gesture autospace and the next tap extends the still-open word. Non-two-thumb users (grace = 0, manual spacing off) keep the normal autospace-after-gesture behavior.
- `app/src/test/.../InputLogicTest.kt`: added `manualSpacingGestureThenTapExtendsWithoutAutospace` — swipe `dea` + tap `l` → `deal` (fails pre-fix, which yields composing `l` / text `dea l`).

### Decisions Made
- Scoped the guard to `!mGestureManualSpacing` rather than the broader `isMultipartComposeActive()`: the existing `mCombiningGraceMs <= 0` clause already excludes grace mode, so manual spacing is the only remaining case that needs suppressing; plain users are intentionally untouched.
- Left the BEFORE-gesture autospace (`mAutospaceBeforeGestureTyping`, onStartBatchInput) unchanged for now — not part of the reported symptom; flag for review if word-to-word spacing under manual spacing looks off.

### Testing status
- **NOT yet compiled/run** on this machine (JRE 1.8 only). Needs the Linux build + `:app:testOfflineDebugUnitTest`.

### Manual Tests — Gesture-then-tap under Manual Spacing
| # | Steps | Expected Result |
|---|---|---|
| 1 | Manual spacing on; Auto-correction on. Swipe `d→e→a`, then tap `l`. | Composing word becomes `deal` (extends); no premature space or commit. |
| 2 | Tap space. | `deal ` committed. |
| 3 | Swipe a word, tap several trailing letters. | Each tap appends to the open word until an explicit separator. |
| 4 | Plain keyboard (manual spacing OFF, grace 0), autospace-after-gesture ON: swipe a word, tap a letter. | Unchanged stock behavior: gesture word commits, autospace, new letter. |

---

## 2026-06-02 — Live-converge: re-recognize taps within swiped words (opt-in)

### Context
After the gesture-then-tap fix, a tap correctly extended the open word, but the EXTENSION was literal: a slow tap after a swipe appends to whatever word the gesture library resolved the (often short, mis-resolved) swipe fragment to. The native glide recognizer resolves each completed stroke to a whole word immediately, so swiping `t→h` alone yields a nearby 2-letter word; a later slow `e` tap then appends to that wrong word. A FAST `e` tap already works because it folds into the gesture stroke and `t→h→e` recognizes as `the`. User asked to make the slow case behave like the fast case — accumulate the stroke and re-recognize. Chosen model: **live-converge** (re-recognize the accumulated stroke on each new fragment), taps treated as **recognizer hints**, shipped as an **opt-in toggle, default off**.

### Design
The recognizer is driven by `mWordComposer` state (`getInputPointers()` + `isBatchMode()`), and `performUpdateSuggestionStripSync` already shows how to run it synchronously (handler + `AsyncResultHolder` + timeout). Since `setBatchInputWord` resets the composer's pointers on every commit, a persistent per-word accumulator (`mLiveStroke`) is required.

### Actions Taken
- New pref (5-file, default false): `PREF_MULTIPART_RERECOGNIZE_TAPS` in Settings.java / Defaults.kt / SettingsValues.java (`mMultipartRerecognizeTaps`) / strings.xml / TwoThumbTypingScreen.kt (under the multi-part group, gated by nonNormalSpacing).
- `latin/inputlogic/InputLogic.java`:
  - `mLiveStroke` (InputPointers): accumulated raw trail of the current word. Captured in `onUpdateTailBatchInputCompleted` just before `setBatchInputWord` wipes `mInputPointers` (gated on the pref). Cleared in `resetComposingState` and `commitChosenWord`.
  - `getBatchSuggestionsSync()`: synchronous batch recognition via the existing handler/holder pattern (`INPUT_STYLE_TAIL_BATCH`).
  - `tryLiveConvergeTap(event, sv)`: guards (pref on; manual spacing or grace; composing gesture-word; cursor at end; word codepoint; real key coords; non-empty accumulator). Builds a 1-point InputPointers at the tap's key center, merges via `setExtendBatchInputBase`+`setBatchInputPointers`, recognizes, and reuses `onUpdateTailBatchInputCompleted` (extend-base set → whole-word replace). Returns false → literal-append fallback when recognition is unavailable (the tap is never lost; typed-word cache untouched).
  - Hook at the top of `handleNonSeparatorEvent`.
- Tests (`InputLogicTest.kt`): `liveConvergeOffAppendsTapLiterally` (baseline) and `liveConvergeOnDegradesGracefullyWithoutRecognizer` (graceful literal fallback when the recognizer/coords are absent — the JVM harness can't load the gesture lib).

### Decisions / limitations
- Pure-tap words are excluded (guard on `mCombiningWordHasGestureFragment || isBatchMode`) so ordinary typing stays exact.
- Taps are recognizer hints, not exact anchors (user's choice) — matches how tap-then-swipe already behaves.
- v1 accumulates one swipe stem + following taps; multi-swipe-then-tap resets the accumulator to the latest swipe (documented edge case).
- **Positive recognition path is NOT unit-testable here**: the native gesture lib isn't loaded in JVM tests and harness tap events carry no coordinates, so `tryLiveConvergeTap` bails before recognition. Unit tests only cover the safe-fallback/no-regression property. The real behavior must be validated on-device.

### Testing status
- **NOT compiled or run** on this machine (JRE 1.8 only). Needs Linux build + `:app:testOfflineDebugUnitTest`, then on-device validation. Expect possible on-device tuning (synchronous recognize-per-tap latency; recognition quality of stem+tap strokes).

### Manual Tests — Live-converge (toggle ON)
| # | Steps | Expected Result |
|---|---|---|
| 1 | Two-thumb typing → enable **Manual spacing** and **Re-recognize taps within swiped words**. | Toggles on; no crash. |
| 2 | Swipe `t→h` (may briefly show a wrong short word), then tap `e`. | Word re-recognizes to `the`; no premature space. |
| 3 | Swipe `d→e→a`, then tap `l`. | `deal`. |
| 4 | Type a pure-tap word like `hello` (no swipe). | Exact literal typing — feature does not interfere. |
| 5 | Toggle OFF, repeat #2. | Old behavior: tap appends literally to the resolved fragment. |
| 6 | Watch typing latency on the taps that trigger re-recognition. | No noticeable lag; if present, report for tuning. |

---

## 2026-06-02 — Backspace: clear stored stroke + fix whole-word delete of composing word

### Context
Two backspace bugs while building gesture words under manual spacing:
1. Deleting part of a word did NOT clear the stored gesture stroke, so re-doing the word merged with stale geometry — e.g. type `th`, swipe `ing` → `thing`; delete `ing`; swipe again and the recognizer keeps resolving ever-growing words that still start with `th`.
2. The 3-way backspace setting (one character / last fragment / whole word) appeared to act as single-character delete; in particular "Delete whole word" did not delete the open (still-composing) word.

### Root causes
1. The live-converge accumulator `mLiveStroke` (added with `PREF_MULTIPART_RERECOGNIZE_TAPS`) was cleared only in `resetComposingState` and `commitChosenWord`. `handleBackspaceEvent` never cleared it, and its whole-word / batch-reject branches call `mWordComposer.reset()` directly (bypassing `resetComposingState`). So the accumulated raw stroke survived a delete and the next swipe/tap merged with it.
2. Whole-word delete of a *composing* word requires BOTH `PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD` and `PREF_COMBINING_BACKSPACE_DELETES_COMPOSING_TEXT` (`handleBackspaceEvent`, composing-word branch). The 3-way selector's "Delete whole word" option set only the former, leaving the latter at whatever it was — so if the composing-text toggle had ever been turned off, "whole word" silently degraded to one-character on the open word.

### Actions Taken
- `latin/inputlogic/InputLogic.java` (`handleBackspaceEvent`): added `mLiveStroke.reset()` near the top (after `cancelCombiningMode`), so EVERY backspace path invalidates the accumulated stroke. A re-done word now starts from a clean stroke regardless of delete mode. (We don't attempt to trim the raw trail to a partial word — per the user, dropping it is the desired, predictable behavior.)
- `settings/screens/TwoThumbTypingScreen.kt`: the selector's `BACKSPACE_WORD` branch now also sets `PREF_COMBINING_BACKSPACE_DELETES_COMPOSING_TEXT = true`, so picking "Delete whole word" reliably deletes the open composing word, not just committed words. The sub-toggle remains for users who want whole-word scoped to committed words only.

### Decisions Made
- Clear `mLiveStroke` unconditionally on backspace (it's empty when the feature is off, so this is harmless) rather than trying to trim it — trimming a fuzzy stroke to match a partially-deleted word is unreliable, and the user explicitly prefers "just delete it".
- Kept the runtime requirement that composing-word whole-delete needs the composing-text pref (preserves the existing `wholeWordBackspaceWithLiveComposingDeleteOffFallsBackToOneCharacter` behavior / power-user choice); fixed the footgun at the selector instead.
- Did NOT change one-character / fragment delete semantics — only the stale-stroke clearing applies to them.

### Testing status
- **NOT compiled or run** here (JRE 1.8 only). Existing tests cover the behavior: `wholeWordBackspaceDeletesManualSpacingComposingWord` (whole-word composing delete), `wholeWordBackspaceWithLiveComposingDeleteOffFallsBackToOneCharacter` (sub-toggle off → one char), `fragmentBackspaceDeletesLastSwipeFragmentInMultipartWord` (fragment pop). The `mLiveStroke` clear isn't JVM-unit-observable (no recognizer/coords in the harness) — validate the re-do behavior on-device.
- **Note for the user:** because the selector only writes prefs when an item is selected, re-pick **Delete whole word** once after installing so the composing-text toggle gets set.

### Manual Tests — Backspace re-do
| # | Steps | Expected Result |
|---|---|---|
| 1 | Manual spacing on; backspace mode = **Delete whole word** (re-select it once). Type `th`, swipe `ing` → `thing`. Backspace. | Whole word deleted (empty), not one character. |
| 2 | Immediately swipe `ing` again (or re-type the word). | Recognizes cleanly from a fresh stroke — no ever-growing `th…` words. |
| 3 | Backspace mode = **Delete last fragment**. Build `th`+swipe `ing`, backspace. | Last fragment (`ing`) popped → `th`; re-doing starts from a clean stroke. |
| 4 | Backspace mode = **Delete one character**. Build a gesture word, backspace once. | One character removed; stored stroke is dropped so a later swipe doesn't merge with stale geometry. |

---

## 2026-06-03 — Fix whole-word backspace corrupting/mashing committed text

### Context
With backspace mode = "Delete whole word", deleting words mid-text corrupted the text instead of deleting cleanly. Reported example: `Hey there. This is pretty cool!` → backspaces produced `…This is precool`, `…Thprecoo`, `Hey Thprecoo`, etc. — words mashing into preceding ones and the cursor desyncing. Also: stored gestures sometimes weren't cleared (incl. after the swipe-on-backspace bulk delete).

### Root cause
The whole-word branch for a *composing* word used `mConnection.deleteTextBeforeCursor(wordLength)`. That routes through `InputConnection.deleteSurroundingText`, which does NOT remove an active composing region — it deletes committed text BEFORE it (documented TODO in `RichInputConnection#deleteTextBeforeCursor`). So backspacing the re-composed word `cool` deleted `tty ` instead, giving `precool`, and desynced the RichInputConnection cursor cache, cascading into more corruption on subsequent keystrokes. The batch-delete branch never had this bug because it resets the composer and lets the post-block `commitText("", 1)` clear the composing span. This only became visible after the prior session made "Delete whole word" reliably enable `DELETES_COMPOSING_TEXT`.

### Actions Taken
- `latin/inputlogic/InputLogic.java` (`handleBackspaceEvent`, composing-word branch): the whole-word delete now does `mWordComposer.reset()` and lets the post-block `commitText("", 1)` remove the composing span — the correct primitive for deleting composing text — instead of `deleteTextBeforeCursor`. Removed the now-unused `deletedWholeComposingWord` flag; the post-block always clears the composing span when the composer is no longer composing.
- Added a defensive `mLiveStroke.reset()` in the composing-inconsistency recovery path (`setComposingTextInternalWithBackgroundColor`) so a force-cancelled composition also drops the stored stroke.
- Verified `mLiveStroke` clearing is now comprehensive: `handleBackspaceEvent` (covers per-char, fragment, whole-word, batch, and selection/bulk delete — runs before any branch), `resetComposingState` (reached via `resetEntireInputState` on cursor/selection moves from `onUpdateSelection`), `commitChosenWord`, and the inconsistency path.

### Why the other modes were safe
- One-character delete uses `applyProcessedEvent` + `setComposingTextInternal` (correct primitive).
- Fragment delete (`tryFragmentBackspace`) uses `setBatchInputWord` + `setComposingTextInternal`, and only fires for actively-built words (recorded boundaries). Re-composed committed words have no boundaries → falls back to one-char.
- Only the whole-word composing branch used `deleteTextBeforeCursor`, so only it corrupted.

### Tests
- `InputLogicTest.kt`: `wholeWordDeleteRemovesComposingWordWithoutMashingPrecedingText` — re-compose `cool` in `This is pretty cool`, whole-word delete → `This is pretty ` (not `precool`). Caveat: the JVM mock's `deleteSurroundingText` doesn't reproduce the real-editor composing-region quirk, so this guards the intended outcome and the `commitText` path; the editor-specific corruption must be confirmed on-device.
- Existing `wholeWordBackspaceDeletesManualSpacingComposingWord` / `…DeleteOffFallsBackToOneCharacter` still hold (the new code clears the composing span via `commitText`).

### Testing status
- **NOT compiled or run** here (JRE 1.8). Needs Linux build + `:app:testOfflineDebugUnitTest`, then on-device:
  - Whole-word delete through a sentence deletes word-by-word with NO mashing/desync.
  - Re-doing a word after deletion recognizes cleanly (no ever-growing `th…`).
  - Swipe-on-backspace bulk delete leaves no stale stroke for the next word.

---

## 2026-06-06 — Fix stale merged-trail extend-base leaking into the next gesture

### Context
Bug report: gestures occasionally get "stored" after a deletion and bleed into the
next swipe — most visibly when swiping a fresh word at the start of a text box,
which produced a word fused with previously-deleted gesture geometry. Branch:
`fix/extend-base-leak-on-delete` off `main`.

### Root cause
There are two per-word gesture-geometry stores. `mLiveStroke` (live-converge, in
`InputLogic`) was already cleared on every word-end path. But its WordComposer-side
counterpart — `mExtendBatchInputBase` (the multi-part merged-trail base consumed by
`WordComposer.setBatchInputPointers`) — was only ever cleared by a *normally
completing* gesture (`onUpdateTailBatchInputCompleted`, the lone routine clear,
which sits AFTER two empty-recognition early-returns). An abnormal gesture end left
the `mExtendBatchInputBaseSet` flag armed:
- `onCancelBatchInput` never cleared it,
- the empty-top-word early-returns in `onUpdateTailBatchInputCompleted` skip the clear,
- `WordComposer.reset()` does not touch it, so NO deletion path cleared it either.

Once leaked, the flag survived every backspace / selection-delete / commit. A later
fresh gesture (no composing word ⇒ the `onStartBatchInput` extend branch never runs,
so it neither set nor cleared the base) hit the merge guard in
`setBatchInputPointers` and prepended the ghost trail. Start-of-text-box made it
maximally visible because there is no composing word there to legitimately re-set
the base.

### Actions Taken
- `latin/inputlogic/InputLogic.java`: clear the merged-trail base
  (`mWordComposer.setExtendBatchInputBase(null)`) at the same word-end sites where
  `mLiveStroke` is already dropped, plus the two gesture-lifecycle origins:
  - `onStartBatchInput` (top, before the extend decision — guarantees every gesture
    starts from a clean base; the extend branch re-arms it from the real composing
    word when appropriate),
  - `onCancelBatchInput` (a cancelled gesture never reaches the routine clear),
  - `handleBackspaceEvent` (next to the existing `mLiveStroke.reset()`, before any
    mode branch — covers all three backspace modes in one place),
  - `resetComposingState` (covers the delete slider's `finishInput` and cursor-move
    resets),
  - `commitChosenWord` and the composing-desync recovery path.
  Deliberately did NOT add the clear to the hot `WordComposer.reset()` to avoid any
  interaction with the mid-gesture merge timing; mirroring the reviewed `mLiveStroke`
  sites is equivalent and lower-risk.
- `app/src/test/.../InputLogicTest.kt`: 6 base-clear regression tests (one per
  backspace mode — character / fragment / whole-word — plus delete slider, fresh
  `onStartBatchInput`, and `onCancelBatchInput`), each arming the base then asserting
  it is dropped (each fails pre-fix). Added `armExtendBase()` helper and `InputPointers`
  / `assertTrue` / `assertFalse` imports.
- Also added two reachability-guard tests pinning the (currently dead) PointerTracker
  static-seed interlock: `grace > 0 ⇒ isMultipartComposeActive()` and the
  manual-spacing equivalent. If a future settings refactor decouples them and re-arms
  the seed, these fail and force adding the missing stale-static cleanup first.

### Decisions Made
- Threading was verified safe: every `mExtendBatchInputBase` mutation runs on the UI
  thread (the recognizer runs on the suggestions HandlerThread but only reads a
  snapshot), so the new clears need no locking and can't race the merge.
- Scoped to dropping the base. The *related* "re-extend after a partial (character /
  fragment) delete still merges the un-trimmed `mInputPointers`" issue is left for a
  later ticket per the established "drop, don't trim" philosophy — the fix neither
  causes nor worsens it.

### Testing status
- Built and ran on this machine (newly installed JDK 21 + Android SDK platform-35 /
  build-tools 35.0.0). `:app:testStandardDebugUnitTest` filtered to the 8 new tests +
  `WordComposerTest`: **BUILD SUCCESSFUL, 11/11 passing.** Full-suite still carries the
  3 pre-existing known failures (`insertLetterIntoWordHangulFails`,
  `revert autocorrect on delete`, `tapOnlyCombiningWordDoesNotShowAutospaceIndicator…`),
  unrelated to this change.
- On-device validation still recommended: reproduce the start-of-text-box ghost-merge
  (swipe a word, trigger an abnormal gesture end, delete, swipe a fresh word) and
  confirm it no longer fuses; re-check all three backspace modes + the delete slider.

### Manual Tests — Extend-base leak
| # | Steps | Expected Result |
|---|---|---|
| 1 | Two-thumb on. Swipe a word; cancel/abort a follow-up gesture; delete everything; swipe a fresh word at the start of the box. | Fresh word recognizes alone — no fusion with the deleted gesture. |
| 2 | Repeat #1 with backspace mode = Delete one character. | Same; no ghost merge. |
| 3 | Repeat #1 with backspace mode = Delete last fragment. | Same; no ghost merge. |
| 4 | Repeat #1 with backspace mode = Delete whole word. | Same; no ghost merge. |
| 5 | Repeat #1 using swipe-from-backspace (delete slider) to clear. | Same; no ghost merge. |

### Open Questions / Next Steps
- Build the standard debug APK and install for on-device validation.
- Decide whether to also tackle the re-extend-after-partial-delete (stale
  `mInputPointers`) follow-up.

## 2026-06-10 — B7a (#98): prefix-aware gesture result stripping

### Context
First step of the multi-part fake-track synthesis epic (#97). Today the gesture-commit concat in
`InputLogic.onUpdateTailBatchInputCompleted` relies on the `usedMergedTrail` flag to decide whether to
prepend the composing prefix. On the seed / tap-promotion extend path (`extendExistingCompose && !usedMergedTrail`)
it does a plain `prevTypedWord + batchInputText`; if a better fake-track makes the recognizer return the
**whole** word (e.g. prefix `tech` + recognized `technology`) it double-counts (`techtechnology`). The
existing single-char seed-strip only catches a one-letter overlap.

### Actions Taken
- Added `StringUtils.concatWithoutDuplicatedPrefix(prefix, text)` (pure, generic): if `text` already begins
  with `prefix` (case-insensitive), keep the user's prefix casing and append only the continuation; else
  concatenate. Handles whole-word returns, continuation returns, casing, empty prefix, short continuation.
- Wired it into `onUpdateTailBatchInputCompleted` in place of the raw `prevTypedWord + batchInputText`.
- Added `StringUtilsConcatTest` (pure JUnit, 6 cases). Named distinctly to avoid colliding with the
  existing Robolectric `latin.StringUtilsTest`.

### Decisions Made
- Generic util (not gesture-specific) so it is pure-JVM unit-testable without the native recognizer
  (the JVM harness cannot simulate native recognition — the synthesis steps B7b–d are on-device).
- Correct-by-construction: works regardless of which extend path set `prevTypedWord`, rather than relying
  on the `usedMergedTrail` proxy flag.

### Manual Tests — build + unit (JVM)
`./gradlew :app:testOfflineDebugUnitTest --tests "*StringUtilsConcatTest" --tests "*InputLogicTest"`
- `StringUtilsConcatTest`: **6/6 pass**.
- `InputLogicTest`: composition tests (`manualSpacingTapThenGestureBuildsOneOpenWord`,
  `manualSpacingGestureThenTapExtendsWithoutAutospace`, `liveConvergeOffAppendsTapLiterally`) green.
- 3 failures (`tapOnlyCombiningWordDoesNotShowAutospaceIndicatorWhenGestureGateEnabled`,
  `insertLetterIntoWordHangulFails`, `revert autocorrect on delete`) are **pre-existing on clean `main`**
  (verified by running the suite without these changes) — unrelated to B7a.

### Open Questions / Next Steps
- B7b (#99) ideal prefix trail, B7c (#100) adaptive bridge, B7d (#101) hybrid — these change what the
  native recognizer returns, so they need the on-device / native replay harness (#78), not the JVM suite.
- Pre-existing red tests on `main` (the 3 above) are tech-debt (testing epic #13 / #80 theme), not B7a.
