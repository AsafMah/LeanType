# LeanTypeDual 2.0.0

## A clean fork reset

LeanTypeDual 2.0.0 starts from LeanBitLab/LeanType commit
`2a9d5fecb67c12b834ba2a1e80ffcdc8ab7d6720`, with the fork's branding and
opt-in swipe shortcut menus. This is a clean reset, **not feature parity with
LeanTypeDual 0.3.0**: the old fork's custom functionality has not been carried
forward. Upstream artwork and the plugin ecosystem retain their provenance.

## Editable, row-aligned swipe shortcuts

- Enable **Swipe up for shortcuts** and/or **Swipe down for shortcuts** in
  **Settings > Gesture typing**. Both are off by default.
- Swipe up from the top row or down from the bottom letter row to open a menu
  aligned with that row. When the number row is visible, it becomes the top
  swipe-up row.
- Edit **Swipe-up shortcuts** and **Swipe-down shortcuts** in
  **Settings > Secondary layouts**.
- Cancelling a menu does not commit a shortcut. Pending glide updates and
  long-press/repeat timers are stopped when the menu takes over, preventing
  stray input and allowing the next gesture to start cleanly.

The app, keyboard, spell checker, settings and APKs are branded **LeanTypeDual**.
Update checks use this fork's releases rather than upstream LeanType releases.

## Choose one of three APKs

All APKs use version `2.0.0` / version code `6000` and include ARMv7 and ARM64
native libraries (`armeabi-v7a`, `arm64-v8a`).

| APK | Minimum Android | Package | Network and installation permissions |
| --- | --- | --- | --- |
| `1-LeanTypeDual_2.0.0-standard-release.apk` | 6.0 (SDK 23) | `com.asafmah.leantypedual` | Internet permission for online features; no package-install permission |
| `1-LeanTypeDual_2.0.0-standardfull-release.apk` | 6.0 (SDK 23) | `com.asafmah.leantypedual` | Same online implementation as standard, plus package-install permission for the in-app updater |
| `2-LeanTypeDual_2.0.0-offline-release.apk` | 5.0 (SDK 21) | `com.asafmah.leantypedual.offline` | No Internet permission; optional plugins are imported separately |

Standard and standardfull are alternatives for the **same installed app**, not
side-by-side packages. Offline has its own package and can coexist with either.
There is **no offlinelite APK** in this release. Optional plugins can have higher
Android or architecture requirements than the keyboard itself.

## Before upgrading from 0.3.0

The retained variants keep their LeanTypeDual package identities and use the
existing release signing key. The version code increases from `4300` to `6000`.
That preserves Android's package/signature upgrade path; it does **not** guarantee
that every old setting, layout, plugin configuration or custom feature migrates.
Export anything important using the old app's available backup/export functions
before upgrading, and review your settings afterwards. Offlinelite users do not
have a same-package 2.0.0 replacement.
