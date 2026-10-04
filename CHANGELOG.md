# LeanTypeDual changelog

## [Unreleased]

### Added
- Experimental unified Emoji, GIFs and Stickers picker in online flavors, using a personal GIPHY API key, explicit search, animated previews and an explicit Android Share fallback when a field rejects insertion. Online media is disabled in incognito/no-learning/password contexts; the offline flavor remains emoji-only.
- Optional swipe-up and swipe-down shortcut menus, aligned with the top and bottom character rows and editable through Secondary layouts. The upper gesture follows the number row when shown. (#156)
- Optional per-field Literal typing through toolbar, pinned keys or custom layouts: preserve native composition and explicit editing while suspending automatic case, correction, suggestions, spaces, expansion, learning and word glide. No saved typing/privacy preferences or learned words are changed.

### Fixed
- Cancel queued glide updates when a swipe shortcut menu takes over the touch gesture. (#156)
- Apply upstream's per-app Force Incognito profiles to online media immediately, without relaxing password, no-learning or global incognito restrictions.
- Match text-expander triggers and prefix lengths against actual typed text, not a pending autocorrection, while preserving explicit manual suggestion choices. ([LeanBitLab/LeanType#570](https://github.com/LeanBitLab/LeanType/pull/570))
- Do not report an autocorrection or record candidate statistics when a text expansion replaced that candidate.
- Treat unavailable surrounding text as unknown rather than a sentence start; use the editor's caps-mode response without changing explicit caps or valid cached-context behavior. ([LeanBitLab/LeanType#571](https://github.com/LeanBitLab/LeanType/pull/571))
- Select the actual existing missing-apostrophe contraction candidate rather than an unrelated higher-ranked suggestion, preserving explicit casing and dictionary shortcut priority. No missing dictionary entries are synthesized. ([LeanBitLab/LeanType#573](https://github.com/LeanBitLab/LeanType/pull/573))

### Reliability & testing
- Exercise swipe menus with glide detection both enabled and disabled, including completed glide paths, pending timers and recovery after cancellation; clarify popup input completion and coordinate spaces. (#156)
- Cover swipe popup window-relative X/Y alignment, selection and fixed placement in docked/floating layouts with and without the number row. Verify media teardown and editor invalidation alongside upstream's explicit-show lifecycle resets.
- Exercise honeycomb row geometry, vertical spacebar gestures, popup animation reuse and native touch routing. Literal integration covers field lifetime, raw composition, stale asynchronous results across on/off, toolbar/pinned activation and retained swipe shortcuts.
- Enforce JSON keyword parity for every toolbar action, including customized primary codes. ([LeanBitLab/LeanType#572](https://github.com/LeanBitLab/LeanType/pull/572))

### Upstream
- Upstream baseline: LeanBitLab/LeanType commit `24ecbb0a6503a48965ef99e8958530a9445bb414` ([4.2.9 beta-429-1](https://github.com/LeanBitLab/LeanType/releases/tag/beta-429-1), prerelease). Upstream release history remains in `docs/releasenote/`; the fork remains version `2.0.0` (`6000`).
- Includes upstream fixes for web-editor backspace lag, double capitalization during fast typing/chording, Pixel haptics, the delete-key icon, Arabic secondary symbols and landscape `TYPE_NULL` fields. Keeps the fork's Literal mode and focused expander/contraction fixes alongside upstream's auto-capitalization controls.
- Backup restore store preservation is now supplied by [LeanBitLab/LeanType#519](https://github.com/LeanBitLab/LeanType/pull/519), and custom-layout emoji search preservation by [LeanBitLab/LeanType#518](https://github.com/LeanBitLab/LeanType/pull/518). Their patch-equivalent standalone fork commits were dropped during the rebase.
- Retain upstream honeycomb layouts, proportional popup placement/animations, toolbar gestures, physical-keyboard translation, OTP and lifecycle changes alongside the fork additions. Branding, swipe menus, glide cancellation, media layers and focused input fixes remain separate patches.

### Changed
- Brand the app, keyboard, spell checker, settings, and APKs as LeanTypeDual, using package `com.asafmah.leantypedual` and version `2.0.0` (`6000`) while retaining upstream icons and plugin compatibility.
- Check this fork's releases for app updates instead of offering upstream LeanType APKs.
- Follow upstream's completed Standard Full merger: build Standard or Offline only. GIF decoding dependencies and device-test fixtures remain attached to Standard; Offline keeps its unavailable-media implementations.
