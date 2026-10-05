package com.nfccontactshare.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.nfccontactshare.R
import com.nfccontactshare.databinding.ItemShareSlideBinding
import com.nfccontactshare.model.ContactCard
import com.nfccontactshare.data.PrefsManager

class ShareSlideAdapter(
    private var myCard: ContactCard,
    private val onSlideClick: (PrefsManager.ShareTarget) -> Unit = {}
) : RecyclerView.Adapter<ShareSlideAdapter.SlideViewHolder>() {

    data class SlideData(
        val target: PrefsManager.ShareTarget,
        val platformName: String,
        val glyphRes: Int,
        val capsuleIconRes: Int = glyphRes,
        val beaconOrbBgRes: Int,
        val capsuleBgRes: Int,
        val badgeText: String,
        val promptText: String
    )

    private val slides = mutableListOf<SlideData>()

    init {
        updateData(myCard)
    }

    fun updateData(card: ContactCard) {
        myCard = card
        slides.clear()

        // 1. Instagram
        val cleanIg = myCard.instagram.trim().removePrefix("@").removePrefix("https://www.instagram.com/").trimEnd('/')
        val igLabel = if (cleanIg.isNotEmpty()) "@$cleanIg" else "Not set (Tap Edit)"
        slides.add(
            SlideData(
                target = PrefsManager.ShareTarget.INSTAGRAM,
                platformName = "Instagram",
                glyphRes = R.drawable.ic_instagram_glyph,
                capsuleIconRes = R.drawable.ic_instagram_glyph,
                beaconOrbBgRes = R.drawable.bg_beacon_orb_instagram,
                capsuleBgRes = R.drawable.bg_capsule_broadcast_instagram,
                badgeText = "Broadcasting: Instagram ($igLabel)",
                promptText = "Touch phones to open Instagram profile"
            )
        )

        // 2. LinkedIn
        val cleanLi = myCard.linkedIn.trim().removePrefix("https://www.linkedin.com/in/").removePrefix("in/").trimEnd('/')
        val liLabel = if (cleanLi.isNotEmpty()) cleanLi else "Not set (Tap Edit)"
        slides.add(
            SlideData(
                target = PrefsManager.ShareTarget.LINKEDIN,
                platformName = "LinkedIn",
                glyphRes = R.drawable.ic_linkedin_glyph,
                capsuleIconRes = R.drawable.ic_linkedin_glyph,
                beaconOrbBgRes = R.drawable.bg_beacon_orb_linkedin,
                capsuleBgRes = R.drawable.bg_capsule_broadcast_linkedin,
                badgeText = "Broadcasting: LinkedIn ($liLabel)",
                promptText = "Touch phones to open LinkedIn profile"
            )
        )

        // 3. WhatsApp (Official WhatsApp icon in center orb + crisp white icon in capsule)
        slides.add(
            SlideData(
                target = PrefsManager.ShareTarget.WHATSAPP,
                platformName = "WhatsApp",
                glyphRes = R.drawable.ic_whatsapp_glyph,
                capsuleIconRes = R.drawable.ic_whatsapp_white,
                beaconOrbBgRes = R.drawable.bg_beacon_orb_whatsapp,
                capsuleBgRes = R.drawable.bg_capsule_broadcast_whatsapp,
                badgeText = "Broadcasting: WhatsApp (${myCard.phone})",
                promptText = "Touch phones to start WhatsApp chat"
            )
        )

        // 4. Contact Card (vCard)
        slides.add(
            SlideData(
                target = PrefsManager.ShareTarget.CONTACT,
                platformName = "Contact",
                glyphRes = R.drawable.ic_contact_glyph,
                capsuleIconRes = R.drawable.ic_contact_glyph,
                beaconOrbBgRes = R.drawable.bg_beacon_orb_contact,
                capsuleBgRes = R.drawable.bg_capsule_broadcast_contact,
                badgeText = "Broadcasting: Contact Card (vCard)",
                promptText = "Touch phones to save contact card"
            )
        )

        // 5. Website
        val site = myCard.website.ifEmpty { "Not set (Tap Edit)" }
        slides.add(
            SlideData(
                target = PrefsManager.ShareTarget.WEBSITE,
                platformName = "Website",
                glyphRes = R.drawable.ic_link_chain,
                capsuleIconRes = R.drawable.ic_link_chain,
                beaconOrbBgRes = R.drawable.bg_beacon_orb_linkedin,
                capsuleBgRes = R.drawable.bg_capsule_broadcast_linkedin,
                badgeText = "Broadcasting: Website ($site)",
                promptText = "Touch phones to open website"
            )
        )

        notifyDataSetChanged()
    }

    fun getTargetAt(position: Int): PrefsManager.ShareTarget? {
        return slides.getOrNull(position)?.target
    }

    fun getPositionForTarget(target: PrefsManager.ShareTarget): Int {
        val index = slides.indexOfFirst { it.target == target }
        return if (index >= 0) index else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val binding = ItemShareSlideBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SlideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        holder.bind(slides[position])
    }

    override fun onViewAttachedToWindow(holder: SlideViewHolder) {
        super.onViewAttachedToWindow(holder)
        // Restarts radar animation whenever slide scrolls back into view
        holder.startRadarAnimation()
    }

    override fun onViewDetachedFromWindow(holder: SlideViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.stopRadarAnimation()
    }

    override fun getItemCount(): Int = slides.size

    inner class SlideViewHolder(private val binding: ItemShareSlideBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(data: SlideData) {
            binding.layoutBeaconOrb.setBackgroundResource(data.beaconOrbBgRes)
            binding.ivBeaconGlyph.setImageResource(data.glyphRes)
            binding.layoutBroadcastCapsule.setBackgroundResource(data.capsuleBgRes)
            binding.ivBroadcastCapsuleIcon.setImageResource(data.capsuleIconRes)
            binding.tvTargetBadge.text = data.badgeText
            binding.tvSlidePrompt.text = data.promptText

            startRadarAnimation()

            binding.layoutBeaconOrb.setOnClickListener {
                onSlideClick(data.target)
            }
        }

        fun startRadarAnimation() {
            try {
                val context = binding.root.context ?: return
                binding.viewRadarOuter.clearAnimation()
                binding.viewRadarMiddle.clearAnimation()
                binding.viewRadarInner.clearAnimation()

                val pulseOuter = AnimationUtils.loadAnimation(context, R.anim.pulse)
                val pulseMiddle = AnimationUtils.loadAnimation(context, R.anim.pulse).apply {
                    startOffset = 250
                }
                val pulseInner = AnimationUtils.loadAnimation(context, R.anim.pulse).apply {
                    startOffset = 500
                }
                binding.viewRadarOuter.startAnimation(pulseOuter)
                binding.viewRadarMiddle.startAnimation(pulseMiddle)
                binding.viewRadarInner.startAnimation(pulseInner)
            } catch (_: Exception) {}
        }

        fun stopRadarAnimation() {
            try {
                binding.viewRadarOuter.clearAnimation()
                binding.viewRadarMiddle.clearAnimation()
                binding.viewRadarInner.clearAnimation()
            } catch (_: Exception) {}
        }
    }
}
