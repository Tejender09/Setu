package com.nfccontactshare.ui

import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.nfccontactshare.databinding.DialogContactReceivedBinding
import com.nfccontactshare.model.ContactCard
import java.io.File
import java.io.FileInputStream

/**
 * Dialog shown when a contact is received, with optional photo display and gallery saving.
 */
object ContactReceivedDialog {

    fun show(
        context: Context,
        card: ContactCard,
        photoFile: File? = null,
        onResult: (saved: Boolean) -> Unit
    ) {
        val binding = DialogContactReceivedBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context)
        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        binding.tvReceivedName.text = card.name
        binding.tvReceivedPhone.text = card.phone
        binding.tvReceivedEmail.text = if (card.email.isNotEmpty()) card.email else "—"
        binding.tvReceivedNote.text = if (card.note.isNotEmpty()) card.note else "—"

        // Social Profile Buttons
        if (card.instagram.isNotEmpty()) {
            binding.btnOpenInstagram.visibility = View.VISIBLE
            val cleanIg = card.instagram.trim().removePrefix("@").removePrefix("https://www.instagram.com/").trimEnd('/')
            binding.btnOpenInstagram.text = "📸 Open @$cleanIg on Instagram"
            binding.btnOpenInstagram.setOnClickListener {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://instagram.com/$cleanIg"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open Instagram: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            binding.btnOpenInstagram.visibility = View.GONE
        }

        if (card.linkedIn.isNotEmpty()) {
            binding.btnOpenLinkedIn.visibility = View.VISIBLE
            val cleanLi = card.linkedIn.trim().removePrefix("https://www.linkedin.com/in/").removePrefix("in/").trimEnd('/')
            binding.btnOpenLinkedIn.text = "💼 Open $cleanLi on LinkedIn"
            binding.btnOpenLinkedIn.setOnClickListener {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://linkedin.com/in/$cleanLi"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open LinkedIn: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            binding.btnOpenLinkedIn.visibility = View.GONE
        }

        // Handle received photo
        if (photoFile != null && photoFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                if (bitmap != null) {
                    binding.ivReceivedPhoto.setImageBitmap(bitmap)
                    binding.cardReceivedPhoto.visibility = View.VISIBLE
                    binding.btnSaveContact.text = "✅ Save Contact & Photo"
                }
            } catch (e: Exception) {
                binding.cardReceivedPhoto.visibility = View.GONE
            }
        } else {
            binding.cardReceivedPhoto.visibility = View.GONE
            binding.btnSaveContact.text = "✅ Save to Contacts"
        }

        binding.btnSaveContact.setOnClickListener {
            dialog.dismiss()
            if (photoFile != null && photoFile.exists()) {
                savePhotoToGallery(context, photoFile, "${card.name}_photo.jpg")
            }
            onResult(true)
        }
        binding.btnDismiss.setOnClickListener {
            dialog.dismiss()
            onResult(false)
        }

        dialog.show()
    }

    private fun savePhotoToGallery(context: Context, sourceFile: File, displayName: String) {
        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NFCShare")
                }
            }

            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                resolver.openOutputStream(imageUri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
                Toast.makeText(context, "📸 Photo saved to Gallery!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
