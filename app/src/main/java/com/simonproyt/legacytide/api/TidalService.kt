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
    
    fun getMixes(): List<Playlist> {
        val url = "${session.config.apiV1Location}pages/for_you?countryCode=${session.countryCode ?: "US"}&deviceType=PHONE"
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.accessToken}")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Mixes error ${response.code()}")
            val responseBody = response.body()?.string() ?: throw IOException("Empty body")
            val mixes = mutableListOf<Playlist>()
            try {
                val json = org.json.JSONObject(responseBody)
                val rows = json.optJSONArray("rows")
                for (i in 0 until (rows?.length() ?: 0)) {
                    val modules = rows?.getJSONObject(i)?.optJSONArray("modules")
                    for (j in 0 until (modules?.length() ?: 0)) {
                        val module = modules?.getJSONObject(j)
                        val moduleType = module?.optString("type")
                        val pagedList = module?.optJSONObject("pagedList")
                        val items = pagedList?.optJSONArray("items")
                        
                        if (items != null) {
                            if (moduleType == "MIX_LIST") {
                                val moduleTitle = module?.optString("title", "Mixes") ?: "Mixes"
                                mixes.add(Playlist(uuid = "HEADER_$j", title = moduleTitle, description = null, numberOfTracks = 0, duration = 0, image = null, squareImage = null, isHeader = true))
                                
                                for (k in 0 until items.length()) {
                                    val item = items.getJSONObject(k)
                                    val id = item.optString("id")
                                    val title = item.optString("title")
                                    val descStr = item.optString("description")
                                    val subTitleStr = item.optString("subTitle")
                                    val desc = if (descStr != "null" && descStr.isNotEmpty()) descStr else if (subTitleStr != "null") subTitleStr else ""
                                    
                                    val images = item.optJSONObject("images")
                                    val largeImage = images?.optJSONObject("LARGE")?.optString("url") 
                                        ?: images?.optJSONObject("MEDIUM")?.optString("url")
                                        ?: images?.optJSONObject("SMALL")?.optString("url")
                                        ?: item.optString("image", null)?.takeIf { it != "null" }
                                        ?: item.optString("squareImage", null)?.takeIf { it != "null" }
                                    
                                    mixes.add(Playlist(id, title, desc, 0, 0, largeImage, largeImage))
                                }
                            } else {
                                for (k in 0 until items.length()) {
                                    val item = items.getJSONObject(k)
                                    if (item.optString("type") == "PLAYLIST") {
                                        val playlistJson = item.optJSONObject("playlist")
                                        playlistJson?.let {
                                            val playlist = gson.fromJson(it.toString(), Playlist::class.java)
                                            mixes.add(playlist)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            return mixes
        }
    }

    private data class TrackItemWrapper(
        val item: Track
    )

    suspend fun getPlaylistTracks(playlistId: String): List<Track> {
        var url = "${session.config.apiV1Location}playlists/$playlistId/items?countryCode=${session.countryCode ?: "US"}&limit=100"
        var request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()

        return withContext(Dispatchers.IO) {
            var response = client.newCall(request).execute()
            if (response.code() == 404) {
                // It might be a mix instead of a playlist
                url = "${session.config.apiV1Location}mixes/$playlistId/items?countryCode=${session.countryCode ?: "US"}&limit=100"
                request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer ${session.accessToken}")
                    .build()
                response = client.newCall(request).execute()
            }
            
            if (!response.isSuccessful) throw IOException("Unexpected code ${response.code()}")
            
            val responseBody = response.body()?.string() ?: throw IOException("Empty response body")
            
            val type = object : com.google.gson.reflect.TypeToken<TidalListResponse<TrackItemWrapper>>() {}.type
            val result: TidalListResponse<TrackItemWrapper> = gson.fromJson(responseBody, type)
            
            return@withContext result.items.mapNotNull { it.item }
        }
    }
    
    private data class SearchResponse(
        val tracks: TidalListResponse<Track>?
    )

    fun searchTracks(query: String): List<Track> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "${session.config.apiV1Location}search?query=$encodedQuery&types=TRACKS&limit=30&countryCode=${session.countryCode ?: "US"}"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.accessToken}")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code ${response.code()}")
            
            val responseBody = response.body()?.string() ?: throw IOException("Empty response body")
            val type = object : TypeToken<SearchResponse>() {}.type
            val result: SearchResponse = gson.fromJson(responseBody, type)
            return result.tracks?.items ?: emptyList()
        }
    }

    suspend fun getStreamManifest(trackId: Long, quality: String = "HIGH"): PlaybackInfo? {
        val url = "${session.config.apiV1Location}tracks/$trackId/playbackinfopostpaywall?countryCode=${session.countryCode ?: "US"}&audioquality=$quality&playbackmode=STREAM&assetpresentation=FULL"
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
    
    fun getTrack(trackId: Long): Track {
        val url = "${session.config.apiV1Location}tracks/$trackId?countryCode=${session.countryCode ?: "US"}"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.accessToken}")
            .get()
            .build()
            
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code ${response.code()}")
            val responseBody = response.body()?.string() ?: throw IOException("Empty response body")
            return gson.fromJson(responseBody, Track::class.java)
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
