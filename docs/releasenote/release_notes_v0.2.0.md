# LeanTypeDual 0.2.0

## Highlights

- Automatic screen-profile detection with foldable support, including a split-keyboard default on large screens and immediate reload when the screen configuration changes.
- Select suggestions from a connected physical keyboard using shortcut keys, shown only while a physical keyboard is active.
- Choose a translation engine and use the translation plugin, with target-language management moved onto the AI Integration screen.
- Find any setting from search: settings screens are now indexed automatically, including two-thumb typing.
- Resize the floating keyboard by dragging, with width and height persisted and previewed live.
- Tune key-gap narrowness on a 0-10 scale independently of key borders, and let toolbar keys span the available width.
- Expand regex shortcuts that use a prefix or capture groups more reliably.

## Build variants

- **Standard Full**: cloud AI and handwriting support; requires Internet permission.
- **Standard**: FOSS standard build; requires Internet permission for opt-in online features and downloads.
- **Offline**: on-device AI; no Internet permission.
- **Offline Lite**: smallest build without AI integration; no Internet permission.

## Upgrade notes

Android version code `4200` is above the previous `0.1.0`/`4100` release and the APKs use the same established LeanTypeDual certificate, so signed builds install in place over an existing LeanTypeDual package. Application IDs are unchanged.

This release merges LeanBitLab/LeanType v4.0.3 through v4.0.8 while keeping LeanTypeDual's Java fallback gesture engine, two-thumb typing, privacy flavors, and separate application ID.
