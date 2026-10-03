package com.nfccontactshare.ui

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nfccontactshare.data.PrefsManager
import com.nfccontactshare.databinding.ActivityProfileBinding
import com.nfccontactshare.model.ContactCard

/**
 * Profile setup screen.
 * Saves Name, Phone, Email, Note/Headline, Instagram, LinkedIn, and Website.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var prefsManager: PrefsManager

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            displayAvatarPreview(uri)
            Toast.makeText(this, "📸 Profile photo updated!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // System status bar insets fix
        WindowCompat.setDecorFitsSystemWindows(window, false)

        prefsManager = PrefsManager(this)

        val isEditMode = intent.getBooleanExtra(EXTRA_EDIT_MODE, false)
        if (!isEditMode && prefsManager.hasProfile()) {
            startActivity(Intent(this, ShareActivity::class.java))
            finish()
            return
        }

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, statusBarInsets.top, 0, statusBarInsets.bottom)
            insets
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        if (prefsManager.hasProfile()) {
            populateExistingProfile()
        }

        requestPermission.launch(Manifest.permission.READ_CONTACTS)

        // Navigation
        binding.btnBack.setOnClickListener {
            if (prefsManager.hasProfile()) {
                finish()
            } else {
                Toast.makeText(this, "Please save your profile first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPreview.setOnClickListener {
            saveAndContinue()
        }

        // Avatar photo picking
        val openPicker = View.OnClickListener {
            pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.layoutAvatarPicker.setOnClickListener(openPicker)
        binding.btnChangeAvatarPhoto.setOnClickListener(openPicker)

        // Actions
        binding.btnSaveProfile.setOnClickListener { saveAndContinue() }
        binding.btnViewHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun displayAvatarPreview(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                binding.ivProfileAvatarPreview.setImageBitmap(bitmap)
                binding.ivProfileAvatarPreview.visibility = View.VISIBLE
                binding.tvProfileAvatarIcon.visibility = View.GONE
            }
        } catch (_: Exception) {}
    }

    private fun populateExistingProfile() {
        val profile = prefsManager.getMyProfile() ?: return
        binding.etName.setText(profile.name)
        binding.etPhone.setText(profile.phone)
        binding.etEmail.setText(profile.email)
        binding.etNote.setText(profile.note)
        binding.etInstagram.setText(profile.instagram.removePrefix("@"))
        binding.etLinkedIn.setText(profile.linkedIn.removePrefix("in/").removePrefix("https://www.linkedin.com/in/"))
        binding.etWebsite.setText(profile.website.removePrefix("https://").removePrefix("http://"))
    }

    private fun saveAndContinue() {
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val note = binding.etNote.text.toString().trim()
        val instagram = binding.etInstagram.text.toString().trim().removePrefix("@")
        val linkedIn = binding.etLinkedIn.text.toString().trim().removePrefix("in/").removePrefix("https://www.linkedin.com/in/")
        val website = binding.etWebsite.text.toString().trim()

        if (name.isEmpty()) {
            binding.etName.error = "Name is required"
            return
        }
        if (phone.isEmpty()) {
            binding.etPhone.error = "Phone number is required"
            return
        }

        val card = ContactCard(
            name = name,
            phone = phone,
            email = email,
            note = note,
            instagram = if (instagram.isNotEmpty()) "@$instagram" else "",
            linkedIn = if (linkedIn.isNotEmpty()) linkedIn else "",
            website = website
        )
        prefsManager.saveMyProfile(card)
        Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, ShareActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_EDIT_MODE = "extra_edit_mode"
    }
}
