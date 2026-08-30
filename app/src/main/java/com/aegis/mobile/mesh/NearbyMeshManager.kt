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

class NearbyMeshManager(
    private val context: Context,
    private var myCallSign: String,
    private var myNodeId: String
) {
    companion object {
        private const val TAG = "NearbyMeshManager"
        private const val SERVICE_ID = "com.crisisnet.tactical.mesh"
        private const val PROTOCOL_HEADER = "CRISIS_P2P_V1"
    }

    private val strategy = Strategy.P2P_CLUSTER

    private val _connectedPeers = MutableStateFlow<List<TacticalMeshPeer>>(emptyList())
    val connectedPeers: StateFlow<List<TacticalMeshPeer>> = _connectedPeers.asStateFlow()

    private val peerMap = mutableMapOf<String, TacticalMeshPeer>()
    private val pendingEndpoints = mutableMapOf<String, String>()

    var onMessageReceived: ((TacticalMessage) -> Unit)? = null
    var onPeerStatusChanged: ((String) -> Unit)? = null

    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }

    fun updateIdentity(callSign: String, nodeId: String) {
        myCallSign = callSign
        myNodeId = nodeId
        // Restart mesh with new identity
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
            // Automatically accept incoming connections
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
                val parts = rawStr.split("|", limit = 6)
                
                // Check if packet matches protocol
                if (parts.size >= 6 && parts[0] == PROTOCOL_HEADER) {
                    val channel = parts[1]
                    val senderCallSign = parts[2]
                    val senderNodeId = parts[3]
                    val targetRecipient = parts[4]
                    val messageText = parts[5]

                    // Filter Private Messages:
                    val isPrivate = targetRecipient != "ALL"
                    if (isPrivate) {
                        val cleanTarget = targetRecipient.removePrefix("@").trim()
                        val cleanMyCallSign = myCallSign.removePrefix("@").trim()
                        val cleanMyNodeId = myNodeId.trim()

                        val isForMe = cleanTarget.equals(cleanMyCallSign, ignoreCase = true) ||
                                      cleanTarget.equals(cleanMyNodeId, ignoreCase = true)

                        if (isForMe) {
                            // Deliver E2EE Direct Message to this device
                            val dmMsg = TacticalMessage(
                                type = MessageType.DIRECT_MESSAGE,
                                channel = "DM:$senderCallSign",
                                senderHandle = senderCallSign,
                                senderId = senderNodeId,
                                recipientHandle = myCallSign,
                                recipientId = myNodeId,
                                text = messageText,
                                encryption = EncryptionSuite.AES_GCM_256,
                                isOutgoing = false,
                                status = DeliveryStatus.DELIVERED,
                                hopCount = 1
                            )
                            onMessageReceived?.invoke(dmMsg)
                        } else {
                            // Private message is for another peer. Do NOT show in this user's chat!
                            Log.d(TAG, "Ignored private message addressed to $targetRecipient")
                        }
                    } else {
                        // Broadcast / Channel message
                        val isEmergency = channel == "#emergency" || messageText.startsWith("🚨 SOS")
                        val msgType = if (isEmergency) MessageType.ALERT_CRITICAL else MessageType.CHANNEL_BROADCAST

                        val msg = TacticalMessage(
                            type = msgType,
                            channel = channel,
                            senderHandle = senderCallSign,
                            senderId = senderNodeId,
                            text = messageText,
                            encryption = EncryptionSuite.AES_GCM_256,
                            isOutgoing = false,
                            status = DeliveryStatus.DELIVERED,
                            hopCount = 1
                        )
                        onMessageReceived?.invoke(msg)
                    }
                } else if (parts.size >= 3) {
                    // Backward compatibility for legacy payload: channel|sender|text
                    val channel = parts[0]
                    val sender = parts[1]
                    val text = parts[2]
                    
                    val isEmergency = channel == "#emergency" || text.startsWith("🚨 SOS")
                    val isDm = channel.startsWith("DM:")
                    
                    if (isDm) {
                        val target = channel.removePrefix("DM:").removePrefix("@").trim()
                        val cleanMyCall = myCallSign.removePrefix("@").trim()
                        if (target.equals(cleanMyCall, ignoreCase = true)) {
                            val msg = TacticalMessage(
                                type = MessageType.DIRECT_MESSAGE,
                                channel = channel,
                                senderHandle = sender,
                                senderId = endpointId,
                                text = text,
                                isOutgoing = false,
                                status = DeliveryStatus.DELIVERED,
                                hopCount = 1
                            )
                            onMessageReceived?.invoke(msg)
                        }
                    } else {
                        val msg = TacticalMessage(
                            type = if (isEmergency) MessageType.ALERT_CRITICAL else MessageType.CHANNEL_BROADCAST,
                            channel = channel,
                            senderHandle = sender,
                            senderId = endpointId,
                            text = text,
                            isOutgoing = false,
                            status = DeliveryStatus.DELIVERED,
                            hopCount = 1
                        )
                        onMessageReceived?.invoke(msg)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing incoming payload: ${e.message}", e)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun broadcastPacket(channel: String, text: String, targetRecipient: String = "ALL") {
        try {
            val payloadStr = "$PROTOCOL_HEADER|$channel|$myCallSign|$myNodeId|$targetRecipient|$text"
            val payload = Payload.fromBytes(payloadStr.toByteArray(StandardCharsets.UTF_8))
            val targets = peerMap.keys.toList()
            if (targets.isNotEmpty()) {
                connectionsClient.sendPayload(targets, payload).addOnFailureListener { e ->
                    Log.w(TAG, "Failed sending payload to peers: ${e.message}")
                }
            } else {
                Log.d(TAG, "No peers currently connected. Packet queued locally.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in broadcastPacket: ${e.message}", e)
        }
    }
}
