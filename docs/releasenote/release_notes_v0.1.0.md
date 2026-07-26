# LeanTypeDual 0.1.0

## Highlights

- Configure a target input method and switch directly to it using toolbar keycode `-10076`, including on Android 6–8.
- Create and persist up to five custom layout slots across symbol mode, rotation, and keyboard reloads.
- Configure auto-correct trigger characters and optionally suppress multi-word suggestions.
- Control first- and next-word suggestions, background services, and immediate suggestion spacing.
- Keep ordinary lowercase words lowercase when typing or swiping without Shift.
- Use the optimized built-in Java gesture engine with lower memory usage, improved ranking, and corrected suggestion casing.
- Browse an updated dictionary download catalog with stale unavailable entries removed and newly published dictionaries added.
- Benefit from more reliable Text Expander placeholder navigation and dictionary/blacklist handling.

## Build variants

- **Standard Full**: cloud AI and handwriting support; requires Internet permission.
- **Standard**: FOSS standard build; requires Internet permission for opt-in online features and downloads.
- **Offline**: on-device AI; no Internet permission.
- **Offline Lite**: smallest build without AI integration; no Internet permission.

## Upgrade notes

LeanTypeDual's visible version series restarts at `0.1.0`. Android version code `4100` remains above the previous `3.10.0`/`4000` release, so the versionCode permits upgrades when APKs use the established LeanTypeDual signing key. The separate `com.asafmah.leantypedual` application ID continues to prevent collisions with upstream LeanType.

LeanTypeDual remains a separate application with the same package identity. Existing settings and dictionaries are retained when upgrading.
