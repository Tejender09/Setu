package com.nfccontactshare.model

import java.io.Serializable

/**
 * Represents a user's contact card that will be shared via NFC + Nearby Connections,
 * with social profiles (Instagram, LinkedIn, Website) and optional attached photo flag.
 */
data class ContactCard(
    val name: String,
    val phone: String,
    val email: String = "",
    val note: String = "",
    val instagram: String = "",
    val linkedIn: String = "",
    val website: String = "",
    val hasPhoto: Boolean = false
) : Serializable {

    /**
     * Serialize to pipe-delimited string for NFC/Nearby transmission.
     */
    fun toTransmitString(): String {
        val photoFlag = if (hasPhoto) "1" else "0"
        return "$name|$phone|$email|$note|$photoFlag|$instagram|$linkedIn|$website"
    }

    companion object {
        /**
         * Deserialize from either pipe-delimited string or standard vCard.
         */
        fun fromTransmitString(data: String): ContactCard? {
            val trimmed = data.trim()
            if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true)) {
                return fromVCard(trimmed)
            }
            return try {
                val parts = trimmed.split("|")
                val hasPhotoFlag = parts.getOrNull(4) == "1"
                ContactCard(
                    name = parts.getOrElse(0) { "" },
                    phone = parts.getOrElse(1) { "" },
                    email = parts.getOrElse(2) { "" },
                    note = parts.getOrElse(3) { "" },
                    hasPhoto = hasPhotoFlag,
                    instagram = parts.getOrElse(5) { "" },
                    linkedIn = parts.getOrElse(6) { "" },
                    website = parts.getOrElse(7) { "" }
                )
            } catch (e: Exception) {
                null
            }
        }

        fun fromVCard(vcard: String): ContactCard? {
            return try {
                var name = ""
                var phone = ""
                var email = ""
                var note = ""
                var instagram = ""
                var linkedIn = ""
                var website = ""

                val lines = vcard.lines()
                for (line in lines) {
                    val l = line.trim()
                    when {
                        l.startsWith("FN:", ignoreCase = true) -> {
                            name = l.substring(3).trim()
                        }
                        l.startsWith("N:", ignoreCase = true) && name.isEmpty() -> {
                            val parts = l.substring(2).split(";")
                            val last = parts.getOrNull(0)?.trim() ?: ""
                            val first = parts.getOrNull(1)?.trim() ?: ""
                            name = "$first $last".trim()
                        }
                        l.startsWith("TEL", ignoreCase = true) -> {
                            val idx = l.indexOf(":")
                            if (idx != -1) {
                                phone = l.substring(idx + 1).trim()
                            }
                        }
                        l.startsWith("EMAIL", ignoreCase = true) -> {
                            val idx = l.indexOf(":")
                            if (idx != -1) {
                                email = l.substring(idx + 1).trim()
                            }
                        }
                        l.startsWith("NOTE:", ignoreCase = true) -> {
                            note = l.substring(5).trim()
                        }
                        l.contains("instagram.com", ignoreCase = true) -> {
                            val idx = l.indexOf(":")
                            if (idx != -1) instagram = l.substring(idx + 1).trim()
                        }
                        l.contains("linkedin.com", ignoreCase = true) -> {
                            val idx = l.indexOf(":")
                            if (idx != -1) linkedIn = l.substring(idx + 1).trim()
                        }
                        l.startsWith("URL", ignoreCase = true) -> {
                            val idx = l.indexOf(":")
                            if (idx != -1) {
                                val url = l.substring(idx + 1).trim()
                                if (url.contains("instagram.com", ignoreCase = true)) instagram = url
                                else if (url.contains("linkedin.com", ignoreCase = true)) linkedIn = url
                                else if (website.isEmpty()) website = url
                            }
                        }
                    }
                }
                if (name.isNotEmpty() || phone.isNotEmpty() || instagram.isNotEmpty() || linkedIn.isNotEmpty()) {
                    ContactCard(
                        name = name.ifEmpty { "Contact" },
                        phone = phone,
                        email = email,
                        note = note,
                        instagram = instagram,
                        linkedIn = linkedIn,
                        website = website
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
