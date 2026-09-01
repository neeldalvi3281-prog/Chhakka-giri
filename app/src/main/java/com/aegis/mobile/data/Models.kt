package com.aegis.mobile.data

import java.util.UUID
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageType {
    CHANNEL_BROADCAST,
    DIRECT_MESSAGE,
    SYSTEM_NOTICE,
    COMMAND_ECHO,
    ALERT_CRITICAL
}

enum class EncryptionSuite {
    AES_GCM_256,
    RSA_2048_E2EE,
    PLAINTEXT_SYS
}

enum class DeliveryStatus {
    SENT,
    DELIVERED,
    QUEUED,
    RELAYED
}

@Entity(tableName = "messages")
data class TacticalMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: MessageType = MessageType.CHANNEL_BROADCAST,
    val channel: String? = "#general",
    val senderHandle: String,
    val senderId: String,
    val recipientHandle: String? = null,
    val recipientId: String? = null,
    val text: String,
    val rawCiphertext: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val encryption: EncryptionSuite = EncryptionSuite.AES_GCM_256,
    val isOutgoing: Boolean = false,
    val status: DeliveryStatus = DeliveryStatus.SENT,
    val ttl: Int = 7,
    val hopCount: Int = 0,
    val securityHash: String? = null
)

data class TacticalMeshPeer(
    val endpointId: String,
    val callSign: String,
    val nodeId: String,
    val rssi: Int = -60,
    val lastSeen: Long = System.currentTimeMillis(),
    val isConnected: Boolean = true,
    val keyFingerprint: String = "A4:9E:C1:2F",
    val hopDistance: Int = 1,
    val hopCount: Int = 1,
    val batteryPercent: Int = 85
)

data class TacticalChannel(
    val id: String,
    val name: String,
    val isEncrypted: Boolean = true,
    val isGeohash: Boolean = false,
    val description: String = ""
)

data class TacticalUserProfile(
    var callSign: String = "@operator#4821",
    val nodeId: String = "NODE-Alpha",
    val keyFingerprint: String = "7A:4F:9C:12",
    var currentChannel: String = "#general",
    var geohashSector: String = "#9q8yy"
)
