package com.poldivers.app.data.hd2.official

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Helldivers Training Manual's public war history: a snapshot of every planet's health every
 * ~5 minutes. The game API only has the current state, so this is what lets the app show a
 * liberation pace right away instead of after minutes of its own sampling.
 */
interface HistoryApi {
    @GET("api/v1/war/history/{index}")
    suspend fun planetHistory(@Path("index") index: Int): List<HistorySnapshot>
}

@Serializable
data class HistorySnapshot(
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("planet_index") val planetIndex: Int = 0,
    @SerialName("current_health") val currentHealth: Double = 0.0,
    @SerialName("max_health") val maxHealth: Double = 0.0,
    @SerialName("player_count") val playerCount: Long = 0,
)
