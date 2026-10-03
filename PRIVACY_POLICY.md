# Privacy Policy for Setu

**Last Updated:** October 3, 2026

**Setu** ("we", "our", or "the app"), developed by **Tejender**, is committed to protecting your privacy. This Privacy Policy explains how your information is handled when you use the Setu Android application.

---

### 1. Zero Cloud Data Collection & Absolute Privacy

Setu is designed from the ground up with a **local-first, zero-knowledge architecture**:
- **We do NOT collect, store, transmit, sell, or monetize any personal information.**
- We do NOT maintain user accounts or remote cloud servers.
- We do NOT use third-party analytics trackers, advertising networks, or data brokers.
- Everything you enter (name, phone number, email, headline, social handles, website, and profile photo) is stored **strictly on your local device** using encrypted Android internal app storage.

---

### 2. Device Permissions and How They Are Used

Setu requests only the minimum Android permissions required to provide proximity sharing:

| Permission | Purpose | Data Handling |
| :--- | :--- | :--- |
| **NFC (`android.permission.NFC`)** | Emulates an NFC Forum Type 4 Tag (Host Card Emulation) to transmit your digital card to another phone when brought into proximity. | Transmitted directly hardware-to-hardware over NFC radio waves only during active tap. No data is stored externally. |
| **Contacts (`android.permission.WRITE_CONTACTS`)** | Allows you to save a received vCard or contact card directly into your phone’s native address book upon tap. | Used strictly on-device to insert a new contact entry into your address book. We do NOT read or upload your existing address book. |
| **Bluetooth & Wi-Fi Direct (`BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `NEARBY_WIFI_DEVICES`)** | Powers the optional Google Nearby Connections feature for high-speed peer-to-peer transmission of high-resolution profile photos. | Data is transmitted directly peer-to-peer between the two devices using local Wi-Fi / Bluetooth. No intermediary server is involved. |
| **Vibration (`android.permission.VIBRATE`)** | Provides brief tactile feedback when an NFC touch or card read is detected. | Hardware control only. |

---

### 3. Contact Exchange History

When you receive a contact card or social profile from another user:
- The exchanged card is saved locally in an internal SQLite database on your device.
- You can search, call, message, open their profiles, or permanently delete individual or all entries from your history at any time.
- This data never leaves your device.

---

### 4. Third-Party Deep Links

When tapping to open another user's profile on **Instagram**, **LinkedIn**, or **WhatsApp**, Setu routes the request to the official native apps installed on your device or to their official web URLs. Your interactions within those third-party services are subject to their respective privacy policies.

---

### 5. Children’s Privacy

Setu does not collect any personal information from anyone, including children under the age of 13.

---

### 6. Changes to This Policy

We may update our Privacy Policy periodically. Any changes will be posted to this page with an updated "Last Updated" date.

---

### 7. Contact Us

If you have questions or concerns regarding this Privacy Policy, please contact:
- **Developer:** Tejender
- **Email:** `tejendersinghshekhawat@gmail.com`
- **GitHub Repository:** [https://github.com/Tejender09/Setu](https://github.com/Tejender09/Setu)
