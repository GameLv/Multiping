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

## Monitoring

- Up to **256 hosts** simultaneously (IP or domain)
- Three check modes per host: **ICMP Ping, HTTP (GET, HEAD, POST), or TCP Connect (custom port)**
- **Per-host settings: check interval, timeout, rolling average window**
- Adaptive backoff — check interval increases automatically for unresponsive hosts
- Countdown to next check shown on each host card

## Alerts

- Traffic light in notification shade — 🟢 green / 🟡 yellow / 🔴 red
- Mark any host as Favourite to get a **push notification** when it goes down
- **Custom alert sound** — any system ringtone or silent
- **No false alerts** when the network is offline — the app detects the difference between "host unreachable" and "no internet"

## Battery & background

- **Four screen-off modes:**
  - power-saving WorkManager poll
  - favourites-only foreground service
  - full pause
  - all foreground service
- Auto-restart after device reboot
- WakeLock only while actively checking

## Interface

- Search by address or name
- Dark theme only
- 12 languages: Russian, English, German, French, Spanish, Portuguese, Chinese, Korean, Japanese, Bashkir, Kazakh, Arabic (RTL)

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

## Link to download the .apk file from GitHub

.apk https://github.com/GameLv/Multiping/releases/download/v4.1.0/MultiPing.apk

## Screens

<table style="border: hidden; border-spacing: 0; background: transparent; width: auto;">
  <tr style="border: hidden;">
    <td style="border: hidden; padding: 0;">
      <img width="270" height="600" alt="Screenshot_20260418-162113" src="https://github.com/user-attachments/assets/a079b362-e028-4486-a92e-bbcfd96d2153" />
    </td>
    <td style="border: hidden; padding: 0;">
      <img width="270" height="600" alt="Screenshot_20260418-162040" src="https://github.com/user-attachments/assets/29c9fbc0-18e2-4fbd-a58c-7840dbdaef23" />
    </td>
    <td style="border: hidden; padding: 0;">
      <img width="270" height="600" alt="Screenshot_20260418-162049" src="https://github.com/user-attachments/assets/c5ce8fb4-2d32-4070-b66a-6067cb3bea84" />
    </td>
  </tr>
</table>

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
