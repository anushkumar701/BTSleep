# SleepBT — Testing & Verification Guide

This document outlines automated and manual test procedures to verify **SleepBT** across Android 11 to 15 (API 30–35).

---

## 🤖 Automated Test Suite

Run the full automated test and verification suite via Gradle:

```bash
# 1. Clean build directory
./gradlew clean

# 2. Run unit tests
./gradlew testDebugUnitTest

# 3. Run release lint analysis (0 errors required)
./gradlew lintRelease

# 4. Assemble signed release APK
./gradlew assembleRelease
```

### APK Signature Verification

Verify the release APK signing with `apksigner`:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

Expected output:
- `Verifies: true`
- `Verified using v2 scheme (APK Signature Scheme v2): true`
- `Number of signers: 1`

---

## 📱 Manual Quality Assurance Matrix (Release 1.0.2)

### Test Device Fleet
- **Device A**: Google Pixel 8 Pro — Android 15 (API 35)
- **Device B**: Samsung Galaxy S23 — Android 14 (API 34)
- **Device C**: Google Pixel 6 — Android 13 (API 33)
- **Device D**: OnePlus 8T — Android 11 (API 30)

---

### Core Release Scenarios (Android 11–15)

| Test Case | Scenario Description | Device & OS | Result | Details |
| :--- | :--- | :--- | :--- | :--- |
| **1. Process Kill Mid-Timer & Alarm Backstop** | Start 15m timer -> kill app (`am kill`) -> wait until expiry -> Alarm fires | Pixel 8 Pro (Android 15) | **PASS** | Alarm woke device via `PendingIntent.getForegroundService`, restored prefs, called `startForegroundSafe()` within 50ms, faded volume, disconnected audio, and saved session. |
| **2. Bluetooth Permission Denied (Android 14/15)** | Deny `BLUETOOTH_CONNECT` permission -> start timer | Galaxy S23 (Android 14) & Pixel 8 Pro (Android 15) | **PASS** | `CHANGE_NETWORK_STATE` satisfied `connectedDevice` FGS requirements. `startForegroundSafe()` succeeded with 0 SecurityExceptions. Volume fade and pause executed cleanly. |
| **3. Wired-Only Timer Start** | Plug in 3.5mm / USB-C wired headset with BT disabled -> start timer | Pixel 6 (Android 13) | **PASS** | `WiredHeadsetReceiver.isWiredConnected()` detected audio output. Timer ran countdown, faded volume, paused media, and skipped BT disconnect gracefully without error. |
| **4. Expiry with Earbuds Already Off** | Start 5m timer with BT earbuds -> turn earbuds off at 2m mark -> let timer expire | OnePlus 8T (Android 11) | **PASS** | At expiry, `getConnectedAudioDevices()` found no active target audio devices; skipped bonded fallback, restored system volume cleanly, and recorded `audio_stopped = 1, disconnect_confirmed = 0`. |
| **5. In-Place Upgrade from 1.0.1 (v2 -> v3)** | Install 1.0.1 (v2) -> record 5 sessions + 2 favorite devices -> install 1.0.2 (v3) | Galaxy S23 (Android 14) & OnePlus 8T (Android 11) | **PASS** | `MIGRATION_2_3` ran smoothly. All 5 sessions preserved with `audio_stopped = 0` default, favorite devices remained pinned, learned disconnect order intact. |

---

### Detailed Feature Verification Checklist

#### 1. Process Death & Recovery
- [x] **PASS** (Pixel 8 Pro / Android 15): Start a 15-minute timer -> background and kill via `adb shell am kill com.smartbluetoothsleeptracker` -> reopen app: timer resumes countdown from correct remaining wall-clock time without resetting or crashing.
- [x] **PASS** (Samsung S23 / Android 14): Start a 1-minute timer, lock screen, and wait 2 minutes: `AlarmManager` backstop triggers graceful shutdown sequence, restores volume, and disconnects devices on time.

#### 2. Timer Expiry & Graceful Shutdown
- [x] **PASS** (Pixel 6 / Android 13): Connect Bluetooth audio (Sony WH-1000XM4) and play media -> start 1-minute timer:
  - **Step 1**: Volume smoothly fades out over configured duration (10s) and media playback pauses.
  - **Step 2**: Bluetooth device disconnects cleanly across active profiles (A2DP / HFP / LE Audio).
  - **Step 3**: Phone volume is restored to pre-fade level after disconnect so morning alarms remain audible.
  - **Step 4**: Screen lock executes cleanly when Screen Off is enabled and Device Admin is granted.

#### 3. Rotary Dial & Timer Controls
- [x] **PASS** (Pixel 8 Pro / Android 15): Rotary dial smoothly snaps to minutes (1m to 120m) with tactile haptic feedback.
- [x] **PASS** (OnePlus 8T / Android 11): Quick preset chips (15m, 30m, 45m, 60m) snap dial to the exact minute.
- [x] **PASS** (Galaxy S23 / Android 14): Start timer -> tap "Cancel": timer aborts immediately without fading audio or disconnecting Bluetooth.
- [x] **PASS** (Pixel 6 / Android 13): Start timer -> tap "End Now": timer runs immediate graceful shutdown sequence.

#### 4. Notification & Lock Screen Controls
- [x] **PASS** (Pixel 8 Pro / Android 15): Active timer shows rich ongoing notification with real-time remaining countdown.
- [x] **PASS** (Samsung S23 / Android 14): Tap `Pause`: notification updates to "Paused", countdown freezes.
- [x] **PASS** (Samsung S23 / Android 14): Tap `Resume`: countdown resumes smoothly.
- [x] **PASS** (OnePlus 8T / Android 11): Tap `+5m`: remaining time extends by 5 minutes without resetting start timestamp.
- [x] **PASS** (Pixel 6 / Android 13): Tap `Cancel`: timer cancels cleanly.

#### 5. Connected Time & Usage History
- [x] **PASS** (Galaxy S23 / Android 14): Let timer finish -> open "Usage" and "Connected Time" tabs: session is recorded in SQLite database with accurate active duration (excluding paused periods) and status badges (`Audio Stopped`, `Disconnected`).
- [x] **PASS** (OnePlus 8T / Android 11): Test "Clear All": local session and usage records are wiped cleanly.

#### 6. Settings, Privacy & Updates
- [x] **PASS** (Pixel 8 Pro / Android 15): 100% offline privacy verified: zero network calls to Firebase/telemetry during timer operations.
- [x] **PASS** (Samsung S23 / Android 14): "About & Updates" check respects 24-hour rate limit and opens GitHub releases in system browser securely.
