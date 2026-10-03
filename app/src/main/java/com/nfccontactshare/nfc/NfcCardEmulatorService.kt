package com.nfccontactshare.nfc

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.nfccontactshare.data.PrefsManager
import com.nfccontactshare.model.ContactCard

/**
 * HostApduService that emulates an NFC Forum Type 4 Tag.
 *
 * Depending on the user's active choice (Contact Card, Instagram, LinkedIn, WhatsApp, Website),
 * it dynamically beams either a native vCard or a direct verified App Link URL.
 *
 * When an App Link (Instagram / LinkedIn) is tapped, the receiver's phone opens the profile
 * directly inside the native Instagram or LinkedIn app!
 */
class NfcCardEmulatorService : HostApduService() {

    private var selectedFile: SelectedFile = SelectedFile.NONE

    private enum class SelectedFile {
        NONE, CC_FILE, NDEF_FILE
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.isEmpty()) return STATUS_FAILED

        val hexCommand = commandApdu.toHex()
        Log.d(TAG, "==> processCommandApdu: $hexCommand (len=${commandApdu.size})")

        // 1. SELECT NDEF Application (AID: D2760000850101 for v2.0 or D2760000850100 for v1.0)
        if (commandApdu.startsWith(APDU_SELECT_NDEF_APPLICATION_V2) ||
            commandApdu.startsWith(APDU_SELECT_NDEF_APPLICATION_V1)
        ) {
            Log.d(TAG, "<- Selected NDEF Application successfully")
            selectedFile = SelectedFile.NONE
            return STATUS_SUCCESS
        }

        // 2. SELECT CC File (File ID: E1 03)
        if (isSelectFile(commandApdu, 0xE1.toByte(), 0x03.toByte())) {
            Log.d(TAG, "<- Selected CC File (E103)")
            selectedFile = SelectedFile.CC_FILE
            return STATUS_SUCCESS
        }

        // 3. SELECT NDEF File (File ID: E1 04)
        if (isSelectFile(commandApdu, 0xE1.toByte(), 0x04.toByte())) {
            Log.d(TAG, "<- Selected NDEF File (E104)")
            selectedFile = SelectedFile.NDEF_FILE
            return STATUS_SUCCESS
        }

        // 4. READ BINARY (00 B0 [P1] [P2] [Le])
        if (commandApdu.size >= 5 && commandApdu[0] == 0x00.toByte() && commandApdu[1] == 0xB0.toByte()) {
            val offset = ((commandApdu[2].toInt() and 0xFF) shl 8) or (commandApdu[3].toInt() and 0xFF)
            val rawLength = commandApdu[4].toInt() and 0xFF
            val length = if (rawLength == 0) 256 else rawLength

            return when (selectedFile) {
                SelectedFile.CC_FILE -> {
                    Log.d(TAG, "<- Reading CC File: offset=$offset, length=$length")
                    readBinary(CAPABILITY_CONTAINER, offset, length)
                }
                SelectedFile.NDEF_FILE -> {
                    val ndefFile = buildNdefFile()
                    Log.d(TAG, "<- Reading NDEF File: total=${ndefFile.size}, offset=$offset, length=$length")
                    readBinary(ndefFile, offset, length)
                }
                SelectedFile.NONE -> {
                    Log.w(TAG, "<- Read binary failed: no file selected")
                    STATUS_FILE_NOT_FOUND
                }
            }
        }

