# Setu 🌉
### Instant Proximity Profile & Contact Sharing for Android

**Setu** (meaning *Bridge*) is a modern Android application that bridges connections between people in seconds. Using **NFC Host Card Emulation (HCE)**, Setu turns your phone into a dynamic virtual smart card that broadcasts your digital profile instantly when touched against any other smartphone — **Zero App Required on the receiver's device!**

---

## ✨ Features

- **⚡ Zero-App NFC Tap Sharing:**
  - Emulates an **NFC Forum Type 4 Tag** via Android Host Card Emulation (HCE).
  - Works with **ANY smartphone** (iPhone, Samsung, Pixel, etc.) straight out of the box — no companion app or download required for the person receiving your profile.
- **📲 Direct Native App Launching:**
  - **Instagram:** Instantly launches your profile directly inside the receiver's native Instagram app (`instagram://user?username=...`) with fallback to web.
  - **LinkedIn:** Opens your professional profile directly in the LinkedIn app.
  - **WhatsApp:** Starts a 1-tap direct chat on WhatsApp without needing to save the number first.
  - **Contact Card (vCard):** Downloads a complete contact card with name, phone, email, title, and social links for 1-tap address book saving.
  - **Website / Portfolio:** Opens your personal portfolio or business site in their default browser.
- **🎛️ Interactive Swipeable Radar Screen:**
  - Modern `ViewPager2` carousel with fluid, unclipped multi-ring radar pulse animations.
  - Real-time HCE broadcasting mode updates as you swipe between platforms.
- **💎 Ambient Luminous Pearl Design:**
  - Frosted glassmorphism (`pearl-glass`), soft ambient gradients, and edge-to-edge system insets.
  - Clean card configuration with photo picker and instant preview.
- **📜 Exchange History:**
  - SQLite database tracking all received cards and tapped connections with timestamp and direct contact actions.
- **📶 Nearby Connections Media Beaming:**
  - Optional high-speed peer-to-peer Bluetooth / Wi-Fi Direct media sharing for full-resolution profile photos and gallery images.

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin 2.0+
- **Platform:** Android 8.0+ (API 26 to API 35)
- **UI Framework:** Android ViewBinding, Material Components 3, ViewPager2, CoordinatorLayout
- **NFC:** Android Host Card Emulation (`HostApduService`, Type 4 NDEF Tag Emulation, AID `D2760000850101`)
- **P2P Networking:** Google Play Services Nearby Connections API (P2P_STAR strategy)
- **Local Storage:** SQLite (`HistoryDbHelper`), SharedPreferences (`PrefsManager`)

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug / Meerkat or Command Line Tools
- JDK 17 or JDK 21
- Android SDK (API 34 / 35)

### Build Debug APK
```bash
# Clone the repository
git clone https://github.com/Tejender09/Setu.git
cd Setu

# Build Debug APK
./gradlew assembleDebug
```

The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Install onto Device via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔒 Permissions Used
- `android.permission.NFC` — Reading and emulating NFC tags.
- `android.permission.BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` — High-speed nearby peer discovery.
- `android.permission.NEARBY_WIFI_DEVICES` — Direct Wi-Fi payload streaming.
- `android.permission.WRITE_CONTACTS` — Saving tapped cards directly into your phone's address book.

---

## 📄 License
MIT License. Created by [Tejender](https://github.com/Tejender09).
