package com.nfccontactshare.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager2.widget.ViewPager2
import com.nfccontactshare.R
import com.nfccontactshare.data.PrefsManager
import com.nfccontactshare.databinding.ActivityShareBinding
import com.nfccontactshare.model.ContactCard
import com.nfccontactshare.nearby.NearbyConnectionsManager
import com.nfccontactshare.nfc.NfcCardEmulatorService
import com.nfccontactshare.nfc.NfcManager
import java.io.File

class ShareActivity : AppCompatActivity(),
    NfcManager.NfcEventListener,
    NearbyConnectionsManager.NearbyEventListener {

    private lateinit var binding: ActivityShareBinding
    private lateinit var prefsManager: PrefsManager
    private lateinit var nfcManager: NfcManager
    private var nearbyManager: NearbyConnectionsManager? = null
    private lateinit var myCard: ContactCard
    private lateinit var slideAdapter: ShareSlideAdapter

    private var selectedPhotoUri: Uri? = null
    private var isScanMode: Boolean = false
    private var isDialogOpen = false
    private var lastNfcTapTime: Long = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    // Photo picker launcher
    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            attachPhoto(uri)
            Toast.makeText(this, "📸 Photo attached! Ready to beam.", Toast.LENGTH_SHORT).show()
        }
    }

    private val nearbyPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }

    private val requestNearbyPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted && selectedPhotoUri != null) {
            startNearbyExchange()
            binding.btnBeamNearby.text = "⏹ Stop Beam"
        } else if (!allGranted) {
            Toast.makeText(this, "Permissions needed for wireless media beam", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestWriteContactsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Contacts permission needed to save", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fix system status bar clipping and ensure clock / battery / icons are visible
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply system bar insets so the top card is never shifted into the notch/clock
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, statusBarInsets.top, 0, statusBarInsets.bottom)
            insets
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        prefsManager = PrefsManager(this)
        myCard = prefsManager.getMyProfile() ?: run {
            Toast.makeText(this, "Please set up your profile first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        nfcManager = NfcManager(this, this)

        binding.tvMyName.text = myCard.name
        binding.tvMyPhone.text = myCard.phone

        // Copy phone to clipboard
        val copyAction = View.OnClickListener {
            if (myCard.phone.isNotEmpty()) {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Phone Number", myCard.phone)
                clipboard.setPrimaryClip(clip)
                vibrateShort()
                Toast.makeText(this, "📋 Phone number copied!", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnCopyPhone.setOnClickListener(copyAction)
        binding.tvMyPhone.setOnClickListener(copyAction)

        // "How does tap sharing work?" modal dialog
        binding.btnHowItWorks.setOnClickListener {
            showHowItWorksDialog()
        }

        binding.btnEditProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java).apply {
                putExtra(ProfileActivity.EXTRA_EDIT_MODE, true)
            }
            startActivity(intent)
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // "Select Photos 📸" button
        binding.btnSelectPhotos.setOnClickListener {
            pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // Remove photo badge on card
        binding.btnRemovePhotoBadge.setOnClickListener {
            removeAttachedPhoto()
        }

        // Beam photo via Nearby button (explicit user trigger only)
        binding.btnBeamNearby.setOnClickListener {
            val manager = nearbyManager
            if (manager != null && manager.isRunning) {
                manager.stopExchange()
                nearbyManager = null
                binding.btnBeamNearby.text = "⚡ Beam Photo via Nearby"
                setIdleState()
                Toast.makeText(this, "Beam stopped.", Toast.LENGTH_SHORT).show()
            } else {
                checkAndStartNearbyBeam()
            }
        }

        // Setup Slide Screen ViewPager2
        setupSlidePager()

        // Switch between "Share Mode" (Host Card Emulation - Zero App) and "Scan Mode" (Reader)
        binding.btnToggleScanMode.setOnClickListener {
            toggleMode()
        }

        // Check if contacts permission is already granted; if not, request it up front
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestWriteContactsLauncher.launch(Manifest.permission.WRITE_CONTACTS)
        }

        // Check if an image was shared to our app directly from Gallery / Photos
        handleIncomingShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun setupSlidePager() {
        binding.vpShareSlides.clipChildren = false
        binding.vpShareSlides.clipToPadding = false
        binding.vpShareSlides.offscreenPageLimit = 2
        (binding.vpShareSlides.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView)?.apply {
            clipChildren = false
            clipToPadding = false
        }

        slideAdapter = ShareSlideAdapter(myCard)
        binding.vpShareSlides.adapter = slideAdapter

        val activeTarget = prefsManager.getActiveShareTarget()
        val initialIndex = slideAdapter.getPositionForTarget(activeTarget)
        binding.vpShareSlides.setCurrentItem(initialIndex, false)
        updateSlideHeader(initialIndex)
        updateIndicators(initialIndex)
        updateCardAccent(activeTarget)

        binding.vpShareSlides.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val target = slideAdapter.getTargetAt(position) ?: return
                prefsManager.setActiveShareTarget(target)
                updateSlideHeader(position)
                updateIndicators(position)
                updateCardAccent(target)
                vibrateShort()

                // Ensure the selected page's radar pulse animation is active
                binding.vpShareSlides.post {
                    val rv = binding.vpShareSlides.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
                    val holder = rv?.findViewHolderForAdapterPosition(position) as? ShareSlideAdapter.SlideViewHolder
                    holder?.startRadarAnimation()
                }

                when (target) {
                    PrefsManager.ShareTarget.INSTAGRAM -> {
                        if (myCard.instagram.isEmpty()) {
                            Toast.makeText(this@ShareActivity, "⚠️ Add Instagram in Edit Profile first!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    PrefsManager.ShareTarget.LINKEDIN -> {
                        if (myCard.linkedIn.isEmpty()) {
                            Toast.makeText(this@ShareActivity, "⚠️ Add LinkedIn in Edit Profile first!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    PrefsManager.ShareTarget.WHATSAPP -> {
                        if (myCard.phone.isEmpty()) {
                            Toast.makeText(this@ShareActivity, "⚠️ Phone required for WhatsApp!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    PrefsManager.ShareTarget.WEBSITE -> {
                        if (myCard.website.isEmpty()) {
                            Toast.makeText(this@ShareActivity, "⚠️ Add Website in Edit Profile first!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    PrefsManager.ShareTarget.CONTACT -> {}
                }
            }
        })

        binding.btnSlidePrev.setOnClickListener {
            val cur = binding.vpShareSlides.currentItem
            if (cur > 0) {
                binding.vpShareSlides.setCurrentItem(cur - 1, true)
            } else {
                binding.vpShareSlides.setCurrentItem(slideAdapter.itemCount - 1, true)
            }
        }

        binding.btnSlideNext.setOnClickListener {
            val cur = binding.vpShareSlides.currentItem
            if (cur < slideAdapter.itemCount - 1) {
                binding.vpShareSlides.setCurrentItem(cur + 1, true)
            } else {
                binding.vpShareSlides.setCurrentItem(0, true)
            }
        }
    }

    private fun updateCardAccent(target: PrefsManager.ShareTarget) {
        when (target) {
            PrefsManager.ShareTarget.INSTAGRAM -> binding.viewCardAccent.setBackgroundResource(R.drawable.bg_card_accent_instagram)
            PrefsManager.ShareTarget.LINKEDIN -> binding.viewCardAccent.setBackgroundResource(R.drawable.bg_card_accent_linkedin)
            PrefsManager.ShareTarget.WHATSAPP -> binding.viewCardAccent.setBackgroundResource(R.drawable.bg_card_accent_whatsapp)
            PrefsManager.ShareTarget.WEBSITE -> binding.viewCardAccent.setBackgroundResource(R.drawable.bg_card_accent_linkedin)
            PrefsManager.ShareTarget.CONTACT -> binding.viewCardAccent.setBackgroundResource(R.drawable.bg_card_accent_contact)
        }
    }

    private fun updateSlideHeader(position: Int) {
        val target = slideAdapter.getTargetAt(position) ?: return
        val title = when (target) {
            PrefsManager.ShareTarget.INSTAGRAM -> "📸 INSTAGRAM"
            PrefsManager.ShareTarget.LINKEDIN -> "💼 LINKEDIN"
            PrefsManager.ShareTarget.WHATSAPP -> "💬 WHATSAPP"
            PrefsManager.ShareTarget.CONTACT -> "📇 CONTACT CARD"
            PrefsManager.ShareTarget.WEBSITE -> "🌐 WEBSITE"
        }
        binding.tvCurrentPlatformTitle.text = title
    }

    private fun updateIndicators(position: Int) {
        val dots = listOf(
            binding.viewDot0,
            binding.viewDot1,
            binding.viewDot2,
            binding.viewDot3,
            binding.viewDot4
        )
        dots.forEachIndexed { index, view ->
            val lp = view.layoutParams
            if (index == position) {
                lp.width = (18 * resources.displayMetrics.density).toInt()
                view.setBackgroundResource(R.drawable.bg_indicator_active)
            } else {
                lp.width = (6 * resources.displayMetrics.density).toInt()
                view.setBackgroundResource(R.drawable.bg_indicator_inactive)
            }
            view.layoutParams = lp
        }
    }

    override fun onResume() {
        super.onResume()
        checkNfcAvailability()
        prefsManager.getMyProfile()?.let {
            myCard = it
            binding.tvMyName.text = myCard.name
            binding.tvMyPhone.text = myCard.phone
            slideAdapter.updateData(myCard)
        }
        applyCurrentMode()

        binding.vpShareSlides.post {
            val cur = binding.vpShareSlides.currentItem
            val rv = binding.vpShareSlides.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
            val holder = rv?.findViewHolderForAdapterPosition(cur) as? ShareSlideAdapter.SlideViewHolder
            holder?.startRadarAnimation()
        }
    }

    override fun onPause() {
        super.onPause()
        disableHceService()
        nfcManager.disableForegroundReader()
        nearbyManager?.stopExchange()
        nearbyManager = null
    }

    /**
     * Check if NFC is enabled on device. If disabled, prompt user to turn it on in Settings.
     */
    private fun checkNfcAvailability() {
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) {
            Toast.makeText(this, "This device does not support NFC", Toast.LENGTH_LONG).show()
        } else if (!adapter.isEnabled) {
            Toast.makeText(this, "⚠️ Please turn ON NFC in Settings to tap", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            } catch (e: Exception) {
                Log.w(TAG, "Cannot open NFC settings: ${e.message}")
            }
        }
    }

    /**
     * Applies either Card Emulation mode (Zero-App touch) or Scan mode (Reader).
     */
    private fun applyCurrentMode() {
        if (isScanMode) {
            // Scan Mode: Enable Reader Mode, Disable HCE (prevents conflict with Embedded tag)
            disableHceService()
            nfcManager.enableForegroundReader()

            binding.layoutSlideContainer.visibility = View.GONE
            binding.layoutScanModeArea.visibility = View.VISIBLE
            binding.btnToggleScanMode.text = "SWITCH TO BROADCAST MODE"
            binding.btnToggleScanMode.setIconResource(R.drawable.ic_contact_glyph)
            binding.progressBar.visibility = View.GONE
        } else {
            // Share Mode (Default): Disable Reader Mode, Enable HCE with exclusive foreground priority
            nfcManager.disableForegroundReader()
            enableHceService()

            binding.layoutSlideContainer.visibility = View.VISIBLE
            binding.layoutScanModeArea.visibility = View.GONE
            binding.btnToggleScanMode.text = "SWITCH TO SCAN MODE"
            binding.btnToggleScanMode.setIconResource(R.drawable.ic_search_scan)
            setIdleState()
        }
    }

    private fun toggleMode() {
        isScanMode = !isScanMode
        applyCurrentMode()
        val modeName = if (isScanMode) "Scan Mode" else "Share Card Mode (Zero-App)"
        Toast.makeText(this, "Switched to $modeName", Toast.LENGTH_SHORT).show()
    }

    /**
     * Dynamically enables our HostApduService ONLY while actively in foreground share mode.
     * When disabled, the phone will NEVER trigger "Select an app to use: Embedded tag" conflict!
     */
    private fun enableHceService() {
        try {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, NfcCardEmulatorService::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            setHcePreferredService()
            Log.d(TAG, "HCE Service enabled for foreground share")
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling HCE service: ${e.message}")
        }
    }

    private fun disableHceService() {
        try {
            unsetHcePreferredService()
            packageManager.setComponentEnabledSetting(
                ComponentName(this, NfcCardEmulatorService::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "HCE Service disabled to prevent background tag conflicts")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling HCE service: ${e.message}")
        }
    }

    /**
     * Registers our HostApduService with exclusive foreground priority.
     * Overrides Samsung Pay / Google Wallet while our app is in foreground!
     */
    private fun setHcePreferredService() {
        val adapter = NfcAdapter.getDefaultAdapter(this) ?: return
        if (!adapter.isEnabled) return
        try {
            val cardEmulation = CardEmulation.getInstance(adapter)
            val componentName = ComponentName(this, NfcCardEmulatorService::class.java)
            val success = cardEmulation.setPreferredService(this, componentName)
            Log.d(TAG, "setPreferredService(NfcCardEmulatorService) result=$success")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting preferred HCE service: ${e.message}")
        }
    }

    private fun unsetHcePreferredService() {
        val adapter = NfcAdapter.getDefaultAdapter(this) ?: return
        try {
            val cardEmulation = CardEmulation.getInstance(adapter)
            cardEmulation.unsetPreferredService(this)
            Log.d(TAG, "unsetPreferredService called")
        } catch (e: Exception) {
            Log.e(TAG, "Error unsetting preferred HCE service: ${e.message}")
        }
    }

    /**
     * Handles images shared from Samsung Gallery / Google Photos via Android Share Sheet
     */
    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type?.startsWith("image/") == true) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: intent.clipData?.getItemAt(0)?.uri

            if (uri != null) {
                attachPhoto(uri)
                Toast.makeText(this, "📸 Photo loaded from Gallery! Tap 'Beam Photo' to send.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun attachPhoto(uri: Uri) {
        selectedPhotoUri = uri
        displayCardPhotoPreview(uri)
        binding.btnBeamNearby.visibility = View.VISIBLE
        binding.btnBeamNearby.text = "⚡ Beam Photo via Nearby"
        setIdleState()
    }

    private fun removeAttachedPhoto() {
        selectedPhotoUri = null
        binding.ivCardPhotoPreview.visibility = View.GONE
        binding.btnRemovePhotoBadge.visibility = View.GONE
        binding.tvPhotoAttachedLabel.visibility = View.GONE
        binding.tvAvatarIcon.visibility = View.VISIBLE
        binding.btnSelectPhotos.text = "SELECT PHOTOS  📸"
        binding.btnBeamNearby.visibility = View.GONE

        nearbyManager?.stopExchange()
        nearbyManager = null
        setIdleState()
        Toast.makeText(this, "Photo removed. Pure Zero-App NFC Touch active.", Toast.LENGTH_SHORT).show()
    }

    private fun displayCardPhotoPreview(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                binding.ivCardPhotoPreview.setImageBitmap(bitmap)
                binding.ivCardPhotoPreview.visibility = View.VISIBLE
                binding.btnRemovePhotoBadge.visibility = View.VISIBLE
                binding.tvPhotoAttachedLabel.visibility = View.VISIBLE
                binding.tvAvatarIcon.visibility = View.GONE
                binding.btnSelectPhotos.text = "CHANGE PHOTO  📸"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying photo thumbnail on card: ${e.message}")
        }
    }

    private fun checkAndStartNearbyBeam() {
        val missing = nearbyPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestNearbyPermissionsLauncher.launch(missing.toTypedArray())
        } else {
            startNearbyExchange()
            binding.btnBeamNearby.text = "⏹ Stop Beam"
        }
    }

    private fun startNearbyExchange() {
        if (isDialogOpen) return
        val current = nearbyManager
        if (current != null && current.isRunning) {
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        nearbyManager = NearbyConnectionsManager(this, myCard, selectedPhotoUri, this).also {
            it.startExchange()
        }
    }

    private val nfcTimeoutRunnable = Runnable {
        if (!isDialogOpen) {
            binding.progressBar.visibility = View.GONE
            setIdleState()
        }
    }

    // ─── NFC Events (Scan Mode) ───────────────────────────────────────────────

    override fun onNfcTapDetected() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNfcTapTime < 1500) return
        lastNfcTapTime = now

        Log.d(TAG, "NFC tap detected in reader mode")
        vibrateShort()

        runOnUiThread {
            binding.tvScanStatus.text = "⚡ Tag Read! Processing..."
            binding.progressBar.visibility = View.VISIBLE
        }

        // Safety timeout to prevent getting stuck in spinner if tag is pulled away or corrupt
        mainHandler.removeCallbacks(nfcTimeoutRunnable)
        mainHandler.postDelayed(nfcTimeoutRunnable, 3500)
    }

    override fun onContactReceivedViaNfc(card: ContactCard) {
        mainHandler.removeCallbacks(nfcTimeoutRunnable)
        runOnUiThread {
            vibrateSuccess()
            showReceivedContact(card, null)
        }
    }

    override fun onNfcReadFailed(reason: String) {
        mainHandler.removeCallbacks(nfcTimeoutRunnable)
        runOnUiThread {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "⚠️ $reason", Toast.LENGTH_SHORT).show()
            setIdleState()
        }
    }

    // ─── Nearby Events (Photo Beam Mode) ──────────────────────────────────────

    override fun onEndpointDiscovered(endpointName: String) {
        runOnUiThread {
            vibrateShort()
            binding.progressBar.visibility = View.VISIBLE
        }
    }

    override fun onConnectionEstablished(endpointId: String) {
        runOnUiThread {
            binding.progressBar.visibility = View.VISIBLE
        }
    }

    override fun onPhotoTransferProgress(percent: Int) {
        runOnUiThread {
            binding.progressBar.visibility = View.VISIBLE
        }
    }

    override fun onContactReceived(card: ContactCard, photoFile: File?) {
        runOnUiThread {
            if (isDialogOpen) return@runOnUiThread
            vibrateSuccess()
            showReceivedContact(card, photoFile)
        }
    }

    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            Log.e(TAG, "Connection failed: $reason")
            setIdleState()
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            if (!isDialogOpen) {
                setIdleState()
            }
        }
    }

    // ─── Dialog & Persistence ─────────────────────────────────────────────────

    private fun showReceivedContact(card: ContactCard, photoFile: File?) {
        isDialogOpen = true
        prefsManager.saveToHistory(card)

        ContactReceivedDialog.show(this, card, photoFile) { saved ->
            isDialogOpen = false
            if (saved) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    saveToPhoneContacts(card)
                    Toast.makeText(this, "✅ ${card.name} saved!", Toast.LENGTH_SHORT).show()
                } else {
                    requestWriteContactsLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                    saveToPhoneContacts(card)
                }
            }
            setIdleState()
        }
    }

    private fun saveToPhoneContacts(card: ContactCard) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val ops = ArrayList<android.content.ContentProviderOperation>()
        val rawContactUri = android.provider.ContactsContract.RawContacts.CONTENT_URI
        ops.add(
            android.content.ContentProviderOperation.newInsert(rawContactUri)
                .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )
        ops.add(
            android.content.ContentProviderOperation.newInsert(
                android.provider.ContactsContract.Data.CONTENT_URI
            )
                .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    android.provider.ContactsContract.Data.MIMETYPE,
                    android.provider.ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                )
                .withValue(
                    android.provider.ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                    card.name
                )
                .build()
        )
        if (card.phone.isNotEmpty()) {
            ops.add(
                android.content.ContentProviderOperation.newInsert(
                    android.provider.ContactsContract.Data.CONTENT_URI
                )
                .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    android.provider.ContactsContract.Data.MIMETYPE,
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                )
                .withValue(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                    card.phone
                )
                .withValue(
                    android.provider.ContactsContract.CommonDataKinds.Phone.TYPE,
                    android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                )
                .build()
            )
        }
        if (card.email.isNotEmpty()) {
            ops.add(
                android.content.ContentProviderOperation.newInsert(
                    android.provider.ContactsContract.Data.CONTENT_URI
                )
                .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    android.provider.ContactsContract.Data.MIMETYPE,
                    android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                )
                .withValue(
                    android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS,
                    card.email
                )
                .build()
            )
        }

        try {
            contentResolver.applyBatch(android.provider.ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save contact: ${e.message}")
        }
    }

    private fun setIdleState() {
        binding.progressBar.visibility = View.GONE
        if (selectedPhotoUri != null) {
            binding.btnBeamNearby.text = "⚡ Beam Photo via Nearby"
            return
        }

        val target = prefsManager.getActiveShareTarget()
        val pos = slideAdapter.getPositionForTarget(target)
        if (binding.vpShareSlides.currentItem != pos) {
            binding.vpShareSlides.setCurrentItem(pos, false)
        }
        updateSlideHeader(pos)
        updateIndicators(pos)
        updateCardAccent(target)
    }

    private fun showHowItWorksDialog() {
        vibrateShort()
        val dialogView = layoutInflater.inflate(R.layout.dialog_how_it_works, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val step2Text = dialogView.findViewById<android.widget.TextView>(R.id.tvHowItWorksStep2)
        val currentTarget = prefsManager.getActiveShareTarget()
        val platformName = when (currentTarget) {
            PrefsManager.ShareTarget.INSTAGRAM -> "Instagram"
            PrefsManager.ShareTarget.LINKEDIN -> "LinkedIn"
            PrefsManager.ShareTarget.WHATSAPP -> "WhatsApp"
            PrefsManager.ShareTarget.WEBSITE -> "browser"
            PrefsManager.ShareTarget.CONTACT -> "phone contacts"
        }
        step2Text?.text = "Automatically pops up a prompt opening your profile directly in their $platformName app."

        dialogView.findViewById<View>(R.id.btnCloseHowItWorks)?.setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btnGotIt)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun vibrateShort() {
        val vibrator = getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(100)
        }
    }

    private fun vibrateSuccess() {
        val vibrator = getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 200), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(300)
        }
    }

    companion object {
        private const val TAG = "ShareActivity"
    }
}
