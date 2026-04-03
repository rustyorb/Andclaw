
# Andclaw 🤖

<p align="center">
  <img src="./icon.png" alt="Andclaw Logo" width="120">
</p>

[![Android](https://img.shields.io/badge/Android-9%2B-brightgreen?logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-blue?logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Website](https://img.shields.io/badge/Website-andclaw.app-blue?logo=googlechrome&logoColor=white)](https://andclaw.app/)
[![Install](https://img.shields.io/badge/Install-Download-green?logo=android&logoColor=white)](https://andclaw.app/#/install)

> **Let AI use your phone like a human** — Runs entirely on-device. No root required. No computer needed.

<p align="center">
  <a href="https://andclaw.app/"><b>🌐 Official Website</b></a> &nbsp;|&nbsp;
  <a href="https://andclaw.app/#/install"><b>📲 Install APK Online</b></a>
</p>

---

## 🌟 Core Features

| Feature | Description |
|------|------|
| **🚫 No Root Required** | Uses accessibility services only, no system-level permissions |
| **💻 No Computer Needed** | Runs entirely on-device without ADB or PC |
| **🧠 AI-Powered** | Supports Kimi Code (Anthropic format), Moonshot, and any OpenAI-compatible API |
| **👁️ Screen Awareness** | Reads UI hierarchy in real time + auto screenshot analysis for WebView/browser |
| **🤏 Human-like Interaction** | Simulates tap, swipe, long press, text input |
| **📸 Multimedia Capabilities** | Camera, video recording, screen recording, screenshots, volume control |
| **📱 Device Control** | Enterprise-level device management in Device Owner mode |
| **🤖 Remote Control** | Control via Telegram Bot or WeChat ClawBot (iLink-based) |
| **🌍 Multi-language Support** | Chinese / English UI |

---

## 📋 Comparison With Other Solutions

| Feature | Andclaw | Open-AutoGLM | Roubao | Doubao Phone |
|-----|:-------:|:--------:|:-------:|:-------:|
| No Computer Required | ✅ | ❌ Requires PC Python | ✅ | ✅ |
| No Special Hardware | ✅ | ✅ | ✅ | ❌ Requires custom device |
| No Shizuku / ADB | ✅ Accessibility-based | ❌ ADB required | ❌ Requires Shizuku | ✅ |
| Remote Control | ✅ Telegram / ClawBot | ❌ | ❌ | ❌ |
| Custom Models | ✅ Multi-provider | ✅ | ✅ | ❌ |
| Open Source | ✅ | ✅ | ✅ | ❌ |
| Native Android | ✅ Kotlin | ❌ Python | ✅ Kotlin | ✅ |

**Key Differences:**

- **Zero external dependencies**: No Shizuku, no ADB, no PC
- **Remote control**: Telegram + WeChat ClawBot dual-channel support
- **Dual perception system**: UI tree + vision (screenshots)
- **Loop detection + retry**: Prevents agent dead loops

---

## 📱 Demo

[![Demo Video](docs/demo_cover.png)](https://www.bilibili.com/video/BV1k8w4zeEL7)  
[![Demo Video](docs/ScreenShot_2026-03-17_202610_426.png)](https://www.bilibili.com/video/BV1WtwKzLEXd)

### Screenshots

<p align="center">
  <b>Main Interface</b>: Device admin mode, network status, remote channels (Telegram / Feishu / ClawBot), chat history<br>
  <img src="docs/screenshots/b726d0df2b63d8b7cc2b83eacec0c2c4.png" width="320"><br><br>

  <b>Remote Connection</b>: Single channel config (example shows ClawBot connected)<br>
  <img src="docs/screenshots/03583636640228432acca1430b1acec2.png" width="320"><br><br>

  <b>AI Agent</b>: Local chat interface with execution progress<br>
  <img src="docs/screenshots/8b3b7975a19d1911e651a966ba48504d.png" width="320">
</p>

---

## 🚀 Quick Start

### Requirements

- Android 12 (API 31) or higher
- Accessibility service enabled (`Settings > Accessibility`)
- Overlay permission
- API Key from:
  - Kimi Code
  - Moonshot
  - Any OpenAI-compatible provider

---

### Installation

**Method 1: Online Install (Recommended)**  
Visit: https://andclaw.app/#/install via Chrome

**Method 2: Build from Source**

```bash
git clone https://github.com/andforce/Andclaw.git
cd Andclaw
./gradlew :app:installDebug
````

Then:

* Enable accessibility service
* Grant overlay permission

---

### Activate Device Owner (Optional but Powerful)

```bash
adb shell dpm set-device-owner com.andforce.andclaw/.DeviceAdminReceiver
```

⚠️ Requires factory reset first

Capabilities unlocked:

* Silent app install/uninstall
* Device lock / reboot / reset
* Disable camera / status bar / USB
* Kiosk mode

---

## 🎯 Usage

### 1. Natural Language Commands

Example:

| Command                              | Execution                        |
| ------------------------------------ | -------------------------------- |
| "Open Bilibili and search AI videos" | Launch app → search → play video |

---

### 2. Agent Loop

```
User Input
    ↓
Capture UI tree (1.5s)
    ↓
Browser? → Yes → Screenshot
    ↓
Send to LLM (context + UI + optional image)
    ↓
LLM returns JSON action
    ↓
Execute action
    ↓
Wait (2.5s)
    ↓
Repeat until complete
```

Loop detection:

* Same action x5 → trigger visual retry
* Max retries: 15

---

### 3. Supported Actions

| Type          | Description            |
| ------------- | ---------------------- |
| intent        | Launch apps, open URLs |
| click         | Tap coordinates        |
| swipe         | Scroll or gesture      |
| long_press    | Long press             |
| text_input    | Inject text            |
| global_action | Back, Home, etc        |
| screenshot    | Save to device         |
| download      | Download via system    |
| wait          | Delay                  |
| camera        | Photo / video          |
| screen_record | Screen recording       |
| volume        | Control volume         |
| dpm           | Device policy actions  |
| finish        | End task               |

---

## 🤖 AI Providers

| Provider          | Format    | Base URL                                                   | Model     |
| ----------------- | --------- | ---------------------------------------------------------- | --------- |
| Kimi Code         | Anthropic | [https://api.kimi.com/coding](https://api.kimi.com/coding) | kimi-k2.5 |
| Moonshot          | OpenAI    | [https://api.moonshot.cn/v1](https://api.moonshot.cn/v1)   | kimi-k2   |
| OpenAI Compatible | OpenAI    | [https://api.openai.com/v1](https://api.openai.com/v1)     | gpt-4o    |

Supports multimodal (text + image)

---

## 📡 Remote Control

### Telegram Bot

* Send text commands
* Receive screenshots/media as files

Commands:

| Command | Function    |
| ------- | ----------- |
| message | Execute     |
| /status | Check state |
| /stop   | Stop task   |

---

### WeChat ClawBot (iLink)

⚠️ Limitation:

* **No media upload support implemented**
* Files saved locally only
* Remote receives text fallback message

---

## ⚠️ Disclaimer

This project is for learning and research only.

The developers are not responsible for:

* Data loss
* Device damage
* Any misuse

**Important:**

Screen data and screenshots are sent to LLM providers.
Be cautious with sensitive information.

---

## 📄 License

MIT License

---

## 🙏 Credits

* TestDPC (Device Owner reference)
* Kimi API (LLM support)

---

<p align="center">
  Made with ❤️ by Andclaw Team
</p>
```

---
 
