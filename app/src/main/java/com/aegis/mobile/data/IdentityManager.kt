package com.aegis.mobile.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Manages persistent device and victim identities across app restarts.
 * Ensures stable device_id, victim_id, node_id, and call_sign.
 */
class IdentityManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("aegis_identity_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEVICE_ID = "key_persistent_device_id"
        private const val KEY_VICTIM_ID = "key_persistent_victim_id"
        private const val KEY_NODE_ID = "key_persistent_node_id"
        private const val KEY_CALL_SIGN = "key_persistent_call_sign"
        private const val KEY_GATEWAY_ENDPOINT = "key_gateway_endpoint"
        private const val KEY_PUBLISHABLE_KEY = "key_publishable_key"
        
        const val DEFAULT_GATEWAY_URL = "https://hohtqhfvoeudftaalyqm.supabase.co/functions/v1/gateway-upload"
    }

    // Stable UUID identities used across the mesh wire protocol, Room storage
    // and the Supabase gateway-upload API (DB columns are uuid type).
    // Short DEV-/VIC- labels are display-only (see deviceLabel/victimLabel).
    val deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id.isNullOrBlank() || !isUuid(id)) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }

    val victimId: String
        get() {
            var id = prefs.getString(KEY_VICTIM_ID, null)
            if (id.isNullOrBlank() || !isUuid(id)) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_VICTIM_ID, id).apply()
            }
            return id
        }

    // Short human-readable labels for terminal display only.
    val deviceLabel: String get() = "DEV-${deviceId.take(8).uppercase()}"
    val victimLabel: String get() = "VIC-${victimId.take(8).uppercase()}"

    private fun isUuid(value: String): Boolean = try {
        UUID.fromString(value)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    var nodeId: String
        get() {
            var id = prefs.getString(KEY_NODE_ID, null)
            if (id.isNullOrBlank()) {
                id = "NODE-${UUID.randomUUID().toString().take(4).uppercase()}"
                prefs.edit().putString(KEY_NODE_ID, id).apply()
            }
            return id
        }
        set(value) {
            prefs.edit().putString(KEY_NODE_ID, value).apply()
        }

    var callSign: String
        get() {
            var cs = prefs.getString(KEY_CALL_SIGN, null)
            if (cs.isNullOrBlank()) {
                cs = "@operator#${(1000..9999).random()}"
                prefs.edit().putString(KEY_CALL_SIGN, cs).apply()
            }
            return cs
        }
        set(value) {
            prefs.edit().putString(KEY_CALL_SIGN, value).apply()
        }

    var gatewayEndpoint: String
        get() = prefs.getString(KEY_GATEWAY_ENDPOINT, DEFAULT_GATEWAY_URL) ?: DEFAULT_GATEWAY_URL
        set(value) {
            prefs.edit().putString(KEY_GATEWAY_ENDPOINT, value).apply()
        }

    var publishableKey: String
        get() {
            val customKey = prefs.getString(KEY_PUBLISHABLE_KEY, null)
            if (!customKey.isNullOrBlank()) return customKey
            return com.aegis.mobile.BuildConfig.SUPABASE_PUBLISHABLE_KEY
        }
        set(value) {
            prefs.edit().putString(KEY_PUBLISHABLE_KEY, value).apply()
        }

    fun resetIdentity() {
        val newDevId = UUID.randomUUID().toString()
        val newVicId = UUID.randomUUID().toString()
        val newNodeId = "NODE-${UUID.randomUUID().toString().take(4).uppercase()}"
        val newCallSign = "@operator#${(1000..9999).random()}"
        
        prefs.edit()
            .putString(KEY_DEVICE_ID, newDevId)
            .putString(KEY_VICTIM_ID, newVicId)
            .putString(KEY_NODE_ID, newNodeId)
            .putString(KEY_CALL_SIGN, newCallSign)
            .apply()
    }
}
