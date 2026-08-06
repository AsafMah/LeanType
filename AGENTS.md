# Repository Guidelines

## Project Overview
LeanType is an Android keyboard (an `InputMethodService` app), forked from HeliBoard/OpenBoard/AOSP LatinIME. On top of the upstream keyboard it adds AI proofreading & translation (cloud and on-device ONNX), Nintype-style two-thumb typing, custom AI toolbar keys, and a floating keyboard. The legacy input engine is **Java**; newer logic, settings, and AI code are **Kotlin**. Settings UI is **Jetpack Compose**. A native **C++** engine (under `app/src/main/jni/`) does dictionary lookup and gesture/glide scoring.

**Fork lineage & "upstream":** the chain is HeliBoard (`Helium314/HeliBoard`, the original) → **`LeanBitLab/LeanType`** (a fork of HeliBoard) → **this repo, `AsafMah/LeanType`** (a fork of LeanBitLab/LeanType). When the maintainer says **"upstream" they mean `LeanBitLab/LeanType`** (`upstream/main`) — NOT HeliBoard. This fork ships as its own distinct, installable app, **"LeanTypeDual"** (its own `applicationId`, so it installs *alongside* the upstream LeanType instead of colliding with it). "Make it distinct" therefore means distinct from `LeanBitLab/LeanType`, not from HeliBoard.

## Architecture & Data Flow
Strict **view → logic → engine** split.

- **Input pipeline:** touch → `MainKeyboardView` → `PointerTracker` (per-pointer state; `BatchInputArbiter` aggregates multi-finger gesture points) → `KeyboardActionListenerImpl` → `LatinIME.onCodeInput` / `onEndBatchInput` → `InputLogic` → `RichInputConnection` (writes to the editor).
- **`InputLogic`** is the central state machine: composing word (`WordComposer`), separators/backspace, auto-capitalization, autospace/PHANTOM space, and the two-thumb combining-mode state machine.
- **Suggestions:** `InputLogic` → `DictionaryFacilitatorImpl` (user-history / main / contacts / apps / personal dicts) → `Suggest.kt` (scores & ranks; calls the native scorer in `app/src/main/jni/`) → `SuggestionStripView`.
- **Layouts:** JSON/text under `assets/layouts/`, parsed by `KeyboardParser`; per-locale popups in `assets/locale_key_texts/`.

## Key Directories
- `app/src/main/java/helium314/keyboard/`
  - `latin/` — core: `LatinIME.java` (service entry), `RichInputConnection.java`, `DictionaryFacilitatorImpl.kt`, `Suggest.kt`
  - `latin/inputlogic/InputLogic.java` — central input state machine
  - `latin/settings/` — `Settings.java`, `Defaults.kt`, `SettingsValues.java`
  - `keyboard/` — view layer: `PointerTracker.java`, `KeyboardSwitcher.java`, `MainKeyboardView`; `keyboard/internal/` (layout parser, `BatchInputArbiter`)
  - `settings/screens/` — Compose settings screens (e.g. `AIIntegrationScreen.kt`)
  - `accessibility/`, `compat/`, `event/`, `latin/utils/`
- `app/src/main/jni/` — native C++ dictionary/suggestion engine (`Android.mk`, `ndkBuild`)
- `app/src/main/assets/layouts/` — layout files (subfolders are `LayoutType`: `main/`, `symbols/`, `functional/`)
- `app/src/main/assets/locale_key_texts/` — per-locale popup keys (`en.txt`, …)
- `app/src/{standard,offline,offlinelite}/` — flavor-only sources (e.g. three `ProofreadService.kt` impls)
- `app/src/test/` — JVM unit tests · `docs/` · `tools/`

## Development Commands
Requires **JDK 17 or 21** and the Android SDK. On Windows use `gradlew.bat` and set `JAVA_HOME` + `ANDROID_HOME` (e.g. `C:\Android\Sdk`).

```bash
# Build an APK (per flavor)
./gradlew :app:assembleStandardDebug        # also assembleOfflineDebug, assembleOfflineliteDebug
# Fast CI compile check (no APK) — what PR CI runs
./gradlew compileOfflineRunTestsKotlin
# Unit tests for one flavor
./gradlew :app:testOfflineDebugUnitTest
# A single test class
./gradlew :app:testOfflineDebugUnitTest --tests "*InputLogicTest"
```

