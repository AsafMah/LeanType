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
