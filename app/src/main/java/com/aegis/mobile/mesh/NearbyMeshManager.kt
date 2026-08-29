package com.aegis.mobile.mesh

import android.content.Context
import com.aegis.mobile.data.DeliveryStatus
import com.aegis.mobile.data.TacticalMessage
import com.aegis.mobile.data.TacticalMeshPeer
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

class NearbyMeshManager(private val context: Context, private val myCallSign: String, private val myNodeId: String) {

    private val serviceId = "com.crisisnet.tactical.mesh"
    private val strategy = Strategy.P2P_CLUSTER

    private val _connectedPeers = MutableStateFlow<List<TacticalMeshPeer>>(emptyList())
    val connectedPeers: StateFlow<List<TacticalMeshPeer>> = _connectedPeers.asStateFlow()

    private val peerMap = mutableMapOf<String, TacticalMeshPeer>()
    var onMessageReceived: ((TacticalMessage) -> Unit)? = null

    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }

    fun startMesh() {
        startAdvertising()
        startDiscovery()
    }

    fun stopMesh() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        peerMap.clear()
        _connectedPeers.value = emptyList()
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(
            "$myCallSign|$myNodeId",
            serviceId,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnFailureListener {
            // Log or fallback
        }
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnFailureListener {
            // Log or fallback
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection(
                "$myCallSign|$myNodeId",
                endpointId,
                connectionLifecycleCallback
            )
        }

        override fun onEndpointLost(endpointId: String) {
            peerMap.remove(endpointId)
            _connectedPeers.value = peerMap.values.toList()
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // Auto accept connection in cluster
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                val peer = TacticalMeshPeer(
                    endpointId = endpointId,
                    callSign = "@peer_${endpointId.take(4)}",
                    nodeId = "NODE-${endpointId.take(4).uppercase()}",
                    rssi = -60,
                    isConnected = true,
                    keyFingerprint = TacticalCrypto.generateFingerprint(endpointId)
                )
                peerMap[endpointId] = peer
                _connectedPeers.value = peerMap.values.toList()
            }
        }

        override fun onDisconnected(endpointId: String) {
            peerMap.remove(endpointId)
            _connectedPeers.value = peerMap.values.toList()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    val rawStr = String(bytes, StandardCharsets.UTF_8)
                    // Parse payload: channel|sender|text
                    val parts = rawStr.split("|", limit = 4)
                    if (parts.size >= 3) {
                        val channel = parts[0]
                        val sender = parts[1]
                        val text = parts[2]
                        val msg = TacticalMessage(
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
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun broadcastPacket(channel: String, text: String) {
        val payloadStr = "$channel|$myCallSign|$text"
        val payload = Payload.fromBytes(payloadStr.toByteArray(StandardCharsets.UTF_8))
        if (peerMap.isNotEmpty()) {
            connectionsClient.sendPayload(peerMap.keys.toList(), payload)
        }
    }
}
