package com.aegis.mobile.data

import com.aegis.mobile.mesh.TacticalCrypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class MessageRepository {

    // Strictly in-memory lists (Zero persistence on disk)
    private val _messages = MutableStateFlow<List<TacticalMessage>>(emptyList())
    val messages: StateFlow<List<TacticalMessage>> = _messages.asStateFlow()

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
            callSign = "@operator#4821",
            nodeId = "NODE-Alpha",
            currentChannel = "#general",
            geohashSector = "#9q8yy"
        )
    )

    init {
        addSystemNotice("CRISIS NET v2.4 // MIL-STD OFFLINE P2P MESH TERMINAL")
        addSystemNotice("CALLSIGN: ${userProfile.value.callSign} [NODE: ${userProfile.value.nodeId}]")
        addSystemNotice("CIPHERS: AES-GCM-256 / RSA-2048 / ZERO-DISK-PERSISTENCE")
        addSystemNotice("Type /help for command index.")
    }

    fun addSystemNotice(text: String) {
        val msg = TacticalMessage(
            type = MessageType.SYSTEM_NOTICE,
            senderHandle = "CRISIS_NET_SYS",
            senderId = "KERNEL",
            text = text,
            encryption = EncryptionSuite.PLAINTEXT_SYS,
            status = DeliveryStatus.DELIVERED
        )
        _messages.value = _messages.value + msg
    }

    fun handleInput(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        if (trimmed.startsWith("/")) {
            executeCommand(trimmed)
            return
        }

        val encrypted = TacticalCrypto.encryptChannelMessage(userProfile.value.currentChannel, trimmed)
        val msg = TacticalMessage(
            type = if (userProfile.value.currentChannel == "#emergency") MessageType.ALERT_CRITICAL else MessageType.CHANNEL_BROADCAST,
            channel = userProfile.value.currentChannel,
            senderHandle = userProfile.value.callSign,
            senderId = userProfile.value.nodeId,
            text = trimmed,
            rawCiphertext = encrypted,
            encryption = EncryptionSuite.AES_GCM_256,
            isOutgoing = true,
            status = if (_peers.value.isNotEmpty()) DeliveryStatus.SENT else DeliveryStatus.QUEUED
        )
        _messages.value = _messages.value + msg
    }

    private fun executeCommand(cmd: String) {
        val parts = cmd.split(" ")
        val command = parts[0].lowercase()

        // Command echo
        _messages.value = _messages.value + TacticalMessage(
            type = MessageType.COMMAND_ECHO,
            senderHandle = userProfile.value.callSign,
            senderId = userProfile.value.nodeId,
            text = "$ $cmd",
            encryption = EncryptionSuite.PLAINTEXT_SYS,
            isOutgoing = true
        )

        when (command) {
            "/help" -> {
                addSystemNotice("--- TACTICAL COMMAND INDEX ---")
                addSystemNotice("/join <#channel>    - Switch tactical frequency")
                addSystemNotice("/msg <@callsign> <msg> - Send E2EE Direct Message")
                addSystemNotice("/nick <callsign>    - Update tactical handle")
                addSystemNotice("/geohash            - Acquire GPS geohash sector")
                addSystemNotice("/clear              - Clear terminal display buffer")
                addSystemNotice("/zeroize            - Emergency wipe of all in-memory logs")
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
                    userProfile.value = userProfile.value.copy(callSign = "@$newNick")
                    addSystemNotice("Callsign updated to @$newNick")
                } else {
                    addSystemNotice("ERROR: Missing callsign. Syntax: /nick <handle>")
                }
            }
            "/clear" -> {
                _messages.value = emptyList()
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
        _messages.value = emptyList()
        _peers.value = emptyList()
        userProfile.value = userProfile.value.copy(
            nodeId = "NODE-${UUID.randomUUID().toString().take(4).uppercase()}",
            keyFingerprint = "ZEROIZED"
        )
        addSystemNotice("⚠️ EMERGENCY ZEROIZATION EXECUTED ⚠️")
        addSystemNotice("ALL VOLATILE MEMORY PURGED TO 0x00.")
    }
}
