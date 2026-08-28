### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget and have little time for marketing. If LeanType helps you daily, please consider becoming a sponsor on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leantype). Even if you can't contribute financially, sharing LeanType with your friends, family, or on social media makes a world of difference to help our project grow. Thank you for your support!

## 🚀 What's New in v4.1.7

- **Unified Offline Edition (`minSdk = 21`)**: Consolidated `offline` and `offlinelite` into a single lightweight **Offline** edition (`com.leanbitlab.leantype.offline`). Supports Android 5.0+ with modular plugin OS version guards (`API 26+` for Offline AI and Handwriting, `API 24+` for Translation).
- **Modernized Handwriting Canvas & Instant Model Lookup**: Added quadratic Bezier ink curve smoothing (`quadTo`), baseline writing guidelines, smooth recognition fade-out animation, watermark hint, and instantaneous model resolution with zero lag.
- **Enhanced Gesture Typing Accuracy**: Integrated personalized session word boost for swiped gestures, high-DPI stroke sampling (`12.5%` key width) for tight corner detection, and context-aware suggestion reranking.
- **Auto-Correction & Capitalization Guardrails**: Fixed sentence-starter auto-capitalization session boost pollution, protected exact in-dictionary typed words against unwanted contraction replacements (`"does"` → `"doesn't"`), and added safe hold-to-delete suggestion purging.
- **Persistent Floating Mode Option**: Added *"Remember floating mode"* setting (`Settings -> Appearance -> Miscellaneous`) to automatically reopen the keyboard directly in floating mode across input sessions and apps until explicitly docked, with smart auto-dismissal when closing search.

## 📦 Choose Your Flavor

| Flavor                                          | Primary Focus                  | AI Engine        | Plugins Setup                  | Internet                         | Self-Updater         |
|:----------------------------------------------- |:------------------------------ |:---------------- |:------------------------------ |:-------------------------------- |:-------------------- |
| **`1-LeanType_4.1.7-standardfull-release.apk`** | **Convenience (Recommended)**  | Cloud AI         | In-app download or File import | Optional (AI/Updates/plugins)    | ✅ In-App Auto Update |
| **`1-LeanType_4.1.7-standard-release.apk`**     | **F-Droid**                    | Cloud AI         | In-app download or File import | Optional (AI/plugins)            | ❌ None               |
| **`2-LeanType_4.1.7-offline-release.apk`**      | **Offline**                    | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None               |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 5.0+), **Offline Translation** (Android 7.0+), **Offline Handwriting Recognition** (Android 8.0+), and **Offline AI Proofreading** (Android 8.0+) via modular plugins, and work 100% offline.
