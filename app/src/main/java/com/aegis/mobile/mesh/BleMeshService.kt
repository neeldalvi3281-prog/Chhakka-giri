package com.aegis.mobile.mesh

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aegis.mobile.AegisApplication
import com.aegis.mobile.MainActivity
import com.aegis.mobile.data.DeliveryStatus
import com.aegis.mobile.data.EncryptionSuite
import com.aegis.mobile.data.MessageType
import com.aegis.mobile.data.TacticalMessage
import java.util.UUID

class BleMeshService : Service() {

    private val binder = LocalBinder()
    private val gcsFilter = GcsFilter()
    private val processedIds = mutableSetOf<String>()

    inner class LocalBinder : Binder() {
        fun getService(): BleMeshService = this@BleMeshService
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1001, createServiceNotification())
    }

    override fun onBind(intent: Intent?): IBinder = binder

    @SuppressLint("MissingPermission")
    fun sendSos(text: String, lat: Double?, lng: Double?): TacticalMessage {
        val now = System.currentTimeMillis()
        val id = "sos-$now-${UUID.randomUUID().toString().take(6)}"

        val locString = if (lat != null && lng != null) " LOC: $lat, $lng" else ""
        val fullText = "⚠️ [SOS DISTRESS] $text$locString"

        val msg = TacticalMessage(
            id = id,
            type = MessageType.ALERT_CRITICAL,
            channel = "#emergency",
            senderHandle = "@operator",
            senderId = "NODE-LOCAL",
            text = fullText,
            rawCiphertext = TacticalCrypto.encryptChannelMessage("#emergency", fullText),
            timestamp = now,
            encryption = EncryptionSuite.AES_GCM_256,
            isOutgoing = true,
            status = DeliveryStatus.SENT,
            ttl = 10
        )

        processedIds.add(id)
        gcsFilter.add(id)

        return msg
    }

    private fun createServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, AegisApplication.CHANNEL_MESH_SERVICE)
            .setContentTitle("Crisis Net Offline Mesh Active")
            .setContentText("Listening and relaying emergency gossip packets over Bluetooth & Wi-Fi Direct.")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
