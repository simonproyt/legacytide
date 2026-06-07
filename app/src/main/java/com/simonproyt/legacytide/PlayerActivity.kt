package com.simonproyt.legacytide

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.simonproyt.legacytide.api.Session
import com.simonproyt.legacytide.api.TidalService
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {

    private var playbackService: PlaybackService? = null
    private var isBound = false
    private lateinit var tidalService: TidalService
    private var currentTrackId: Long = -1L

    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var imgArt: ImageView
    private lateinit var tvPosition: TextView
    private lateinit var tvDuration: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: ImageView
    private lateinit var btnPrev: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var btnLyricsToggle: ImageView
    private lateinit var btnQuality: TextView
    private lateinit var recyclerLyrics: androidx.recyclerview.widget.RecyclerView
    private lateinit var lyricsAdapter: LyricsAdapter
    private var currentLyricsList: List<com.simonproyt.legacytide.api.models.TimedLyric> = emptyList()
    private var isLyricsVisible = false
    private var maxTrackQuality: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressTask = object : Runnable {
        override fun run() {
            updateProgress()
            updateLyricsSync()
            handler.postDelayed(this, 100)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as PlaybackService.LocalBinder
            playbackService = binder.getService()
            isBound = true
            handler.post(updateProgressTask)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.simonproyt.legacytide.PLAYBACK_STATE") {
                val isPlaying = intent.getBooleanExtra("IS_PLAYING", false)
                val title = intent.getStringExtra("TRACK_TITLE")
                val artist = intent.getStringExtra("TRACK_ARTIST")
                val cover = intent.getStringExtra("TRACK_COVER")
                val trackQuality = intent.getStringExtra("TRACK_QUALITY")
                
                maxTrackQuality = trackQuality
                updateUI(title, artist, cover, isPlaying)
                
                val trackId = intent.getLongExtra("TRACK_ID", -1L)
                if (trackId != -1L && trackId != currentTrackId) {
                    currentTrackId = trackId
                    fetchLyrics(trackId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val session = Session().apply {
            accessToken = intent.getStringExtra("ACCESS_TOKEN") ?: ""
            userId = intent.getLongExtra("USER_ID", -1L)
            countryCode = intent.getStringExtra("COUNTRY_CODE") ?: "US"
        }
        tidalService = TidalService(session)

        tvTitle = findViewById(R.id.tv_player_title)
        tvArtist = findViewById(R.id.tv_player_artist)
        imgArt = findViewById(R.id.img_player_art)
        tvPosition = findViewById(R.id.tv_position)
        tvDuration = findViewById(R.id.tv_duration)
        seekBar = findViewById(R.id.seekbar)
        btnPlay = findViewById(R.id.btn_play)
        btnPrev = findViewById(R.id.btn_prev)
        btnNext = findViewById(R.id.btn_next)
        btnLyricsToggle = findViewById(R.id.btn_lyrics_toggle)
        btnQuality = findViewById(R.id.btn_quality)
        
        // Setup initial quality text
        val prefs = getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE)
        val currentQuality = prefs.getString("audio_quality", "HIGH") ?: "HIGH"
        btnQuality.text = currentQuality
        
        recyclerLyrics = findViewById(R.id.recycler_lyrics)
        recyclerLyrics.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        lyricsAdapter = LyricsAdapter()
        recyclerLyrics.adapter = lyricsAdapter

        btnLyricsToggle.setOnClickListener {
            isLyricsVisible = !isLyricsVisible
            recyclerLyrics.visibility = if (isLyricsVisible) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        btnQuality.setOnClickListener {
            showQualityDialog()
        }

        btnPlay.setOnClickListener {
            startService(Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_TOGGLE_PLAYBACK
            })
        }
        btnPrev.setOnClickListener {
            startService(Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PREVIOUS
            })
        }
        btnNext.setOnClickListener {
            startService(Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_NEXT
            })
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playbackService?.getPlayer()?.seekTo(progress.toLong())
                    tvPosition.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, PlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
        registerReceiver(playbackReceiver, IntentFilter("com.simonproyt.legacytide.PLAYBACK_STATE"))
        
        // Initial state
        updateUI(
            PlaybackService.currentTitle,
            PlaybackService.currentArtist,
            PlaybackService.currentCover,
            PlaybackService.isPlaying
        )
        if (PlaybackService.currentTrackId != -1L) {
            currentTrackId = PlaybackService.currentTrackId
            fetchLyrics(currentTrackId)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        unregisterReceiver(playbackReceiver)
        handler.removeCallbacks(updateProgressTask)
    }

    private fun updateUI(title: String?, artist: String?, cover: String?, isPlaying: Boolean) {
        tvTitle.text = title ?: "Unknown Track"
        tvArtist.text = artist ?: "Unknown Artist"
        
        btnPlay.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        
        if (cover != null && cover.isNotEmpty()) {
            val imageUrl = "https://resources.tidal.com/images/${cover.replace('-', '/')}/320x320.jpg"
            Picasso.with(this).load(imageUrl).placeholder(android.R.color.transparent).into(imgArt)
        } else {
            imgArt.setImageDrawable(null)
        }
    }

    private fun updateProgress() {
        playbackService?.getPlayer()?.let { player ->
            val position = player.currentPosition
            val duration = player.duration

            if (duration > 0) {
                seekBar.max = duration.toInt()
                seekBar.progress = position.toInt()
                tvDuration.text = formatTime(duration)
            }
            tvPosition.text = formatTime(position)
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun parseLyrics(raw: String): List<com.simonproyt.legacytide.api.models.TimedLyric> {
        // Try parsing as JSON array first (some APIs return subtitles as JSON)
        try {
            val jsonArray = org.json.JSONArray(raw)
            val list = mutableListOf<com.simonproyt.legacytide.api.models.TimedLyric>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val text = obj.optString("text", obj.optString("line", ""))
                val timeStr = obj.optString("timestamp", "")
                var timeMs = 0L
                if (timeStr.isNotEmpty()) {
                    if (timeStr.contains(":")) {
                        val parts = timeStr.split(":")
                        if (parts.size == 2) {
                            val min = parts[0].toLongOrNull() ?: 0L
                            val secParts = parts[1].split(".")
                            val sec = secParts[0].toLongOrNull() ?: 0L
                            val ms = if (secParts.size > 1) {
                                val m = secParts[1]
                                if (m.length == 1) m.toLongOrNull()?.times(100) ?: 0L
                                else if (m.length == 2) m.toLongOrNull()?.times(10) ?: 0L
                                else m.toLongOrNull() ?: 0L
                            } else 0L
                            timeMs = min * 60000 + sec * 1000 + ms
                        }
                    } else {
                        timeMs = timeStr.toLongOrNull() ?: 0L
                    }
                }
                list.add(com.simonproyt.legacytide.api.models.TimedLyric(timeMs, text))
            }
            if (list.isNotEmpty()) return list
        } catch (e: Exception) {
            // Ignore and fall through to LRC parser
        }

        // Try standard LRC format parsing
        val regex = Regex("\\[(\\d{1,}):(\\d{2})(?:\\.(\\d{1,3}))?\\](.*)")
        val timedLyrics = mutableListOf<com.simonproyt.legacytide.api.models.TimedLyric>()
        for (line in raw.lines()) {
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val msStr = match.groupValues[3]
                val ms = if (msStr.isEmpty()) 0L else if (msStr.length == 1) msStr.toLong() * 100 else if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                val timeMs = min * 60000 + sec * 1000 + ms
                val text = match.groupValues[4].trim()
                timedLyrics.add(com.simonproyt.legacytide.api.models.TimedLyric(timeMs, text))
            }
        }
        if (timedLyrics.isNotEmpty()) return timedLyrics
        
        // Fallback to plain text lyrics
        return raw.lines().map { com.simonproyt.legacytide.api.models.TimedLyric(-1L, it) }
    }

    private fun updateLyricsSync() {
        if (currentLyricsList.isEmpty() || currentLyricsList.first().timestampMs == -1L) return
        val currentPosition = playbackService?.getPlayer()?.currentPosition ?: return
        
        var newIndex = -1
        for (i in currentLyricsList.indices) {
            if (currentPosition >= currentLyricsList[i].timestampMs) {
                newIndex = i
            } else {
                break
            }
        }
        
        if (newIndex != -1) {
            lyricsAdapter.setCurrentIndex(newIndex)
            // Offset by height/2 minus a bit of space so it doesn't push the text too far down
            val offset = (recyclerLyrics.height / 2) - 100 
            (recyclerLyrics.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.scrollToPositionWithOffset(newIndex, offset)
        }
    }

    private fun fetchLyrics(trackId: Long) {
        currentLyricsList = listOf(com.simonproyt.legacytide.api.models.TimedLyric(-1L, "Loading lyrics..."))
        lyricsAdapter.submitList(currentLyricsList)
        btnLyricsToggle.visibility = android.view.View.VISIBLE
        recyclerLyrics.visibility = if (isLyricsVisible) android.view.View.VISIBLE else android.view.View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val rawLyrics = tidalService.getLyrics(trackId)
            withContext(Dispatchers.Main) {
                if (rawLyrics == null || rawLyrics.startsWith("Error ") || rawLyrics.startsWith("Exception: ") || rawLyrics.startsWith("Parse error: ")) {
                    currentLyricsList = emptyList()
                    lyricsAdapter.submitList(currentLyricsList)
                    btnLyricsToggle.visibility = android.view.View.GONE
                    recyclerLyrics.visibility = android.view.View.GONE
                } else {
                    currentLyricsList = parseLyrics(rawLyrics)
                    lyricsAdapter.submitList(currentLyricsList)
                    btnLyricsToggle.visibility = android.view.View.VISIBLE
                    recyclerLyrics.visibility = if (isLyricsVisible) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
        }
    }

    private fun showQualityDialog() {
        var qualities = arrayOf("LOW", "HIGH", "LOSSLESS", "HI_RES")
        
        // Filter based on max available quality
        maxTrackQuality?.let { maxQ ->
            val maxIndex = qualities.indexOf(maxQ)
            if (maxIndex != -1) {
                qualities = qualities.sliceArray(0..maxIndex)
            }
        }

        val prefs = getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE)
        val currentQuality = prefs.getString("audio_quality", "HIGH") ?: "HIGH"
        val checkedItem = qualities.indexOf(currentQuality).takeIf { it >= 0 } ?: 1

        android.app.AlertDialog.Builder(this)
            .setTitle("Select Audio Quality")
            .setSingleChoiceItems(qualities, checkedItem) { dialog, which ->
                val selectedQuality = qualities[which]
                prefs.edit().putString("audio_quality", selectedQuality).apply()
                btnQuality.text = selectedQuality
                
                // Notify service to change quality mid-playback
                val intent = Intent(this, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_CHANGE_QUALITY
                }
                startService(intent)
                
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
