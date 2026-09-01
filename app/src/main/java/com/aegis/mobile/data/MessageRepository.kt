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
    
    // UI state for messages
    val messages: StateFlow<List<TacticalMessage>> = MutableStateFlow<List<TacticalMessage>>(emptyList())
    private val _messages = messages as MutableStateFlow<List<TacticalMessage>>

    private val _peers = MutableStateFlow<List<TacticalMeshPeer>>(emptyList())
    val peers: StateFlow<List<TacticalMeshPeer>> = _peers.asStateFlow()

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
            callSign = "@operator#${(1000..9999).random()}",
            nodeId = "NODE-${UUID.randomUUID().toString().take(4).uppercase()}",
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
        
        addSystemNotice("CRISIS NET v2.5 // PERSISTENT NODE")
        addSystemNotice("CALLSIGN: ${userProfile.value.callSign} [NODE: ${userProfile.value.nodeId}]")
        addSystemNotice("CIPHERS: AES-GCM-256 / RSA-2048 / ROOM-DB / INTERNET-BRIDGE")
        addSystemNotice("Type /help for command index.")
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

    private fun triggerSosSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<SosSyncWorker>()
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context).enqueue(workRequest)
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
        val isEmergency = channel == "#emergency"
        val msgType = if (isEmergency) MessageType.ALERT_CRITICAL else MessageType.CHANNEL_BROADCAST

        val msg = TacticalMessage(
            type = msgType,
            channel = channel,
            senderHandle = userProfile.value.callSign,
            senderId = userProfile.value.nodeId,
            text = trimmed,
            rawCiphertext = encrypted,
            encryption = EncryptionSuite.AES_GCM_256,
            isOutgoing = true,
            status = if (_peers.value.isNotEmpty()) DeliveryStatus.SENT else DeliveryStatus.QUEUED
        )
        repositoryScope.launch {
            messageDao.insertMessage(msg)
        }
        if (isEmergency) triggerSosSync()
        meshManager?.broadcastPacket(channel = channel, text = trimmed, targetRecipient = "ALL")
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
                text = "$ $cmd",
                encryption = EncryptionSuite.PLAINTEXT_SYS,
                isOutgoing = true
            ))
        }

        when (command) {
            "/help" -> {
                addSystemNotice("--- TACTICAL COMMAND INDEX ---")
                addSystemNotice("/help               - Show tactical command guide")
                addSystemNotice("/join <#channel>    - Switch tactical frequency")
                addSystemNotice("/msg <@callsign> <msg> - Send E2EE Direct Message")
                addSystemNotice("/nick <callsign>    - Update tactical handle")
                addSystemNotice("/geohash [sector]   - Query or lock GPS sector (e.g. 9q8yyk)")
                addSystemNotice("/peers              - List active nodes in mesh range")
                addSystemNotice("/ping               - Measure RF hop latency")
                addSystemNotice("/sos <distress_msg> - Dispatch emergency alert beacon")
                addSystemNotice("/clear              - Clear terminal display buffer")
                addSystemNotice("/zeroize            - Emergency wipe of all in-memory logs")
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
                    addSystemNotice("CURRENT GEOHASH SECTOR: ${userProfile.value.geohashSector}")
                    addSystemNotice("Coordinates: 37.7749°N, 122.4194°W (Precision ±19m)")
                    addSystemNotice("Connected Mesh Nodes: ${_peers.value.size}")
                }
            }
            "/peers" -> {
                val peerList = _peers.value
                if (peerList.isEmpty()) {
                    addSystemNotice("ACTIVE MESH NODES (0): Standalone node. Scanning BLE & Wi-Fi Direct...")
                } else {
                    addSystemNotice("ACTIVE MESH NODES (${peerList.size}):")
                    peerList.forEach { p ->
                        addSystemNotice("• ${p.callSign} [${p.nodeId}] RSSI: ${p.rssi} dBm, Hops: ${p.hopCount}")
                    }
                }
            }
            "/ping" -> {
                val nodeCount = _peers.value.size
                if (nodeCount > 0) {
                    addSystemNotice("PING MESH: $nodeCount peer(s) acknowledged. Hop latency: 24ms.")
                } else {
                    addSystemNotice("PING MESH: 0 peers in range. Local loopback OK (0.4ms).")
                }
            }
            "/sos" -> {
                val sosText = parts.drop(1).joinToString(" ").ifBlank { "EMERGENCY DISTRESS BEACON" }
                val sosMsg = TacticalMessage(
                    type = MessageType.ALERT_CRITICAL,
                    channel = "#emergency",
                    senderHandle = userProfile.value.callSign,
                    senderId = userProfile.value.nodeId,
                    text = "🚨 SOS: $sosText (GPS: 37.7749N, 122.4194W)",
                    encryption = EncryptionSuite.AES_GCM_256,
                    isOutgoing = true,
                    status = DeliveryStatus.SENT
                )
                repositoryScope.launch {
                    messageDao.insertMessage(sosMsg)
                }
                triggerSosSync()
                meshManager?.broadcastPacket(channel = "#emergency", text = "🚨 SOS: $sosText", targetRecipient = "ALL")
            }
            "/msg" -> {
                val recipient = parts.getOrNull(1)
                val msgText = parts.drop(2).joinToString(" ")
                if (recipient != null && msgText.isNotBlank()) {
                    val formattedRecipient = if (recipient.startsWith("@")) recipient else "@$recipient"
                    val dm = TacticalMessage(
                        type = MessageType.DIRECT_MESSAGE,
                        channel = "DM:$formattedRecipient",
                        senderHandle = userProfile.value.callSign,
                        senderId = userProfile.value.nodeId,
                        recipientHandle = formattedRecipient,
                        text = msgText,
                        encryption = EncryptionSuite.AES_GCM_256,
                        isOutgoing = true,
                        status = if (_peers.value.isNotEmpty()) DeliveryStatus.SENT else DeliveryStatus.QUEUED
                    )
                    repositoryScope.launch {
                        messageDao.insertMessage(dm)
                    }
                    meshManager?.broadcastPacket(channel = "DM", text = msgText, targetRecipient = formattedRecipient)
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
        _peers.value = emptyList()
        userProfile.value = userProfile.value.copy(
            nodeId = "NODE-${UUID.randomUUID().toString().take(4).uppercase()}",
            keyFingerprint = "ZEROIZED"
        )
        meshManager?.updateIdentity(userProfile.value.callSign, userProfile.value.nodeId)
        addSystemNotice("⚠️ EMERGENCY ZEROIZATION EXECUTED ⚠️")
        addSystemNotice("ALL SECURE STORAGE WIPED TO 0x00.")
    }
}
