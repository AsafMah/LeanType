# LeanTypeDual changelog

## [Unreleased]

### Added
- Experimental unified Emoji, GIFs and Stickers picker in online flavors, using a personal GIPHY API key, explicit search, animated previews and an explicit Android Share fallback when a field rejects insertion. Online media is disabled in incognito/no-learning/password contexts; the offline flavor remains emoji-only.
- Optional swipe-up and swipe-down shortcut menus, aligned with the top and bottom character rows and editable through Secondary layouts. The upper gesture follows the number row when shown. (#156)

### Fixed
- Cancel queued glide updates when a swipe shortcut menu takes over the touch gesture. (#156)
- Apply upstream's per-app Force Incognito profiles to online media immediately, without relaxing password, no-learning or global incognito restrictions.

### Reliability & testing
- Exercise swipe menus with glide detection both enabled and disabled, including completed glide paths, pending timers and recovery after cancellation; clarify popup input completion and coordinate spaces. (#156)
- Cover swipe popup window-relative X/Y alignment, selection and fixed placement in docked/floating layouts with and without the number row. Verify media teardown and editor invalidation alongside upstream's explicit-show lifecycle resets.

### Upstream
- Rebased `v2` onto LeanBitLab/LeanType commit `10f29237acc559e7ef7185ebbb3ed6fe8973c14a` (upstream 4.2.6); upstream release history remains in `docs/releasenote/`. The fork remains version `2.0.0` (`6000`).
- Backup restore store preservation is now supplied by [LeanBitLab/LeanType#519](https://github.com/LeanBitLab/LeanType/pull/519), and custom-layout emoji search preservation by [LeanBitLab/LeanType#518](https://github.com/LeanBitLab/LeanType/pull/518). Their patch-equivalent standalone fork commits were dropped during the rebase.
- Retain newer upstream floating-window, emoji focus, physical-keyboard navigation, app-profile and permission-removal changes alongside the fork additions. Branding, swipe menus, glide cancellation and the three media layers remain separate patches.

### Changed
- Brand the app, keyboard, spell checker, settings, and APKs as LeanTypeDual, using package `com.asafmah.leantypedual` and version `2.0.0` (`6000`) while retaining upstream icons and plugin compatibility.
- Check this fork's releases for app updates instead of offering upstream LeanType APKs.
- Follow upstream's completed Standard Full merger: build Standard or Offline only. GIF decoding dependencies and device-test fixtures remain attached to Standard; Offline keeps its unavailable-media implementations.
