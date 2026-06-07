package com.simonproyt.legacytide.api

import com.simonproyt.legacytide.api.models.Playlist
import com.simonproyt.legacytide.api.models.Track
import com.simonproyt.legacytide.api.models.PlaybackInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

class TidalService(private val session: Session) {
    private val client = session.client
    private val gson = Gson()

    // Example response for playlists is wrapped in an object containing "items"
    private data class TidalListResponse<T>(
        val limit: Int,
        val offset: Int,
        val totalNumberOfItems: Int,
        val items: List<T>
    )

    fun getUserPlaylists(): List<Playlist> {
        val userId = session.userId ?: throw IllegalStateException("Not logged in")
        val url = "${session.config.apiV1Location}users/$userId/playlists?countryCode=${session.countryCode ?: "US"}"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.accessToken}")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code ${response.code()}")
            
            val responseBody = response.body()?.string() ?: throw IOException("Empty response body")
            val type = object : TypeToken<TidalListResponse<Playlist>>() {}.type
            val result: TidalListResponse<Playlist> = gson.fromJson(responseBody, type)
            return result.items
        }
    }

    private data class TrackItemWrapper(
        val item: Track
    )

    fun getPlaylistTracks(playlistId: String): List<Track> {
        val url = "${session.config.apiV1Location}playlists/$playlistId/items?countryCode=${session.countryCode ?: "US"}"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.accessToken}")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code ${response.code()}")
            
            val responseBody = response.body()?.string() ?: throw IOException("Empty response body")
            
            val type = object : TypeToken<TidalListResponse<TrackItemWrapper>>() {}.type
            val result: TidalListResponse<TrackItemWrapper> = gson.fromJson(responseBody, type)
            
            return result.items.mapNotNull { it.item }
        }
    }

    suspend fun getStreamManifest(trackId: Long): PlaybackInfo? {
        val url = "${session.config.apiV1Location}tracks/$trackId/playbackinfopostpaywall?countryCode=${session.countryCode ?: "US"}&audioquality=HIGH&playbackmode=STREAM&assetpresentation=FULL"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body()?.string()
                    responseBody?.let {
                        return@withContext gson.fromJson(it, PlaybackInfo::class.java)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
    }

    suspend fun getLyrics(trackId: Long): String? {
        val url = "${session.config.apiV1Location}tracks/$trackId/lyrics?countryCode=${session.countryCode ?: "US"}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body()?.string()
                    responseBody?.let {
                        try {
                            val jsonObject = org.json.JSONObject(it)
                            if (jsonObject.has("subtitles")) {
                                return@withContext jsonObject.getString("subtitles")
                            } else if (jsonObject.has("lyrics")) {
                                return@withContext jsonObject.getString("lyrics")
                            } else {
                                return@withContext "Raw response: $it"
                            }
                        } catch (e: Exception) {
                            return@withContext "Parse error: $it"
                        }
                    }
                } else {
                    return@withContext "Error ${response.code()}: ${response.body()?.string()}"
                }
            } catch (e: Exception) {
                return@withContext "Exception: ${e.message}"
            }
            null
        }
    }
}