        Log.w(TAG, "<- Unknown APDU command: $hexCommand")
        return STATUS_FAILED
    }

    override fun onDeactivated(reason: Int) {
        val reasonStr = when (reason) {
            DEACTIVATION_LINK_LOSS -> "LINK_LOSS"
            DEACTIVATION_DESELECTED -> "DESELECTED"
            else -> "UNKNOWN ($reason)"
        }
        Log.d(TAG, "NFC Card Emulation de-activated: $reasonStr")
        selectedFile = SelectedFile.NONE
    }

    private fun isSelectFile(commandApdu: ByteArray, id1: Byte, id2: Byte): Boolean {
        if (commandApdu.size < 7) return false
        if (commandApdu[0] != 0x00.toByte() || commandApdu[1] != 0xA4.toByte()) return false

        val lc = commandApdu[4].toInt() and 0xFF
        if (lc >= 2 && commandApdu.size >= 5 + lc) {
            val fileId1 = commandApdu[5]
            val fileId2 = commandApdu[6]
            return fileId1 == id1 && fileId2 == id2
        }
        return false
    }

    private fun readBinary(data: ByteArray, offset: Int, length: Int): ByteArray {
        if (offset >= data.size) {
            return STATUS_SUCCESS
        }
        val actualLength = minOf(length, data.size - offset)
        val response = ByteArray(actualLength + 2)
        System.arraycopy(data, offset, response, 0, actualLength)
        response[response.size - 2] = 0x90.toByte()
        response[response.size - 1] = 0x00.toByte()
        return response
    }

    /**
     * Dynamically builds NDEF message according to the user's active choice:
     * - Contact Card (vCard)
     * - Instagram profile (opens Instagram app directly)
     * - LinkedIn profile (opens LinkedIn app directly)
     * - WhatsApp chat (opens WhatsApp chat)
     * - Website
     */
    private fun buildNdefFile(): ByteArray {
        val prefs = PrefsManager(applicationContext)
        val profile = prefs.getMyProfile()
        val target = prefs.getActiveShareTarget()

        Log.d(TAG, "buildNdefFile: active target = $target")

        val ndefMessage: NdefMessage = when (target) {
            PrefsManager.ShareTarget.INSTAGRAM -> {
                val rawIg = profile?.instagram?.trim() ?: ""
                val handle = rawIg
                    .removePrefix("@")
                    .removePrefix("https://www.instagram.com/")
                    .removePrefix("http://www.instagram.com/")
                    .removePrefix("https://instagram.com/")
                    .removePrefix("http://instagram.com/")
                    .trimEnd('/')
                val url = if (handle.isNotEmpty()) "https://www.instagram.com/$handle/" else "https://www.instagram.com/"
                Log.d(TAG, "Broadcasting Instagram URL: $url")
                NdefMessage(arrayOf(NdefRecord.createUri(url)))
            }
            PrefsManager.ShareTarget.LINKEDIN -> {
                val rawLi = profile?.linkedIn?.trim() ?: ""
                val id = rawLi
                    .removePrefix("https://www.linkedin.com/in/")
                    .removePrefix("http://www.linkedin.com/in/")
                    .removePrefix("https://linkedin.com/in/")
                    .removePrefix("http://linkedin.com/in/")
                    .removePrefix("in/")
                    .trimEnd('/')
                val url = if (id.isNotEmpty()) "https://www.linkedin.com/in/$id/" else "https://www.linkedin.com/"
                Log.d(TAG, "Broadcasting LinkedIn URL: $url")
                NdefMessage(arrayOf(NdefRecord.createUri(url)))
            }
            PrefsManager.ShareTarget.WHATSAPP -> {
                val cleanPhone = (profile?.phone ?: "").replace(" ", "").replace("-", "").removePrefix("+")
                val url = "https://wa.me/$cleanPhone"
                Log.d(TAG, "Broadcasting WhatsApp URL: $url")
                NdefMessage(arrayOf(NdefRecord.createUri(url)))
            }
            PrefsManager.ShareTarget.WEBSITE -> {
                val raw = profile?.website?.trim() ?: ""
                val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
                Log.d(TAG, "Broadcasting Website URL: $url")
                NdefMessage(arrayOf(NdefRecord.createUri(url)))
            }
            PrefsManager.ShareTarget.CONTACT -> {
                buildContactVCardMessage(profile)
            }
        }

        val ndefBytes = ndefMessage.toByteArray()
        val file = ByteArray(2 + ndefBytes.size)
        file[0] = ((ndefBytes.size ushr 8) and 0xFF).toByte()
        file[1] = (ndefBytes.size and 0xFF).toByte()
        System.arraycopy(ndefBytes, 0, file, 2, ndefBytes.size)
        return file
    }

    private fun buildContactVCardMessage(profile: ContactCard?): NdefMessage {
        val name = profile?.name?.trim() ?: "Contact"
        val phone = profile?.phone?.trim() ?: ""
        val email = profile?.email?.trim() ?: ""
        val note = profile?.note?.trim() ?: ""
        val ig = profile?.instagram?.trim() ?: ""
        val li = profile?.linkedIn?.trim() ?: ""

        val vcard = buildString {
            append("BEGIN:VCARD\r\n")
            append("VERSION:2.1\r\n")
            append("N:;$name;;;\r\n")
            append("FN:$name\r\n")
            if (phone.isNotEmpty()) append("TEL;CELL:$phone\r\n")
            if (email.isNotEmpty()) append("EMAIL;INTERNET:$email\r\n")
            if (note.isNotEmpty()) append("NOTE:$note\r\n")
            if (ig.isNotEmpty()) {
                val cleanIg = ig.removePrefix("@")
                append("URL:https://instagram.com/$cleanIg\r\n")
            }
            if (li.isNotEmpty()) {
                val cleanLi = li.removePrefix("in/").removePrefix("https://www.linkedin.com/in/")
                append("URL:https://linkedin.com/in/$cleanLi\r\n")
            }
            append("END:VCARD\r\n")
        }

        val vcardRecord = NdefRecord.createMime("text/vcard", vcard.toByteArray(Charsets.UTF_8))
        val records = if (phone.isNotEmpty()) {
            val cleanPhone = phone.replace(" ", "").replace("-", "")
            val telRecord = NdefRecord.createUri(Uri.parse("tel:$cleanPhone"))
            arrayOf(vcardRecord, telRecord)
        } else {
            arrayOf(vcardRecord)
        }
        return NdefMessage(records)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (this.size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "NfcHceService"

        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val STATUS_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())

        private val APDU_SELECT_NDEF_APPLICATION_V2 = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), 0x07.toByte(),
            0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(), 0x85.toByte(), 0x01.toByte(), 0x01.toByte()
        )

        private val APDU_SELECT_NDEF_APPLICATION_V1 = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), 0x07.toByte(),
            0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(), 0x85.toByte(), 0x01.toByte(), 0x00.toByte()
        )

        private val CAPABILITY_CONTAINER = byteArrayOf(
            0x00.toByte(), 0x0F.toByte(), // CCLEN: 15 bytes
            0x20.toByte(),                // Mapping version 2.0
            0x00.toByte(), 0xFF.toByte(), // MLe: max read 255 bytes
            0x00.toByte(), 0xFF.toByte(), // MLc: max write 255 bytes
            0x04.toByte(),                // Type = NDEF File Control
            0x06.toByte(),                // Length = 6 bytes
            0xE1.toByte(), 0x04.toByte(), // File ID = E104
            0x08.toByte(), 0x00.toByte(), // Max NDEF size = 2048 bytes
            0x00.toByte(),                // Read access: free (no protection)
            0xFF.toByte()                 // Write access: read-only
        )
    }
}
