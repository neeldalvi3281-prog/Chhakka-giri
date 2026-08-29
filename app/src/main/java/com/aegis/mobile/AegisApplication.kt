package com.aegis.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class AegisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val meshChannel = NotificationChannel(
                CHANNEL_MESH_SERVICE,
                "Aegis Mesh Network Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the offline Bluetooth mesh gossip relay active."
            }

            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY_ALERTS,
                "Emergency SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical emergency broadcasts from nearby survivors."
                enableVibration(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(meshChannel)
            manager.createNotificationChannel(emergencyChannel)
        }
    }

    companion object {
        const val CHANNEL_MESH_SERVICE = "aegis_mesh_channel"
        const val CHANNEL_EMERGENCY_ALERTS = "aegis_sos_channel"
    }
}
