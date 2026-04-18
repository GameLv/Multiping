# MultiPing — Network Monitor

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="100" alt="MultiPing icon"/>
</p>

<p align="center">
  Free and open-source network monitoring for Android
</p>

<p align="center">
  <a href="https://github.com/GameLv/Multiping/releases/latest"><img src="https://img.shields.io/github/v/release/GameLv/Multiping?label=GitHub&logo=github" alt="GitHub Release"/></a>
  <a href="https://github.com/GameLv/Multiping/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-purple.svg" alt="License: MIT"/></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-green?logo=android" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/No%20ads-No%20trackers-blue" alt="No ads"/>
</p>

---

## Features

- Monitor up to **256 hosts** simultaneously (IP or domain)
- **Traffic light** in notification shade — 🟢 green / 🟡 yellow / 🔴 red
- Online counter: `28/160`
- Two check modes per host: **ICMP Ping** or **HTTP** (GET, HEAD, POST)
- Per-host: interval, timeout, rolling average window
- **Favourite hosts** with push alerts on failure
- **Custom alert sound** — any system ringtone or silent
- Network awareness — no false alerts when offline
- **Battery saving** modes for screen-off:
  - Power-saving WorkManager poll
  - Favourites-only Foreground Service
  - Full pause
- Adaptive backoff interval for unresponsive hosts
- Auto-start after reboot
- Search by address or name
- **12 languages** including Arabic (RTL)

## Privacy

- ✅ No ads
- ✅ No trackers
- ✅ No analytics
- ✅ No Google Play Services
- ✅ No Firebase
- ✅ All data stored locally

## Requirements

- Android 8.0+ (API 26)
- No special permissions beyond what's listed in `AndroidManifest.xml`

## Building

```bash
# Clone
git clone https://github.com/GameLv/Multiping.git
cd Multiping

# Debug build
./gradlew assembleDebug

# Release build (requires keystore)
./gradlew assembleRelease
```

Open in **Android Studio** (Hedgehog or newer):
```
File → Open → select the Multiping folder
```
Wait for Gradle sync, then `Run → Run 'app'`.

> **Windows users:** Make sure the project path contains no spaces or Cyrillic characters.
> Move to `C:\Projects\Multiping` if needed.

## F-Droid

This app is designed to meet F-Droid inclusion requirements:
- MIT License
- No proprietary dependencies
- Reproducible builds
- `fastlane/` metadata included

[![Get it on F-Droid](https://fdroid.gitlab.io/artwork/badge/get-it-on.png)](https://f-droid.org/packages/com.multiping/)

## License

```
MIT License — see LICENSE file
```

## Contact

GameLv@yandex.ru
