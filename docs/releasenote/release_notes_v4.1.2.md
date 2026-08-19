### 💖 Support Our Work
As an open-source, community-funded project, we operate on a very limited budget and have little time for marketing. If LeanType helps you daily, please consider becoming a sponsor on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leantype). Even if you can't contribute financially, sharing LeanType with your friends, family, or on social media makes a world of difference to help our project grow. Thank you for your support!

---

## 🚀 What's New in v4.1.2

### 🎙️ Offline Voice Recognition Engine Overhaul (Whisper AI)
* **Distil-Whisper & Whisper.cpp Migration**: Completely replaced legacy Vosk with OpenAI's Whisper model architecture running via `whisper.cpp` for state-of-the-art speech-to-text accuracy.
* **Dedicated Voice Recognition Language Selector**: Choose between **🌐 Follow keyboard language (Default)**, **🪄 Auto-detect spoken language (`auto`)**, or select from **99+ Whisper languages** localized with their native display names.
* **Compact Q5_1 Quantized Models**: Added optimized Q5_1 quantized models for Distil-Small.en, Multilingual Tiny, Base, and Small models (~31MB to 160MB).
* **In-Toolbar Voice UI & Waveform Visualizer**: Replaced overlays with a sleek, non-intrusive in-toolbar indicator featuring real-time audio visualizer waveforms and an immediate stop button.
* **Zero-Allocation Audio DSP Pipeline**: Added real-time IIR bandpass filtering (80Hz–7500Hz) and soft-knee dynamics limiter for crystal-clear voice capture.
* **Smart Spoken Punctuation & Voice Commands**: Automatic capitalization, auto-punctuation, and hands-free voice commands (*"comma"*, *"period"*, *"question mark"*, *"new line"*, *"clear"*).
* **In-App Speech Model Manager**: Download, import local `.bin` models, or remove models directly within Settings → Voice.
* **Configurable Silence Timeout & RAM Caching**: Choose 3s, 5s, 8s, or 15s silence timeouts, with an option to keep Whisper models cached in RAM for instant dictation.

---

### 🔄 In-App Updates & Community Hub
* **In-App Streaming Self-Updater**: Check and download the latest LeanType GitHub releases directly within the app (standardfull builds).
* **Collapsible What's New Changelog**: View recent release notes and version changes on the new Updates screen.
* **Community & Social Links**: Quick access to official LeanType channels across Telegram, Reddit, GitHub, X/Twitter, Matrix, and Discord.
* **Sponsorship & Open Collective Integration**: Integrated support for Open Collective alongside GitHub Sponsors.

---

### 🧠 Smarter Predictions & Personal Dictionary Learning
* **Custom Auto-Learn Frequency Threshold**: Fine-tune how many times a new word must be typed before it is automatically added to your personal dictionary.
* **Continuous Next-Word Prediction Backoff**: Implemented N-gram prediction backoff and defensive cache copying so next-word suggestions remain fluid and continuous.
* **Unblocked Auto-Learning**: Fixed dictionary learning filters so custom vocabulary is reliably learned from typed history.

---

### 🎨 Toolbar, Clipboard & Theming Enhancements
* **Unified Toolbar & Clipboard Alignment**: Configure key alignment for both the main toolbar and clipboard view (*Start, Center, End, Auto-Span*).
* **Theme Key Borders Isolation**: Turning off key borders now strictly affects keyboard letter keys, keeping spacebar accents and rounded clipboard cards intact.
* **Floating Mode Overlay**: Restored `SYSTEM_ALERT_WINDOW` permission for uninterrupted floating keyboard windowing.
* **Debug Dictionary Dump Toast**: Added instant Toast visual feedback when triggering dynamic dictionary dumps in Debug Settings.

---

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_4.1.2-standardfull-release.apk`** | **Recommended**. Cloud AI + Handwriting + In-App Updater | Internet | 
| **`1-LeanType_4.1.2-standard-release.apk`** | **F-Droid Build**. Standard - FOSS Only | Internet |
| **`2-LeanType_4.1.2-offline-release.apk`** | **Privacy Focused**. Offline AI (Local Models) | No Internet |
| **`3-LeanType_4.1.2-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. Zero AI integrations. | No Internet |