PowerShell with a pinned JDK:
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
.\gradlew.bat :app:assembleStandardDebug --no-daemon
```

## Code Conventions & Common Patterns
- **Language:** Java for the legacy engine (`latin/`, `keyboard/`); Kotlin for new logic/settings/AI. Match the file's existing language.
- **Naming:** Java instance fields are `m`-prefixed (`mSpaceState`); SharedPreferences keys are `PREF_*` constants.
- **Settings 5-file pattern (MUST touch all five):**
  1. `latin/settings/Settings.java` — `public static final String PREF_X = "x";`
  2. `latin/settings/Defaults.kt` — `const val PREF_X = <default>`
  3. `latin/settings/SettingsValues.java` — `public final <T> mX;` + read it in the constructor
  4. `res/values/strings.xml` — title/summary strings
  5. `settings/screens/<Screen>.kt` — a `Setting{…}` entry added to the screen list
  `SettingsContainer` auto-aggregates the per-screen lists.
- **State / config access:** `Settings.getValues()` returns a cached `SettingsValues` (read once, not per keystroke). Some cross-pointer state is `static` in `PointerTracker` (`sInGesture`, aggregated pointers).
- **Flavor isolation:** prefer **source-set separation** (`app/src/standard` vs `offline` vs `offlinelite`) over `BuildConfig.FLAVOR` checks. **Never** add the `INTERNET` permission to the `offline`/`offlinelite` manifests — all network activity is `standard`-only and opt-in.
- **Performance:** the key-input and suggestion paths run on the main thread; avoid allocations in hot paths.
- **IME dialogs:** an `AlertDialog` `EditText` cannot reliably receive typed input inside the IME process — intercept `onCodeInput`/`onTextInput` into a `TextView` instead (see clipboard/emoji search modes).

## Important Files
- Entry / manifest: `app/src/main/java/helium314/keyboard/latin/LatinIME.java`, `app/src/main/AndroidManifest.xml`
- Input core: `latin/inputlogic/InputLogic.java`, `keyboard/PointerTracker.java`, `latin/RichInputConnection.java`
- Dictionaries / suggestions: `latin/DictionaryFacilitatorImpl.kt`, `latin/Suggest.kt`, `app/src/main/jni/`
- Settings: `latin/settings/Settings.java`, `Defaults.kt`, `SettingsValues.java`, `settings/screens/*.kt`
- Flavor AI: `app/src/standard/.../ProofreadService.kt` (Gemini), `app/src/offline/.../ProofreadService.kt` (ONNX)
- Build: `app/build.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/proguard-rules.pro`
- Docs: `docs/FEATURES.md`, `docs/TWO_THUMB_TYPING_INTERNALS.md`, `docs/IMPROVEMENT_PLAN.md`, `layouts.md`, `CONTRIBUTING.md`

## Runtime/Tooling Preferences
- **Android:** `compileSdk` 36, `targetSdk` 35, `minSdk` 21; Java/Kotlin JVM target 17. Build with JDK 17 or 21.
- **Toolchain:** Gradle 8.13 (wrapper), Kotlin 2.2.21, Compose BOM 2025.11.01 (`material3`, `navigation-compose`). Package management is **Gradle only** (no npm/bun/yarn).
- **Native:** ABIs `armeabi-v7a`, `arm64-v8a`; built via `ndkBuild` (`app/src/main/jni/Android.mk`).
- **Flavors** (dimension `privacy`, appId base `com.asafmah.leantypedual`):
  - `standard` — cloud AI (Gemini, `generativeai`), has `INTERNET`.
  - `standardfull` — cloud AI plus handwriting, has `INTERNET`.
  - `offline` — on-device llama.cpp / GGUF, **no** `INTERNET`; appId `+.offline`, minSdk 26.
  - `offlinelite` — no AI, smallest; **no** `INTERNET`; appId `+.offlinelite`.
- **Build types:** `debug` (no minify, `+.debug`), `release` (minify + shrink + signed via `keystore.properties`), `runTests` (CI variant that skips known-failing tests), `debugNoMinify` (fast IDE builds).
- **CI:** `.github/workflows/build-test-auto.yml` runs `compileOfflineRunTestsKotlin` on PRs touching `app/src/main/java**`; `build-debug-apk.yml` runs `assembleDebug` on manual dispatch. Release chores live in `tools/release.py`.

## Testing & QA
- **JVM-only** (no `androidTest`/device): JUnit4 + **Robolectric 4.14.1** (simulates `LatinIME`/`Context`/prefs/key events on the JVM) + **Mockito 5.17.0**. Tests live in `app/src/test/java/helium314/keyboard/`. `testOptions.unitTests.isIncludeAndroidResources = true`.
- **Run:** `./gradlew :app:testOfflineDebugUnitTest` (add `--tests "*ClassName"` for one class).
- **Key tests:** `InputLogicTest.kt` (typing/autocorrect/combining-mode/Hangul), `SuggestTest.kt`, `WordComposerTest.java`, `DictionaryGroupTest.kt` (reflection + Mockito on the package-internal `DictionaryGroup`), `SettingsContainerTest.kt` (settings wiring), `KeyboardParserTest.kt`, `ClipboardDaoTest.kt`.
- **Conventions:** `@Test`; method names use camelCase or backtick form; obtain `Context` via Robolectric; package-internal classes are exercised via reflection (`Class.forName(...).declaredConstructors`).
- **Known failures:** the full debug unit suite has ~11 pre-existing failures (in `KeyboardParserTest`, `XLinkTest`, `StringUtilsTest` emoji, and `InputLogicTest` Hangul/autocorrect-revert/autospace-indicator) that are environment/data-dependent and usually unrelated to a change. The `runTests` build type exists to skip these on CI. **Verify a change by diffing failures against an `origin/main` baseline run, not by absolute pass count.**
- **Coverage gap:** gesture/glide recognition needs the native engine, so JVM unit tests exercise tap-based logic, not native gesture recognition (a trace/replay harness is planned — see `docs/IMPROVEMENT_PLAN.md`).
- **Expectation:** new behavior MUST add/update unit tests; any settings change updates `SettingsContainerTest.kt`. Keep PRs single-responsibility (see `CONTRIBUTING.md`).

## Review Workflow
Before merging a non-trivial change — correctness-sensitive input/dictionary logic, a design decision, or a risky refactor — get an **independent cross-model second opinion** with the `rubber_duck` tool. It sends the current conversation to a *different* model (default `github-copilot/gpt-5.5`) for an adversarial review, with no context re-pasting.
- Use `effort: high` for correctness/design passes; lower tiers for quick sanity checks.
- Treat its output as adversarial input, not gospel: it sees the conversation but not tool/scout internals, so verify its claims against the code before acting (it has caught real bugs and unverified assertions in this repo's PRs).
- Especially worth running before merging changes to `InputLogic`, `DictionaryFacilitatorImpl`/`Suggest`, or anything touching the two-thumb/spacing state machine.

## Project Board & Issue Tracking
The roadmap lives in GitHub Project #3 ("Two-Thumb & Keyboard Roadmap", `gh project … --owner AsafMah`), with a `Status` field (`Todo` / `In Progress` / `Done`) and epics (`[Epic]` issues) parenting sub-issues. **Keep it current as you work — it is the single source of truth, not a chat promise:**
- When you **open a PR** for an issue, set that issue (and its PR, once added) to **In Progress**, and bump the parent epic to **In Progress** if it was `Todo`.
- When a PR **merges** (and its issue closes), move both the issue and PR to **Done**; if every sub-issue of an epic is `Done`, move the epic to `Done`.
- **Add** any issue/PR you create to the project, and close issues a merged PR resolves (use `Fixes #N` in the PR body, or `gh issue close` if the squash/merge message only referenced `(#N)`).
- Field/option IDs for scripting: project `PVT_kwHOAGIGz84BZwMC`, Status field `PVTSSF_lAHOAGIGz84BZwMCzhUsrio` (Todo `f75ad846`, In Progress `47fc9ee4`, Done `98236657`); set via `gh project item-edit --id <itemId> --field-id <fieldId> --single-select-option-id <optId> --project-id <projId>`.
This convention is loaded every session, so any agent (and future-you) is expected to follow it without being re-told.

## Changelog & Releases
Keep `CHANGELOG.md` current — it is LeanTypeDual's own history, not a per-line provenance log.
- **Every user-facing or notable change** gets a line under `## [Unreleased]` (or the in-progress version), grouped `Added` / `Changed` / `Fixed` / `Reliability & testing`, with the `(#N)` issue/PR ref. Internal-only refactors go under `Changed`/`Reliability`; do not enumerate them in the user-facing fastlane note.
- **Provenance is coarse, not per-entry.** Do NOT tag each line ours/LeanType/HeliBoard. When upstream code is merged in, add a single `Upstream` marker line under that release (e.g. `Upstream — merged HeliBoard 3.9`). Everything not under an `Upstream` marker is original to this fork by default. The fork-only feature set lives in the README, not the changelog.
- **Versioning:** LeanTypeDual restarted its visible SemVer at `0.1.0`, independently of upstream. To preserve Android upgrades from the previous `3.10.0`/`4000` fork release, `versionCode` uses the offset formula `4000 + major*1000 + minor*100 + patch*10` (`0.1.0` → `4100`). On release, also add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (terse, user-facing bullets only). Release chores: `tools/release.py`.
- On cutting a release, rename `[Unreleased]` to the version + date and start a fresh `[Unreleased]`.
