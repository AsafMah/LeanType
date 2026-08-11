### 💖 Support Our Work
As an open-source, community-funded project, we operate on a very limited budget and have little time for marketing. If LeanType helps you daily, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). Even if you can't contribute financially, sharing LeanType with your friends, family, or on social media makes a world of difference to help our project grow. Thank you for your support!

## 🚀 What's New in v4.1.1

### ✨ New Features & Enhancements
- **Privacy-First OTP Notification Listener**: Replaced sensitive `RECEIVE_SMS` permission with a notification-based OTP detection engine (`OtpNotificationListenerService`), ensuring complete user privacy and Play Store policy compliance.
- **Dynamic SMS Application Selector**: Added SMS app package selector in Settings → Text Correction → OTP Auto-Fill to allow selecting specific messaging apps (Google Messages, Signal, WhatsApp, Telegram, etc.) for OTP extraction.
- **Optimized Landscape Keyboard Default Scale**: Adjusted default landscape keyboard height scale factor to 45% (0.45f) for improved screen visibility and posture in landscape mode.

### 🐛 Bug Fixes & Stability Improvements
- **Clipboard Edit & Search Layout Switch Interception**: Intercepted layout-switching keys (`?123` Symbols, `Shift`, `Caps Lock`) locally in `ClipboardHistoryView`, allowing in-place layout toggling on the embedded bottom row without closing the inline edit/search bar.
- **Word-Gesture Suppression during Inline Input**: Added transient gesture suppression in `GestureEnabler` & `PointerTracker` to prevent word-gesture strokes from leaking and committing to underlying text fields while editing or searching clipboard items.
- **Unified Inline Clipboard Swipe Parity & Text Scrolling**: Constrained inline text areas with `weight = 1f` so long text never overlaps action buttons (`Save`, `Close`), enabled auto-scrolling cursor visibility (`bringPointIntoView`), and unified spacebar cursor swipe and delete selection swipe across both edit and search modes.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.1.1-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.1.1-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.1.1-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.1.1-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
