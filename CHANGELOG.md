# Changelog

All notable changes to **SleepBT** are documented in this file.

## [1.0.2] - 2026-10-25

### Summary
Version release 1.0.2 featuring a clean adaptive earbud battery display (removing the Case column and disclaimers whenever case status cannot be fetched), and a comprehensive 6-step interactive in-app tutorial explaining Volume Fade, Reconnect Blocker, Usage, Hearing Health, and Settings.

### 🔋 Clean Adaptive Battery Display
- **Case Column Removed When Unavailable**: In `HomeScreen.kt`, if case battery telemetry is unsupported or unavailable on the connected earbuds (`caseLevel !in 0..100`), the Case column and dividers are cleanly omitted. The card renders Left and Right cleanly without dead `--` placeholders or disclaimers. Case is only rendered when telemetry is verified.

### 📚 Comprehensive 6-Step In-App Tutorial
- **Step 1 • Sleep Timer & Presets**: How to set duration with the smooth rotary dial and quick preset pills (15m, 30m, 45m, 60m).
- **Step 2 • Volume Fade**: Clearly explains why music fades smoothly over 10–30s to prevent sudden awakenings, plus the pro tip: pressing any physical volume button during fade adds +5m.
- **Step 3 • Reconnect Blocker**: Dedicated step explaining how SleepBT blocks aggressive earbuds from immediately reconnecting, beeping, and waking the user up.
- **Step 4 • Usage & Primary Sleep Target**: Highlights daily/weekly session stats and how to tap the Star ★ on connected earbuds to designate the primary sleep device.
- **Step 5 • Hearing Health & Ear Rest**: Explains the WHO-standard daily sound exposure gauge, ear rest score, and fatigue risk band (Low, Moderate, High).
- **Step 6 • Settings & Controls**: Customizing auto-extend minutes, alert lead times, dark/light themes, and optional Shizuku controls.

## [1.0.5] - 2026-10-25

### Summary
Human-perspective real-world test case audit and hardening across bedtime falling asleep scenarios, premature disconnects, VoIP call isolation, physical volume button responsiveness, smartwatch coexistence, neat notification presentation, and UI responsiveness.

### 🧪 Human Persona Test Scenarios & Fixes
- **Bedtime False Auto-Extend Fix**: Fixed race condition where Android 14+ internal volume fade downward steps were misclassified as user volume-up inputs when broadcast delivery was delayed. Hardened detection in `PlaybackController` to verify `newVol > prevVol` and `currentVol > lastProgrammaticVolume`.
- **Physical Volume Key Extension in Warning & Fade Windows**: Registered `VOLUME_CHANGED_ACTION` with `RECEIVER_EXPORTED` on Android 14+ (API 34+) and added an active warning volume listener during the pre-disconnect alert window, enabling one-touch volume button extension.
- **VoIP & Cellular Call Protection**: Added `AudioManager.MODE_IN_COMMUNICATION` check alongside telephony calls (`MODE_IN_CALL`, `MODE_RINGTONE`), safeguarding active WhatsApp, Telegram, Google Meet, Zoom, and Discord calls from mid-call audio cutoff.
- **Smartwatch & Multi-Device Isolation**: Hardened `TimerService` disconnector to only target the designated audio peripheral, preventing paired fitness bands, smartwatches, or LE peripherals from unexpected disconnection.
- **Silent Speaker Leak Protection**: In `finishSessionOnManualDisconnect()`, immediately calls `stealAudioFocusAndPause()` when earbuds are put in the charging case or disconnected mid-timer, preventing ambient sleep sounds or music from blaring out of the phone speaker.
- **Safe Volume Restoration**: In `restoreVolume()`, forces audio pause prior to restoring media volume, preventing sudden audio blasts on the phone speaker.
- **Interactive Primary Sleep Target on Home Screen**: Tapping the star badge on `ConnectionStatusBar` now directly toggles the Primary Sleep Target device with instantaneous UI feedback and DB upsert fallback for newly paired devices.
- **Sleek Minimalist Notifications**: Eliminated redundant "Disconnecting" titles; streamlined status to `Ending Soon • [Device]` with clear remaining time badge (`Ending Soon • 85%`) and one-tap extension actions.
- **Startup Crash Fix**: Initialized `disconnector = BluetoothDisconnector(this, db)` in `SleepBTApp.onCreate()`, fixing `UninitializedPropertyAccessException` when `HomeViewModel` and broadcast receivers collect `cooldownState`.
- **Zero-Lag Concurrent App Refresh**: Added `refreshMutex` with `tryLock()` guard and single-query batch DB caching in `BluetoothMonitor`, eliminating UI stutter and queued refresh storms.

