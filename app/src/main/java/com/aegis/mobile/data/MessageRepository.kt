package com.aegis.mobile.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aegis.mobile.mesh.NearbyMeshManager
import com.aegis.mobile.mesh.TacticalCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MessageRepository(private val context: Context, private val messageDao: MessageDao) {

    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    val identityManager = IdentityManager(context)

    // UI state for messages
    val messages: StateFlow<List<TacticalMessage>> = MutableStateFlow<List<TacticalMessage>>(emptyList())
    private val _messages = messages as MutableStateFlow<List<TacticalMessage>>

    private val _peers = MutableStateFlow<List<TacticalMeshPeer>>(emptyList())
    val peers: StateFlow<List<TacticalMeshPeer>> = _peers.asStateFlow()

    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

    private val _channels = MutableStateFlow<List<TacticalChannel>>(
        listOf(
            TacticalChannel("#general", "General", isEncrypted = true, description = "Default tactical mesh frequency"),
            TacticalChannel("#emergency", "Emergency", isEncrypted = true, description = "Priority SOS alerts"),
            TacticalChannel("#logistics", "Logistics", isEncrypted = true, description = "Supply chain coordination"),
            TacticalChannel("#sector-9", "Sector 9", isEncrypted = true, description = "Tactical operational zone"),
            TacticalChannel("#team-alpha", "Team Alpha", isEncrypted = true, description = "Special response unit")
        )
    )
    val channels: StateFlow<List<TacticalChannel>> = _channels.asStateFlow()

    val userProfile = MutableStateFlow(
        TacticalUserProfile(
            callSign = identityManager.callSign,
            nodeId = identityManager.nodeId,
            currentChannel = "#general",
            geohashSector = "#9q8yy"
        )
    )

    var meshManager: NearbyMeshManager? = null

    init {
        repositoryScope.launch {
            messageDao.getAllMessages().collect { dbMessages ->
                _messages.value = dbMessages
            }
        }
        
        addSystemNotice("AEGIS PROTOCOL v3.0 // SECURE NODE")
        addSystemNotice("CALLSIGN: ${userProfile.value.callSign} [NODE: ${userProfile.value.nodeId}]")
        addSystemNotice("DEVICE ID: ${identityManager.deviceLabel} | VICTIM ID: ${identityManager.victimLabel}")
        addSystemNotice("CIPHERS: AES-GCM-256 / ROOM-PERSIST / STORE-AND-FORWARD / GATEWAY-SYNC")
        addSystemNotice("Type /help for command index.")
    }

    fun updateLocation(lat: Double, lng: Double) {
        _currentLocation.value = Pair(lat, lng)
    }

    fun setPeers(peerList: List<TacticalMeshPeer>) {
        _peers.value = peerList
    }

    fun addSystemNotice(text: String) {
        val msg = TacticalMessage(
            type = MessageType.SYSTEM_NOTICE,
            channel = userProfile.value.currentChannel,
            senderHandle = "AEGIS_SYS",
            senderId = "KERNEL",
            text = text,
            encryption = EncryptionSuite.PLAINTEXT_SYS,
            status = DeliveryStatus.DELIVERED
        )
        repositoryScope.launch {
            messageDao.insertMessage(msg)
        }
    }

    fun receiveIncomingMessage(msg: TacticalMessage) {
        repositoryScope.launch {
            messageDao.insertMessage(msg)
        }
        if (msg.type == MessageType.ALERT_CRITICAL) {
            triggerSosSync()
        }
    }

    fun triggerSosSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<SosSyncWorker>()
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context).enqueue(workRequest)
    }

    fun sendSos(customText: String, lat: Double? = null, lng: Double? = null) {
        val actualLat = lat ?: _currentLocation.value?.first
        val actualLng = lng ?: _currentLocation.value?.second
        val stableMessageId = UUID.randomUUID().toString()

        val locString = if (actualLat != null && actualLng != null) {
            " (GPS: ${String.format("%.4f", actualLat)}°N, ${String.format("%.4f", actualLng)}°E)"
        } else ""

        val displayText = if (customText.startsWith("🚨 SOS") || customText.startsWith("⚠️ [SOS")) {
            customText
        } else {
            "🚨 SOS: $customText$locString"
        }

        val sosMsg = TacticalMessage(
            id = stableMessageId,
            type = MessageType.ALERT_CRITICAL,
            channel = "#emergency",
            senderHandle = userProfile.value.callSign,
            senderId = userProfile.value.nodeId,
            victimId = identityManager.victimId,
            originDeviceId = identityManager.deviceId,
            latitude = actualLat,
            longitude = actualLng,
            text = displayText,
            encryption = EncryptionSuite.AES_GCM_256,
            isOutgoing = true,
            status = if (_peers.value.isNotEmpty()) DeliveryStatus.SENT else DeliveryStatus.QUEUED,
            ttl = 7,
            hopCount = 0
        )

        repositoryScope.launch {
            messageDao.insertMessage(sosMsg)
        }

        meshManager?.broadcastMessage(sosMsg, targetRecipient = "ALL")
        triggerSosSync()
    }

    fun handleInput(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        if (trimmed.startsWith("/")) {
            executeCommand(trimmed)
            return
        }

        val channel = userProfile.value.currentChannel
        val encrypted = TacticalCrypto.encryptChannelMessage(channel, trimmed)
        val isEmergency = channel == "#emergency" || trimmed.startsWith("🚨 SOS")

        if (isEmergency) {
            sendSos(trimmed)
            return
        }

        val msg = TacticalMessage(
            id = UUID.randomUUID().toString(),
            type = MessageType.CHANNEL_BROADCAST,
            channel = channel,
            senderHandle = userProfile.value.callSign,
            senderId = userProfile.value.nodeId,
            originDeviceId = identityManager.deviceId,
            text = trimmed,
            rawCiphertext = encrypted,
            encryption = EncryptionSuite.AES_GCM_256,
            isOutgoing = true,
            status = if (_peers.value.isNotEmpty()) DeliveryStatus.SENT else DeliveryStatus.QUEUED,
            ttl = 7,
            hopCount = 0
        )

        repositoryScope.launch {
            messageDao.insertMessage(msg)
        }
        meshManager?.broadcastMessage(msg, targetRecipient = "ALL")
    }

    private fun executeCommand(cmd: String) {
        val parts = cmd.split(" ")
        val command = parts[0].lowercase()

        // Command echo
        repositoryScope.launch {
            messageDao.insertMessage(TacticalMessage(
                type = MessageType.COMMAND_ECHO,
                channel = userProfile.value.currentChannel,
                senderHandle = userProfile.value.callSign,
                senderId = userProfile.value.nodeId,
                originDeviceId = identityManager.deviceId,
                text = "$ $cmd",
                encryption = EncryptionSuite.PLAINTEXT_SYS,
                isOutgoing = true,
                status = DeliveryStatus.DELIVERED
            ))
        }

        when (command) {
            "/help" -> {
                addSystemNotice("--- AEGIS TACTICAL COMMAND INDEX ---")
                addSystemNotice("/help               - Show tactical command guide")
                addSystemNotice("/sos <distress_msg> - Dispatch emergency SOS beacon")
                addSystemNotice("/join <#channel>    - Switch tactical frequency")
                addSystemNotice("/msg <@callsign> <msg> - Send E2EE Direct Message")
                addSystemNotice("/nick <callsign>    - Update tactical handle")
                addSystemNotice("/geohash [sector]   - Query or lock GPS sector")
                addSystemNotice("/peers              - List active nodes in mesh range")
                addSystemNotice("/sync               - Manually force Gateway SOS Sync")
                addSystemNotice("/clear              - Clear terminal display buffer")
                addSystemNotice("/zeroize            - Emergency wipe of all local storage")
            }
            "/sos" -> {
                val sosText = parts.drop(1).joinToString(" ").ifBlank { "EMERGENCY DISTRESS BEACON" }
                sendSos(sosText)
            }
            "/sync" -> {
                addSystemNotice("Triggering background Gateway upload check...")
                triggerSosSync()
            }
            "/geohash" -> {
                val targetSector = parts.getOrNull(1)
                if (targetSector != null) {
                    val formatted = if (targetSector.startsWith("#")) targetSector else "#$targetSector"
                    userProfile.value = userProfile.value.copy(
                        currentChannel = formatted,
                        geohashSector = formatted
                    )
                    addSystemNotice("TARGET GEOHASH SECTOR LOCKED -> $formatted")
                } else {
                    val lat = _currentLocation.value?.first ?: 23.0301
                    val lng = _currentLocation.value?.second ?: 72.5852
                    addSystemNotice("CURRENT GEOHASH SECTOR: ${userProfile.value.geohashSector}")
                    addSystemNotice("Coordinates: ${String.format("%.4f", lat)}°N, ${String.format("%.4f", lng)}°E")
                    addSystemNotice("Connected Mesh Nodes: ${_peers.value.size}")
                }
            }
            "/peers" -> {
                val peerList = _peers.value
                if (peerList.isEmpty()) {
                    addSystemNotice("ACTIVE MESH NODES (0): Scanning Nearby Connections BLE & Wi-Fi Direct...")
                } else {
                    addSystemNotice("ACTIVE MESH NODES (${peerList.size}):")
                    peerList.forEach { p ->
                        addSystemNotice("• ${p.callSign} [${p.nodeId}] RSSI: ${p.rssi} dBm, Hops: ${p.hopCount}")
                    }
                }
            }
            "/msg" -> {
                val recipient = parts.getOrNull(1)
                val msgText = parts.drop(2).joinToString(" ")
                if (recipient != null && msgText.isNotBlank()) {
                    val formattedRecipient = if (recipient.startsWith("@")) recipient else "@$recipient"
                    val dm = TacticalMessage(
                        id = UUID.randomUUID().toString(),
                        type = MessageType.DIRECT_MESSAGE,
                        channel = "DM:$formattedRecipient",
                        senderHandle = userProfile.value.callSign,
                        senderId = userProfile.value.nodeId,
                        recipientHandle = formattedRecipient,
                        recipientId = formattedRecipient,
                        originDeviceId = identityManager.deviceId,
                        text = msgText,
                        encryption = EncryptionSuite.AES_GCM_256,
                        isOutgoing = true,
                        status = if (_peers.value.isNotEmpty()) DeliveryStatus.SENT else DeliveryStatus.QUEUED,
                        ttl = 7,
                        hopCount = 0
                    )
                    repositoryScope.launch {
                        messageDao.insertMessage(dm)
                    }
                    meshManager?.broadcastMessage(dm, targetRecipient = formattedRecipient)
                } else {
                    addSystemNotice("ERROR: Syntax: /msg <@recipient> <message>")
                }
            }
            "/join" -> {
                val target = parts.getOrNull(1)
                if (target != null) {
                    val formatted = if (target.startsWith("#")) target else "#$target"
                    userProfile.value = userProfile.value.copy(currentChannel = formatted)
                    addSystemNotice("SWITCHED FREQUENCY -> $formatted [AES-GCM LOCKED]")
                } else {
                    addSystemNotice("ERROR: Missing channel name. Syntax: /join #channel")
                }
            }
            "/nick" -> {
                val newNick = parts.getOrNull(1)
                if (newNick != null) {
                    val formatted = if (newNick.startsWith("@")) newNick else "@$newNick"
                    userProfile.value = userProfile.value.copy(callSign = formatted)
                    identityManager.callSign = formatted
                    meshManager?.updateIdentity(formatted, userProfile.value.nodeId)
                    addSystemNotice("Callsign updated to $formatted")
                } else {
                    addSystemNotice("ERROR: Missing callsign. Syntax: /nick <handle>")
                }
            }
            "/clear" -> {
                repositoryScope.launch {
                    messageDao.deleteAllMessages()
                }
                addSystemNotice("TERMINAL DISPLAY BUFFER CLEARED.")
            }
            "/zeroize" -> {
                emergencyZeroize()
            }
            else -> {
                addSystemNotice("UNKNOWN COMMAND: $command. Type /help for syntax.")
            }
        }
    }

    fun emergencyZeroize() {
        repositoryScope.launch {
            messageDao.deleteAllMessages()
        }
        identityManager.resetIdentity()
        _peers.value = emptyList()
        userProfile.value = userProfile.value.copy(
            callSign = identityManager.callSign,
            nodeId = identityManager.nodeId,
            keyFingerprint = "ZEROIZED"
        )
        meshManager?.updateIdentity(userProfile.value.callSign, userProfile.value.nodeId)
        addSystemNotice("⚠️ EMERGENCY ZEROIZATION EXECUTED ⚠️")
        addSystemNotice("ALL PERSISTENT ROOM STORAGE AND IDENTITIES PURGED TO 0x00.")
    }
}
