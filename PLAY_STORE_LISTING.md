# Google Play Console — Store Listing & Launch Guide for Setu

This document contains everything needed to publish **Setu** to the Google Play Store.

---

## 📋 1. Store Listing Metadata

### App Details
- **App Name:** `Setu: NFC Profile & Contact Share` *(35 / 50 characters)*
- **Package Name:** `com.nfccontactshare`
- **Default Language:** English (United States) - en-US
- **Category:** Business / Social

---

### Short Description *(80 characters max)*
> **"Instant zero-app NFC profile and contact card sharing with a single phone touch."**
*(79 characters)*

---

### Full Description *(4,000 characters max)*

```markdown
Bridge connections instantly with Setu — the fastest, most modern way to exchange social profiles and contact cards with a single phone touch. Zero apps required for the receiver!

Turn your Android phone into an active digital smart card. Whether you're networking at a conference, meeting a client, or connecting with friends, Setu lets you share exactly who you are in under one second.

✨ ZERO-APP TAP SHARING
Touch your phone against any other smartphone (iPhone, Android, Samsung, Pixel) to share your profile instantly. The other person does not need Setu or any special app installed — their phone natively detects your card and opens your link immediately!

📲 DIRECT NATIVE APP LAUNCHING
Unlike ordinary digital business cards that open clunky web viewers, Setu directly triggers native apps on the receiver's phone:
• Instagram: Opens your profile directly inside their native Instagram app.
• LinkedIn: Launches your professional profile inside their LinkedIn app.
• WhatsApp: Initiates a direct 1-tap WhatsApp chat without saving the number first.
• Contact Card (vCard): Downloads your complete contact details for 1-tap address book saving.
• Website / Portfolio: Opens your custom link or company site in their default browser.

🎛️ SWIPEABLE RADAR PLATFORM SELECTOR
You have complete freedom over what you share. Simply swipe across the interactive radar screen to switch broadcasting modes between Instagram, LinkedIn, WhatsApp, vCard, or your Website in real time.

💎 AMBIENT LUMINOUS DESIGN
Built with a fluid pearl-glass aesthetic, tactile pulse waves, and an edge-to-edge layout tailored for modern Android devices. Setup your card in seconds with custom avatars, role headlines, and instant card previews.

📜 EXCHANGE HISTORY & PRIVACY
Keep track of everyone you meet with an offline exchange log. Review cards, send messages, call, or manage contacts with complete peace of mind.
• 100% Offline & Private: Your profile and contacts never leave your device.
• No Cloud Tracking: Zero third-party analytics or server logging.

Upgrade your networking game. Download Setu and bridge the gap with a single touch!
```

---

## 🛡️ 2. Google Play Data Safety Section Declarations

When completing the **Data safety** form in Google Play Console:

1. **Does your app collect or share any user data?**
   - Select: **No** *(Setu stores all data strictly locally on the device)*.
2. **Is all user data transferred over a secure connection?**
   - Select: **Yes** *(Local NFC and direct peer-to-peer transmission)*.
3. **Do you provide a way for users to request data deletion?**
   - Select: **Yes** *(Users can clear all history and edit/delete their profile locally anytime)*.

---

## 🔑 3. Generating a Production Release Keystore

Before publishing to Google Play, you sign the app bundle (`.aab`) with your private release key.

Run this command in PowerShell to create your production release keystore:

```powershell
keytool -genkey -v -keystore setu-release.jks -alias setu-key -keyalg RSA -keysize 2048 -validity 10000
```

> **IMPORTANT:**
> 1. Store `setu-release.jks` and your keystore password in a safe, secure place (e.g. password manager).
> 2. **NEVER commit `setu-release.jks` into git!** It is already included in `.gitignore`.

---

## 📦 4. Building the Production App Bundle (.aab)

Once your release key is generated, build the bundle using Gradle:

```powershell
./gradlew bundleRelease
```

The resulting file:
`app/build/outputs/bundle/release/app-release.aab`
is the exact file to upload to the **Internal Testing**, **Closed Testing**, or **Production** track on [Google Play Console](https://play.google.com/console).

---

## 🎨 5. Graphic Assets Checklist

| Asset | Dimensions | Status |
| :--- | :--- | :--- |
| **App Icon** | 512 x 512 PNG | ✅ Ready (`icon_512.png` & adaptive vector) |
| **Feature Graphic** | 1024 x 500 PNG | 🎨 Template ready |
| **Phone Screenshots** | Min 2 screenshots (1080 x 2400) | 📸 Capture from test phone |
| **Privacy Policy URL** | Live public URL | ✅ `https://github.com/Tejender09/Setu/blob/main/PRIVACY_POLICY.md` |
