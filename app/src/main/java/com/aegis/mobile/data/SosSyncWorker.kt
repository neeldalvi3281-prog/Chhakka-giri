package com.aegis.mobile.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SosSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SosSyncWorker"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.messageDao()
        val identityManager = IdentityManager(applicationContext)

        val pendingMessages = dao.getPendingSosMessages()
        if (pendingMessages.isEmpty()) {
            Log.d(TAG, "No pending SOS messages to upload.")
            return Result.success()
        }

        Log.d(TAG, "Found ${pendingMessages.size} pending SOS messages to sync via Gateway...")

        val gatewayDeviceId = identityManager.deviceId
        val endpointUrl = identityManager.gatewayEndpoint

        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        // Process in batches of 50
        val batches = pendingMessages.chunked(50)
        var allBatchesSucceeded = true

        val JSON = "application/json; charset=utf-8".toMediaType()

        for (batch in batches) {
            val batchIds = batch.map { it.id }
            try {
                val messagesArray = JSONArray()
                for (msg in batch) {
                    val msgObj = JSONObject().apply {
                        put("message_id", msg.id)
                        put("victim_id", msg.victimId ?: identityManager.victimId)
                        put("origin_device_id", msg.originDeviceId ?: gatewayDeviceId)
                        put("type", "SOS")
                        if (msg.latitude != null) put("latitude", msg.latitude) else put("latitude", JSONObject.NULL)
                        if (msg.longitude != null) put("longitude", msg.longitude) else put("longitude", JSONObject.NULL)
                        put("text", msg.text)
                        put("ttl", msg.ttl)
                        put("hop_count", msg.hopCount)
                        put("created_at", isoFormatter.format(Date(msg.timestamp)))
                    }
                    messagesArray.put(msgObj)
                }

                val payload = JSONObject().apply {
                    put("gateway_device_id", gatewayDeviceId)
                    put("messages", messagesArray)
                }

                val requestBody = payload.toString().toRequestBody(JSON)
                val requestBuilder = Request.Builder()
                    .url(endpointUrl)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .header("X-Aegis-Gateway", gatewayDeviceId)

                val publishableKey = identityManager.publishableKey
                if (publishableKey.isNotBlank()) {
                    requestBuilder.header("apikey", publishableKey)
                    requestBuilder.header("Authorization", "Bearer $publishableKey")
                }

                val request = requestBuilder.build()

                Log.d(TAG, "Uploading batch of ${batch.size} messages to $endpointUrl (Gateway: $gatewayDeviceId)")
                val response = okHttpClient.newCall(request).execute()

                response.use { res ->
                    val responseBody = res.body?.string().orEmpty()
                    Log.d(TAG, "Gateway response (${res.code}): $responseBody")

                    if (res.isSuccessful) {
                        var isSuccess = true
                        var failedIds = emptySet<String>()

                        if (responseBody.isNotBlank()) {
                            try {
                                val jsonRes = JSONObject(responseBody)
                                isSuccess = jsonRes.optBoolean("success", true)
                                
                                val acceptedCount = jsonRes.optInt("accepted", -1)
                                val duplicatesCount = jsonRes.optInt("duplicates", -1)
                                val failedCount = jsonRes.optInt("failed", 0)

                                Log.d(TAG, "AegisSync: Received=${jsonRes.optInt("received", batch.size)}, Accepted=$acceptedCount, Duplicates=$duplicatesCount, Failed=$failedCount")

                                if (jsonRes.has("failed_ids")) {
                                    val fArray = jsonRes.getJSONArray("failed_ids")
                                    val fSet = mutableSetOf<String>()
                                    for (i in 0 until fArray.length()) {
                                        fSet.add(fArray.getString(i))
                                    }
                                    failedIds = fSet
                                } else if (failedCount > 0 && acceptedCount == 0 && duplicatesCount == 0) {
                                    isSuccess = false
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Non-JSON or raw response from gateway: ${e.message}")
                            }
                        }

                        if (isSuccess) {
                            val successfulIds = batchIds.filter { it !in failedIds }
                            if (successfulIds.isNotEmpty()) {
                                dao.markAsUploaded(successfulIds)
                                Log.d(TAG, "AegisSync: Marked ${successfulIds.size} SOS messages as UPLOADED in Room DB.")
                            }
                            if (failedIds.isNotEmpty()) {
                                Log.w(TAG, "AegisSync: ${failedIds.size} messages failed backend acceptance, will retry.")
                                allBatchesSucceeded = false
                            }
                        } else {
                            Log.w(TAG, "AegisSync: Gateway batch response indicated failure: $responseBody")
                            allBatchesSucceeded = false
                        }
                    } else if (res.code in 400..499) {
                        Log.e(TAG, "AegisSync: Client error ${res.code} from Gateway: $responseBody")
                        // For 4xx errors, we do not retry blindly
                        allBatchesSucceeded = false
                    } else {
                        Log.e(TAG, "AegisSync: Server error ${res.code} from Gateway: $responseBody")
                        allBatchesSucceeded = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during gateway SOS upload batch: ${e.message}", e)
                allBatchesSucceeded = false
            }
        }

        return if (allBatchesSucceeded) Result.success() else Result.retry()
    }
}
