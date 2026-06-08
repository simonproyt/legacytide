package com.simonproyt.legacytide

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simonproyt.legacytide.api.Session
import com.simonproyt.legacytide.api.TidalService
import com.simonproyt.legacytide.api.models.Playlist
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var session: Session
    private lateinit var tidalService: TidalService
    private lateinit var recyclerPlaylists: RecyclerView
    private lateinit var progressPlaylists: ProgressBar
    private lateinit var tvHeader: TextView
    private lateinit var btnNavHome: TextView
    private lateinit var btnNavPlaylists: TextView
    private lateinit var btnNavSearch: TextView
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var nowPlayingHelper: NowPlayingHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val accessToken = intent.getStringExtra("ACCESS_TOKEN")
        val userId = intent.getLongExtra("USER_ID", -1)
        val countryCode = intent.getStringExtra("COUNTRY_CODE")

        if (accessToken == null || userId == -1L) {
            finish()
            return
        }

        session = Session(this).apply {
            this.accessToken = accessToken
            this.userId = userId
            this.countryCode = countryCode
        }

        // Setup 10MB API cache
        val cacheSize = 10 * 1024 * 1024L
        val cache = okhttp3.Cache(java.io.File(cacheDir, "http_cache"), cacheSize)
        session.client = session.client.newBuilder().cache(cache).build()

        tidalService = TidalService(session)

        try {
            val picasso = Picasso.Builder(this)
                .downloader(com.jakewharton.picasso.OkHttp3Downloader(session.client))
                .build()
            Picasso.setSingletonInstance(picasso)
        } catch (e: Exception) {
            // Picasso singleton already set
        }

        recyclerPlaylists = findViewById(R.id.recycler_playlists)
        progressPlaylists = findViewById(R.id.progress_playlists)
        tvHeader = findViewById(R.id.tv_header)
        btnNavHome = findViewById(R.id.btn_nav_home)
        val btnNavPlaylists = findViewById<View>(R.id.btn_nav_playlists)
        btnNavPlaylists.setOnClickListener {
            val intent = Intent(this, CollectionActivity::class.java)
            startActivity(intent)
        }
        btnNavSearch = findViewById(R.id.btn_nav_search)

        playlistAdapter = PlaylistAdapter { playlist ->
            val intent = Intent(this, TracksActivity::class.java).apply {
                putExtra("PLAYLIST_ID", playlist.uuid)
                putExtra("PLAYLIST_TITLE", playlist.title)
                putExtra("ACCESS_TOKEN", session.accessToken)
                putExtra("USER_ID", session.userId)
                putExtra("COUNTRY_CODE", session.countryCode)
            }
            startActivity(intent)
        }

        recyclerPlaylists.layoutManager = LinearLayoutManager(this)
        recyclerPlaylists.adapter = playlistAdapter

        btnNavHome.setOnClickListener {
            tvHeader.text = "My Mixes"
            loadMixes()
        }


        btnNavSearch.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java).apply {
                putExtra("ACCESS_TOKEN", session.accessToken)
                putExtra("USER_ID", session.userId)
                putExtra("COUNTRY_CODE", session.countryCode)
            }
            startActivity(intent)
        }

        nowPlayingHelper = NowPlayingHelper(this, session)

        // Default to Home
        tvHeader.text = "My Mixes"
        loadMixes()
    }

    override fun onResume() {
        super.onResume()
        nowPlayingHelper.onResume()
        
        val isOfflinePref = getSharedPreferences("LegacyTidePrefs", MODE_PRIVATE)
            .getBoolean("offline_mode", false)
        
        if (isOfflinePref) {
            startActivity(Intent(this, DownloadsActivity::class.java))
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        nowPlayingHelper.onPause()
    }


    private fun loadMixes() {
        progressPlaylists.visibility = View.VISIBLE
        recyclerPlaylists.visibility = View.GONE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mixes = tidalService.getMixes()
                withContext(Dispatchers.Main) {
                    playlistAdapter.submitList(mixes)
                    progressPlaylists.visibility = View.GONE
                    recyclerPlaylists.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressPlaylists.visibility = View.GONE
                    if (e is java.net.SocketTimeoutException || e is java.net.UnknownHostException || e is java.net.ConnectException) {
                        android.widget.Toast.makeText(this@MainActivity, "Connection lost. Switching to Offline Mode.", android.widget.Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@MainActivity, DownloadsActivity::class.java))
                        finish()
                    } else {
                        android.widget.Toast.makeText(this@MainActivity, "Error loading mixes: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }


}
