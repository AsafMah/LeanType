# Preparing LeanTypeDual releases

Use `.github/workflows/release.yml` on the intended `v2`-based candidate.
Do not run `tools/release.py`: it is upstream maintenance tooling that changes
translations, branding and dictionaries, not this fork's release command.

## Metadata and local checks

Keep `app/build.gradle.kts` authoritative for `versionName` and `versionCode`.
The fork's code formula is `4000 + major * 1000 + minor * 100 + patch * 10`.
Prepare `docs/releasenote/release_notes_v<versionName>.md`, starting with
`# LeanTypeDual <versionName>`, and
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
Move the fork's changelog entries into a dated release-preparation section and
leave a fresh Unreleased section. The preparation date is not a publication date.

```text
python -B -m unittest discover -s docs/scripts -p "test_*.py" -v
python docs/scripts/generate_release_notes.py
```

The notes script uses Gradle's version for branch builds (including branch `v2`).
For tag builds it also requires the exact tag `v<versionName>`. Missing, empty,
header-only or wrong-version notes fail rather than becoming placeholder notes.
Historical upstream notes are archival, not the fork's release source.

## Signed candidate, before tagging

The registered `Release` workflow accepts a manual dispatch from a pushed
candidate branch:

```text
gh workflow run release.yml --repo AsafMah/LeanType --ref <candidate-branch>
```

Confirm the resulting run's `headSha` is the candidate commit. Manual dispatch
builds and uploads artifacts only; it cannot create a GitHub release.
The runner needs repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, and `KEY_PASSWORD`. All four are required. They must describe the
existing LeanTypeDual release key, not a new key. Never copy them locally or put
keystore material in logs/artifacts.

CI runs the real native, minified release builds for standard, standardfull and
offline. It requires exactly one APK per flavor, verifies signatures including
Android 5/6 v1 compatibility at each flavor's minimum SDK, and requires R8
mappings. The `release-apks` artifact contains three APKs, SHA-256 checksums,
notes and the exact source commit; `release-verification` contains signature
reports and R8 mappings.

Download artifacts into a new directory. Independently run `apksigner verify
--verbose --print-certs` and `aapt dump badging` on every candidate APK. Check:

- Package, version name/code, label, min SDK, target SDK, native ABIs and
  non-debuggable status agree with the source and release notes.
- The signer certificate SHA-256 matches the corresponding **published** old
  APK for every retained flavor/package identity, not just one flavor.
- Standard and standardfull share the base package; offline has `.offline`.
  There is no offlinelite output. Do not infer settings migration from matching
  certificates.
- Exercise the minified APK on a device, including swipe activation/editing,
  cancellation, ordinary typing and the old-version upgrade path. A signed
  build or a successful debug smoke test does not establish this.

## Approval boundary

Open a focused preparation PR targeting **v2**, and verify its base after
creation; do not target old `main`. Record the candidate run, signer comparisons,
artifacts and remaining device checks in the PR.

Merging that PR, pushing the final version tag and publishing a public release
each require explicit maintainer approval. A version-tag workflow creates only
a **draft**, after its signed-build gates pass. Review the draft's notes, all
three APKs and checksums before separately approving publication.
