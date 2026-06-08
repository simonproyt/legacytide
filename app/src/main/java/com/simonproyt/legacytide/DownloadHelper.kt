package com.simonproyt.legacytide

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simonproyt.legacytide.api.Session
import com.simonproyt.legacytide.api.TidalService
import com.simonproyt.legacytide.api.models.Album
import com.simonproyt.legacytide.api.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadHelper(private val context: Context, private val session: Session) {

    private val tidalService = TidalService(session)
    private val gson = Gson()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("DOWNLOADS", "Downloads", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private val downloadsDir: File
        get() = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir

    private val metadataFile: File
        get() = File(downloadsDir, "downloads_metadata.json")

    private val albumsFile: File
        get() = File(downloadsDir, "offline_albums.json")

    fun getDownloadedAlbums(): List<Album> {
        if (!albumsFile.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<Album>>() {}.type
            gson.fromJson(albumsFile.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAlbumsPublic(albums: List<Album>) {
        albumsFile.writeText(gson.toJson(albums))
    }

    fun getDownloadedTracks(): List<Track> {
        if (!metadataFile.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<Track>>() {}.type
            gson.fromJson(metadataFile.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveMetadata(tracks: List<Track>) {
        metadataFile.writeText(gson.toJson(tracks))
    }

    fun isDownloaded(trackId: Long): Boolean {
        val tracks = getDownloadedTracks()
        if (tracks.none { it.id == trackId }) return false
        val fileMp4 = File(downloadsDir, "$trackId.mp4")
        val fileFlac = File(downloadsDir, "$trackId.flac")
        return fileMp4.exists() || fileFlac.exists()
    }

    fun getLocalFileUri(trackId: Long): String? {
        val fileMp4 = File(downloadsDir, "$trackId.mp4")
        if (fileMp4.exists()) return fileMp4.toURI().toString()
        val fileFlac = File(downloadsDir, "$trackId.flac")
        if (fileFlac.exists()) return fileFlac.toURI().toString()
        return null
    }

    fun deleteTrack(trackId: Long) {
        val tracks = getDownloadedTracks().toMutableList()
        val track = tracks.find { it.id == trackId }
        
        if (track != null) {
            tracks.remove(track)
            saveMetadata(tracks)
            
            // Delete audio file
            File(downloadsDir, "$trackId.mp4").delete()
            File(downloadsDir, "$trackId.flac").delete()
            
            // Note: We don't delete cover art here because other tracks in the same album might still need it.
            // A more complex cleanup routine could be added later to remove orphaned covers.
        }
    }

    fun deleteAlbum(albumId: Long) {
        val albums = getDownloadedAlbums().toMutableList()
        val album = albums.find { it.id == albumId }
        
        if (album != null) {
            albums.remove(album)
            saveAlbumsPublic(albums)
            
            // Delete all tracks belonging to this album
            val tracks = getDownloadedTracks()
            tracks.filter { it.album?.id == albumId }.forEach {
                deleteTrack(it.id)
            }
            
            // Delete cover art
            File(downloadsDir, "${albumId}_160.jpg").delete()
            File(downloadsDir, "${albumId}_320.jpg").delete()
        }
    }

    fun downloadTrack(track: Track) {
        val intent = android.content.Intent(context, DownloadService::class.java).apply {
            putExtra("DOWNLOAD_TYPE", "TRACK")
            putExtra("TRACK_ID", track.id)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun downloadAlbum(albumId: Long) {
        val intent = android.content.Intent(context, DownloadService::class.java).apply {
            putExtra("DOWNLOAD_TYPE", "ALBUM")
            putExtra("ALBUM_ID", albumId)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun downloadPlaylist(playlistId: String) {
        val intent = android.content.Intent(context, DownloadService::class.java).apply {
            putExtra("DOWNLOAD_TYPE", "PLAYLIST")
            putExtra("PLAYLIST_ID", playlistId)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    suspend fun downloadTrackSyncPublic(track: Track) {
        try {
            if (isDownloaded(track.id)) return

            val quality = context.getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE)
                .getString("download_quality", "LOSSLESS") ?: "LOSSLESS"

            val playbackInfo = tidalService.getStreamManifest(track.id, quality) ?: return
            if (playbackInfo.manifestMimeType != "application/vnd.tidal.bts") {
                Log.e("DownloadHelper", "Unsupported manifest type: ${playbackInfo.manifestMimeType}")
                return
            }

            val decodedBytes = android.util.Base64.decode(playbackInfo.manifest, android.util.Base64.DEFAULT)
            val decodedJson = String(decodedBytes, Charsets.UTF_8)
            val jsonObject = org.json.JSONObject(decodedJson)
            val urls = jsonObject.getJSONArray("urls")
            val url = urls.getString(0)

            val fileExtension = if (quality == "LOSSLESS") "flac" else "mp4"
            val destFile = File(downloadsDir, "${track.id}.$fileExtension")

            val request = Request.Builder().url(url).build()
            session.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body() ?: return
                FileOutputStream(destFile).use { out ->
                    body.byteStream().use { input ->
                        input.copyTo(out)
                    }
                }
            }

            val tracks = getDownloadedTracks().toMutableList()
            if (tracks.none { it.id == track.id }) {
                tracks.add(track)
                saveMetadata(tracks)
            }
            
            // Broadcast update
            val intent = android.content.Intent("com.simonproyt.legacytide.DOWNLOAD_COMPLETE")
            intent.putExtra("TRACK_ID", track.id)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e("DownloadHelper", "Failed to download track sync", e)
        }
    }
}
