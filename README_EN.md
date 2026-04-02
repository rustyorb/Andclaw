# Andclaw 🤖

<p align="center">
  <img src="./icon.png" alt="Andclaw Logo" width="120">
</p>

[![Android](https://img.shields.io/badge/Android-12%2B-brightgreen?logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-blue?logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Website](https://img.shields.io/badge/Website-andclaw.app-blue?logo=googlechrome&logoColor=white)](https://andclaw.app/)
[![Install](https://img.shields.io/badge/Download-Install-green?logo=android&logoColor=white)](https://andclaw.app/#/install)

> **Let AI use your phone like a human** — Runs entirely on-device, no Root required, no PC needed.

<p align="center">
  <a href="https://andclaw.app/"><b>🌐 Official Website</b></a> &nbsp;|&nbsp;
  <a href="https://andclaw.app/#/install"><b>📲 Download APK</b></a>
</p>

---

## 🌟 Key Features

| Feature | Description |
|---------|-------------|
| **🚫 No Root Required** | Pure Accessibility Service implementation — no system privileges needed |
| **💻 No PC Needed** | Runs entirely on the phone; no ADB or PC companion required |
| **🧠 AI Powered** | Supports Kimi Code (Anthropic format), Moonshot, and any OpenAI-compatible API |
| **👁️ Screen Aware** | Real-time UI hierarchy parsing + automatic screenshot for WebView/browser scenes |
| **🤏 Human-like Operations** | Simulates tap, swipe, long-press, and text input gestures |
| **📸 Multimedia** | Photo, video, screen recording, screenshot, and volume control |
| **📱 Device Management** | Enterprise-grade Device Owner policies (silent install, Kiosk mode, etc.) |
| **🤖 Remote Control** | **Telegram Bot** or **WeChat ClawBot (iLink)** dual channels: remotely dispatch commands; screenshot/media delivery varies by channel (see below) |
| **🌍 Multi-language** | Chinese and English interface support |

## 📋 Comparison with Other Solutions

| Feature | Andclaw | [Open-AutoGLM](https://github.com/zai-org/Open-AutoGLM) | [Roubao](https://github.com/Turbo1123/roubao) | Doubao Phone |
|---------|:-------:|:-------:|:-------:|:-------:|
| No PC required | ✅ | ❌ Needs PC (Python) | ✅ | ✅ |
| No dedicated hardware | ✅ | ✅ | ✅ | ❌ Special device required |
| No Shizuku / ADB | ✅ Accessibility Service | ❌ ADB control | ❌ Requires Shizuku | ✅ |
| Remote control | ✅ Telegram / ClawBot | ❌ | ❌ | ❌ |
| Custom model | ✅ Multi-provider | ✅ | ✅ | ❌ Doubao only |
| Open source | ✅ | ✅ | ✅ | ❌ |
| Native Android | ✅ Kotlin | ❌ Python | ✅ Kotlin | ✅ |

**Andclaw's core advantages:**
- **Zero external dependencies**: Built on Android Accessibility Service — no Shizuku, no ADB, no PC
- **Remote control**: Supports **Telegram Bot** and **WeChat ClawBot (iLink)**; Telegram delivers real media files, ClawBot currently returns text notifications (see "Remote Channels" below)
- **UI hierarchy + visual dual-mode perception**: Prefers Accessibility node tree; falls back to screenshot analysis in WebView/browser scenes
- **Loop detection + screenshot retry**: Automatically takes a screenshot after 5 repeated actions and retries visually to prevent infinite loops

---

## 📱 Demo

[![Demo video](docs/demo_cover.png)](https://www.bilibili.com/video/BV1k8w4zeEL7)
[![Demo video](docs/ScreenShot_2026-03-17_202610_426.png)](https://www.bilibili.com/video/BV1WtwKzLEXd)

### Screenshots

<p align="center">
  <b>Main screen</b>: Device Owner mode, network status, remote channel (Telegram / Lark / ClawBot), and conversation history entry<br>
  <img src="docs/screenshots/b726d0df2b63d8b7cc2b83eacec0c2c4.png" alt="Andclaw main screen: device management and remote connection" width="320"><br><br>
  <b>Remote connection</b>: Single-channel configuration; shown here with ClawBot (WeChat) connected and logged in<br>
  <img src="docs/screenshots/03583636640228432acca1430b1acec2.png" alt="Remote connection settings: ClawBot and bridge status" width="320"><br><br>
  <b>AI Agent</b>: Local conversation UI with task execution progress and completion feedback<br>
  <img src="docs/screenshots/8b3b7975a19d1911e651a966ba48504d.png" alt="Andclaw AI Agent conversation UI" width="320">
</p>

---

## 🚀 Quick Start

### Requirements

- **Android version**: Android 12 (API 31) or higher
- **Accessibility Service**: Must be enabled manually in `Settings > Accessibility`
- **Overlay permission**: Required to display the emergency stop floating button
- **API Key**: Obtain from [Kimi Code](https://www.kimi.com/code/console), [Moonshot Platform](https://platform.moonshot.cn/), or any OpenAI-compatible provider

### Installation

**Option 1: Online Install with Chrome (Recommended)**

Visit [andclaw.app/#/install](https://andclaw.app/#/install) with Chrome and follow the on-screen steps.

**Option 2: Build from Source**

1. **Clone the repository**
   ```bash
   git clone https://github.com/andforce/Andclaw.git
   cd Andclaw
   ```

2. **Create `local.properties`** with your API keys:
   ```properties
   kimi_key=your_kimi_api_key
   tg_token=your_telegram_bot_token   # Optional
   ```

3. **Build and install**
   ```bash
   ./gradlew :app:installDebug
   ```

4. **Grant permissions**
   - After opening the app, enable the **Accessibility Service** as prompted
   - Grant **Display over other apps** permission

5. **Activate Device Owner** (first-time setup only)

   > ⚠️ **Important**: Due to Android security restrictions, the device must be **factory reset** before enabling Device Owner mode. Without Device Owner mode, AI automation capabilities are significantly limited.

   ```bash
   adb shell dpm set-device-owner com.andforce.andclaw/.DeviceAdminReceiver
   ```

   Device Owner unlocks:
   - ✅ **App management**: Silent install/uninstall, hide/show/suspend apps, block uninstall, auto-grant permissions, list installed apps
   - ✅ **Device control**: Remote lock screen, reboot, factory reset, disable camera/status bar/lock screen, USB data control, location toggle
   - ✅ **Kiosk mode**: Lock Task single-app mode, replace default launcher, disable safe mode/factory reset

   > Full capability list: [ACTIONS.md](./ACTIONS.md)

6. **Create a Telegram Bot** (optional)

   1. Open Telegram and search for **@BotFather**
   2. Send `/newbot` to create a new bot
   3. Follow the prompts to set the bot name and username (username must end with `bot`)
   4. Copy the **Bot Token** provided (format: `123456789:ABCdefGHIjklMNOpqrsTUVwxyz`)
   5. Enter the Bot Token in the Andclaw settings page

---

## 🎯 Usage

### 1. Text Commands

Tell Andclaw what you want to do:

| Example command | AI execution flow |
|----------------|------------------|
| "Open Bilibili, search for AI learning videos, and play one" | Locate Bilibili icon → Tap → Enter search → Type "AI learning" → Search → Select video → Play |

### 2. AI Agent Loop

```
User command
    ↓
[1.5 s] → Capture screen UI tree (Accessibility Service)
    ↓
Browser/WebView? ── Yes ──→ Auto screenshot (visual analysis assist)
    ↓                              ↓
Send to LLM (system prompt + last 12 messages + screen data [+ screenshot])
    ↓
AI returns JSON action decision
    ↓
Parse failed? ── Yes ──→ Correction prompt retry (once)
    ↓
Execute action (click/swipe/input/intent/dpm/photo/screen-record/…)
    ↓
[2.5 s] → Re-capture screen  ←────────────────────┐
    ↓                                              │
Loop detection (same action 5× in a row?)          │
    ↓ Yes                                          │
Screenshot + visual retry (max 3 rounds, stop at 15) │
    ↓ No                                           │
Task complete? ── No ───────────────────────────────┘
    ↓
Yes → Done
```

### 3. Supported Action Types

| Type | Description |
|------|-------------|
| `intent` | Launch apps/activities, open URLs, dial, SMS, set alarms, and other system Intents |
| `click` | Simulate tap at screen coordinates (x, y) |
| `swipe` | Swipe gesture (scroll, page flip); supports custom duration |
| `long_press` | Long press; supports custom duration |
| `text_input` | Inject text into the currently focused field (SET_TEXT → clipboard-paste fallback) |
| `global_action` | System-level actions: Back, Home, Recents, Notifications, Quick Settings |
| `screenshot` | Capture and save to `Pictures/Andclaw/`; auto-sends as file on Telegram; sends a text notice on ClawBot |
| `download` | Download a file directly via DownloadManager (no browser needed) |
| `wait` | Wait for page load / UI transition and re-check screen (max 10 seconds) |
| `camera` | Take photo (`take_photo`), start video (`start_video`), stop video (`stop_video`) |
| `screen_record` | Screen recording (`start_record` / `stop_record`), saved to `Movies/Andclaw/` |
| `volume` | Volume control: set, increase, decrease, mute/unmute, query current volume |
| `dpm` | Device Policy Manager actions (Device Owner mode only) |
| `finish` | Task complete; stop the Agent |

### 4. Supported AI Providers

| Provider | API Format | Base URL | Default Model |
|----------|-----------|----------|---------------|
| **Kimi Code** | Anthropic Messages | `https://api.kimi.com/coding` | `kimi-k2.5` |
| **Moonshot** | OpenAI Chat Completions | `https://api.moonshot.cn/v1` | `kimi-k2-turbo-preview` |
| **OpenAI-compatible** | OpenAI Chat Completions | `https://api.openai.com/v1` | `gpt-4o` |

Multimodal input (text + base64 screenshot) is supported across all formats.

#### Kimi Code vs Moonshot API

Moonshot AI offers two independent API services with **non-interchangeable** API keys:

| Dimension | Kimi Code API | Moonshot Platform API |
|-----------|--------------|----------------------|
| **Endpoint** | `https://api.kimi.com/coding` | `https://api.moonshot.cn/v1` |
| **Protocol** | Anthropic Messages (`/v1/messages`) | OpenAI-compatible (`/v1/chat/completions`) |
| **Auth** | `x-api-key` header | `Authorization: Bearer` |
| **Key source** | [Kimi Code Console](https://www.kimi.com/code/console) | [Moonshot Platform](https://platform.moonshot.cn/console/api-keys) |
| **Purpose** | Optimised for Coding Agent | General-purpose LLM API |

### 5. Remote Channels: Telegram and WeChat ClawBot

Andclaw supports two remote channels, configurable separately in **MDM Settings / AI Settings**; both can be active simultaneously and neither replaces local Agent logic.

**Busy notice (both channels)**: When the **Agent is already executing a task**, a new plain-text command from a remote channel will not interrupt the current task — the sender receives a **busy notice** instead. Use `/stop` to stop the current task first, then send a new command. `/status` and `/stop` are always available regardless of Agent state.

#### Telegram Bot

Remote control via Telegram Bot; once the token is configured and the app is running, long-polling starts automatically.

| Command | Description |
|---------|-------------|
| Send text directly | Dispatched to the Agent as a command |
| `/status` | Query Agent state (running/idle, current task, Chat ID) |
| `/stop` | Stop the currently running task |

Screenshots, photos, videos, audio recordings, and screen recordings are sent as **files** to the active Telegram conversation upon success (subject to Chat ID whitelist).

#### WeChat ClawBot (iLink)

Connects the device to WeChat via the **iLink** bot protocol using long-polling HTTP (aligned with the `wechat-acp` / `weclaw-proxy` style).

**Login and configuration (current version)**

- In the **AI Settings page** (MDM module `AiSettingsActivity`), scroll to the **ClawBot** section, fill in the Base URL and Bot Type, then tap **Scan QR to Login** to initiate WeChat login.
- The app fetches a QR code from the bridge service in real time and displays it; after scanning with the **ClawBot Plugin** in WeChat, the page keeps polling for status (waiting / scanned / confirmed).
- Once confirmed, the device persists the session and automatically starts the ClawBot long-poll bridge. You can then send text commands from WeChat directly.

| Capability | Notes |
|------------|-------|
| Text commands | ✅ Supported: user messages enter the Agent via polling; replies go via `sendmessage` |
| Typing indicator | ✅ Supported: fetches `typing_ticket` from `getconfig`, then calls `sendtyping` |
| Image / Video / Audio delivery | ⚠️ **iLink media sending API not implemented** in this repo; when the Agent needs to notify the remote side, the app **attempts** to send a plain-text notice (file saved locally, media protocol not connected); no binary is uploaded; if the ClawBot bridge is not running, only local save occurs — see logcat `RemoteBridgeManager` |

Therefore: when using ClawBot, check the actual files at `Pictures/Andclaw`, `Movies/Andclaw`, etc. on the device itself; the remote side relies on the text channel as currently implemented.

#### WeChat ClawBot Conversation Example

The screenshot below shows a real interaction via WeChat ClawBot: the user first asks to "go to home screen", then asks for a screenshot; Andclaw reports execution progress in WeChat and notifies the user that the media has been saved locally after the screenshot action.

<p align="center">
  <img src="docs/7f2df18ea9811356ef19e7767988ec9c.jpg" alt="WeChat ClawBot remote control example" width="360">
</p>

---

## ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=andforce/Andclaw&type=Date)](https://star-history.com/#andforce/Andclaw&Date)

---

## 📄 License

This project is open-sourced under the [MIT License](LICENSE).

---

## 🙏 Acknowledgements

- [TestDPC](https://github.com/googlesamples/android-testdpc) — Device Owner feature reference
- [Kimi API](https://platform.moonshot.cn/) — Large language model support

---

## ⚠️ Disclaimer

This project is for learning and research purposes only. The developers are not liable for any data loss, device damage, or other losses resulting from the use of this software. Please use AI automation features with caution and avoid using them in scenarios involving sensitive information. Screen UI data and screenshots are sent to LLM providers — please be mindful of privacy.

---

<p align="center">
  Made with ❤️ by Andclaw Team
</p>