## [1.0.4] - 2026-10-14

### Summary
Comprehensive bug fixes for accidental timer auto-extend, volume key detection during fade, neat minimalist notification, tutorial clarity for volume fade & reconnect blocker, prompt mid-session disconnect handling, case battery status display, instantaneous app refresh, and lively primary sleep target toggle.

### ⏱️ Volume Key Extend & Accidental 5-Minute Auto-Extend Fix
- **Programmatic Fade Echo Immunity**: Fixed race condition in `PlaybackController` where internal downward volume steps during fade triggered false-positive user cancellations. The controller now tracks programmatic change timestamps and volumes, ignoring initial broadcasts and internal steps. Only deliberate volume increases or changes outside the step window trigger cancellation.
- **Physical Volume Button Feature Preserved**: Retained the core user feature where pressing the physical volume button during fade out cancels the fade, restores the original volume, and extends the timer by 5 minutes.
- **Call Protection Refinement**: Removed `AudioManager.MODE_IN_COMMUNICATION` from `isCallActive` in `TimerService`. Only genuine telephony calls (`MODE_IN_CALL` / `MODE_RINGTONE`) trigger call protection, preventing audio players or games from rogue auto-extending.
- **Deliberate Shake Gesture**: Raised accelerometer threshold from 13.5 to 18.5 m/s² and required a deliberate two-pulse shake sequence within 1000ms in `ShakeDetector`, eliminating accidental extends from bed rustles or placing phone on nightstand.

### 🔔 Neat and Clean Notification
- **Streamlined Layout**: Modernized `AppNotifications.timerNotification` with clean titles, device branding, and concise status ("Turns off at X:XX • Fades audio" / "Disconnecting in 1m • Press volume key to extend").
- **Concise Actions**: Replaced lengthy button text with standard, clean action buttons (`[ Pause ]` / `[ Resume ]`, `[ +5m ]`, `[ Stop ]`).

### 📚 Interactive Tutorial Clarity
- **Clear Volume Fade & Reconnect Blocker Explanations**: Updated Step 2 in `TutorialDialog` with simple, plain English explanations detailing why volume fades gently before stopping (preventing sudden wake-ups) and why reconnect blocker keeps earbuds from automatically reconnecting while sleeping.

### 🔌 Prompt Mid-Session Disconnect Handling
- **5-Second Grace Disconnect**: In `TimerService`, reduced mid-session disconnect detection from 60s to 5s. If all target devices or connected audio devices disconnect (or Bluetooth is turned off), the service promptly finishes the session, updates UI state, and stops cleanly without lingering or auto-extending.
- **Auto-Target Assignment**: Starting a timer without picking a specific device now auto-binds to currently connected audio devices so disconnect detection always functions.

### 🔋 Grounded Earbuds Case Battery Support
- **Hardware-Grounded Providers**: Replaced speculative metadata reflection and synthetic "Good" fallbacks with verified hardware providers (`AppleCaseBatteryProvider` via BLE proximity pairing adverts for AirPods/Beats on Android 12+, and `GalaxyBudsCaseBatteryProvider` via RFCOMM SPP for Samsung Galaxy Buds).
- **Honest Telemetry Reporting**: Never synthesizes or fabricates battery percentages. Unsupported earbuds (boAt, OnePlus, Realme, Xiaomi, Noise, Pixel Buds) truthfully display `--` with clear explanation.
- **Dynamic Charging & Staleness Status**: Earbud battery card displays real-time charging bolt indicator when case is charging, and notes "seen Nm ago" when case telemetry is older than 5 minutes.

### ⏱️ Accurate Usage Timing & Synchronization
- **Eliminated Live Double-Counting**: Fixed `TimerService` to properly record background listening duration before starting a timer and reset connection timestamps, preventing active Bluetooth connection time from double-counting with completed sessions.
- **Synchronized Chart & Device Statistics**: Aligned Week and Month chart aggregation logic with device stats so bar totals mathematically match device stat summaries.
- **Sub-30s Session Purge**: Filtered out ghost sessions under 30 seconds from incrementing daily session counts or appearing in usage history.

### ⚡ Instantaneous App Refresh
- **Persistent Profile Proxies**: Cached `BluetoothProfile` proxies (`A2DP` and `HEADSET`) on startup in `BluetoothMonitor`, allowing profile device queries to complete synchronously in < 1ms instead of blocking for 1.5s on every refresh.

