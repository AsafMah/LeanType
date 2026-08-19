# LeanType

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/leantype_banner_dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="docs/images/leantype_banner_light.svg">
  <img alt="LeanType Banner" src="docs/images/leantype_banner_light.svg">
</picture>

<div align="center">

[![Latest Release](https://img.shields.io/github/v/release/LeanBitLab/HeliboardL?style=flat-square&color=6366f1&label=Release)](https://github.com/LeanBitLab/HeliboardL/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/LeanBitLab/HeliboardL/total?style=flat-square&color=10b981&label=Downloads)](https://github.com/LeanBitLab/HeliboardL/releases)
[![Stars](https://img.shields.io/github/stars/LeanBitLab/HeliboardL?style=flat-square&color=f59e0b&label=Stars)](https://github.com/LeanBitLab/HeliboardL/stargazers)
[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg?style=flat-square)](https://www.gnu.org/licenses/gpl-3.0)
[![Sponsor](https://img.shields.io/badge/Sponsor-LeanBitLab-ff69b4?style=flat-square&logo=github)](https://github.com/sponsors/LeanBitLab)

**A private, smart, and deeply customizable open-source Android keyboard.**  
*Forked from [HeliBoard](https://github.com/Helium314/HeliBoard) / OpenBoard / AOSP LatinIME.*

[Download APKs](#-download) • [Flavor Comparison](#-flavor-comparison) • [Features](#-features) • [Setup Guide](#-setup-guide) • [Ecosystem](#-ecosystem--plugins)

</div>

---

## 🚀 Overview

**LeanType** combines the trusted, lightweight, privacy-focused foundation of HeliBoard with modern productivity features: **Multi-Provider Cloud & Offline AI proofreading**, **On-Device Whisper Voice Typing**, **Handwriting Recognition**, **Smart Toolbar Auto-Spanning**, **Built-in Self-Updater**, and **Rich Text Tools**—while keeping you in complete control over your data.

---

## 📦 Flavor Comparison

LeanType is available in **4 distinct flavors** designed to match your exact privacy preferences, hardware specifications, and feature requirements:

| Feature / Capability | 🌟 Standard Full<br>`-standardfull-release.apk` | 🌿 Standard (FOSS)<br>`-standard-release.apk` | 🛡️ Offline AI<br>`-offline-release.apk` | ⚡ Offline Lite<br>`-offlinelite-release.apk` |
| :--- | :---: | :---: | :---: | :---: |
| **Target Audience** | **Recommended** for full feature set | F-Droid / 100% Pure FOSS users | Privacy purists wanting **Local AI** | Minimalists wanting **Zero AI** |
| **Cloud AI** *(Gemini, Groq, OpenAI)* | ✅ Yes | ✅ Yes | ❌ No | ❌ No |
| **Offline AI** *(Local GGUF via llama.cpp)* | ❌ No | ❌ No | ✅ **Yes** | ❌ No |
| **Voice Typing** *(On-device Whisper)* | ✅ **Yes** *(via plugin)* | ✅ **Yes** *(via plugin)* | ✅ **Yes** *(via plugin)* | ✅ **Yes** *(via plugin)* |
| **Handwriting Input** *(ML Kit)* | ✅ **Yes** *(via plugin)* | ❌ No *(Proprietary-free)* | ❌ No | ❌ No |
| **In-App Self-Updater** | ✅ **Yes** *(GitHub Releases)* | ❌ No *(F-Droid managed)* | ❌ No | ❌ No |
| **Dynamic Downloader** | ✅ Dictionaries & Models | ✅ Dictionaries | ❌ Manual loading only | ❌ Manual loading only |
| **Internet Permission** | 🌐 Required *(Opt-in features)* | 🌐 Required *(Opt-in features)* | 🚫 **None** *(OS-level blocked)* | 🚫 **None** *(OS-level blocked)* |
| **Package ID** | `com.leanbitlab.leantype` | `com.leanbitlab.leantype` | `com.leanbitlab.leantype.offline` | `com.leanbitlab.leantype.offlinelite` |
| **Min Android Version** | Android 6.0+ *(SDK 23)* | Android 6.0+ *(SDK 23)* | Android 8.0+ *(SDK 26)* | Android 5.0+ *(SDK 21)* |
| **Approximate APK Size** | **~23 MB** | **~11 MB** | **~67 MB** | **~26 MB** |

> [!TIP]
> **Side-by-Side Installation**: The `offline` and `offlinelite` flavors use distinct application package IDs, allowing them to be installed **concurrently** with `standardfull` on the same device without conflicts!

---

## ✨ Features

### 🤖 AI Integration & Smart Tools
- **Multi-Provider Cloud AI**: Integrated proofreading, grammar correction, and text rewriting powered by **Google Gemini**, **Groq** (Llama 3.3, Mixtral, DeepSeek), or any **OpenAI-compatible** custom endpoint.
- **Dynamic Model Fetching**: Automatically fetches and populates the latest available model IDs directly from your provider.
- **🛡️ Offline Neural Proofreading (GGUF)**: Run compact, quantized GGUF language models directly on your device via embedded `llama.cpp`—100% private, zero network access (`offline` flavor).
- **🌐 AI Translation**: Select text and translate it instantly to any target language with dedicated model selection.
- **🧠 Custom AI Keys & Capsules**: Assign custom prompts, personas (`#editor`, `#proofread`), and themed tag capsules to 10 customizable toolbar keys.

### 🎙️ Voice & Handwriting Input
- **On-Device Whisper Voice Typing**: High-accuracy speech recognition powered by compact quantized **Distil-Whisper models** via the [LeanType Voice Plugin](https://github.com/LeanBitLab/Leantype-Voice-Plugin).
- **Interactive Voice Toolbar**: Real-time waveform audio visualizer, silence detection sensitivity slider, and background keep-alive options.
- **✍️ Handwriting Recognition**: Draw characters or words directly on an expansive writing canvas using the [LeanType Handwriting Plugin](https://github.com/LeanBitLab/Leantype-Handwriting-Plugin) (`standardfull` flavor).

### ⌨️ Layouts, Navigation & Typing
- **👆 Gesture / Glide Typing**: Smooth swipe typing powered by native C++ libraries (`libjni_latinime.so`).
- **📐 Smart Auto-Spanning Toolbar**: Dynamically expands and balances toolbar keys symmetrically to prevent awkward gaps across portrait, landscape, and tablet widths.
- **🧭 Dedicated Text Editing Panel**: Gboard-style precision DPAD arrow navigation, selection mode (Shift + arrows), select word, select all, and editing shortcuts.
- **🖱️ Touchpad Mode**: Swipe up on the spacebar to control the cursor freely across the screen, including full-screen laptop-style touchpad mode.
- **🪟 Floating & Resizable Keyboard**: Detach into a moveable floating window with persistent positioning for multitasking.
- **⌨️ Dual Toolbar / Split Suggestions**: Option to split suggestions from the quick-action toolbar.
- **🎨 Custom Layout Profiles**: Save up to 5 custom layout profiles with persistent slot index tracking.
- **⌨️ Direct Switch Target IME**: Bind keycode `-10076` to any toolbar key to switch directly to a specific target keyboard (e.g. Japanese, Korean, or Chinese IME).

### 📋 Clipboard & Productivity
- **🔍 Searchable Clipboard History**: Search through copied snippets in real-time, fold pinned items by default, and enjoy timed swipe-to-delete undo protection.
- **📸 Screenshot Suggestions**: Detects recently taken screenshots and offers instant 1-tap sharing via the suggestion strip or clipboard history.
- **📝 Text Expander**: Built-in shortcut expansion with dynamic variables (`%date%`, `%time%`, `%clipboard%`, `%cursor%`, custom placeholders).
- **✉️ Auto-Read OTP**: Automatically extracts and suggests one-time verification codes from incoming SMS notifications.
- **📚 Smart Learning & Session Boost**: Adaptive personal dictionary learning threshold (1 to 5 times) and dynamic session word boosting.
- **🚫 Blacklist & Regex Filtering**: Filter offensive words or unwanted suggestions with custom regex pattern support.
- **🔄 Google Dictionary Import**: Seamlessly import personal dictionary words exported from Gboard.
- **🔄 In-App Streaming Updater**: Direct GitHub release checks and streaming APK self-updating with single-version changelogs (`standardfull` flavor).

---

## 📥 Download

<table border="0">
  <tr>
    <td align="center" valign="middle">
      <a href="https://github.com/LeanBitLab/HeliboardL/releases/latest">
        <img alt="Get it on GitHub" src="docs/images/get-it-on-github.png" height="80">
      </a>
    </td>
    <td align="center" valign="middle">
      <a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/LeanBitLab/HeliboardL">
        <img alt="Get it on Obtainium" src="docs/images/get-it-on-obtainium.png" height="55">
      </a>
    </td>
    <td align="center" valign="middle">
      <a href="https://f-droid.org/en/packages/com.leanbitlab.leantype/index.html">
        <img alt="Get it on F-Droid" src="docs/images/get-it-on-fdroid.png" height="80">
      </a>
    </td>
  </tr>
</table>

---

## 📸 Screenshots

<table>
  <tr>
    <td><img src="docs/images/1.png" width="180" alt="Keyboard Main View"/></td>
    <td><img src="docs/images/2.png" width="180" alt="AI Proofreading"/></td>
    <td><img src="docs/images/3.png" width="180" alt="Clipboard Search"/></td>
    <td><img src="docs/images/4.png" width="180" alt="Text Editing Panel"/></td>
    <td><img src="docs/images/5.png" width="180" alt="Settings Screen"/></td>
    <td><img src="docs/images/6.png" width="180" alt="Floating Keyboard"/></td>
  </tr>
</table>

---

## 🛠️ Setup Guide

### 1. Cloud AI Setup (Gemini / Groq / OpenAI)
1. Obtain an API key from [Google AI Studio](https://aistudio.google.com/apikey) or [Groq Console](https://console.groq.com/keys).
2. Open **Settings → AI Integration → Set AI Provider**.
3. Select your provider, paste your API token, and pick your preferred model and target language.
4. 👉 **[Read the Full AI & Prompts Guide](docs/FEATURES.md)**

### 2. Voice Input Setup (Whisper AI)
1. Install the companion [LeanType Voice Plugin](https://github.com/LeanBitLab/Leantype-Voice-Plugin/releases/latest).
2. Open **Settings → Voice typing → Whisper Speech Models**.
3. Download or import your preferred Distil-Whisper model (e.g. *Distil-Whisper Small English* ~35 MB).
4. Tap the microphone icon on the keyboard toolbar to start typing with your voice!

### 3. Gesture Typing Setup
1. In the `standard` and `standardfull` builds, open **Settings → Gesture typing** to download the gesture library automatically.
2. In `offline` builds, [download the library manually](https://github.com/erkserkserks/openboard/tree/46fdf2b550035ca69299ce312fa158e7ade36967/app/src/main/jniLibs) and load it via *Settings → Gesture typing → Load gesture library*.

### 4. Offline AI Setup (GGUF Models)
1. Download a compatible GGUF model (such as `Qwen2.5-0.5B-Instruct-Q4_K_M.gguf` or `Llama-3.2-1B-Instruct-Q4_K_M.gguf`).
2. In LeanType (`offline` build), navigate to **Settings → Advanced → GGUF Model (.gguf)** and select the file from your local storage.

---

## 🧩 Ecosystem & Plugins

Expand LeanType with official companion plugins:

| Plugin | Repository | Description |
| :--- | :--- | :--- |
| 🎙️ **Voice Plugin** | [LeanBitLab/Leantype-Voice-Plugin](https://github.com/LeanBitLab/Leantype-Voice-Plugin) | On-device Distil-Whisper speech-to-text engine |
| ✍️ **Handwriting Plugin** | [LeanBitLab/Leantype-Handwriting-Plugin](https://github.com/LeanBitLab/Leantype-Handwriting-Plugin) | ML Kit Digital Ink canvas recognition engine |
| 🎨 **Community Themes** | [GitHub: `leantype-theme`](https://github.com/topics/leantype-theme) | Browse and share custom color themes |

---

## 🤝 Community & Contributing

- **Bug Reports & Feature Requests**: [Open a GitHub Issue](https://github.com/LeanBitLab/HeliboardL/issues)
- **Discussion & Support**: [GitHub Discussions](https://github.com/LeanBitLab/HeliboardL/discussions)
- **Official Telegram Channel**: [@LeanBitLab](https://t.me/leanbitlab)
- **Theme Creators**: Tag your repository with `leantype-theme` to appear in our theme catalog.

---

## 💖 Support the Project

Building and maintaining privacy-first, on-device AI and keyboard technologies requires continuous hardware testing, compute for model optimization, and development time.

If LeanType improves your daily typing workflow, please consider sponsoring our work!

<div align="left">
  <a href="https://github.com/sponsors/LeanBitLab">
    <img src="https://img.shields.io/static/v1?label=Sponsor%20on%20GitHub&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86" height="38" alt="Sponsor LeanBitLab"/>
  </a>
</div>

---

## 📜 Credits & Acknowledgments

- **[HeliBoard](https://github.com/Helium314/HeliBoard)** by Helium314 — the foundational keyboard project
- **[OpenBoard](https://github.com/openboard-team/openboard)** & **[AOSP LatinIME](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)**
- **[llama.cpp](https://github.com/ggerganov/llama.cpp)** & **[llamacpp-kotlin](https://github.com/ljcamargo/llamacpp-kotlin)** — on-device local LLM execution
- **[whisper.cpp](https://github.com/ggerganov/whisper.cpp)** & **[Distil-Whisper](https://github.com/huggingface/distil-whisper)** — on-device speech recognition
- All [contributors](https://github.com/LeanBitLab/HeliboardL/graphs/contributors) and open-source supporters!

---

## ⚖️ License

LeanType is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.  
See the [LICENSE](LICENSE) file for details.
