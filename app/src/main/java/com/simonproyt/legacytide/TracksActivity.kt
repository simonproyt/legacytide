package com.simonproyt.legacytide

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simonproyt.legacytide.api.Session
import com.simonproyt.legacytide.api.TidalService
import com.simonproyt.legacytide.api.models.Track
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TracksActivity : AppCompatActivity() {

    private lateinit var session: Session
    private lateinit var tidalService: TidalService
    private lateinit var recyclerTracks: RecyclerView
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var nowPlayingHelper: NowPlayingHelper
    
    private lateinit var btnNavHome: TextView
    private lateinit var btnNavPlaylists: TextView
    private lateinit var btnNavSearch: TextView
    private var playlistId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracks)

        val accessToken = intent.getStringExtra("ACCESS_TOKEN")
        val userId = intent.getLongExtra("USER_ID", -1)
        val countryCode = intent.getStringExtra("COUNTRY_CODE")
        playlistId = intent.getStringExtra("PLAYLIST_ID") ?: ""
        val playlistTitle = intent.getStringExtra("PLAYLIST_TITLE")

        title = playlistTitle ?: "Tracks"

        if (accessToken == null || userId == -1L || playlistId.isEmpty()) {
            finish()
            return
        }

        session = Session().apply {
            this.accessToken = accessToken
            this.userId = userId
            this.countryCode = countryCode
        }
        tidalService = TidalService(session)

        trackAdapter = TrackAdapter { track ->
            PlaybackQueue.tracks = ArrayList(trackAdapter.getTracks())
            PlaybackQueue.currentIndex = PlaybackQueue.tracks.indexOfFirst { it.id == track.id }
            
            val intent = Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PLAY
                putExtra(PlaybackService.EXTRA_ACCESS_TOKEN, session.accessToken)
                putExtra(PlaybackService.EXTRA_USER_ID, session.userId)
                putExtra(PlaybackService.EXTRA_COUNTRY_CODE, session.countryCode)
            }
            startService(intent)
        }
        recyclerTracks = findViewById(R.id.recycler_tracks)
        recyclerTracks.layoutManager = LinearLayoutManager(this)
        
        btnNavHome = findViewById(R.id.btn_nav_home)
        btnNavPlaylists = findViewById(R.id.btn_nav_playlists)
        val btnNavPlaylists = findViewById<View>(R.id.btn_nav_playlists)
        btnNavPlaylists.setOnClickListener {
            val intent = Intent(this, CollectionActivity::class.java)
            startActivity(intent)
            finish()
        }
        btnNavSearch = findViewById(R.id.btn_nav_search)

        btnNavHome.setOnClickListener { finish() }
        btnNavSearch.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java).apply {
                putExtra("ACCESS_TOKEN", session.accessToken)
                putExtra("USER_ID", session.userId)
                putExtra("COUNTRY_CODE", session.countryCode)
            }
            startActivity(intent)
            finish()
        }

        recyclerTracks.adapter = trackAdapter
        
        nowPlayingHelper = NowPlayingHelper(this, session)

        loadTracks()
    }

    override fun onResume() {
        super.onResume()
        nowPlayingHelper.onResume()
    }

    override fun onPause() {
        super.onPause()
        nowPlayingHelper.onPause()
    }

    private fun loadTracks() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tracks = tidalService.getPlaylistTracks(playlistId)
                withContext(Dispatchers.Main) {
                    trackAdapter.submitList(tracks)
                    if (tracks.isEmpty()) {
                        android.widget.Toast.makeText(this@TracksActivity, "No tracks found", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@TracksActivity, "Error loading tracks: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }


}
