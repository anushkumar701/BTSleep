# 🌙 SleepBT — Smart Bluetooth Sleep Tracker & Auto Disconnector

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**SleepBT** is a modern, high-precision Android application designed to ensure peaceful, undisturbed sleep by automatically fading audio volume and safely disconnecting Bluetooth audio devices (earbuds, headphones, speakers) after a set sleep timer expires.

---

## ✨ Features

- **⏱️ Ergonomic Rotary Dial**: Easily dial your desired sleep timer from 1 to 120 minutes with haptic feedback.
- **🛡️ Active Reconnect Protection**: Prevents accidental or auto-reconnections by headphones during sleep with a live countdown timer and immediate "Allow Reconnect Now" override.
- **🔊 Smooth Volume Fade**: Gradual audio volume attenuation before disconnection so your sleep is never interrupted abruptly.
- **📊 Usage & Session Analytics**: Track your daily listening duration and review past sleep sessions with verified green confirmation badges.
- **⚡ Ergonomic Center-Stage Layout**: High action button positioning (Cancel, Pause, Extend) placed directly below the timer for effortless one-handed thumb reach.
- **🎨 Glassmorphism Dark UI**: Vibrant HSL-tailored dark theme built with Jetpack Compose & Material 3.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 1.9+
- **UI Framework**: Jetpack Compose, Material Design 3
- **Architecture**: MVVM with Kotlin Coroutines & `StateFlow`
- **Database**: Room Database (`SessionEntity`, `DailyUsageEntity`, `DeviceEntity`)
- **Preferences**: Jetpack DataStore
- **System Services**: Android BluetoothManager, A2DP / HFP Profile Listeners, NotificationManager

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Jellyfish / Ladybug or newer
- JDK 17+
- Android SDK 34 (Android 14)

### Build via Command Line
```bash
# Clone the repository
git clone https://github.com/anushkumar701/BTSleep.git
cd BTSleep

# Build debug APK
./gradlew assembleDebug -x uploadCrashlyticsMappingFileDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📬 Contact & Support

Created & maintained by **Midnight Compiler**.
- **Email**: [midnightcompiler01@gmail.com](mailto:midnightcompiler01@gmail.com)
- **GitHub**: [anushkumar701](https://github.com/anushkumar701)

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
