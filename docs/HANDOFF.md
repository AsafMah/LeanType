# LeanTypeDual — Session Handoff (updated 2026-09-05)

This document lets a new agent/session resume without re-deriving context. It records
**what shipped, exactly where everything sits, what is still open, and the traps that cost
time**. Release and device receipts below are historical; the unreleased integration in
§2.5 has not been installed or verified on a physical device.

Read alongside `AGENTS.md` (repo conventions, which remain authoritative).

> **Current-state refresh 2026-09-05:** §1 and §2.5 distinguish stabilized `dev` from
> the pending LeanBitLab v4.2.0 integration. §5, §6, §11 and §12 reflect v0.3.0,
> LeanBitLab v4.1.8, the Shift fix, the fork-invariant gates, and experiment cleanup.
> Sections 2.1–2.4, 3 and 7 remain historical context.

---

## 1. TL;DR — state in one screen

| Thing | State |
|---|---|
| `main` | `b90d79de1` — released LeanTypeDual 0.3.0 |
| `dev` integration base | `07ef7536f` — v4.1.8 (#149), Shift fix (#150), and experiment cleanup (#151), including the fork-invariant gates (#148) |
| Current version | `0.3.0` / versionCode `4300` on both `main` and `dev` |
| Tag `v0.1.0` | Pushed **and published** with 4 signed APKs |
| Tag `v0.3.0` | **Published and latest** with 4 signed APKs, all verified after download |
| Upstream integrated | LeanBitLab/LeanType **v4.1.8** (`cbfaf21a`), covering v4.1.3–v4.1.8 |
| Phone (SM-S936B) | Last verified with signed **0.3.0/4300**, debug, and EXP packages; wireless ADB is currently unavailable |
| Tablet | Never verified — still outstanding, low risk |
| Upstream integration under review | v4.2.0, pinned `1383390cb9c48b859f56b6499210cbccbd91996f`; ancestry-preserving merge into the stabilized `dev` base, not `main` |

**Upstream integration and comprehensive review come first; no release or installation is
authorized by this work.** Device verification of the unreleased changes remains outstanding,
as do unrelated #106 and deliberate triage of backed-up old branches/worktrees.
The two-track/ideal-prefix experiment was falsified on device and was removed by #151;
the proven pointer-id normalization remains. See §12.

---

## 2. What happened this session

### 2.1 Released 0.1.0
- Merged release PR #125 → `dev`, promoted → `main` (#128), tagged `v0.1.0`, published.
- Release: https://github.com/AsafMah/LeanType/releases/tag/v0.1.0
- Built by Release workflow run **31126608829**.
- Contents: v4.0.2 upstream layer, the 0.1.0 version reset, the consolidated fail-fast
  signing workflow, and two device-verified regression fixes:
  - memory-trim crash (`onTrimMemory` → `cancelAllPointerTrackers` NPE)
  - duplicate "Expand immediately" switch in Text Expander
- Closed tracking issue **#119**.

### 2.2 Merged upstream v4.0.3 → v4.0.8 (PR #126)
131 upstream commits, pinned at tag SHA `dec87806dfc8e4da2cef57bd68ef6eb116edfc8c`.

Upstream brings: foldable/screen-profile detection with split-keyboard defaults,
physical-keyboard suggestion shortcuts, translation plugin + engine selector, a unified
`SettingsModule` registry with automatic settings-search indexing, floating-keyboard
drag-to-resize, key-gap narrowness scale, auto-spanning toolbar keys, text-expander regex
fixes, and many emoji/clipboard layout fixes.

### 2.3 Released 0.2.0
- Version bumped to `0.2.0`/`4200` (PR #129 → `dev`, #130 → `main`), tag `v0.2.0` pushed.
- Debug APK built, installed, and smoke-tested on the phone.
- Signed artifacts were delayed by a runner outage (§5) and **published on 2026-08-20**:
  four signed APKs, release marked latest, all four verified after download.

### 2.4 Released 0.3.0 and stabilized dev
- Released `0.3.0`/`4300` from `main` (`b90d79de1`) with four verified signed APKs.
- Merged LeanBitLab v4.1.8 in #149 with real upstream ancestry preserved. The audit kept the
  fork's four flavors, bundled offline llama/GGUF and dictionaries, offlinelite no-AI behavior,
  and network-free offline tiers.
- Added source/config and packaged-APK invariant gates in #148. Release CI now verifies the
  effective app IDs, minSdk values, INTERNET permissions, dictionary contents, exact four-APK
  set, and low-API signature coverage.
- Fixed fast Shift double-tap/Caps Lock in #150. The fix replaces an arbitrary 100 ms minimum
  with the real invariant: two presses must have an intervening release boundary.
- Removed the falsified DUAL_POINTER, ideal-prefix and re-timing experiment machinery while
  retaining the proven pointer-id normalization and side-by-side EXP packaging (#151).

### 2.5 Upstream v4.2.0 integration (not released)

- The provisional merge is `0ab4172f3727d3821eeb34f3e91f91230286380a`, with parents
  `07ef7536f5fa93e7ec729dd5dd5aeba5bb195807` and
  `1383390cb9c48b859f56b6499210cbccbd91996f`. The prior upstream ancestor is
  `cbfaf21a16194ce934c048affac563446f8cebbf`.
- Shared-file audit includes the semantic auto-resolutions in `InputLogic.java` and
  `KeyboardActionListenerImpl.kt`, not just Git conflicts. The build delta is CameraX
  dependencies only: identity, version, four flavors, bundled offline llama/dictionaries,
  release signing, and EXP packaging stay fork-owned.
- Keep upstream OCR/camera, math, sound, and voice functionality. Offline tiers can import
  local addons; new sound/OCR in-app downloads are cloud-only. OCR respects `nouserlib`.
- The Shift state machine remains identical to stabilized dev and retains all eight
  release-boundary tests. Explicit short dictionary shortcuts remain eligible for
  autocorrection without weakening upstream's protection for unknown short tokens.
- Forced auto-capitalization now reaches the unified input-type guard before adding
  sentence capitalization, preserving URI/email/password exclusions with either toggle.
- Cloud responses marked as token-truncated fail before text replacement; Gemini also
  honors the new cloud max-token setting for both proofreading and translation.
- Independent review identified additional OCR/camera lifecycle, sound-pack containment,
  math privacy/replacement, and voice-state blockers. Correction work is isolated from
  the merge worktree and must be integrated and revalidated before this branch can land.
- The four known Windows `ParserTest` failures remain the baseline; do not add new skips
  or baseline entries to conceal integration regressions.

---

## 3. Release ordering rationale (do not "fix" this)

0.1.0 was tagged **before** the upstream merge landed on `dev`, deliberately:

- 0.1.0's device + signing verification was only valid for that exact tree.
- Folding 131 unverified upstream commits into it would have invalidated that.
- Fork `versionName` is independent of upstream's; Android upgrade continuity depends on
  identical `applicationId` + signing key + monotonic `versionCode`, not the name.

An adversarial cross-model review confirmed this ordering as SOUND. Keep versionCode monotonic;
the latest release, 0.3.0, uses `4300`, so the next release must be above it.

---

## 4. Fork invariants — verify these after ANY upstream merge

| Invariant | Expected |
|---|---|
| `applicationId` | `com.asafmah.leantypedual` (+ `.offline`, `.offlinelite`, `.debug`) |
| Version | Fork's own (`0.3.0`/`4300` currently) — **never** take upstream's `4.x`/`410x` |
| Flavors | Keep `standard`, `standardfull`, bundled-llama `offline` (minSdk 26), and no-AI `offlinelite` (minSdk 21). Upstream v4.1.7 deletes `offlinelite`; reject that product change |
| `INTERNET` permission | Only `app/src/standard/` and `app/src/standardfull/` manifests. `offline`/`offlinelite` have **no manifest at all** and inherit the network-free main one |
| Offline assets | `offline`/`offlinelite` bundle dictionaries; standard/full exclude them. Keep `offlineImplementation("io.github.ljcamargo:llamacpp-kotlin:0.4.0")` |
| Floating overlay | `SYSTEM_ALERT_WINDOW` in main is accepted for floating mode, but access remains user-granted via system settings and normal docked operation works without it |
| Java fallback gesture engine | `SwipeGestureEngine.initialize(this)` in `LatinIME.onCreate`; fallback/native selector in `GestureTypingScreen` + `WelcomeWizard` |
| Two-thumb typing | Own screen + settings; **must** be registered in the `modules` list in `SettingsContainer.kt` (upstream's new registry drives settings search) |
| AndroidX Startup | Exactly **one** `InitializationProvider` in the main manifest, containing all initializer removals |
| Badges | `docs/badges/*.svg` — keep ours, never upstream's generated ones |

Mechanical checks (these replace hand-written greps):

```bash
python tools/check_fork_invariants.py
# after all four release APKs are assembled:
python tools/check_apk_invariants.py --apk-dir app/build/outputs/apk
```

---

## 5. Release procedure (verified through v0.3.0)

**Status: done.** `v0.3.0` was published on **2026-08-20** with all four signed APKs and is
marked latest: https://github.com/AsafMah/LeanType/releases/tag/v0.3.0

The runner outage that blocked it resolved on its own — Release run **31128748928** succeeded
at 2026-08-06 22:04 UTC and produced the draft. Everything below is the verified procedure,
kept because it is what the next release should follow.

### If runners stall again

The outage signature, so it is recognised rather than re-debugged:

- Job status `cancelled` after **exactly ~15 minutes**
- `steps: []` (never started) and `timing.billable.UBUNTU.total_ms == 0`
- Affected runs were `31126300886`, `31126946237` (Unit tests) and `31128410212` (Release)

This is **infrastructure, not code**. Do not "fix" tests in response to it. The two runs that
stayed stuck in `queued` after the outage (`31128410212`, `31126946237`) have since been
cancelled; if it recurs, clear the stragglers with
`gh run cancel <id> --repo AsafMah/LeanType`.

### Cut the release

```bash
gh workflow run release.yml --repo AsafMah/LeanType --ref vX.Y.Z
gh run list --repo AsafMah/LeanType --workflow release.yml --limit 3
```

The workflow builds all four signed flavors, runs `tools/check_apk_invariants.py` against the
packaged app IDs/minSdk/permissions/dictionary contents, verifies signatures (including explicit
API 21–23 v1/JAR checks), and — because `github.ref` is a tag — creates a **draft** GitHub Release
with the APKs attached.

Then verify the artifacts **after download** (do not trust the build alone):

```bash
gh release download vX.Y.Z --repo AsafMah/LeanType --pattern "*.apk" --dir build/release-vX.Y.Z
# for each APK:
#   apkanalyzer manifest application-id | version-name | version-code | min-sdk | permissions
#   apksigner verify --print-certs
```

Expected for every APK (all four **verified passing** through 0.3.0):
- signer SHA-256 `c032eafcd7ce9197fd9e636f2c86b1590f0a84f8f73016c66d63c1382af81554`
- matching version name / versionCode (`0.3.0` / `4300` for the current release)
- `INTERNET` only in standard + standardfull
- bundled dictionaries in offline + offlinelite, and none in standard + standardfull
- v1/JAR `true` for standard (minSdk 23), standardfull (23), offlinelite (21); offline is
  minSdk 26 and legitimately reports `v1=false` by default

Publish:

```bash
gh release edit vX.Y.Z --repo AsafMah/LeanType --draft=false --latest \
  --title "LeanTypeDual X.Y.Z" --notes-file docs/releasenote/release_notes_vX.Y.Z.md
```

Then set PR/issue items to **Done** on Project #3 (§8) and, ideally, install the signed
Standard Full APK over the phone's production package (`adb install -r`).

> **Signing cannot be done locally.** `keystore.properties` holds placeholder values; the
> real key exists only as GitHub Actions secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
> `KEY_ALIAS`, `KEY_PASSWORD`). Do **not** create a replacement key — it would break
> upgrade continuity for every installed user.

---

## 6. Build & test recipes (Windows, verified working)

Gradle needs both env vars.

```bash
# JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot   (21.0.11 does NOT exist)
# ANDROID_HOME=C:/Android/Sdk
./gradlew.bat compileOfflineRunTestsKotlin                 # fast gate, ~1-2 min
python tools/check_fork_invariants.py                      # fork identity/privacy/product gate
./gradlew.bat :app:testOfflineRunTestsUnitTest --continue  # what CI runs, ~50 s
./gradlew.bat :app:assembleStandardfullDebug               # phone build, ~2 min
```

Note `./gradlew.bat` — bare `gradlew.bat` is not on PATH in this shell.

### Known-failing tests (do NOT treat as regressions)

`:app:testOfflineRunTestsUnitTest` (the CI variant) on **Windows** → **4 failures**, all
`ParserTest` (`canLoadKeyboard`, `dvorak has 4 rows`, `de_DE has extra keys`, `popup key
count …`). These are asset/locale-ordering issues that **pass on Linux CI**. The final
post-v4.1.8/post-Shift/post-cleanup run was **343 tests, 4 failed, 8 skipped**; the authoritative
checker reported no new failures.

Always run:

```bash
python tools/check_test_results.py \
  --results-dir app/build/test-results/testOfflineRunTestsUnitTest \
  --baseline tools/test_baselines/runTests-windows.txt \
  --started-after <epoch-seconds-recorded-before-gradle>
```

It refuses stale or self-inconsistent results before diffing failure **names**. Do not infer
correctness from Gradle's exit code or hand-count JUnit XML.

The detailed full-debug comparison below is historical evidence from the v4.1.2 merge, not the
current runTests baseline:

| Baseline | Result |
|---|---|
| `origin/dev` (`6ac372de3`) | 320 tests, **12 failed** |
| v4.1.2 merge branch | 324 tests, **5 failed** |

Still failing on the merge branch — treat these as the current expected set:

- `InputLogicTest > tapOnlyCombiningWordDoesNotShowAutospaceIndicatorWhenGestureGateEnabled`
- `ParserTest` ×4 — `canLoadKeyboard`, `de_DE has extra keys`, `dvorak has 4 rows`,
  `popup key count does not depend on shift for (for simple layout)` (the same four as the CI
  variant)

The seven that stopped failing were `SubtypeTest > subtypeStaysEnabledOnEdits`, `InputLogicTest
> immediate regex expansion…`, `InputLogicTest > insertLetterIntoWordHangulFails`,
`ParserTest > backgroundType`, `XLinkTest > otherLinks`, and `StringUtilsTest` ×2
(`detectEmojisAtEndFail`, `isEmojiDetectsAllAvailableEmojis`).

**Attribute that carefully**, in three tiers. Both runs were on the same machine minutes apart,
which controls for toolchain, locale and machine state — but not for a remote host being
reachable.

- **Attributable:** `SubtypeTest > subtypeStaysEnabledOnEdits`, `InputLogicTest > immediate
  regex expansion…`, and `insertLetterIntoWordHangulFails` — deterministic logic tests with no
  external inputs, and the two §7 defects were separately confirmed fixed on a pristine v4.1.2
  checkout.
- **Plausible but unconfirmed:** `StringUtilsTest` ×2 and `ParserTest > backgroundType` — both
  depend on bundled data/assets that this merge does change, so the merge is the likely cause,
  but neither was isolated.
- **Not attributable:** `XLinkTest > otherLinks` — a live network call to Codeberg, which can
  flip with no code change at all. Don't count it as a fix.

**Always diff failing test *names* against a baseline run of the merge base — never compare
absolute pass counts.** Both the "12 failures" figure and a "5 failures" figure are correct —
for different baselines. Quote neither without saying which tree it came from.

---

## 7. Upstream bugs we inherited — fixed in v4.1.2 (historical)

**Both are resolved.** They are kept here because the *technique* is the reusable lesson:
when a merge produces failures, reproduce them on a pristine upstream checkout before blaming
your own merge.

Two tests failed identically on a pristine upstream `v4.0.8` checkout, proving they were
inherited rather than merge damage, and each was guarded with the repo's `runTests` skip so CI
gated on real failures:

1. **`SubtypeTest > subtypeStaysEnabledOnEdits`**
   `IllegalArgumentException: List has more than one element` at `SubtypeTest.kt:84` —
   `getEnabledSubtypes(false)` returned more than one subtype, most likely because upstream
   v4.0.4 added auto-persisting of default typing-language subtypes at startup.

2. **`InputLogicTest > immediate regex expansion triggers for symbol prefixed regex`**
   Typing `@john` with regex shortcut `@\w+` yielded `user_mentionohn`, expected
   `user_mention`. Immediate expansion fired at `@j` and the remaining letters were appended.

Upstream **v4.1.2 fixes both**, verified two independent ways: on a pristine v4.1.2 checkout
(`LeanType-check-upstream-main`) `SubtypeTest` runs 3 tests with 0 failures, and in the merged
tree both tests pass on the debug variant where the guards never applied. Both guards were
removed in **PR #137** (`e46454efb`), so the two tests now actually execute on CI. Neither
needs reporting upstream.

v4.1.2 also fixes the long-standing `InputLogicTest > insertLetterIntoWordHangulFails`, which
was never an inherited-defect case.

The guards that remain elsewhere are unrelated (Linux-only `ParserTest` ordering, `XLinkTest`
network calls, dictionary-dependent cases, emoji-data versioning) and look like:

```kotlin
if (BuildConfig.BUILD_TYPE == "runTests") return // reason; see #12
```

---

## 8. Conventions you must follow

- **Branching:** `main` is release-only; `dev` is the integration base for all PRs. Work on
  `merge/upstream-vX.Y.Z`, `chore/release-X.Y.Z`, `promote/vX.Y.Z`, `feat/…`, `fix/…`.
- **`gh pr create` gotcha:** from this fork it defaults the base repo to the *parent*
  (LeanBitLab) and fails with "No commits between…". Always pass
  `--repo AsafMah/LeanType`.
- **Changelog:** `CHANGELOG.md` records *LeanTypeDual's own* releases. Provenance is coarse:
  one `### Upstream` marker line per release, never per-entry tagging. Every notable entry
  carries a `(#N)` ref.
- **Versioning:** SemVer `versionName`; fork-offset `versionCode` =
  `4000 + major*1000 + minor*100 + patch*10` — 0.1.0→`4100`, 0.2.0→`4200`,
  0.3.0→`4300`. **Keep `versionCode` monotonic above `4300`.** Each release also needs
  `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and
  `docs/releasenote/release_notes_v<version>.md`.
- **Project #3 board** (`gh project … --owner AsafMah`):
  - project id `PVT_kwHOAGIGz84BZwMC`
  - Status field `PVTSSF_lAHOAGIGz84BZwMCzhUsrio`
  - options: Todo `f75ad846`, In Progress `47fc9ee4`, Done `98236657`
  - add items with `gh project item-add 3 --owner AsafMah --url <pr-or-issue-url>`
- **Settings 5-file pattern** still applies (`Settings.java`, `Defaults.kt`,
  `SettingsValues.java`, `strings.xml`, screen file) — **plus a 6th step now**: register the
  screen in the `modules` list in `SettingsContainer.kt` or it won't appear in settings search.

---

## 9. Traps that cost real time this session

1. **`gh` silently switches accounts.** It flipped to `lurebat` mid-session and
   `gh pr merge` failed with "does not have the correct permissions". Fix:
   `gh auth switch --hostname github.com --user AsafMah`. Check `gh auth status` before
   any write operation.

2. **Tag pushes do not reliably trigger the Release workflow.** `git push origin v0.1.0`
   produced no run. Workaround that works and still creates the draft release (because
   `github.ref` becomes the tag):
   `gh workflow run release.yml --repo AsafMah/LeanType --ref v0.1.0`.
   PR-opened events also failed to trigger "Unit tests"; dispatch manually with
   `gh workflow run build-test-auto.yml --ref <branch>`.

3. **`dev` → `main` cannot be merged by GitHub.** `main` carries release commits that were
   never merged back into `dev`, plus stale files upstream deleted, so GitHub reports
   "the merge commit cannot be cleanly created". The working promotion recipe (used for
   both #128 and #130) — `main` is a release pointer, so record both parents but adopt
   `dev`'s tree verbatim:

   ```bash
   git worktree add -b promote/vX.Y.Z <dir> origin/main
   cd <dir>
   git merge --no-ff --no-commit origin/dev   # will conflict; ignore
   git read-tree -u --reset origin/dev        # tree := dev's tree exactly
   git diff --stat origin/dev                 # MUST be empty
   git commit -m "Release LeanTypeDual X.Y.Z"
   git push -u origin promote/vX.Y.Z
   # then open a PR into main and merge it (mergeable cleanly)
   ```

   Files that exist only on `main` and are intentionally dropped: `TextEditView.java`,
   `SpacedTokens.kt`, `text_edit_view.xml` (removed upstream in the v3.9.1 integration) and
   `.github/workflows/build-release-apk.yml` (consolidated into `release.yml`).

4. **`apksigner verify` reports `v1=false` when it doesn't need v1.** For an APK whose
   effective minSdk ≥ 24, the default verify does not evaluate the JAR signature even when
   one exists. To prove low-API installability you must bound the check:
   `apksigner verify --min-sdk-version 21 --max-sdk-version 23 <apk>`. The release workflow
   now enforces this for every flavor installable below API 24.

5. **The `edit` tool fails in secondary worktrees.** It errors inside the unrelated
   `llm-wiki` extension with `The "paths[0]" property must be of type string, got undefined`.
   Workaround used throughout: exact-match string replacement via a script, or full-file
   `write`.

6. **Watch line endings when scripting edits.** Writing `\r\n` into a file that git stores
   as LF produced a whole-file diff (2,500+ lines) for a 2-line change, and doubled CRs
   (`\r\r\n`). Normalize with `text.replace(/\r+\n/g, '\n')` and confirm with
   `git diff --stat` before committing.

7. **Mergiraf auto-resolves conflicts silently.** It reported "Solved N conflicts" for
   `WelcomeWizard.kt`, `TextExpanderUtils.kt`, `SuggestionStripView.kt`, `Settings.java`,
   `ClipboardHistoryView.kt`. Those were audited this session and are clean, but **always
   audit them** — it can drop fork behavior or duplicate sibling blocks.

8. **Don't run two Gradle test suites concurrently.** Doing so made one appear to hang for
   an hour (it had actually written all results); it was daemon contention, not a code hang.

---

## 10. Upstream merge recipe (for the next release)

```bash
git fetch upstream --tags --prune
git ls-remote --tags --refs upstream          # get the exact tag SHA; never assume upstream/main
git worktree add -b merge/upstream-vX.Y.Z <dir> origin/dev
cd <dir>
git merge-tree --write-tree --name-only origin/dev <tag-sha>   # preview conflicts
git merge --no-ff --no-commit <tag-sha>
```

Conflict decisions taken this round (useful precedent):

- `app/build.gradle.kts` → **ours** (fork version) always.
- `docs/badges/*.svg` → **ours**.
- `LatinIME.onCreate` → upstream deleted `updateWrappedContext()` entirely (app language now
  applied in `attachBaseContext`); keep only the fork's `SwipeGestureEngine.initialize(this)`.
- `InputLogic` manual-pick `mLastComposedWord.deactivate()` → **theirs** (upstream added it
  in `dee0db75` then reverted it in `d6850b5c2`; the revert is intentional).
- `Suggest.kt` → keep the fork's `filterMultiWordSuggestions(...)` helper **and** add
  upstream's emoji-dictionary exclusion, in both the typing and batch paths.
- `SettingsContainer.kt` → take upstream's `modules`-registry `createSettings`, then
  re-register the fork's two-thumb module + `SCREEN_NAV_TWO_THUMB_TYPING` key.
- `MainSettingsScreen.kt` `@Preview` → arity must match the merged 14-parameter signature
  (neither side's count was right).

Also consolidated a **pre-existing** duplicated selection-reset block in
`InputLogic.onUpdateSelection` (two identical guards; the second was a strict superset).

---

## 11. Environment / device details

- Repo root: `C:/Users/mahle/programming/LeanType` (currently on stale branch
  `merge/upstream-v3.9.3`).
- **The whole tree moved** from `C:/Users/mahle/` to `C:/Users/mahle/programming/`. If
  `git worktree list` ever marks every worktree `prunable` again, that is a *relocation
  artifact, not abandonment* — fix it with `git worktree repair <new-paths...>` from the repo
  root. **Never** reach for `git worktree prune` in that state; it drops the admin records
  and orphans live work.
- `core.longpaths=true` is set on this repo. Without it, `git worktree remove` fails with
  `Filename too long` on worktrees that have deep Gradle build output, leaving a
  de-registered but half-deleted directory behind.

### Secondary worktrees (after the 2026-08-20 cleanup)

Kept deliberately:

| Path (under `C:/Users/mahle/programming/`) | Branch / HEAD | Why |
|---|---|---|
| `LeanType-bksp` | `issue37-slide-target-actions` | backs **PR #106** (open) |
| `LeanType-two-thumb-pr` | `pr/upstream-two-thumb-step1` | backs **LeanBitLab PR #240** (open) |
| `LeanType-check-origin-dev` | detached at `origin/dev` | baseline test runs |
| `LeanType-check-origin-main` | detached at `origin/main` | baseline for the released tree |
| `LeanType-check-upstream-main` | detached at upstream `v4.1.8` | "does this fail upstream too?" checks |

`check-upstream-main` tracks the upstream tag currently integrated; it is now at `v4.1.8`
(`cbfaf21a`). Re-point it whenever the merge target changes, and use it the same way: reproduce
any new merge failure on the pristine tag before blaming your own merge.

Unfinished work — all six branches are now **backed up on `origin`** (pushed 2026-08-20 purely
as backups: no PRs, delete with `git push origin --delete <branch>` once triaged). The commits
are safe; what still needs a decision is whether each line of work continues:

| Path | Branch | State |
|---|---|---|
| `LeanType-a11` | `feat/spacing-a11-insight` | 4 commits; PRs #95/#93 closed unmerged; issues #24, #26 open |
| `LeanType-gates` | `feat/spacing-gate-model` | 2 commits (shares one with `a11`); PR #94 closed unmerged; issue #24 open |
| `LeanType-b7a` | `b7a-prefix-aware-stripping` | 2 commits + uncommitted debug logging in `InputLogic.java`; never PR'd; issues #98, #99 open |
| `LeanType-swipe` | `feat/statistical-swipe-decoder` | 1 commit + an uncommitted `swipetest` build variant; never PR'd |
| `LeanType-upstream-shortcut-rows` | `feat/upstream-shortcut-rows` | 1 local build-differentiation commit on top of the pushed `pr/upstream-shortcut-rows` |
| `LeanType-upstream-two-thumb-step1` | `feat/upstream-two-thumb-step1` | 1 local build-differentiation commit on top of the pushed `pr/upstream-two-thumb-step1` |

**The backup covers commits, not working trees.** Two of these still hold uncommitted changes
that exist nowhere else: `LeanType-b7a` (modified `InputLogic.java` — debug logging) and
`LeanType-swipe` (modified `app/build.gradle.kts` plus an untracked `app/src/swipetest/`).
Commit or discard those deliberately; deleting either worktree without doing so loses them.
The other four are clean.

Removed on 2026-08-20 (≈5.6 GB reclaimed) — all fully merged or superseded, and **every branch
was kept**, so any of them can be restored with `git worktree add <dir> <branch>`:
`LeanType-release-311`, `LeanType-release-020`, `LeanType-promote-010`, `LeanType-qol`,
`LeanType-preview`, `LeanType-corpus`, `LeanType-badges`, `LeanType-replay`,
`LeanType-shortcut-pr`, `LeanType-upstream-399`, `LeanType-upstream-402`,
`LeanType-upstream-408`, `LeanType-upstream-shift-fix`.

Short-lived upstream/feature worktrees come and go with §10's merge recipe and app-native child
sessions. Retire each one after its PR lands; do not treat an active session worktree as stale.

### Device

- Phone: Samsung **SM-S936B**, Android 16, wireless ADB. Device id
  `adb-R5CY13MP25X-jUf01K._adb-tls-connect._tcp` (IP/port changes each toggle; rediscover
  with `adb mdns services`). The user must re-toggle Wireless debugging when it drops.
- Last verified on 2026-08-20 with signed `com.asafmah.leantypedual` 0.3.0 plus separate
  `.debug` and `.exp` packages. The active IME may have changed since then; query
  `settings get secure default_input_method` rather than assuming.
- Tablet: not connected at any point this session.

### Reproducing the memory-trim crash path on device

```bash
adb shell pidof com.asafmah.leantypedual.debug
adb shell input keyevent 3                      # background it first
adb shell am send-trim-memory <pid> 20          # 20 = TRIM_MEMORY_UI_HIDDEN
adb shell pidof com.asafmah.leantypedual.debug  # same PID => survived
adb logcat -d -b crash -t 30                    # must be empty
```

`am send-trim-memory` refuses levels while the process is foreground ("Unable to set a
background trim level on a foreground process") and refuses to *raise* a level twice.

---

## 12. Suggested next steps

1. **Device-verify the Shift fix (#150)** when SM-S936B wireless debugging is available:
   single tap gives temporary Shift; fast double-tap locks with the lock icon; several letters
   stay uppercase; a later Shift tap unlocks; duplicate press without release does not lock.
2. **Device-smoke the v4.1.8 sync (#149):** normal typing/suggestions, all four flavor identities,
   floating mode both without and with the user-granted overlay permission, custom sounds,
   text edit layout, dictionary availability in network-free builds, and no offlinelite AI/network
   UI leakage.
3. **The falsified gesture experiment is removed (#147).** `PointerIdNormalizer` remains because
   it fixes the real no-id-0/zero-suggestions path; DUAL_POINTER, ideal-prefix synthesis,
   re-timing controls and the misleading host harness are gone. Do not reintroduce them without
   measuring the actual closed gesture library loaded on device.
4. **Tablet smoke** — the only never-executed release gate.
5. **Issue #131 — "Java gesture not working with custom layouts"** is an open bug filed
   against the fork's own fallback gesture engine; likely the highest-value functional work.
6. Triage the six unfinished worktrees in §11. Their branches are backed up on `origin` now, so
   there's no deadline — but `LeanType-b7a` and `LeanType-swipe` still hold uncommitted changes
   that the backup does not cover.
7. Track upstream Shift report `LeanBitLab/LeanType#475` and accelerated-delete report
   `LeanBitLab/LeanType#423`; avoid permanent fork-only drift once upstream fixes land.
