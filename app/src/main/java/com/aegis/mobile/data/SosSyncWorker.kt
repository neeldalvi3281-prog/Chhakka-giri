package com.aegis.mobile.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SosSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.messageDao()

        val pendingMessages = dao.getPendingSosMessages()
        if (pendingMessages.isEmpty()) {
            return Result.success()
        }

        val client = OkHttpClient()
        val JSON = "application/json; charset=utf-8".toMediaType()

        var allSuccessful = true

        for (msg in pendingMessages) {
            try {
                // Formatting payload
                val jsonPayload = JSONObject().apply {
                    put("id", msg.id)
                    put("senderId", msg.senderId)
                    put("callSign", msg.senderHandle)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                }
                
                // Using a public test endpoint acting as the internet bridge/relay
                val request = Request.Builder()
                    .url("https://httpbin.org/post")
                    .post(jsonPayload.toString().toRequestBody(JSON))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    dao.markAsRelayed(msg.id)
                } else {
                    allSuccessful = false
                }
            } catch (e: Exception) {
                Log.e("SosSyncWorker", "Error syncing SOS message", e)
                allSuccessful = false
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }
}
