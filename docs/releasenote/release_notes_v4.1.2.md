### 💖 Support Our Work
As an open-source, community-funded project, we operate on a very limited budget and have little time for marketing. If LeanType helps you daily, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). Even if you can't contribute financially, sharing LeanType with your friends, family, or on social media makes a world of difference to help our project grow. Thank you for your support!

## 🚀 What's New in v4.1.2

### ✨ New Features & Enhancements
- **Dedicated Voice Recognition Language Selector**: Added customizable voice language selection in Settings → Voice with Auto-Detection (`auto`), Follow Active Keyboard Language (Default), and support for 99+ Whisper languages localized with native display names.
- **Inline Clipboard Item Swipe Gestures**: Enabled full inline editing by swiping right on history entries, and quick swipe-left removal with a 5-second undo bar.
- **Privacy-First OTP Notification Listener**: Notification-based OTP detection engine (`OtpNotificationListenerService`) ensuring complete user privacy without `RECEIVE_SMS` permission.
- **Dynamic SMS Application Selector**: Added SMS app package selector in Settings → Text Correction → OTP Auto-Fill to allow selecting specific messaging apps for OTP extraction.

### 🐛 Bug Fixes & Stability Improvements
- **Debug Settings Dictionary Dump Toast**: Added instant Toast visual feedback when triggering dynamic dictionary dumps.
- **Unified Inline Clipboard Swipe Parity**: Constrained inline text areas with `weight = 1f` so long text never overlaps action buttons, enabled auto-scrolling cursor visibility (`bringPointIntoView`), and unified spacebar cursor swipe and delete selection swipe across both edit and search modes.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.1.2-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.1.2-standard-release.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.1.2-offline-release.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.1.2-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
