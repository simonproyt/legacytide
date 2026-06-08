package com.simonproyt.legacytide

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.simonproyt.legacytide.api.Session
import com.simonproyt.legacytide.api.TidalService
import com.simonproyt.legacytide.api.models.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val downloadMutex = kotlinx.coroutines.sync.Mutex()
    private var isQueueEmpty = true
    private lateinit var downloadHelper: DownloadHelper
    private lateinit var tidalService: TidalService
    private lateinit var notificationManager: NotificationManager
    private val channelId = "DOWNLOADS_FOREGROUND"

    override fun onCreate() {
        super.onCreate()
        val session = Session(applicationContext).apply {
            val prefs = getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE)
            accessToken = prefs.getString("ACCESS_TOKEN", null)
            userId = prefs.getLong("USER_ID", -1)
            countryCode = prefs.getString("COUNTRY_CODE", "US") ?: "US"
        }
        downloadHelper = DownloadHelper(this, session)
        tidalService = TidalService(session)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Downloads (Foreground)", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val type = it.getStringExtra("DOWNLOAD_TYPE") ?: return@let
            
            // Show initial queued notification if starting fresh
            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("LegacyTide Downloads")
                .setContentText("Added to download queue...")
                .setOngoing(true)
            
            startForeground(1, builder.build())

            serviceScope.launch {
                downloadMutex.withLock {
                    try {
                        when (type) {
                        "TRACK" -> {
                            val trackId = it.getLongExtra("TRACK_ID", -1)
                            if (trackId != -1L) {
                                val track = tidalService.getTrack(trackId)
                                updateNotification(builder, "Downloading ${track.title}", 0, 0)
                                downloadHelper.downloadTrackSyncPublic(track)
                            }
                        }
                        "ALBUM" -> {
                            val albumId = it.getLongExtra("ALBUM_ID", -1)
                            if (albumId != -1L) {
                                val tracks = tidalService.getAlbumTracks(albumId)
                                val total = tracks.size
                                tracks.forEachIndexed { index, track ->
                                    updateNotification(builder, "Downloading ${track.title}", total, index)
                                    downloadHelper.downloadTrackSyncPublic(track)
                                }
                                val album = tidalService.getAlbum(albumId)
                                val downloadedAlbums = downloadHelper.getDownloadedAlbums().toMutableList()
                                if (downloadedAlbums.none { a -> a.id == albumId }) {
                                    downloadedAlbums.add(album)
                                    downloadHelper.saveAlbumsPublic(downloadedAlbums)
                                }
                            }
                        }
                        "PLAYLIST" -> {
                            val playlistId = it.getStringExtra("PLAYLIST_ID")
                            if (playlistId != null) {
                                val tracks = tidalService.getPlaylistTracks(playlistId)
                                val total = tracks.size
                                tracks.forEachIndexed { index, track ->
                                    updateNotification(builder, "Downloading ${track.title}", total, index)
                                    downloadHelper.downloadTrackSyncPublic(track)
                                }
                            }
                        }
                    }
                    Unit
                } catch (e: Exception) {
                    Log.e("DownloadService", "Error during download", e)
                    Unit
                } finally {
                    // Cleanup
                }
                }
                
                if (!downloadMutex.isLocked) {
                    updateNotification(builder, "Downloads complete", 0, 0)
                    stopForeground(false)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun updateNotification(builder: NotificationCompat.Builder, titleText: String, max: Int, progress: Int) {
        if (max > 0) {
            builder.setContentText("$titleText (${progress + 1}/$max)")
            builder.setProgress(max, progress, false)
        } else {
            builder.setContentText(titleText)
            builder.setProgress(0, 0, false)
        }
        notificationManager.notify(1, builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
