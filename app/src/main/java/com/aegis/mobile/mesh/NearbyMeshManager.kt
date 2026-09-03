package com.aegis.mobile.mesh

import android.content.Context
import android.util.Log
import com.aegis.mobile.data.DeliveryStatus
import com.aegis.mobile.data.EncryptionSuite
import com.aegis.mobile.data.MessageType
import com.aegis.mobile.data.TacticalMessage
import com.aegis.mobile.data.TacticalMeshPeer
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.UUID

class NearbyMeshManager(
    private val context: Context,
    private var myCallSign: String,
    private var myNodeId: String
) {
    companion object {
        private const val TAG = "NearbyMeshManager"
        private const val SERVICE_ID = "com.crisisnet.tactical.mesh"
        private const val PROTOCOL_V2_HEADER = "AEGIS_P2P_V2"
        private const val PROTOCOL_V1_HEADER = "CRISIS_P2P_V1"
    }

    private val strategy = Strategy.P2P_CLUSTER

    private val _connectedPeers = MutableStateFlow<List<TacticalMeshPeer>>(emptyList())
    val connectedPeers: StateFlow<List<TacticalMeshPeer>> = _connectedPeers.asStateFlow()

    private val peerMap = mutableMapOf<String, TacticalMeshPeer>()
    private val pendingEndpoints = mutableMapOf<String, String>()

    private val processedMessageIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val gcsFilter = GcsFilter()

    var onMessageReceived: ((TacticalMessage) -> Unit)? = null
    var onPeerStatusChanged: ((String) -> Unit)? = null

    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }

    fun updateIdentity(callSign: String, nodeId: String) {
        myCallSign = callSign
        myNodeId = nodeId
        stopMesh()
        startMesh()
    }

    fun startMesh() {
        try {
            startAdvertising()
            startDiscovery()
            Log.d(TAG, "Mesh engine started for node $myNodeId ($myCallSign)")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting mesh: ${e.message}", e)
        }
    }

    fun stopMesh() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
            peerMap.clear()
            pendingEndpoints.clear()
            _connectedPeers.value = emptyList()
            Log.d(TAG, "Mesh engine stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mesh: ${e.message}", e)
        }
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(strategy)
            .setDisruptiveUpgrade(false)
            .build()

        val endpointName = "$myCallSign|$myNodeId"
        connectionsClient.startAdvertising(
            endpointName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising active as $endpointName")
        }.addOnFailureListener { e ->
            Log.w(TAG, "startAdvertising failure: ${e.message}")
        }
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(strategy)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery active for service $SERVICE_ID")
        }.addOnFailureListener { e ->
            Log.w(TAG, "startDiscovery failure: ${e.message}")
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Discovered endpoint: $endpointId (${info.endpointName})")
            pendingEndpoints[endpointId] = info.endpointName
            
            // Auto request connection in cluster
            connectionsClient.requestConnection(
                "$myCallSign|$myNodeId",
                endpointId,
                connectionLifecycleCallback
            ).addOnFailureListener { e ->
                Log.w(TAG, "requestConnection failed to $endpointId: ${e.message}")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            pendingEndpoints.remove(endpointId)
            if (peerMap.containsKey(endpointId)) {
                val removed = peerMap.remove(endpointId)
                _connectedPeers.value = peerMap.values.toList()
                removed?.let { onPeerStatusChanged?.invoke("PEER DISCONNECTED: ${it.callSign} [${it.nodeId}]") }
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with $endpointId (${connectionInfo.endpointName})")
            pendingEndpoints[endpointId] = connectionInfo.endpointName
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                val endpointName = pendingEndpoints.remove(endpointId) ?: ""
                val parts = endpointName.split("|")
                val peerCallSign = parts.getOrNull(0)?.ifBlank { null } ?: "@peer_${endpointId.take(4)}"
                val peerNodeId = parts.getOrNull(1)?.ifBlank { null } ?: "NODE-${endpointId.take(4).uppercase()}"

                val peer = TacticalMeshPeer(
                    endpointId = endpointId,
                    callSign = peerCallSign,
                    nodeId = peerNodeId,
                    rssi = -55,
                    isConnected = true,
                    hopCount = 1,
                    hopDistance = 1,
                    keyFingerprint = TacticalCrypto.generateFingerprint(endpointId)
                )
                peerMap[endpointId] = peer
                _connectedPeers.value = peerMap.values.toList()
                Log.d(TAG, "Connected to peer: $peerCallSign [$peerNodeId]")
                onPeerStatusChanged?.invoke("PEER CONNECTED: $peerCallSign [$peerNodeId] (1 HOP)")
            } else {
                Log.w(TAG, "Connection rejected or failed for $endpointId: ${result.status.statusCode}")
                pendingEndpoints.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            val removed = peerMap.remove(endpointId)
            pendingEndpoints.remove(endpointId)
            _connectedPeers.value = peerMap.values.toList()
            removed?.let { onPeerStatusChanged?.invoke("PEER DISCONNECTED: ${it.callSign} [${it.nodeId}]") }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return

            try {
                val rawStr = String(bytes, StandardCharsets.UTF_8)
                val parts = rawStr.split("|")
                val header = parts.getOrNull(0)

                if (header == PROTOCOL_V2_HEADER && parts.size >= 14) {
                    val channel = parts[1]
                    val senderCallSign = parts[2]
                    val senderNodeId = parts[3]
                    val targetRecipient = parts[4]
                    val messageText = parts[5]
                    val messageId = parts[6]
                    val victimId = parts[7].ifBlank { null }
                    val originDeviceId = parts[8].ifBlank { null }
                    val latitude = parts[9].toDoubleOrNull()
                    val longitude = parts[10].toDoubleOrNull()
                    val ttl = parts[11].toIntOrNull() ?: 7
                    val incomingHopCount = parts[12].toIntOrNull() ?: 0
                    val timestamp = parts[13].toLongOrNull() ?: System.currentTimeMillis()

                    // Store-and-Forward / DTN Deduplication
                    if (processedMessageIds.contains(messageId) || gcsFilter.contains(messageId)) {
                        Log.d(TAG, "Ignoring already processed message: $messageId")
                        return
                    }

                    processedMessageIds.add(messageId)
                    gcsFilter.add(messageId)

                    val newHopCount = incomingHopCount + 1

                    // Check if private or broadcast
                    val isPrivate = targetRecipient != "ALL"
                    val isEmergency = channel == "#emergency" || messageText.startsWith("🚨 SOS") || messageText.contains("[SOS DISTRESS]")
                    val msgType = when {
                        isEmergency -> MessageType.ALERT_CRITICAL
                        isPrivate -> MessageType.DIRECT_MESSAGE
                        else -> MessageType.CHANNEL_BROADCAST
                    }

                    val incomingMsg = TacticalMessage(
                        id = messageId,
                        type = msgType,
                        channel = channel,
                        senderHandle = senderCallSign,
                        senderId = senderNodeId,
                        recipientHandle = if (isPrivate) targetRecipient else null,
                        recipientId = if (isPrivate) targetRecipient else null,
                        victimId = victimId,
                        originDeviceId = originDeviceId,
                        latitude = latitude,
                        longitude = longitude,
                        text = messageText,
                        timestamp = timestamp,
                        encryption = EncryptionSuite.AES_GCM_256,
                        isOutgoing = false,
                        status = DeliveryStatus.RELAYED,
                        ttl = ttl,
                        hopCount = newHopCount
                    )

                    // Re-relay to mesh peers if TTL allows and not private for someone else
                    if (newHopCount < ttl && !isPrivate) {
                        relayPacket(incomingMsg, excludeEndpointId = endpointId)
                    }

                    // Deliver locally
                    if (!isPrivate || isAddressedToMe(targetRecipient)) {
                        onMessageReceived?.invoke(incomingMsg)
                    }

                } else if (header == PROTOCOL_V1_HEADER && parts.size >= 6) {
                    val channel = parts[1]
                    val senderCallSign = parts[2]
                    val senderNodeId = parts[3]
                    val targetRecipient = parts[4]
                    val messageText = parts[5]
                    val pseudoId = "v1-${senderNodeId}-${messageText.hashCode()}"

                    if (!processedMessageIds.add(pseudoId)) return

                    val isEmergency = channel == "#emergency" || messageText.startsWith("🚨 SOS")
                    val incomingMsg = TacticalMessage(
                        id = pseudoId,
                        type = if (isEmergency) MessageType.ALERT_CRITICAL else MessageType.CHANNEL_BROADCAST,
                        channel = channel,
                        senderHandle = senderCallSign,
                        senderId = senderNodeId,
                        text = messageText,
                        isOutgoing = false,
                        status = DeliveryStatus.RELAYED,
                        hopCount = 1
                    )
                    onMessageReceived?.invoke(incomingMsg)
                } else if (parts.size >= 3) {
                    // Legacy fallback
                    val channel = parts[0]
                    val sender = parts[1]
                    val text = parts[2]
                    val pseudoId = "legacy-${sender}-${text.hashCode()}"
                    if (!processedMessageIds.add(pseudoId)) return

                    val isEmergency = channel == "#emergency" || text.startsWith("🚨 SOS")
                    val incomingMsg = TacticalMessage(
                        id = pseudoId,
                        type = if (isEmergency) MessageType.ALERT_CRITICAL else MessageType.CHANNEL_BROADCAST,
                        channel = channel,
                        senderHandle = sender,
                        senderId = endpointId,
                        text = text,
                        isOutgoing = false,
                        status = DeliveryStatus.RELAYED,
                        hopCount = 1
                    )
                    onMessageReceived?.invoke(incomingMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing incoming payload: ${e.message}", e)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun isAddressedToMe(targetRecipient: String): Boolean {
        val cleanTarget = targetRecipient.removePrefix("@").trim()
        val cleanMyCall = myCallSign.removePrefix("@").trim()
        val cleanMyNode = myNodeId.trim()
        return cleanTarget.equals(cleanMyCall, ignoreCase = true) || cleanTarget.equals(cleanMyNode, ignoreCase = true)
    }

    private fun relayPacket(msg: TacticalMessage, excludeEndpointId: String) {
        try {
            val payloadStr = formatV2Payload(msg, targetRecipient = "ALL")
            val payload = Payload.fromBytes(payloadStr.toByteArray(StandardCharsets.UTF_8))
            val targets = peerMap.keys.filter { it != excludeEndpointId }
            if (targets.isNotEmpty()) {
                connectionsClient.sendPayload(targets, payload).addOnFailureListener { e ->
                    Log.w(TAG, "Failed relaying packet to peers: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error relaying packet: ${e.message}", e)
        }
    }

    fun broadcastMessage(msg: TacticalMessage, targetRecipient: String = "ALL") {
        try {
            processedMessageIds.add(msg.id)
            gcsFilter.add(msg.id)

            val payloadStr = formatV2Payload(msg, targetRecipient)
            val payload = Payload.fromBytes(payloadStr.toByteArray(StandardCharsets.UTF_8))
            val targets = peerMap.keys.toList()
            if (targets.isNotEmpty()) {
                connectionsClient.sendPayload(targets, payload).addOnFailureListener { e ->
                    Log.w(TAG, "Failed sending payload to peers: ${e.message}")
                }
            } else {
                Log.d(TAG, "No peers currently connected. Packet queued locally in Room.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in broadcastMessage: ${e.message}", e)
        }
    }

    fun broadcastPacket(channel: String, text: String, targetRecipient: String = "ALL") {
        val msg = TacticalMessage(
            id = UUID.randomUUID().toString(),
            channel = channel,
            senderHandle = myCallSign,
            senderId = myNodeId,
            text = text,
            isOutgoing = true,
            status = DeliveryStatus.SENT
        )
        broadcastMessage(msg, targetRecipient)
    }

    private fun formatV2Payload(msg: TacticalMessage, targetRecipient: String): String {
        val latStr = msg.latitude?.toString() ?: ""
        val lngStr = msg.longitude?.toString() ?: ""
        val victimStr = msg.victimId ?: ""
        val originStr = msg.originDeviceId ?: ""
        return "$PROTOCOL_V2_HEADER|${msg.channel.orEmpty()}|${msg.senderHandle}|${msg.senderId}|$targetRecipient|${msg.text}|${msg.id}|$victimStr|$originStr|$latStr|$lngStr|${msg.ttl}|${msg.hopCount}|${msg.timestamp}"
    }
}
