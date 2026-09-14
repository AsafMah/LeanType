# LeanTypeDual changelog

## [Unreleased]

### Added
- Optional swipe-up and swipe-down shortcut menus, aligned with the top and bottom character rows and editable through Secondary layouts. The upper gesture follows the number row when shown. (#156)

### Fixed
- Cancel queued glide updates when a swipe shortcut menu takes over the touch gesture. (#156)

### Reliability & testing
- Exercise swipe menus with glide detection both enabled and disabled, including completed glide paths, pending timers and recovery after cancellation; clarify popup input completion and coordinate spaces. (#156)

### Upstream
- Based on LeanBitLab/LeanType commit `2a9d5fecb67c12b834ba2a1e80ffcdc8ab7d6720`; upstream release history remains in `docs/releasenote/`.

### Changed
- Brand the app, keyboard, spell checker, settings, and APKs as LeanTypeDual, using package `com.asafmah.leantypedual` and version `2.0.0` (`6000`) while retaining upstream icons and plugin compatibility.
- Check this fork's releases for app updates instead of offering upstream LeanType APKs.
