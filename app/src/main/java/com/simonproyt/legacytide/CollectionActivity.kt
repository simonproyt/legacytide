package com.simonproyt.legacytide

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simonproyt.legacytide.api.Config
import com.simonproyt.legacytide.api.Session
import com.simonproyt.legacytide.api.TidalService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CollectionActivity : AppCompatActivity() {

    private lateinit var tidalService: TidalService
    private lateinit var session: Session
    
    private lateinit var btnPlaylists: Button
    private lateinit var btnAlbums: Button
    private lateinit var btnTracks: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var nowPlayingHelper: NowPlayingHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collection)

        val sharedPreferences = getSharedPreferences("LegacyTidePrefs", MODE_PRIVATE)
        val accessToken = sharedPreferences.getString("ACCESS_TOKEN", null)
        val userId = sharedPreferences.getLong("USER_ID", -1)
        val countryCode = sharedPreferences.getString("COUNTRY_CODE", "US")

        if (accessToken == null || userId == -1L) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val config = Config()
        session = Session(config).apply {
            this.accessToken = accessToken
            this.userId = userId
            this.countryCode = countryCode
        }
        
        // Setup cache
        val cacheSize = 10 * 1024 * 1024L
        val cache = okhttp3.Cache(java.io.File(cacheDir, "http_cache"), cacheSize)
        session.client = session.client.newBuilder().cache(cache).build()

        tidalService = TidalService(session)

        findViewById<ImageButton>(R.id.btn_back_collection).setOnClickListener {
            finish()
        }

        btnPlaylists = findViewById(R.id.btn_tab_playlists)
        btnAlbums = findViewById(R.id.btn_tab_albums)
        btnTracks = findViewById(R.id.btn_tab_tracks)
        progressBar = findViewById(R.id.progress_collection)
        recyclerView = findViewById(R.id.recycler_collection)
        recyclerView.layoutManager = LinearLayoutManager(this)

        btnPlaylists.setOnClickListener { selectTab(0) }
        btnAlbums.setOnClickListener { selectTab(1) }
        btnTracks.setOnClickListener { selectTab(2) }

        // Load playlists by default
        selectTab(0)
        
        nowPlayingHelper = NowPlayingHelper(this, session)
    }

    override fun onResume() {
        super.onResume()
        nowPlayingHelper.onResume()
    }

    override fun onPause() {
        super.onPause()
        nowPlayingHelper.onPause()
    }

    private fun selectTab(index: Int) {
        val activeBg = "#333333"
        val activeText = "#FFFFFF"
        val inactiveBg = android.graphics.Color.TRANSPARENT
        val inactiveText = "#888888"

        btnPlaylists.setBackgroundColor(if (index == 0) android.graphics.Color.parseColor(activeBg) else inactiveBg)
        btnPlaylists.setTextColor(android.graphics.Color.parseColor(if (index == 0) activeText else inactiveText))

        btnAlbums.setBackgroundColor(if (index == 1) android.graphics.Color.parseColor(activeBg) else inactiveBg)
        btnAlbums.setTextColor(android.graphics.Color.parseColor(if (index == 1) activeText else inactiveText))

        btnTracks.setBackgroundColor(if (index == 2) android.graphics.Color.parseColor(activeBg) else inactiveBg)
        btnTracks.setTextColor(android.graphics.Color.parseColor(if (index == 2) activeText else inactiveText))

        when (index) {
            0 -> loadPlaylists()
            1 -> loadAlbums()
            2 -> loadTracks()
        }
    }

    private fun loadPlaylists() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        progressBar.visibility = View.VISIBLE
        recyclerView.adapter = null
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val playlists = tidalService.getUserPlaylists()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    val adapter = PlaylistAdapter { playlist ->
                        val intent = Intent(this@CollectionActivity, TracksActivity::class.java).apply {
                            putExtra("PLAYLIST_UUID", playlist.uuid)
                            putExtra("PLAYLIST_TITLE", playlist.title)
                        }
                        startActivity(intent)
                    }
                    adapter.submitList(playlists)
                    recyclerView.adapter = adapter
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@CollectionActivity, "Failed to load playlists", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadAlbums() {
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        progressBar.visibility = View.VISIBLE
        recyclerView.adapter = null
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val albums = tidalService.getFavoriteAlbums()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    val adapter = AlbumAdapter { album ->
                        val intent = Intent(this@CollectionActivity, AlbumActivity::class.java).apply {
                            putExtra("ALBUM_ID", album.id)
                            putExtra("ALBUM_TITLE", album.title)
                        }
                        startActivity(intent)
                    }
                    adapter.submitList(albums)
                    recyclerView.adapter = adapter
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@CollectionActivity, "Failed to load albums", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadTracks() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        progressBar.visibility = View.VISIBLE
        recyclerView.adapter = null
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tracks = tidalService.getFavoriteTracks()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    val adapter = TrackAdapter { track ->
                        PlaybackQueue.tracks = ArrayList(tracks)
                        PlaybackQueue.currentIndex = PlaybackQueue.tracks.indexOfFirst { it.id == track.id }
                        
                        val playIntent = Intent(this@CollectionActivity, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_PLAY
                            putExtra(PlaybackService.EXTRA_ACCESS_TOKEN, session.accessToken)
                            putExtra(PlaybackService.EXTRA_USER_ID, session.userId)
                            putExtra(PlaybackService.EXTRA_COUNTRY_CODE, session.countryCode)
                        }
                        startService(playIntent)
                        
                        val playerIntent = Intent(this@CollectionActivity, PlayerActivity::class.java)
                        startActivity(playerIntent)
                    }
                    adapter.submitList(tracks)
                    recyclerView.adapter = adapter
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@CollectionActivity, "Failed to load tracks", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
