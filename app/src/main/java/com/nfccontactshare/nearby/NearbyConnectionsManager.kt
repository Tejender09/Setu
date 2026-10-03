package com.nfccontactshare.nearby

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.nfccontactshare.model.ContactCard
import java.io.File

/**
 * Peer-to-peer manager supporting both Contact Card data AND full-resolution Photo files.
 */
class NearbyConnectionsManager(
    private val context: Context,
    private val myContactCard: ContactCard,
    private val myPhotoUri: Uri? = null,
    private val listener: NearbyEventListener
) {

    interface NearbyEventListener {
        fun onContactReceived(card: ContactCard, photoFile: File?)
        fun onEndpointDiscovered(endpointName: String)
        fun onConnectionEstablished(endpointId: String)
        fun onPhotoTransferProgress(percent: Int)
        fun onConnectionFailed(reason: String)
        fun onDisconnected()
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val connectedEndpoints = mutableSetOf<String>()
    private val connectingEndpoints = mutableSetOf<String>()

    private var iReceivedPeerCard = false
    private var peerReceivedMyCard = false

    // State for pairing incoming card with incoming photo file
    private var pendingCard: ContactCard? = null
    private var incomingFilePayloadId: Long? = null
    private var receivedPhotoFile: File? = null

    var isRunning = false
        private set

    // ─── Payload Callback ─────────────────────────────────────────────────────

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val data = String(bytes, Charsets.UTF_8)
                    Log.d(TAG, "Bytes payload received from $endpointId: $data")

                    if (data == ACK_PAYLOAD) {
                        Log.d(TAG, "Peer acknowledged receipt of our card! ✅")
                        peerReceivedMyCard = true
                        checkBothExchangedAndScheduleDisconnect(endpointId)
                        return
                    }

                    val card = ContactCard.fromTransmitString(data)
                    if (card != null) {
                        Log.d(TAG, "Card received: ${card.name}, hasPhoto=${card.hasPhoto}")
                        iReceivedPeerCard = true
                        sendAck(endpointId)

                        if (card.hasPhoto) {
                            pendingCard = card
                            // If photo already arrived before card metadata:
                            if (receivedPhotoFile != null && receivedPhotoFile!!.exists()) {
                                listener.onContactReceived(card, receivedPhotoFile)
                                checkBothExchangedAndScheduleDisconnect(endpointId)
                            }
                        } else {
                            listener.onContactReceived(card, null)
                            checkBothExchangedAndScheduleDisconnect(endpointId)
                        }
                    }
                }

                Payload.Type.FILE -> {
                    Log.d(TAG, "Incoming file payload id=${payload.id}")
                    incomingFilePayloadId = payload.id
                    val javaFile = payload.asFile()?.asJavaFile()
                    if (javaFile != null) {
                        receivedPhotoFile = javaFile
                    }
                }

                else -> {}
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (incomingFilePayloadId != null && update.payloadId == incomingFilePayloadId) {
                val total = update.totalBytes
                val transferred = update.bytesTransferred
                if (total > 0) {
                    val percent = ((transferred * 100) / total).toInt()
                    listener.onPhotoTransferProgress(percent)
                }

                if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                    Log.d(TAG, "Photo file payload transfer COMPLETE from $endpointId! ✅")
                    val file = receivedPhotoFile
                    val card = pendingCard
                    if (card != null && file != null && file.exists()) {
                        listener.onContactReceived(card, file)
                        pendingCard = null
                        checkBothExchangedAndScheduleDisconnect(endpointId)
                    }
                }
            }
        }
    }

    // ─── Connection Lifecycle Callback ────────────────────────────────────────

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with: ${info.endpointName} ($endpointId)")
            listener.onEndpointDiscovered(info.endpointName)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    Log.e(TAG, "acceptConnection failed: ${e.message}")
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            connectingEndpoints.remove(endpointId)

            if (result.status.isSuccess) {
                Log.d(TAG, "Connected successfully to $endpointId")
                connectedEndpoints.add(endpointId)
                iReceivedPeerCard = false
                peerReceivedMyCard = false
                pendingCard = null
                receivedPhotoFile = null
                listener.onConnectionEstablished(endpointId)

                // Dispatch contact card (with hasPhoto flag)
                val cardToSend = myContactCard.copy(hasPhoto = myPhotoUri != null)
                sendContact(endpointId, cardToSend)

                // If user selected a photo, dispatch it as a FILE payload
                if (myPhotoUri != null) {
                    sendPhotoFile(endpointId, myPhotoUri)
                }

                // Resend card after 900ms if peer hasn't acknowledged
                mainHandler.postDelayed({
                    if (!peerReceivedMyCard && connectedEndpoints.contains(endpointId)) {
                        Log.d(TAG, "Resending card to $endpointId (retry)")
                        sendContact(endpointId, cardToSend)
                    }
                }, 900)
            } else {
                Log.e(TAG, "Connection failed: ${result.status.statusMessage}")
                listener.onConnectionFailed(result.status.statusMessage ?: "Could not connect")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            connectedEndpoints.remove(endpointId)
            connectingEndpoints.remove(endpointId)
            listener.onDisconnected()
        }
    }

    // ─── Endpoint Discovery Callback ──────────────────────────────────────────

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Discovered endpoint: ${info.endpointName} ($endpointId)")
            listener.onEndpointDiscovered(info.endpointName)

            if (connectedEndpoints.contains(endpointId) || connectingEndpoints.contains(endpointId)) {
                return
            }

            val myId = myContactCard.phone.ifEmpty { myContactCard.name }
            val peerId = info.endpointName
            val isInitiator = myId < peerId

            if (isInitiator) {
                Log.d(TAG, "We are designated initiator — requesting connection to $endpointId")
                requestConnectionTo(endpointId)
            } else {
                Log.d(TAG, "Peer is initiator — waiting, setting 1200ms fallback")
                mainHandler.postDelayed({
                    if (!connectedEndpoints.contains(endpointId) && !connectingEndpoints.contains(endpointId)) {
                        Log.d(TAG, "Fallback triggered — requesting connection to $endpointId")
                        requestConnectionTo(endpointId)
                    }
                }, 1200)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            connectingEndpoints.remove(endpointId)
        }
    }

    private fun requestConnectionTo(endpointId: String) {
        if (connectedEndpoints.contains(endpointId) || connectingEndpoints.contains(endpointId)) return
        connectingEndpoints.add(endpointId)

        val myId = myContactCard.phone.ifEmpty { myContactCard.name }
        connectionsClient.requestConnection(
            myId,
            endpointId,
            connectionLifecycleCallback
        ).addOnFailureListener { e ->
            connectingEndpoints.remove(endpointId)
            Log.e(TAG, "requestConnection failed: ${e.message}")
        }
    }

    private fun checkBothExchangedAndScheduleDisconnect(endpointId: String) {
        if (iReceivedPeerCard && peerReceivedMyCard && pendingCard == null) {
            Log.d(TAG, "Both sides successfully exchanged! Disconnecting gracefully in 2s...")
            mainHandler.postDelayed({
                if (connectedEndpoints.contains(endpointId)) {
                    try {
                        connectionsClient.disconnectFromEndpoint(endpointId)
                        connectedEndpoints.remove(endpointId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error disconnecting endpoint: ${e.message}")
                    }
                }
            }, 2000)
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun startExchange() {
        if (isRunning) return
        isRunning = true
        startAdvertising()
        startDiscovery()
    }

    fun stopExchange() {
        isRunning = false
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
            connectedEndpoints.clear()
            connectingEndpoints.clear()
            pendingCard = null
            receivedPhotoFile = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping exchange: ${e.message}")
        }
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        val myId = myContactCard.phone.ifEmpty { myContactCard.name }
        connectionsClient.startAdvertising(
            myId,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising active (P2P_CLUSTER)")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed: ${e.message}")
            listener.onConnectionFailed("Advertising failed: ${e.message}")
        }
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery active (P2P_CLUSTER)")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed: ${e.message}")
        }
    }

    private fun sendContact(endpointId: String, card: ContactCard) {
        val payload = Payload.fromBytes(card.toTransmitString().toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)
            .addOnSuccessListener { Log.d(TAG, "Sent contact card to $endpointId") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to send card: ${e.message}") }
    }

    private fun sendPhotoFile(endpointId: String, photoUri: Uri) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(photoUri, "r")
            if (pfd != null) {
                val filePayload = Payload.fromFile(pfd)
                connectionsClient.sendPayload(endpointId, filePayload)
                    .addOnSuccessListener { Log.d(TAG, "Sent photo payload to $endpointId") }
                    .addOnFailureListener { e -> Log.e(TAG, "Failed to send photo payload: ${e.message}") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening photo for send: ${e.message}")
        }
    }

    private fun sendAck(endpointId: String) {
        val payload = Payload.fromBytes(ACK_PAYLOAD.toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)
            .addOnSuccessListener { Log.d(TAG, "Sent ACK to $endpointId") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to send ACK: ${e.message}") }
    }

    companion object {
        private const val TAG = "NearbyConnectionsMgr"
        const val SERVICE_ID = "com.nfccontactshare.nearby.cluster.v7"
        private const val ACK_PAYLOAD = "__ACK__"
    }
}
