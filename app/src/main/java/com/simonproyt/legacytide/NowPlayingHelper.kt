package com.simonproyt.legacytide

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso

class NowPlayingHelper(private val activity: Activity, private val session: com.simonproyt.legacytide.api.Session) {

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.simonproyt.legacytide.PLAYBACK_STATE") {
                val trackId = intent.getLongExtra("TRACK_ID", -1L)
                val isPlaying = intent.getBooleanExtra("IS_PLAYING", false)
                val title = intent.getStringExtra("TRACK_TITLE")
                val artist = intent.getStringExtra("TRACK_ARTIST")
                val cover = intent.getStringExtra("TRACK_COVER")
                updateNowPlayingUI(trackId, title, artist, cover, isPlaying)
            }
        }
    }

    fun onResume() {
        activity.registerReceiver(playbackReceiver, IntentFilter("com.simonproyt.legacytide.PLAYBACK_STATE"))
        // Initial state sync
        updateNowPlayingUI(
            PlaybackService.currentTrackId,
            PlaybackService.currentTitle,
            PlaybackService.currentArtist,
            PlaybackService.currentCover,
            PlaybackService.isPlaying
        )
    }

    fun onPause() {
        activity.unregisterReceiver(playbackReceiver)
    }

    private fun updateNowPlayingUI(trackId: Long, title: String?, artist: String?, cover: String?, isPlaying: Boolean) {
        val layout = activity.findViewById<View>(R.id.layout_now_playing) ?: return
        if (trackId == -1L) {
            layout.visibility = View.GONE
            return
        }
        
        layout.visibility = View.VISIBLE
        activity.findViewById<TextView>(R.id.tv_now_playing_title).text = title ?: "Unknown Track"
        activity.findViewById<TextView>(R.id.tv_now_playing_artist).text = artist ?: "Unknown Artist"
        
        val playBtn = activity.findViewById<ImageView>(R.id.btn_play_pause)
        playBtn.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        playBtn.setOnClickListener {
            val intent = Intent(activity, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_TOGGLE_PLAYBACK
            }
            activity.startService(intent)
        }
        
        layout.setOnClickListener {
            val intent = Intent(activity, PlayerActivity::class.java).apply {
                putExtra("ACCESS_TOKEN", session.accessToken)
                putExtra("USER_ID", session.userId)
                putExtra("COUNTRY_CODE", session.countryCode)
            }
            activity.startActivity(intent)
        }
        
        val artView = activity.findViewById<ImageView>(R.id.img_now_playing_art)
        if (cover != null && cover.isNotEmpty()) {
            val imageUrl = "https://resources.tidal.com/images/${cover.replace('-', '/')}/320x320.jpg"
            Picasso.with(activity).load(imageUrl).into(artView)
        } else {
            artView.setImageDrawable(null)
        }
    }
}
