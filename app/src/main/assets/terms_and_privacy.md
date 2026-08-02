# SleepBT Terms of Service & Privacy Policy

**Effective Date:** Version 1.0.0 — August 2026

Welcome to **SleepBT**, your privacy-focused Bluetooth sleep timer and ear health protector. Please read these Terms of Service and Privacy Policy carefully before using the application.

---

### 1. 100% On-Device & Offline Privacy
* **Zero Server Infrastructure:** SleepBT operates entirely offline on your device. We do not host, operate, or connect to any remote servers.
* **No Telemetry or Tracking:** SleepBT contains **no analytics SDKs**, no crash-reporting tracking, no ad networks, and no telemetry services. 
* **Local Data Storage:** All application data, timer presets, connected device history, and listening duration metrics are saved exclusively on your device using encrypted local SQLite database and DataStore files (`sleepbt_prefs` and `sleepbt.db`).
* **No Cloud Backups:** Since no data is transmitted to external servers, deleting the app will permanently remove all locally saved configuration data.

---

### 2. Bluetooth Device Disconnection & System Controls
* **Bluetooth Management:** SleepBT uses Android Bluetooth system APIs (`BluetoothAdapter` and `BluetoothDevice`) to detect connected audio endpoints (headphones, earbuds, speakers) and trigger disconnection upon timer expiry.
* **Smart Volume Fade Out:** To protect your hearing and prevent sudden audio disruptions during sleep, SleepBT gradually reduces system media volume prior to disconnecting audio streams.
* **Playback Pause:** SleepBT issues media focus requests and playback pause commands to ensure audio content from Spotify, YouTube, Apple Music, podcasts, and video players stops playing cleanly.

---

### 3. Screen Locking & Device Administrator Permission
* **Optional Lock Feature:** If enabled in Settings, SleepBT can automatically turn off and lock your device screen when the sleep timer expires.
* **Device Administrator Permission:** This feature requires standard Android Device Administrator (`SleepBTDeviceAdmin`) privileges (`force-lock`). SleepBT uses this permission **solely** to call `DevicePolicyManager.lockNow()`. No other administrative policies (such as password policies or remote wipes) are requested or used.

---

### 4. Health Awareness & Disclaimer
* **Ear Health Metrics:** SleepBT tracks daily listening duration to encourage safe audio consumption and prevent long-term noise-induced hearing fatigue.
* **Not Medical Advice:** All health insights, duration thresholds, and listening metrics provided within SleepBT are for general personal awareness only and do not constitute medical diagnosis, advice, or treatment.

---

### 5. Battery Optimization & Background Execution
* **Background Timer Reliability:** To ensure your sleep timer fires reliably while your phone is in deep sleep mode (Doze mode), SleepBT utilizes a Foreground Service with a persistent notification.
* **OEM Battery Savers:** On certain Android devices (e.g., Xiaomi, Samsung, OnePlus), you may need to exempt SleepBT from aggressive battery optimization settings to prevent the OS from killing the timer in the background.

---

### 6. Updates to Terms
* **Version Control:** If these Terms of Service or Privacy Policy are updated in future releases, you will be prompted to review and re-accept the updated terms upon launching the application.

---

### 7. Contact & Open Governance
SleepBT is crafted by **DreamSync** with an absolute commitment to user privacy, offline security, and open device management.

By tapping **"Agree & Continue"**, you confirm that you have read, understood, and agreed to these Terms of Service and Privacy Policy.
