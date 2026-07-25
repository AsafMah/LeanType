### 💖 Support Our Work
* We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New in v4.0.1

### 🛠️ Bug Fixes & Stability Improvements
- **PointerTracker TimerProxy Safety**: Fixed `NullPointerException` on `TimerProxy.startTypingStateTimer` by defensively defaulting static `sTimerProxy` to `TimerProxy.NULL` and guarding accesses when proxy map references are cleared during view teardowns or transitions.
- **Dynamic InputConnection & Long Press Fix**: Fixed issue where re-opening the keyboard in Launcher or search fields dropped character input and long-press popup key selections by dynamically fetching the live system `InputConnection`.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.0.1-standardfull-debug.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.0.1-standard-debug.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.0.1-offline-debug.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.0.1-offlinelite-debug.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
