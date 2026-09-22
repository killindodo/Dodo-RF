package com.killindodo.dodo_rf.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.killindodo.dodo_rf.model.CapturedSignal
import com.killindodo.dodo_rf.model.GenericResponse
import com.killindodo.dodo_rf.model.StatusResponse
import com.killindodo.dodo_rf.model.StoredSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class DodoApiClient(
    var baseUrl: String = "http://192.168.4.1"
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    fun updateHost(ip: String) {
        val cleanIp = ip.trim().removePrefix("http://").removePrefix("https://").removeSuffix("/")
        baseUrl = "http://$cleanIp"
    }

    suspend fun getStatus(): Result<StatusResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/status")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                val bodyStr = response.body?.string() ?: ""
                val status = gson.fromJson(bodyStr, StatusResponse::class.java)
                Result.success(status)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setFrequency(mhz: Double): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf("freq" to mhz)
            val json = gson.toJson(payload)
            val body = json.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/api/set_freq")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSignals(): Result<List<StoredSignal>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/signals")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val bodyStr = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<StoredSignal>>() {}.type
                val signals: List<StoredSignal> = gson.fromJson(bodyStr, type) ?: emptyList()
                Result.success(signals)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSignal(name: String, code: String, freq: Double): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf(
                "name" to name,
                "code" to code,
                "freq" to freq
            )
            val json = gson.toJson(payload)
            val body = json.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/api/save")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val bodyStr = response.body?.string() ?: "{}"
                val res = gson.fromJson(bodyStr, GenericResponse::class.java)
                Result.success(res.id ?: -1)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun replaySignal(id: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf("id" to id)
            val json = gson.toJson(payload)
            val body = json.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/api/replay")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSignal(id: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf("id" to id)
            val json = gson.toJson(payload)
            val body = json.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/api/delete")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importSignals(signalsJson: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val body = signalsJson.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/api/import")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val bodyStr = response.body?.string() ?: "{}"
                val res = gson.fromJson(bodyStr, GenericResponse::class.java)
                Result.success(res.count ?: 0)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
