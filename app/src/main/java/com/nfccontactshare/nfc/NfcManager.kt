package com.nfccontactshare.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.ReaderCallback
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import com.nfccontactshare.model.ContactCard

/**
 * Manages NFC foreground reader mode.
 *
 * Supports reading standard NDEF tags, NFC smart cards, and Android Host Card Emulation (HCE) devices
 * via IsoDep fallback.
 */
class NfcManager(
    private val activity: Activity,
    private val listener: NfcEventListener
) {

    interface NfcEventListener {
        /** Called when an NFC tap is physically detected in reader mode. */
        fun onNfcTapDetected()
        /** Called when a ContactCard is successfully read and parsed over NFC. */
        fun onContactReceivedViaNfc(card: ContactCard)
        /** Called when a tag was tapped but contact data could not be read or parsed. */
        fun onNfcReadFailed(reason: String)
    }

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val isNfcAvailable: Boolean get() = nfcAdapter != null
    val isNfcEnabled: Boolean get() = nfcAdapter?.isEnabled == true

    private val readerCallback = ReaderCallback { tag ->
        val tagId = tag.id?.toHex() ?: "unknown"
        Log.d(TAG, "NFC tag detected in reader mode: $tagId, techs=${tag.techList.joinToString()}")
        activity.runOnUiThread { listener.onNfcTapDetected() }
        tryReadTag(tag)
    }

    /** Enable NFC reader mode while in Scan Mode. */
    fun enableForegroundReader() {
        // We do NOT use FLAG_READER_SKIP_NDEF_CHECK so Android automatically negotiates NDEF!
        nfcAdapter?.enableReaderMode(
            activity,
            readerCallback,
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V,
            Bundle()
        )
        Log.d(TAG, "NFC reader mode enabled (all techs, auto-NDEF)")
    }

    /** Disable NFC reader mode to allow Host Card Emulation (HCE) to function cleanly. */
    fun disableForegroundReader() {
        nfcAdapter?.disableReaderMode(activity)
        Log.d(TAG, "NFC reader mode disabled")
    }

    private fun tryReadTag(tag: Tag) {
        Thread {
            var card: ContactCard? = null

            // 1. Try standard Android Ndef tech
            try {
                val ndef = Ndef.get(tag)
                if (ndef != null) {
                    ndef.connect()
                    val msg = ndef.ndefMessage ?: ndef.cachedNdefMessage
                    if (msg != null) {
                        card = parseContactFromNdef(msg)
                    }
                    try { ndef.close() } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "Standard Ndef.get read failed: ${e.message}")
            }

            // 2. If Ndef was null or failed (common for Android HCE devices), try IsoDep APDU reading
            if (card == null) {
                val isoDep = IsoDep.get(tag)
                if (isoDep != null) {
                    Log.d(TAG, "Attempting IsoDep Type 4 NDEF APDU read...")
                    card = readNdefViaIsoDep(isoDep)
                }
            }

            // 3. Dispatch result to UI
            if (card != null) {
                Log.d(TAG, "Successfully read contact: ${card.name}, ${card.phone}")
                activity.runOnUiThread { listener.onContactReceivedViaNfc(card) }
            } else {
                Log.w(TAG, "No contact card could be parsed from tag")
                activity.runOnUiThread { listener.onNfcReadFailed("Could not parse contact from tag") }
            }
        }.start()
    }

    /**
     * Reads Type 4 Tag NDEF file directly over ISO-DEP (ISO 7816-4 APDUs).
     * Essential for reading Android phones emulating Type 4 Tag vCard.
     */
    private fun readNdefViaIsoDep(isoDep: IsoDep): ContactCard? {
        try {
            isoDep.connect()
            isoDep.timeout = 3000

            // 1. Select NDEF Application v2.0
            val selectV2 = byteArrayOf(
                0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), 0x07.toByte(),
                0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(), 0x85.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte()
            )
            var resp = isoDep.transceive(selectV2)
            if (!isApduSuccess(resp)) {
                // Fallback: Select v1.0
                val selectV1 = byteArrayOf(
                    0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), 0x07.toByte(),
                    0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(), 0x85.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte()
                )
                resp = isoDep.transceive(selectV1)
                if (!isApduSuccess(resp)) {
                    Log.w(TAG, "IsoDep: Select NDEF App failed (${resp.toHex()})")
                    return null
                }
            }

            // 2. Select NDEF File (E1 04)
            val selectNdef = byteArrayOf(
                0x00.toByte(), 0xA4.toByte(), 0x00.toByte(), 0x0C.toByte(), 0x02.toByte(),
                0xE1.toByte(), 0x04.toByte()
            )
            resp = isoDep.transceive(selectNdef)
            if (!isApduSuccess(resp)) {
                val selectNdefAlt = byteArrayOf(
                    0x00.toByte(), 0xA4.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte(),
                    0xE1.toByte(), 0x04.toByte()
                )
                resp = isoDep.transceive(selectNdefAlt)
                if (!isApduSuccess(resp)) {
                    Log.w(TAG, "IsoDep: Select NDEF File failed (${resp.toHex()})")
                    return null
                }
            }

            // 3. Read NLEN (first 2 bytes)
            val readNlen = byteArrayOf(0x00.toByte(), 0xB0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte())
            resp = isoDep.transceive(readNlen)
            if (resp.size < 4 || !isApduSuccess(resp)) {
                Log.w(TAG, "IsoDep: Read NLEN failed")
                return null
            }

            val nlen = ((resp[0].toInt() and 0xFF) shl 8) or (resp[1].toInt() and 0xFF)
            Log.d(TAG, "IsoDep: NLEN = $nlen bytes")
            if (nlen <= 0 || nlen > 4096) return null

            // 4. Read NDEF data in blocks of max 128 bytes
            val ndefBytes = ByteArray(nlen)
            var bytesRead = 0
            while (bytesRead < nlen) {
                val toRead = minOf(128, nlen - bytesRead)
                val offset = 2 + bytesRead
                val p1 = ((offset ushr 8) and 0xFF).toByte()
                val p2 = (offset and 0xFF).toByte()
                val le = (toRead and 0xFF).toByte()
                val readCmd = byteArrayOf(0x00.toByte(), 0xB0.toByte(), p1, p2, le)
                val chunk = isoDep.transceive(readCmd)
                if (chunk.size < 2 || !isApduSuccess(chunk)) {
                    Log.w(TAG, "IsoDep: Read chunk at offset $offset failed")
                    return null
                }
                val dataLen = chunk.size - 2
                System.arraycopy(chunk, 0, ndefBytes, bytesRead, minOf(dataLen, toRead))
                bytesRead += minOf(dataLen, toRead)
            }

            val ndefMessage = NdefMessage(ndefBytes)
            return parseContactFromNdef(ndefMessage)
        } catch (e: Exception) {
            Log.e(TAG, "IsoDep reading exception: ${e.message}")
            return null
        } finally {
            try { isoDep.close() } catch (e: Exception) {}
        }
    }

    private fun parseContactFromNdef(ndefMessage: NdefMessage): ContactCard? {
        for (record in ndefMessage.records) {
            // Check URI records (e.g. instagram, linkedin, whatsapp, tel)
            try {
                val uri = record.toUri()
                if (uri != null) {
                    val urlStr = uri.toString()
                    when {
                        urlStr.contains("instagram.com", ignoreCase = true) -> {
                            val handle = uri.pathSegments.lastOrNull()?.trimEnd('/') ?: "Instagram"
                            return ContactCard(name = "@$handle", phone = "", instagram = handle, note = "Instagram: $urlStr")
                        }
                        urlStr.contains("linkedin.com", ignoreCase = true) -> {
                            val id = uri.pathSegments.lastOrNull()?.trimEnd('/') ?: "LinkedIn"
                            return ContactCard(name = id, phone = "", linkedIn = id, note = "LinkedIn: $urlStr")
                        }
                        urlStr.contains("wa.me", ignoreCase = true) -> {
                            val phone = uri.pathSegments.lastOrNull() ?: ""
                            return ContactCard(name = "WhatsApp", phone = phone, note = "WhatsApp: $urlStr")
                        }
                        uri.scheme == "tel" -> {
                            val phone = uri.schemeSpecificPart
                            return ContactCard(name = "Contact", phone = phone)
                        }
                        else -> {
                            return ContactCard(name = uri.host ?: "Web Link", phone = "", website = urlStr, note = urlStr)
                        }
                    }
                }
            } catch (e: Exception) {}

            // Check payload strings (vCard / custom string)
            try {
                val payload = String(record.payload, Charsets.UTF_8)
                val card = ContactCard.fromTransmitString(payload)
                if (card != null) {
                    return card
                }
            } catch (e: Exception) {}
        }
        return null
    }

    private fun isApduSuccess(response: ByteArray): Boolean {
        if (response.size < 2) return false
        val sw1 = response[response.size - 2]
        val sw2 = response[response.size - 1]
        return sw1 == 0x90.toByte() && sw2 == 0x00.toByte()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "NfcManager"
    }
}