### ⭐ Lively Primary Sleep Target Toggle
- **Reactive State Observation**: In `UsageScreen`, dynamically derived `selectedDevice` from `state.deviceStats` by address. Toggling "Set as Primary Sleep Target (Favorite)" now updates the bottom sheet, card status, and star badge instantaneously with haptic feedback, while triggering `btMonitor.refresh()` for immediate home screen updates.

## [1.0.3] - 2026-10-13

### Summary
Hardware-level Bluetooth real-time responsiveness, rotary dial haptic actuation, accurate neckband vs TWS auto-detection, case battery decoding, and disconnect test verification fixes.

### ⚡ Real-Time Bluetooth Connection Detection
- **Exported Broadcast Receiver on Android 13+ (API 33+)**: Resolved issue where Bluetooth connection events were dropped when connected while the app was already open. System broadcasts (`ACTION_ACL_CONNECTED`, `ACTION_ACL_DISCONNECTED`, `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED`, `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED`) now register with `ContextCompat.RECEIVER_EXPORTED`.
- **Zero-Latency Audio Framework Callback**: Registered `AudioManager.AudioDeviceCallback` directly inside `BluetoothMonitor`. Connecting or disconnecting any Bluetooth headphone immediately triggers `onAudioDevicesAdded` / `onAudioDevicesRemoved` for live UI updates.

### 🎛️ Rotary Dial Haptics Fix
- **Bypassed OEM Touch Muting**: Mapped haptic attributes to `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION` with `VibrationAttributes.USAGE_ALARM` fallback, preventing Samsung, Xiaomi, Oppo, and Vivo OS settings from muting dial vibrations.
- **Calibrated Motor Pulse & Native View Tick**: Calibrated tick pulse to 25ms duration at amplitude 180 (overcoming mechanical inertia on ERM/LRA motors) and combined it with `view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, FLAG_IGNORE_VIEW_SETTING)` inside `RotaryDial`.

### 🎧 Automatic Earbuds vs Neckband Detection
- **Comprehensive Indian & Global Neckband Heuristics**: Added automatic detection for popular neckband series (`boAt Rockerz`, `OnePlus Bullets Wireless`, `Realme Buds Wireless`, `Boult Curve`, `Noise Tune`, `pTron Tangent`, `Sony WI-`, `Zebronics Zeb-Evolve`, etc.) and TWS earbuds (`AirPods`, `Galaxy Buds`, `Pixel Buds`, `Airdopes`, `Buds Air`, etc.).
- **Auto-Correction of Stored Devices**: Automatically re-infers and updates devices previously misclassified as `EARBUDS` by default in Room DB without requiring manual user changes.

### 🔋 Battery Status & Case Decoding
- **Binary Byte & String Decoder**: Corrected Google Fast Pair / Android Bluetooth metadata parser (keys 10, 11, 12, 23) to safely decode both raw binary bytes (`0..100`) and UTF-8 percentage strings.
- **Accurate UI Adaptability**: Neckbands display a streamlined single battery pill with device type badge. TWS earbuds display individual Left, Right, and Case percentages (or "Closed" if lid is shut).

### 🧪 Test Disconnect Button False-Negative Fix
- **Live Disconnect Verification**: Enhanced `isDeviceActuallyConnected` in `BluetoothDisconnector` with synchronous `AudioManager` audio routing checks and `BluetoothAdapter` profile state verification.
- **Polling Loop & Winning Method Persistence**: Added a 3-second post-execution polling loop in `testDisconnect` to accommodate OEM Bluetooth stack teardown delays and persist the verified winning method to Room DB.

## [1.0.2] - 2026-10-09

### Summary
Comprehensive production hardening, security lockdown, battery optimization, process resilience, database migrations, and architectural cleanup release.

