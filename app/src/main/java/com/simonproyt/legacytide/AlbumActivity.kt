package com.simonproyt.legacytide

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simonproyt.legacytide.api.Config
import com.simonproyt.legacytide.api.Session
import com.simonproyt.legacytide.api.TidalService
import com.simonproyt.legacytide.api.models.Track
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumActivity : AppCompatActivity() {

    private lateinit var tidalService: TidalService
    private lateinit var session: Session
    private var albumId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album)

        albumId = intent.getLongExtra("ALBUM_ID", 0)
        val albumTitle = intent.getStringExtra("ALBUM_TITLE") ?: "Album"

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

        val tvTitle = findViewById<TextView>(R.id.tv_album_title)
        tvTitle.text = albumTitle
        
        loadAlbumDetails()
    }

    private fun loadAlbumDetails() {
        val progressBar = findViewById<ProgressBar>(R.id.progress_album)
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_album_tracks)
        val ivCover = findViewById<ImageView>(R.id.iv_album_cover)
        val tvDate = findViewById<TextView>(R.id.tv_album_date)
        
        progressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val album = tidalService.getAlbum(albumId)
                val tracks = tidalService.getAlbumTracks(albumId)
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    
                    val uuid = album.cover
                    if (uuid != null && uuid.isNotBlank()) {
                        val imageUrl = if (uuid.startsWith("http")) uuid else "https://resources.tidal.com/images/${uuid.replace("-", "/")}/320x320.jpg"
                        Picasso.with(this@AlbumActivity).load(imageUrl).into(ivCover)
                    }
                    
                    if (album.releaseDate != null) {
                        tvDate.text = "Released: ${album.releaseDate}"
                    } else {
                        tvDate.visibility = View.GONE
                    }
                    
                    recyclerView.layoutManager = LinearLayoutManager(this@AlbumActivity)
                    val adapter = TrackAdapter { track ->
                        // Pass session credentials to PlayerActivity
                        val playerIntent = android.content.Intent(this@AlbumActivity, PlayerActivity::class.java).apply {
                            putExtra("TRACK_ID", track.id)
                            putExtra("TRACK_TITLE", track.title)
                            putExtra("TRACK_ARTIST", track.artist?.name ?: "Unknown Artist")
                            putExtra("TRACK_ALBUM", track.album?.title ?: "Unknown Album")
                            
                            val coverUuid = track.album?.cover
                            if (coverUuid != null && coverUuid.isNotBlank()) {
                                val coverUrl = if (coverUuid.startsWith("http")) coverUuid else "https://resources.tidal.com/images/${coverUuid.replace("-", "/")}/1280x1280.jpg"
                                putExtra("TRACK_IMAGE", coverUrl)
                            }
                            
                            putExtra("ACCESS_TOKEN", session.accessToken ?: "")
                            putExtra("USER_ID", session.userId ?: -1L)
                            putExtra("COUNTRY_CODE", session.countryCode ?: "US")
                        }
                        startActivity(playerIntent)
                    }
                    adapter.submitList(tracks)
                    recyclerView.adapter = adapter
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@AlbumActivity, "Failed to load album", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
