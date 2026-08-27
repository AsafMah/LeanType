### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget and have little time for marketing. If LeanType helps you daily, please consider becoming a sponsor on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leantype). Even if you can't contribute financially, sharing LeanType with your friends, family, or on social media makes a world of difference to help our project grow. Thank you for your support!

## 🚀 What's New in v4.1.6

- **Ultra-Lightweight APKs (~9.8 MB)**: Unbundled dictionaries across all flavors in favor of on-demand downloads via DictManager, dramatically reducing baseline download size and storage footprint.
- **Modular Offline AI Dynamic Plugin**: Decoupled the local GGUF AI engine into a standalone dynamic plugin (`LeanType-Offline-AI-Plugin`), keeping the core keyboard fast and lean.
- **Instant Offline Translation Hot-Reload & Persistence**: Direct filesystem inspection and proactive plugin cache invalidation ensure imported translation models (`.zip`) are instantly recognized and retained across dialog reopenings without requiring a keyboard restart.
- **Refined Setup Wizard & Unified Plugins Hub**: Streamlined the Welcome Wizard across all flavors and integrated Offline AI into the centralized Plugins Hub alongside Voice, Handwriting, and Translation.

## 📦 Choose Your Flavor

| Flavor                                          | Primary Focus                  | AI Engine        | Plugins Setup                  | Internet                         | Self-Updater         |
|:----------------------------------------------- |:------------------------------ |:---------------- |:------------------------------ |:-------------------------------- |:-------------------- |
| **`1-LeanType_4.1.6-standardfull-release.apk`** | **Convenience (Recommended)**  | Cloud AI         | In-app download or File import | Optional (AI/Updates/plugins)    | ✅ In-App Auto Update |
| **`1-LeanType_4.1.6-standard-release.apk`**     | **F-Droid**                    | Cloud AI         | In-app download or File import | Optional (AI/plugins)            | ❌ None               |
| **`2-LeanType_4.1.6-offline-release.apk`**      | **Offline AI**                 | Local LLM Plugin | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None               |
| **`3-LeanType_4.1.6-offlinelite-release.apk`**  | **Offline Lite**               | None             | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None               |

> 💡 **Plugin Compatibility**: All 4 flavors support **Offline Handwriting Recognition**, **Offline Translation**, and **Offline Voice Dictation** via plugins, and work 100% offline.

> 📢 **Upcoming Flavor Consolidation (Next Release)**: Starting from the next release, the `offline` and `offlinelite` flavors will be merged into a single unified **Offline** edition (lightweight without bundled AI). Users who want local LLM offline AI proofreading can easily load the dynamic **Offline AI Plugin** from the Plugins Hub at any time.
