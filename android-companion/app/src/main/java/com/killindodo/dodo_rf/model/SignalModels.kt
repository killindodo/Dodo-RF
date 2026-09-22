package com.killindodo.dodo_rf.model

import com.google.gson.annotations.SerializedName

data class CapturedSignal(
    @SerializedName("code") val code: String,
    @SerializedName("frequency") val frequency: Double,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("bits") val bits: Int = 24,
    @SerializedName("protocol") val protocol: Int = 1,
    @SerializedName("pulse") val pulse: Int = 0
)

data class StoredSignal(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("code") val code: String,
    @SerializedName("freq") val freq: Double
)

data class StatusResponse(
    @SerializedName("state") val state: String = "Listening",
    @SerializedName("freq") val freq: Double = 433.92,
    @SerializedName("new_signals") val newSignals: List<CapturedSignal>? = null
)

data class GenericResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("id") val id: Int? = null,
    @SerializedName("count") val count: Int? = null
)
