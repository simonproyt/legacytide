package com.simonproyt.legacytide

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.simonproyt.legacytide.api.Session
import com.simonproyt.legacytide.api.TidalService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackService : Service() {
    private var player: ExoPlayer? = null
    private lateinit var session: Session
    private lateinit var tidalService: TidalService

    inner class LocalBinder : android.os.Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }
    private val binder = LocalBinder()

    fun getPlayer(): ExoPlayer? = player
    
    companion object {
        const val ACTION_PLAY = "com.simonproyt.legacytide.PLAY"
        const val ACTION_TOGGLE_PLAYBACK = "com.simonproyt.legacytide.TOGGLE_PLAYBACK"
        const val ACTION_NEXT = "com.simonproyt.legacytide.NEXT"
        const val ACTION_PREVIOUS = "com.simonproyt.legacytide.PREVIOUS"
        const val EXTRA_ACCESS_TOKEN = "ACCESS_TOKEN"
        const val EXTRA_USER_ID = "USER_ID"
        const val EXTRA_COUNTRY_CODE = "COUNTRY_CODE"
        
        var currentTrackId: Long = -1L
        var currentTitle: String? = null
        var currentArtist: String? = null
        var currentCover: String? = null
        var isPlaying: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        val loadControl = com.google.android.exoplayer2.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                32000, // min buffer 32s
                64000, // max buffer 64s
                2500, // min buffer for playback 2.5s
                5000 // min buffer for playback after rebuffer 5s
            ).build()
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
        player?.addListener(object : com.google.android.exoplayer2.Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = playWhenReady
                broadcastState()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == com.google.android.exoplayer2.Player.STATE_ENDED) {
                    playNextTrack()
                }
            }
        })
    }

    private fun broadcastState() {
        val intent = Intent("com.simonproyt.legacytide.PLAYBACK_STATE")
        intent.putExtra("TRACK_ID", currentTrackId)
        intent.putExtra("TRACK_TITLE", currentTitle)
        intent.putExtra("TRACK_ARTIST", currentArtist)
        intent.putExtra("TRACK_COVER", currentCover)
        intent.putExtra("IS_PLAYING", isPlaying)
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PLAY) {
            val accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN)
            val userId = intent.getLongExtra(EXTRA_USER_ID, -1)
            val countryCode = intent.getStringExtra(EXTRA_COUNTRY_CODE)
            
            if (accessToken != null) {
                session = Session().apply {
                    this.accessToken = accessToken
                    this.userId = userId
                    this.countryCode = countryCode
                }
                tidalService = TidalService(session)
                
                PlaybackQueue.getCurrentTrack()?.let { track ->
                    currentTrackId = track.id
                    currentTitle = track.title
                    currentArtist = track.artist?.name
                    currentCover = track.album?.cover
                    playTrack(track.id)
                }
            }
        } else if (intent?.action == ACTION_TOGGLE_PLAYBACK) {
            val playing = player?.playWhenReady ?: false
            player?.playWhenReady = !playing
        } else if (intent?.action == ACTION_NEXT) {
            playNextTrack()
        } else if (intent?.action == ACTION_PREVIOUS) {
            playPreviousTrack()
        }
        return START_NOT_STICKY
    }

    private fun playNextTrack() {
        PlaybackQueue.next()?.let { track ->
            currentTrackId = track.id
            currentTitle = track.title
            currentArtist = track.artist?.name
            currentCover = track.album?.cover
            playTrack(track.id)
        }
    }

    private fun playPreviousTrack() {
        PlaybackQueue.previous()?.let { track ->
            currentTrackId = track.id
            currentTitle = track.title
            currentArtist = track.artist?.name
            currentCover = track.album?.cover
            playTrack(track.id)
        }
    }

    private fun playTrack(trackId: Long) {
        broadcastState()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val playbackInfo = tidalService.getStreamManifest(trackId)
                withContext(Dispatchers.Main) {
                    if (playbackInfo != null) {
                        if (playbackInfo.manifestMimeType == "application/vnd.tidal.bts") {
                            val decodedBytes = android.util.Base64.decode(playbackInfo.manifest, android.util.Base64.DEFAULT)
                            val decodedJson = String(decodedBytes, Charsets.UTF_8)
                            val jsonObject = org.json.JSONObject(decodedJson)
                            val urls = jsonObject.getJSONArray("urls")
                            val url = urls.getString(0)
                            
                            val okHttpFactory = com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource.Factory(session.client)
                            val dataSourceFactory = DefaultDataSource.Factory(this@PlaybackService, okHttpFactory)
                            val mediaSource = com.google.android.exoplayer2.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(MediaItem.fromUri(url))
                            
                            player?.setMediaSource(mediaSource)
                        } else {
                            val uri = Uri.parse("data:application/dash+xml;base64,${playbackInfo.manifest}")
                            val okHttpFactory = com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource.Factory(session.client)
                            val dataSourceFactory = DefaultDataSource.Factory(this@PlaybackService, okHttpFactory)
                            
                            val mediaSource = DashMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(MediaItem.fromUri(uri))

                            player?.setMediaSource(mediaSource)
                        }
                        
                        player?.prepare()
                        player?.playWhenReady = true
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error playing track", e)
            }
        }
    }


    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
