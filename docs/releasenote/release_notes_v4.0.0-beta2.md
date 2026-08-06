### 💖 Support Our Work
* We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab). A huge thank you to all our current supporters!

## 🚀 What's New

### 🛠️ Bug Fixes & Stability
- **Memory & Crash Fix**: Fixed OutOfMemoryError crashes on high-DPI Android 16 devices by enabling `largeHeap` and trimming memory when UI is hidden.
- **Multilingual ANR Fix**: Prevented deadlock hangs during secondary dictionary lookups.
- **Clipboard & Threading**: Optimized thread pooling and scoped MediaStore observers to keyboard visibility.

### 🤖 AI Enhancements
- **Proofread Anti-Answering Guard**: Prevented models (Qwen, Llama) from turning questions into multi-paragraph essays during proofreading.
- **Clean Translation Parser**: Automatically strips section headers (`Translated text:`) and trailing reasoning blocks from translation outputs.

### 🌟 UI & Keyboard Improvements
- **Hardware Keyboard Mode**: Added setting to show only the toolbar when a physical keyboard is connected.
- **Toolbar Key Spacing**: Added equal key distribution for unscrollable expanded and dual toolbars.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.0.0-beta2-standardfull-debug.apk`** | **Recommended**. Cloud AI + Handwrite  | Internet | 
| **`1-LeanType_4.0.0-beta2-standard-debug.apk`** | **Fdroid Build**. Standard - Foss only | Internet |
| **`2-LeanType_4.0.0-beta2-offline-debug.apk`** | **Privacy Focused**. Offline AI | No Internet |
| **`3-LeanType_4.0.0-beta2-offlinelite-debug.apk`** | **Minimalist**. Pure FOSS. No AI Integration. | No Internet |
