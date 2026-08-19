# LeanType Features & Setup Guide

LeanType combines a lightweight, privacy-focused keyboard foundation with cutting-edge productivity tools: **Multi-Provider Cloud & Offline AI**, **On-Device Whisper Voice Typing**, **Handwriting Recognition**, **Dual-Engine In-Keyboard Translation**, **Rich Text Utilities**, and **Deep UI Customization**.

---

## 📑 Index

| Section | Description |
| :--- | :--- |
| 🆕 **[Summary of New Features](#-summary-of-new-features)** | Complete matrix of all features & settings locations |
| 🤖 **[Multi-Provider Cloud AI](#1-multi-provider-cloud-ai)** | Google Gemini, Groq, and OpenAI-compatible inference |
| 🧠 **[Custom AI Keys & Keywords](#2-custom-ai-keys--keywords)** | 10 custom toolbar prompt keys, personas, and themed capsules |
| 🛡️ **[Offline Neural Proofreading (GGUF)](#3-offline-neural-proofreading-gguf)** | 100% on-device private LLM execution via `llama.cpp` |
| 🌐 **[Dual-Engine In-Keyboard Translation](#4-dual-engine-in-keyboard-translation)** | AI Translation vs Google Translation Plugin with auto-fallback |
| 🎙️ **[On-Device Whisper Voice Typing](#5-on-device-whisper-voice-typing)** | Fast speech-to-text with quantized multilingual Whisper models |
| ✍️ **[Handwriting Input](#6-handwriting-input)** | Draw letters directly on a handwriting canvas (ML Kit) |
| 🧭 **[Dedicated Text Editing Panel](#7-dedicated-text-editing-panel)** | Gboard-style precision DPAD arrow navigation & selection mode |
| 📐 **[Smart Auto-Spanning Toolbar](#8-smart-auto-spanning-toolbar)** | Symmetrical dynamic toolbar key expansion across screen widths |
| 🖱️ **[Touchpad Mode & Gestures](#9-touchpad-mode--gestures)** | Spacebar swipe gesture & full-screen laptop-style touchpad |
| 🪟 **[Floating & Resizable Keyboard](#10-floating--resizable-keyboard)** | Draggable, resizable floating keyboard window |
| ⌨️ **[Dual Toolbar & Split Suggestions](#11-dual-toolbar--split-suggestions)** | Split toolbar actions and word suggestions into separate rows |
| 📝 **[Text Expander](#12-text-expander)** | Shortcut expansion with dynamic template placeholders |
| 📋 **[Searchable Clipboard, Editing & Gestures](#13-searchable-clipboard-editing--gestures)** | Real-time search, swipe-to-edit inline, swipe-to-delete undo, pinned folding, and sliding select |
| 📸 **[Screenshot Suggestions & Capture](#14-screenshot-suggestions--capture)** | Recent screenshot suggestion strip and clipboard storage |
| 🔎 **[Emoji Search](#15-emoji-search)** | Search for emojis by keyword with an Emoji Dictionary |
| 🚫 **[Blocked Words & Regex Blacklist](#16-blocked-words--regex-blacklist)** | Filter out offensive or unwanted words using custom regex patterns |
| ✉️ **[Privacy-First OTP Auto-Fill](#17-privacy-first-otp-auto-fill)** | Notification-based OTP extraction from messaging apps without SMS permissions |
| 📚 **[Adaptive Personal Dictionary Learning](#18-adaptive-personal-dictionary-learning)** | Customizable repeat learning thresholds & session word boosting |
| 👆 **[Gesture / Glide Typing](#19-gesture--glide-typing)** | Smooth swipe typing powered by native C++ library |
| ⌨️ **[Direct Switch Target IME](#20-direct-switch-target-ime)** | Switch directly to a specific target keyboard with keycode `-10076` |
| 🎨 **[Custom Layout Profiles](#21-custom-layout-profiles)** | Save up to 5 custom layout profiles with persistent slot tracking |
| 🔄 **[In-App Streaming Self-Updater](#22-in-app-streaming-self-updater)** | Direct GitHub release checks and streaming APK installer |
| 📦 **[Flavor Architecture & Privacy](#23-flavor-architecture--privacy)** | Breakdown of Standard Full, Standard FOSS, Offline, and Lite |

---

## 🆕 Summary of New Features

| Feature | Description | Settings Location |
| :--- | :--- | :--- |
| **Multi-Provider Cloud AI** | Proofread, rewrite, and fix grammar via Gemini, Groq, or OpenAI-compatible custom endpoints. | `AI Integration > Set AI Provider` |
| **Custom AI Keys** | 10 customizable toolbar keys with prompt templates, hashtags (`#editor`, `#proofread`), and tag capsules. | `AI Integration > Custom Keys` |
| **Offline Proofreading (GGUF)** | Zero-network, on-device neural proofreading powered by embedded `llama.cpp`. | `Advanced > GGUF Model (.gguf)` |
| **Dual-Engine Translation** | Translate selected text via Cloud AI or dedicated Translation Plugin with auto-fallback. | `AI Integration / Text correction > Translation method` |
| **Whisper Voice Typing** | On-device speech-to-text with quantized multilingual Whisper models and audio visualizer. | `Voice typing > Whisper Speech Models` |
| **Handwriting Recognition** | Draw characters on a dedicated canvas with independent language selection (Standard Full flavor). | `Libraries > Handwriting Input Plugin` |
| **Text Editing Panel** | Precision DPAD arrow navigation, Shift selection mode, and clipboard shortcuts. | Toolbar > Text Editing Icon |
| **Auto-Spanning Toolbar** | Dynamically expands and balances toolbar keys symmetrically across device widths. | `Appearance > Toolbar auto-spacing` |
| **Touchpad Mode** | Swipe up on Spacebar to activate full cursor control and laptop-style touchpad gestures. | `Gesture typing > Vertical spacebar swipe` |
| **Floating Keyboard** | Detach keyboard into a draggable, resizable window with persistent positioning. | Toolbar > Floating Keyboard |
| **Split Toolbar & Suggestions** | Separates suggestions from the toolbar into a dual-row view. | `Appearance > Split toolbar & suggestions` |
| **Text Expander** | Expand custom shortcuts using dynamic placeholders (`%date%`, `%time%`, `%clipboard%`, `%cursor%`). | `Text correction > Text Expander` |
| **Clipboard History & Inline Edit** | Search history, swipe-right to edit inline, swipe-left to delete with undo, fold pinned clips, and slide-select. | Clipboard Toolbar > Search / Swipe items |
| **Screenshot Suggestions** | Instant 1-tap sharing of recently taken screenshots via the suggestion strip. | `Text correction > Suggest recent screenshots` |
| **Emoji Search** | Search emojis by name/keyword directly from the emoji palette. | `Emoji Key > Search Icon` |
| **Blocked Words Blacklist** | Prevent unwanted words from being suggested with regex pattern matching. | `Text correction > Blocked words blacklist` |
| **Privacy-First OTP Auto-Fill** | Extracts OTP verification codes from incoming notifications with app package selector. | `Text correction > OTP Auto-Fill` |
| **Smart Learning & Boost** | Adjustable personal dictionary learning threshold (1-5 times) and temporary session word boost. | `Text correction > Dictionary learning threshold` |
| **Gesture Typing** | Swipe typing powered by native C++ spatial scoring engine. | `Gesture typing > Enable gesture typing` |
| **Direct Switch Target IME** | Fast 1-tap switching to another configured IME using custom keycode `-10076`. | `Preferences > Direct Switch Target IME` |
| **Custom Layout Profiles** | Store up to 5 custom keyboard layouts with persistent slot tracking. | `Languages > Custom layouts` |
| **In-App Self-Updater** | Checks GitHub releases and streams updates directly (`standardfull` flavor). | `About > Check for updates` |

---

## 1. Multi-Provider Cloud AI

LeanType connects directly with top AI providers for ultra-fast proofreading, grammar corrections, tone adjustments, and rewrites.

### Supported Providers

| Provider | Privacy Level | Setup Speed | Free Tier | Best For |
| :--- | :---: | :---: | :---: | :--- |
| **Groq** | 🟡 Average | 🟢 Fast | High RPM | **Lightning-fast inference speeds** |
| **Google Gemini** | 🔴 Standard | 🟢 Fast | Generous | **High-quality general reasoning** |
| **OpenAI-Compatible** | ⚙️ *Custom* | 🟡 Moderate | *Custom* | **Any custom endpoint (OpenRouter, DeepSeek, Mistral)** |

### Setup Instructions
1. Obtain an API key:
   - **Google Gemini**: [Google AI Studio](https://aistudio.google.com/apikey) (key starts with `AIzaSy...`).
   - **Groq**: [Groq Console](https://console.groq.com/keys) (key starts with `gsk_...`).
   - **OpenAI-compatible**: [OpenRouter](https://openrouter.ai/keys), [DeepSeek Platform](https://platform.deepseek.com), or your local LLM server.
2. In LeanType, open **Settings → AI Integration → Set AI Provider**.
3. Select your provider, paste your API token, and pick your preferred model and target language.

---

## 2. Custom AI Keys & Keywords

You can assign custom prompts, personas, and custom label tags to **10 dedicated toolbar keys**.

### Custom Text Capsules
- Assign custom labels (e.g. `French`, `Rephrase`, `Reply`) in **Settings → AI Integration → Custom Keys**.
- Enable **Show tags on keyboard** to render them as themed pill capsules directly on the keyboard toolbar.

### AI Persona Keywords (Hashtags)
Include these hashtags in your custom prompts to enforce strict system roles:

| Keyword | Persona / Role | System Instruction Injected |
| :--- | :--- | :--- |
| `#editor` | **Text Editor** | "Output ONLY the edited text. Do not add any conversational filler." |
| `#outputonly` | **Strict Output** | "Output ONLY the result. Do not add introductions or explanations." |
| `#proofread` | **Proofreader** | "Fix grammar and spelling errors. Output ONLY the fixed text." |
| `#paraphrase` | **Rewriter** | "Rewrite using different words while preserving original meaning." |
| `#summarize` | **Summarizer** | "Provide a concise, direct summary." |
| `#expand` | **Content Writer** | "Expand on the text with more details." |
| `#toneshift` | **Tone Adjuster** | "Adjust the tone as requested." |
| `#append` | **Append Mode** | Adds output to the end of the text field instead of replacing. |
| `#showthought` | **Show Thinking** | Preserves reasoning output (`<think>...</think>`) from reasoning models. |

---

## 3. Offline Neural Proofreading (GGUF)

> [!IMPORTANT]
> **Zero-Network Guarantee**: This feature runs 100% locally via embedded `llama.cpp` and is available in the **Offline** build flavor (`-offline-release.apk`). No internet permission exists in the manifest.

### Setup Instructions
1. Download a compact GGUF model:
   - **Qwen 2.5 0.5B Instruct (Q4_K_M)**: Extremely lightweight & fast (~350 MB).
   - **Llama 3.2 1B Instruct (Q4_K_M)**: High-quality compact reasoning (~900 MB).
   - **Qwen 2.5 1.5B Instruct (Q4_K_M)**: High intelligence for modern devices (~1.1 GB).
2. Open **Settings → Advanced → GGUF Model (.gguf)** and select the `.gguf` file from your storage.
3. Configure sampling temperature, Top-K, Top-P, and custom system instructions.

---

## 4. Dual-Engine In-Keyboard Translation

LeanType offers a flexible translation architecture allowing you to toggle between:
1. **AI Provider Translation**: Uses Gemini, Groq, OpenAI, or local GGUF models with customizable prompts.
2. **Translation Plugin (Google / ML Kit)**: Instant, on-device translation engine powered by the [LeanType Translation Plugin](https://github.com/LeanBitLab/LeanType-Translation-Plugin).
3. **Auto Mode**: Prefers the fast Translation Plugin, with seamless automatic fallback to your configured AI provider.

### How to Setup
1. In LeanType, open **Settings → Text correction / AI Integration → Translation method**.
2. Select **Auto**, **Translation Plugin**, or **AI Provider**.
3. If using the plugin, tap **Download Plugin** to install the companion APK.
4. Tap the **Translate** icon on the toolbar to translate selected text or entire input fields.

---

## 5. On-Device Whisper Voice Typing

LeanType integrates high-accuracy, private speech-to-text powered by OpenAI's Whisper architecture via `whisper.cpp` and the [LeanType Voice Plugin](https://github.com/LeanBitLab/Leantype-Voice-Plugin).

### Available Multilingual Whisper Models
- **Tiny** (`ggml-tiny-q5_1.bin`): **~32 MB** — Ultra-fast, minimal memory usage, 99+ languages.
- **Base** (`ggml-base-q5_1.bin`): **~57 MB** — Best balance of accuracy and speed for daily typing.
- **Small** (`ggml-small-q5_1.bin`): **~182 MB** — High accuracy for complex vocabulary and accents.
- **Custom Model**: Import any standard `.bin` GGML Whisper model from device storage.

### Setup Instructions
1. Install the companion [LeanType Voice Plugin](https://github.com/LeanBitLab/Leantype-Voice-Plugin/releases/latest).
2. Open **Settings → Voice typing → Whisper Speech Models**.
3. Tap **Download** on your preferred model (e.g. *Multilingual Base* ~57 MB).
4. Configure voice options:
   - **Voice Recognition Language**: Choose **Follow keyboard language (Default)**, **Auto-detect spoken language (`auto`)**, or pick from 99+ specific Whisper languages.
   - **Audio Visualizer**: Displays a real-time sound waveform directly on the keyboard toolbar.
   - **Silence Detection**: Configurable auto-stop sensitivity slider.
   - **Keep Model in Memory**: Prevents model reload latency during consecutive voice typing sessions.
5. Tap the **Microphone** icon on the toolbar to start voice typing.

---

## 6. Handwriting Input

> [!NOTE]
> Available in the **Standard Full** (`-standardfull-release.apk`) build flavor.

Draw letters, words, or symbols directly on a handwriting recognition canvas using your finger or stylus.

### Setup Instructions
1. Open **Settings → Libraries → Handwriting Input Plugin**.
2. Tap **Download** to install the companion [LeanType Handwriting Plugin](https://github.com/LeanBitLab/Leantype-Handwriting-Plugin).
3. Select your preferred **Handwriting recognition language** (e.g. English, Chinese, Devanagari, Japanese, etc.), independent of your active keyboard typing language.
4. Tap the **Handwriting (Pencil)** icon on the keyboard toolbar to open the drawing canvas.
5. Draw characters naturally—the handwriting engine transcribes strokes into text in real-time.

---

## 7. Dedicated Text Editing Panel

A Gboard-style precision editing panel designed for frictionless text manipulation:
- **DPAD Arrow Keys**: Move cursor character-by-character or line-by-line.
- **Selection Mode (Shift + DPAD)**: Highlight text with precision.
- **Quick Selection**: 1-tap **Select Word** and **Select All**.
- **Clipboard Actions**: Direct Cut, Copy, and Paste buttons within the panel.
- **Line Navigation**: Jump directly to Start of Line or End of Line.

---

## 8. Smart Auto-Spanning Toolbar

The **Auto-Spanning Toolbar** dynamically measures available screen width and proportionately balances toolbar keys symmetrically.
- Eliminates awkward blank space on large screens, tablets, and landscape orientation.
- Unifies alignment between standard toolbar keys and clipboard action rows.
- Configure via **Settings → Appearance → Toolbar auto-spacing**.

---

## 9. Touchpad Mode & Gestures

Turn the entire keyboard space into a fluid laptop-style trackpad:
- **Activate via Swipe**: Swipe up on the **Spacebar** to toggle Touchpad Mode.
- **Activate via Toolbar**: Tap the **Touchpad** icon in the toolbar.

### Trackpad Gestures
- **1-Finger Drag**: Smooth, pixel-perfect cursor movement.
- **1-Finger Double Tap**: Selects the word under the cursor.
- **1-Finger Long Press & Drag**: Starts continuous text selection.
- **2-Finger Drag Left/Right**: Jumps word-by-word.
- **2-Finger Swipe Up / Down**: Undo / Redo.
- **2-Finger Tap**: Inserts a space.
- **2-Finger Double Tap**: Copies selected text (or Pastes if nothing is selected).
- **2-Finger Long Press**: Continuous backspace deletion.

---

## 10. Floating & Resizable Keyboard

Detach LeanType into a moveable, resizable floating window:
- Tap the **Floating Keyboard** icon on the toolbar.
- Drag the bottom handle to reposition anywhere on the screen.
- Drag corner handles to resize.
- Enable **Persistent Floating Mode** to keep the keyboard floating across app switches.

---

## 11. Dual Toolbar & Split Suggestions

Split your toolbar and suggestion strip into two independent rows for fast, unhindered access to both word predictions and quick actions.
- Configure via **Settings → Appearance → Split toolbar & suggestions**.

---

## 12. Text Expander

Define custom abbreviations that instantly expand into rich text templates with dynamic variables:

### Supported Dynamic Placeholders
- `%date%`: Inserts current date (YYYY-MM-DD).
- `%time%`: Inserts current local time (HH:MM).
- `%tomorrow%`: Inserts tomorrow's date.
- `%clipboard%`: Inserts latest copied clipboard content.
- `%cursor%`: Places typing cursor at this exact position after expansion.
- `%greeting%`: Inserts time-appropriate greeting (*Good morning*, *Good afternoon*, *Good evening*).
- `%bullets%` / `%list%`: Inserts templated bulleted or numbered lists.
- `%custom_variable%`: Prompts an interactive popup to fill in custom text on the fly.

### Setup Instructions
1. Open **Settings → Text correction → Text Expander**.
2. Tap **+ (Add)**, define the shortcut (e.g. `brb`), and enter your expansion template.

---

## 13. Searchable Clipboard, Editing & Gestures

LeanType features a comprehensive, privacy-first clipboard manager with rich gestural editing:

- **🔍 Real-Time Search**: Filter through your entire clipboard history instantly using the inline search bar on the toolbar.
- **✏️ Swipe-Right Inline Editing**: Swipe right on any clipboard snippet to edit its text directly inside the keyboard toolbar (`[Text│] [✔] [✕]`):
  - **Tap-to-Position Cursor**: Tap anywhere in the text strip to place the cursor accurately.
  - **Gesture Support in Edit Buffer**: Swipe on the spacebar to glide the cursor horizontally, or swipe left from Backspace to delete words in the edit strip.
  - **In-Place Layout Switching**: Toggle `?123` Symbols, `Shift`, and Caps Lock directly on the bottom row without losing your active edit session.
- **🗑️ Swipe-Left to Delete with Undo**: Swipe left on any clip to remove it, backed by a 5-second timed undo bar to restore accidental deletions.
- **📌 Pin / Unpin & Folding**: Long-press any snippet to pin it permanently. Enable **Fold pinned items** to keep pinned clips collapsed under an expandable `▶ Pinned (N)` header.
- **👆 Sliding Clipboard Selection**: Hold the Clipboard key, slide your finger over the desired clip, and release to paste and return to typing immediately.
- **🖼️ Image & Screenshot History**: Captures and displays copied images and screenshots with rich visual thumbnails.

---

## 14. Screenshot Suggestions & Capture

- **Instant Suggestion**: Automatically detects newly captured screenshots (within 4 minutes) and presents a thumbnail preview in the suggestion strip for 1-tap insertion.
- **Clipboard Sync**: Automatically saves captured screenshots into your clipboard image history.
- Enable via **Settings → Text correction → Suggest recent screenshots**.

---

## 15. Emoji Search

- Search through thousands of emojis by keyword or name directly inside the emoji palette.
- **Setup**: Ensure an **Emoji Dictionary** (e.g. *Emoji English*) is enabled under **Settings → Text correction → Dictionary**.

---

## 16. Blocked Words & Regex Blacklist

Prevent offensive, sensitive, or unwanted words from ever appearing in the suggestion strip:
- Supports literal words and custom **regular expression (regex)** patterns.
- Manage rules via **Settings → Text correction → Blocked words blacklist**.

---

## 17. Privacy-First OTP Auto-Fill

- **Zero SMS Permissions (`RECEIVE_SMS`)**: Uses Android's secure `NotificationListenerService` to parse verification codes directly from incoming notifications without accessing private SMS message stores.
- **Dynamic Messaging App Selector**: Choose which specific messaging apps (Google Messages, Signal, WhatsApp, Telegram, etc.) LeanType should monitor for OTP codes.
- **1-Tap Insertion**: Automatically detects OTP codes and offers them in the suggestion strip for instant 1-tap pasting.
- Manage via **Settings → Text correction → OTP Auto-Fill**.

---

## 18. Adaptive Personal Dictionary Learning

LeanType learns your vocabulary organically as you type:
- **Adjustable Learning Threshold**: Choose how many times a new word must be typed (1 to 5 times) before it is automatically added to your personal dictionary.
- **Session Word Boost**: Temporarily boosts recently typed, verified words for immediate next-word ranking during active typing sessions.
- **Google Dictionary Import**: Import existing user dictionaries exported from Gboard.
- Configure via **Settings → Text correction → Dictionary learning threshold**.

---

## 19. Gesture / Glide Typing

- Smooth swipe typing powered by native C++ spatial scoring (`libjni_latinime.so`).
- Supports floating preview text, customizable trail colors, and space-aware gesture input.
- In `standard` and `standardfull` builds, the gesture library is downloaded automatically via **Settings → Gesture typing**.

---

## 20. Direct Switch Target IME

Map the custom keycode `-10076` (`SWITCH_TO_USER_IME`) to any toolbar key:
- Switches directly to a designated secondary input method (e.g. Japanese, Korean, or Voice IME) without opening the system IME selection dialog.
- Configure via **Settings → Preferences → Direct Switch Target IME**.

---

## 21. Custom Layout Profiles

- Create and save up to **5 persistent custom layout profiles**.
- Switch between layout profiles seamlessly while preserving active slot indices across orientation and symbol states.
- Manage via **Settings → Languages → Custom layouts**.

---

## 22. In-App Streaming Self-Updater

> [!NOTE]
> Available in the **Standard Full** (`-standardfull-release.apk`) build flavor.

- Automatically checks GitHub releases for updates in the background.
- Streams and installs updates directly without requiring third-party app stores.
- View single-version changelogs directly inside the update dialog.
- Configure check frequency under **Settings → About → Check for updates**.

---

## 23. Flavor Architecture & Privacy

LeanType is published in **4 purpose-built flavors**:

| Flavor | Cloud AI | Offline AI | Voice Input | Handwriting | In-App Updates | Internet Permission | Min SDK | Approx Size |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Standard Full** | ✅ | ❌ | ✅ *(Plugin)* | ✅ *(Plugin)* | ✅ | 🌐 Required *(Opt-in)* | SDK 23 (6.0+) | **~23 MB** |
| **Standard (FOSS)** | ✅ | ❌ | ✅ *(Plugin)* | ❌ | ❌ | 🌐 Required *(Opt-in)* | SDK 23 (6.0+) | **~11 MB** |
| **Offline AI** | ❌ | ✅ *(GGUF)* | ✅ *(Plugin)* | ❌ | ❌ | 🚫 **None** | SDK 26 (8.0+) | **~67 MB** |
| **Offline Lite** | ❌ | ❌ | ✅ *(Plugin)* | ❌ | ❌ | 🚫 **None** | SDK 21 (5.0+) | **~26 MB** |

> [!TIP]
> **Concurrent Installation**: The `offline` (`com.leanbitlab.leantype.offline`) and `offlinelite` (`com.leanbitlab.leantype.offlinelite`) builds use unique package IDs, allowing you to install them alongside `standardfull` on the same device!



