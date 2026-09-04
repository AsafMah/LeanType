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
| 📷 **[Offline Camera OCR & Screenshot Extraction](#24-offline-camera-ocr--screenshot-extraction)** | In-keyboard camera viewfinder, automated screenshot extraction pill, and advanced formatting cleaners |
| 🔢 **[Inline Math Calculation Suggestions](#25-inline-math-calculation-suggestions)** | High-precision arithmetic expression evaluator on typing `=` with 1-tap replacement |
| 🎵 **[Custom Sound Packs & Audio Customization](#26-custom-sound-packs--audio-customization)** | Zero-latency key audio engine, 12+ built-in presets, remote repository catalog, and `.zip` imports |

---

## 🆕 Summary of New Features

| Feature | Description | Settings Location |
| :--- | :--- | :--- |
| **Multi-Provider Cloud AI** | Proofread, rewrite, and fix grammar via Gemini, Groq, or OpenAI-compatible custom endpoints. | `AI Integration > Set AI Provider` |
| **Custom AI Keys** | 10 customizable toolbar keys with prompt templates, hashtags (`#editor`, `#proofread`), and tag capsules. | `AI Integration > Custom Keys` |
| **Offline Proofreading (GGUF)** | Zero-network, on-device neural proofreading powered by embedded `llama.cpp`. | `Advanced > GGUF Model (.gguf)` |
| **Multi-Mode In-Keyboard Translation** | Translate text on-device (Offline ML Kit), via Translation Plugin, or Cloud/Local AI with auto-fallback. | `Translation > Translation Mode` |
| **Whisper Voice Typing** | On-device speech-to-text with quantized multilingual Whisper models and audio visualizer. | `Voice typing > Whisper Speech Models` |
| **Handwriting Recognition** | Draw characters on a dedicated canvas with in-app model manager (Standard Full flavor). | `Handwriting > Handwriting recognition` |
| **Offline Camera & Screenshot OCR** | Live in-keyboard camera scanner and screenshot suggestion pill with rich text cleaners (casing, join styles, dehyphenation). | `OCR & Text Extraction` / `Plugins > OCR` |
| **Inline Math Calculation** | Instant arithmetic calculation suggestions on typing `=` with 1-tap expression replacement. | `Text correction > Inline math calculation` |
| **Custom Sound Packs** | Zero-latency key audio engine with 12+ built-in presets, remote catalog downloads, and `.zip` import. | `Plugins > Sound` / `Preferences > Sound on keypress` |
| **Text Editing Panel** | Precision DPAD arrow navigation, Shift selection mode, and clipboard shortcuts. | Toolbar > Text Editing Icon |
| **Auto-Spanning Toolbar** | Dynamically expands and balances toolbar keys symmetrically across device widths. | `Appearance > Toolbar auto-spacing` |
| **Touchpad Mode** | Swipe up on Spacebar to activate full cursor control and laptop-style touchpad gestures. | `Gesture typing > Vertical spacebar swipe` |
| **Floating Keyboard** | Detach keyboard into a draggable, resizable window with persistent positioning. | Toolbar > Floating Keyboard |
| **Split Toolbar & Suggestions** | Separates suggestions from the toolbar into a dual-row view. | `Appearance > Split toolbar & suggestions` |
| **Versatile Text Expander** | Expand shortcuts with dynamic variables, citation stripper (`%clipboard:clean%`), and modifiers. | `Text correction > Text Expander` |
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
> **Zero-Network Guarantee**: This feature runs 100% locally via the companion [**LeanType Offline AI Plugin**](https://github.com/LeanBitLab/LeanType-Offline-AI-Plugin) powered by `llama.cpp` and is available in the **Offline** build flavor (`-offline-release.apk`). No internet permission exists in the manifest.

### Setup Instructions
1. Download `ai_plugin-arm64-v8a.apk` (or `ai_plugin-x86_64.apk`) from the [LeanType Offline AI Plugin Releases](https://github.com/LeanBitLab/LeanType-Offline-AI-Plugin/releases/latest).
2. In LeanType, open **Settings → Plugins → Offline AI** and tap **Load Offline AI plugin** to load the `.apk`.
3. Download a compact GGUF model:
   - **Qwen 2.5 0.5B Instruct (Q4_K_M)**: Extremely lightweight & fast (~350 MB).
   - **Llama 3.2 1B Instruct (Q4_K_M)**: High-quality compact reasoning (~900 MB).
   - **Qwen 2.5 1.5B Instruct (Q4_K_M)**: High intelligence for modern devices (~1.1 GB).
4. Open **Settings → Advanced → GGUF Model (.gguf)** and select the `.gguf` file from your storage.
5. Configure sampling temperature, Top-K, Top-P, and custom system instructions.

---

## 4. Multi-Mode In-Keyboard Translation

LeanType offers a flexible translation architecture supporting all app flavors:

1. **Translation Plugin** (Supported across all flavors):
   - High-speed, private translation powered by the companion [LeanType Translation Plugin](https://github.com/LeanBitLab/LeanType-Translation-Plugin/releases/latest).
   - In-app model downloads for online builds, and browser download + local file importing for offline builds.
2. **Built-in Offline Translation (ML Kit)**:
   - 100% On-Device & Private translation on supported builds.
   - Download 59+ language translation models directly inside keyboard settings (~30 MB per language pack).
3. **Cloud & Local AI Translation**:
   - Uses your configured **AI Provider** (Google Gemini, Groq, OpenAI, Ollama, or local GGUF models) with customizable translation prompts.

### How to Setup
1. **Online Flavors (`Standard` / `Standard Full`)**: Open **Settings → Translation** and tap **Download Plugin** to install the [LeanType Translation Plugin](https://github.com/LeanBitLab/LeanType-Translation-Plugin/releases/latest) automatically.
2. **Offline Flavors (`Offline` / `Offline Lite`)**: Download `translation_plugin-arm64-v8a.apk` from [GitHub Releases](https://github.com/LeanBitLab/LeanType-Translation-Plugin/releases/latest) and load it in **Settings → Plugins → Translation**.
3. Download or import your required source and target language pairs.
4. Tap the **Translate** icon on the keyboard toolbar to instantly translate selected text or entire input fields.

---

## 5. On-Device Whisper Voice Typing

LeanType integrates high-accuracy, private speech-to-text powered by OpenAI's Whisper architecture via `whisper.cpp` and the [LeanType Voice Plugin](https://github.com/LeanBitLab/LeanType-Voice-Plugin).

### Available Multilingual Whisper Models
- **Tiny** (`ggml-tiny.bin`): **~39 MB** — Ultra-fast, minimal memory usage, 99+ languages.
- **Base** (`ggml-base.bin`): **~74 MB** — Best balance of accuracy and speed for daily typing.
- **Small** (`ggml-small.bin`): **~244 MB** — High accuracy for complex vocabulary and accents.
- **Custom Model**: Import any standard `.bin` GGML Whisper model from device storage.

### Setup Instructions
1. Download and install the [LeanType Voice Plugin APK](https://github.com/LeanBitLab/LeanType-Voice-Plugin/releases/latest) on your Android device (installed as a background IPC service).
2. Grant **Microphone permission** to the LeanType Voice Plugin.
3. In LeanType, open **Settings → Voice typing** (or **Settings → Plugins → Voice**) and tap **Whisper Speech Models**.
4. Download or import your preferred model (e.g. *Multilingual Base* ~74 MB).
5. Configure voice options:
   - **Voice Recognition Language**: Choose **Follow keyboard language (Default)**, **Auto-detect spoken language (`auto`)**, or pick from 99+ specific Whisper languages.
   - **Audio Visualizer**: Displays a real-time sound waveform directly on the keyboard toolbar.
   - **Silence Detection**: Configurable auto-stop sensitivity slider.
   - **Keep Model in Memory**: Prevents model reload latency during consecutive voice typing sessions.
6. Tap the **Microphone** icon on the toolbar to start voice typing.

---

## 6. Handwriting Input

Draw letters, words, or symbols directly on a handwriting recognition canvas using your finger or stylus via the companion [LeanType Handwriting Plugin](https://github.com/LeanBitLab/Leantype-Handwriting-Plugin) (supported across all flavors).

### Setup Instructions
1. **Online Flavors**: Open **Settings → Handwriting** and tap **Download Plugin** to install the [LeanType Handwriting Plugin](https://github.com/LeanBitLab/Leantype-Handwriting-Plugin/releases/latest).
2. **Offline Flavors**: Download `handwriting_plugin-arm64-v8a.apk` from [GitHub Releases](https://github.com/LeanBitLab/Leantype-Handwriting-Plugin/releases/latest) and load it in **Settings → Plugins → Handwriting**.
3. Use the **Offline Handwriting Models** dialog to download recognition packs directly (or import downloaded `.zip` model packs on offline builds).
4. Customize stroke width, stroke fade timeout, and recognition sensitivity.
5. Tap the **Handwriting (Pencil)** icon on the keyboard toolbar to open the drawing canvas and write naturally.

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
- Drag corner handles to resize with live real-time proportional key scaling.
- Enable **Persistent Floating Mode** to keep the keyboard floating across app switches.

---

## 11. Dual Toolbar & Split Suggestions

Split your toolbar and suggestion strip into two independent rows for fast, unhindered access to both word predictions and quick actions.
- Configure via **Settings → Appearance → Split toolbar & suggestions**.

---

## 12. Versatile Text Expander & Modifiers

Define custom abbreviations that instantly expand into rich text templates with dynamic variables, citation cleaning, and chained text modifiers:

### Supported Dynamic Placeholders
- `%date%`: Inserts current date (YYYY-MM-DD).
- `%time%`: Inserts current local time (HH:MM).
- `%tomorrow%`: Inserts tomorrow's date.
- `%clipboard%`: Inserts latest copied clipboard content.
- `%cursor%`: Places typing cursor at this exact position after expansion.
- `%greeting%`: Inserts time-appropriate greeting (*Good morning*, *Good afternoon*, *Good evening*).
- `%bullets%` / `%list%`: Inserts templated bulleted or numbered lists.
- `%custom_variable%`: Prompts an interactive popup to fill in custom text on the fly.

### Composable Clipboard Modifiers
Transform clipboard content on the fly by appending modifiers (`%clipboard:<mod1>:<mod2>%`):
- `%clipboard:clean%` / `%clipboard:nocite%`: Automatically strips bracketed Wikipedia / academic citations (`[1]`, `[1][2]`, `[note 1]`, `[citation needed]`) and cleans formatting.
- `%clipboard:singleline%` / `%clipboard:oneline%`: Flattens multi-line text into a single line.
- `%clipboard:title%`: Converts clipboard text to Title Case.
- `%clipboard:slug%` / `%clipboard:kebab%`: Converts text into a kebab-case URL slug (e.g. `my-awesome-post`).
- `%clipboard:snake%` / `%clipboard:camel%`: Converts text to `snake_case` or `camelCase`.
- `%clipboard:upper%` / `%clipboard:lower%`: Converts text to UPPERCASE or lowercase.
- `%clipboard:trim%`: Removes leading and trailing whitespace.
- `%clipboard:unquote%`: Strips outer quotation marks.
- `%clipboard:nourl%`: Removes URLs from text.
- `%clipboard:replace(pattern, replacement)%`: Performs custom regex find-and-replace.

### Setup Instructions
1. Open **Settings → Text correction → Text Expander**.
2. Tap **+ (Add)**, define the shortcut (e.g. `cite`), and enter your expansion template (e.g. `%clipboard:clean%`).

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
 
LeanType is published in **3 purpose-built flavors**:
 
| Flavor | Cloud AI | Offline AI | Voice Input | Handwriting | OCR Extraction | Translation | In-App Updates | Internet Permission | Min SDK | Approx Size |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Standard Full** | ✅ | ❌ | ✅ *(Plugin)* | ✅ *(Plugin)* | ✅ *(Plugin)* | ✅ *(Plugin/AI)* | ✅ | 🌐 Optional *(Opt-in)* | SDK 23 (6.0+) | **~10.8 MB** |
| **Standard (FOSS)** | ✅ | ❌ | ✅ *(Plugin)* | ✅ *(Plugin)* | ✅ *(Plugin)* | ✅ *(Plugin/AI)* | ❌ | 🌐 Optional *(Opt-in)* | SDK 23 (6.0+) | **~10.8 MB** |
| **Offline** | ❌ | ✅ *(Plugin on 8.0+)* | ✅ *(Plugin)* | ✅ *(Plugin)* | ✅ *(Plugin)* | ✅ *(Plugin)* | ❌ | 🚫 **None** | SDK 21 (5.0+) | **~9.8 MB** |

> [!TIP]
> **Concurrent Installation**: The `offline` (`com.leanbitlab.leantype.offline`) build uses a unique package ID, allowing you to install it alongside `standardfull` on the same device!

---

## 24. Offline Camera OCR & Screenshot Extraction

LeanType features an on-device OCR engine powered by ML Kit via the [LeanType OCR Plugin](https://github.com/LeanBitLab/LeanType-Ocr-Plugin) (supported across all flavors), enabling instant text extraction from live camera feeds or captured screenshots with zero internet connectivity.

### 📷 In-Keyboard Camera Scanner
- Tap the **Camera / OCR** key on the toolbar to open a live camera viewfinder embedded directly inside the keyboard window.
- **Controls**: Top flash toggle, real-time autofocus, shutter capture button, and gallery image picker fallback.
- **Instant Result Strip**: Displays recognized text immediately with 1-tap options to copy, insert into the current input field, or apply transformations.

### 🖼️ Screenshot Extraction Suggestion Pill
- Automatically detects screenshots captured on your device (within 4 minutes) and renders a unified compact pill on the suggestion strip: `[OCR] [Screenshot] [X]`.
- Tap **`[OCR]`** to extract text directly from the screenshot without leaving your current app.
- Tap **`[Screenshot]`** to paste or share the image directly.
- Tap **`[X]`** to dismiss the suggestion.

### 🛠️ Advanced Text Formatting Cleaners
Customize how extracted text is processed and formatted before insertion:
- **Casing Transformations**: Original, UPPERCASE, lowercase, Title Case, or Sentence case.
- **Line Joining Modes**: Keep original line breaks, merge all lines into a single continuous paragraph, or automatically rejoin hyphenated words split across lines (`anti-` + `gravity` $\rightarrow$ `antigravity`).
- **Punctuation Normalization**: Cleans irregular punctuation marks, curly quotes, and repeated spaces.
- **Bullet & List-Marker Stripping**: Cleans away bullet characters (`•`, `-`, `*`, `1.`, `a)`) for clean paragraph flow.
- **Whitespace & Noise Filtering**: Automatically strips leading/trailing blank spaces and filtered OCR noise artifacts.
- **Configurable Preferences**: Auto-copy recognized text to clipboard, auto-insert directly into text fields, persistent camera flash state, and search indexing support.

---

## 25. Inline Math Calculation Suggestions

Perform calculations instantly while typing in any app without switching to an external calculator:

### 🔢 How It Works
- Type any arithmetic expression followed immediately by an equals sign (`=`).
- The evaluated result appears instantly as a clean suggestion chip in the suggestion strip (e.g. typing `25*4=` offers `100`).
- Tap the chip to replace the entire typed math expression in-place with the evaluated answer.

### ➕ Supported Operations & Math Functions
- **Basic Arithmetic**: Addition (`+`), subtraction/unary negation (`-`, `−`), multiplication (`*`, `×`), division (`/`, `÷`).
- **Percentages**: e.g. `500-15%=` $\rightarrow$ `425`, `200+10%=` $\rightarrow$ `220`.
- **Exponents & Powers**: e.g. `2^8=` $\rightarrow$ `256`.
- **Grouping Parentheses**: e.g. `(12+8)/4=` $\rightarrow$ `5`.
- **High Precision**: Built with a pure Kotlin `BigDecimal` parsing engine with robust scientific formatting and division-by-zero protection.

### ⚙️ Settings
- Enable or disable via **Settings → Text correction → Inline math calculation**.

---

## 26. Custom Sound Packs & Audio Customization

LeanType includes a zero-latency native keypress audio feedback engine that delivers rich auditory tactile response:

### 🎵 12+ Built-in Audio Presets
- **iOS Tap**: Crisp, modern Apple-style click sound.
- **Mechanical Cherry MX**: Classic mechanical keyboard tactile switch clicks.
- **Thocky Mechanical**: Deep, resonant mechanical switch sound profile.
- **Vintage Typewriter**: Authentic acoustic typewriter key strikes and carriage feel.
- **Retro CRT Terminal**: Nostalgic 80s phosphor green terminal clicks.
- **Bubble Pop**: Playful, gentle bubble popping sounds.
- **Soft Velvet / Pudding**: Muted, low-profile quiet typing experience.
- **Woodblock Minimal**: Clean organic wooden percussion clicks.
- **Acoustic Marimba**: Melodic wooden bar acoustic chime feedback.
- **Modern Crisp Tick**: Subtle, high-frequency modern key tick.
- **Sci-Fi Cyberpunk**: Futuristic electronic digital interface hums.
- **8-Bit Chiptune Arcade**: Retro arcade game console key blips.

### 🌐 Remote Sound Pack Repository & Custom Imports
- **Remote Catalog**: Download additional physical modeling and synthesized instrument packs on demand from the official GitHub sound pack repository (`LeanBitLab/LeanType-Sound-Packs`).
- **Unbundled Light Footprint**: Sound packs are unbundled from the core APK to keep download sizes under 11 MB.
- **Custom `.zip` Pack Import**: Import custom sound packs packaged as a `.zip` containing a `soundpack.json` manifest and keypress audio files (`.wav` or `.ogg`).
- **Live Audition & Volume**: Audition sounds with live sample playback (▶️) and fine-tune keypress audio volume independently from system media volume.
- **Dedicated Settings Screen**: Access via **Settings → Plugins → Keypress Audio / Sound** or **Settings → Preferences → Sound on keypress**.