### 🛡️ Process Resilience & Reliability (P0)
- **Sticky Service & Alarm Death Recovery**: Added robust null-intent recovery and alarm backstop activation (`ACTION_ALARM_BACKSTOP`) to `TimerService` (`START_STICKY`). When revived by Android or triggered by `PendingIntent.getForegroundService`, the service reconstructs active state, start time, and accumulated pause duration from persistent DataStore prefs, immediately invokes `startForeground()` within milliseconds to comply with Android 14/15 background service limits, and either resumes countdown or executes immediate graceful shutdown if the wall clock target has passed.
- **AlarmManager Backstop**: Scheduled `AlarmManager.setAlarmClock` matching timer expiration to guarantee timely expiry even under aggressive OEM background task killing.
- **Double Expiry Prevention**: Introduced atomic `isExpiring` guard preventing concurrent expiry runs between the background alarm and active coroutine tick loop.
- **Preloaded Settings**: Settings are loaded synchronously via `app.prefs.settings.first()` prior to timer start, restore, or expiry executions.
- **Resilient Disconnect Teardown**: Replaced uncancelable coroutine suspensions with `suspendCancellableCoroutine` in `BluetoothDisconnector`, added `cont.invokeOnCancellation` cleanup, and bounded disconnect execution with an overall 15 s timeout (`withTimeoutOrNull(15_000L)`). Guaranteed in `TimerService` that media volume restoration, screen lock, and session logging execute in a `finally` block even if disconnect times out.
- **Honest Disconnect Accounting**: Added `audio_stopped` and `disconnect_confirmed` tracking to `SessionEntity` (Room DB schema v3). System dialog fallback (`ACTION_REQUEST_DISABLE`) now post-verifies adapter state before marking success and only prompts at most once per expiry. Disconnect attempts now only target actually connected devices rather than all bonded devices. Reflective disconnect methods failing 3+ times consecutively are demoted.
- **Explicit Room Migrations**: Added migrations `MIGRATION_1_2` (adding `error_message`) and `MIGRATION_2_3` (adding `audio_stopped`) with `fallbackToDestructiveMigrationOnDowngrade()`, preserving user history, favorites, and learned disconnect methods during upgrades from 1.0.1.

### 🔒 Security, Privacy & Telemetry (P1)
- **Foreground Service Type Qualification**: Added `CHANGE_NETWORK_STATE` (normal install-time permission) and wrapped all `startForeground` invocations in `startForegroundSafe` with graceful error handling to guarantee `connectedDevice` foreground service type requirements on Android 14 and 15 without crashes.
- **Receiver Export Security**: Explicitly registered all dynamic receivers (`BluetoothMonitor`, `PlaybackController`) with `RECEIVER_NOT_EXPORTED` on Android 13+ (API 33+).
- **Complete Firebase Removal & Zero Remote Telemetry**: Fully removed all Firebase SDK dependencies (Firestore, Auth, Analytics, Crashlytics, Google Services). The app operates 100% offline with zero external network tracking or telemetry, reducing the release APK size by >54% (down to 3.2 MB).
- **Permission Minimization**: Pruned unused manifest permissions (`ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `REQUEST_INSTALL_PACKAGES`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `AD_ID`, `ACCESS_ADSERVICES_*`).
- **Lightweight Update Checker**: Replaced custom in-app APK installer with a rate-limited (max 1/day) GitHub releases check that directs users securely to browser downloads, eliminating `FileProvider` and install package attack surfaces.

### ⚡ Performance & Battery Optimization (P2)
- **Eliminated 1-Second Broadcast Spam & Dead Code**: Removed periodic tick broadcasts and `com.sleepbt.TIMER_END` broadcasts from `TimerService`. The UI directly observes `SleepBTApp.timerState` StateFlow without IPC overhead.
- **Cached Settings & Reduced Disk I/O**: Cached `AppSettings` in `TimerService` during active countdowns to eliminate repeated disk reads during timer ticks.
- **Atomic Usage Aggregation**: Added atomic SQLite `recordUsage` query in `DailyUsageDao` to prevent race conditions during session logging.
- **ProGuard & Build Rules**: Streamlined `proguard-rules.pro` with targeted keep rules for reflective Bluetooth profiles and Room schemas. Added keystore credential validation during release builds.

### 🎛️ User Experience & Wording (P3)
- **Accurate Feature Copy**: Renamed "Ear Health" screen to "Connected Time" to transparently reflect Bluetooth connection duration rather than decibel sound exposure or medical metrics.
- **Legal & Docs Transparency**: Updated `Privacy Policy`, `Terms of Service`, `network_security_config.xml`, and `README.md` to accurately disclose crash diagnostics, optional analytics, and GitHub update checks.

### 🧪 Verification & Tooling (P4)
- **Automated Unit Tests**: Added unit test coverage for pure `TimerCalculations` math (process death restore, active duration excluding pauses, auto-extend caps), `MethodOrderResolver` demotion, in-memory Room `recordUsage`, Room migrations (1→2 and 2→3), and disconnect timeout teardown.
- **Tooling & AGP**: Upgraded Android Gradle Plugin to 8.6.0 for official compileSdk 35 support.
