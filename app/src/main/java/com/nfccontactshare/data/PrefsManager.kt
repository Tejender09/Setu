package com.nfccontactshare.data

import android.content.Context
import android.content.SharedPreferences
import com.nfccontactshare.model.ContactCard

/**
 * Manages saving and loading the user's profile, social accounts, and active share target.
 */
class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Share Targets ────────────────────────────────────────────────────────

    enum class ShareTarget {
        CONTACT,
        INSTAGRAM,
        LINKEDIN,
        WHATSAPP,
        WEBSITE
    }

    fun getActiveShareTarget(): ShareTarget {
        val targetName = prefs.getString(KEY_ACTIVE_TARGET, ShareTarget.CONTACT.name)
        return try {
            ShareTarget.valueOf(targetName ?: ShareTarget.CONTACT.name)
        } catch (e: Exception) {
            ShareTarget.CONTACT
        }
    }

    fun setActiveShareTarget(target: ShareTarget) {
        prefs.edit().putString(KEY_ACTIVE_TARGET, target.name).apply()
    }

    // ─── My Profile ───────────────────────────────────────────────────────────

    fun saveMyProfile(card: ContactCard) {
        prefs.edit()
            .putString(KEY_MY_NAME, card.name)
            .putString(KEY_MY_PHONE, card.phone)
            .putString(KEY_MY_EMAIL, card.email)
            .putString(KEY_MY_NOTE, card.note)
            .putString(KEY_MY_INSTAGRAM, card.instagram)
            .putString(KEY_MY_LINKEDIN, card.linkedIn)
            .putString(KEY_MY_WEBSITE, card.website)
            .apply()
    }

    fun getMyProfile(): ContactCard? {
        val name = prefs.getString(KEY_MY_NAME, null) ?: return null
        val phone = prefs.getString(KEY_MY_PHONE, null) ?: return null
        return ContactCard(
            name = name,
            phone = phone,
            email = prefs.getString(KEY_MY_EMAIL, "") ?: "",
            note = prefs.getString(KEY_MY_NOTE, "") ?: "",
            instagram = prefs.getString(KEY_MY_INSTAGRAM, "") ?: "",
            linkedIn = prefs.getString(KEY_MY_LINKEDIN, "") ?: "",
            website = prefs.getString(KEY_MY_WEBSITE, "") ?: ""
        )
    }

    fun hasProfile(): Boolean = prefs.contains(KEY_MY_NAME)

    // ─── History ──────────────────────────────────────────────────────────────

    fun saveToHistory(card: ContactCard) {
        val history = getHistory().toMutableList()
        // Avoid duplicates by phone number or name
        history.removeAll { it.phone == card.phone && it.name == card.name }
        history.add(0, card) // newest first
        val encoded = history.joinToString(HISTORY_SEPARATOR) { it.toTransmitString() }
        prefs.edit().putString(KEY_HISTORY, encoded).apply()
    }

    fun getHistory(): List<ContactCard> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return raw.split(HISTORY_SEPARATOR).mapNotNull { ContactCard.fromTransmitString(it) }
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    companion object {
        private const val PREFS_NAME = "nfc_contact_share_prefs"
        private const val KEY_MY_NAME = "my_name"
        private const val KEY_MY_PHONE = "my_phone"
        private const val KEY_MY_EMAIL = "my_email"
        private const val KEY_MY_NOTE = "my_note"
        private const val KEY_MY_INSTAGRAM = "my_instagram"
        private const val KEY_MY_LINKEDIN = "my_linkedin"
        private const val KEY_MY_WEBSITE = "my_website"
        private const val KEY_ACTIVE_TARGET = "active_share_target"
        private const val KEY_HISTORY = "history"
        private const val HISTORY_SEPARATOR = "|||"
    }
}
