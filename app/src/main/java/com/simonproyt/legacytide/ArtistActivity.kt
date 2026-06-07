package com.simonproyt.legacytide

import android.content.Intent
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

class ArtistActivity : AppCompatActivity() {

    private lateinit var tidalService: TidalService
    private lateinit var session: Session
    private var artistId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artist)

        artistId = intent.getLongExtra("ARTIST_ID", 0)
        val artistName = intent.getStringExtra("ARTIST_NAME") ?: "Artist"

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

        val tvName = findViewById<TextView>(R.id.tv_artist_name)
        tvName.text = artistName
        
        loadArtistDetails()
    }

    private fun loadArtistDetails() {
        val progressBar = findViewById<ProgressBar>(R.id.progress_artist)
        val recyclerTopTracks = findViewById<RecyclerView>(R.id.recycler_artist_top_tracks)
        val recyclerAlbums = findViewById<RecyclerView>(R.id.recycler_artist_albums)
        val ivPicture = findViewById<ImageView>(R.id.iv_artist_picture)
        
        progressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val artist = tidalService.getArtist(artistId)
                val topTracks = tidalService.getArtistTopTracks(artistId)
                val albums = tidalService.getArtistAlbums(artistId)
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    
                    val uuid = artist.picture
                    if (uuid != null && uuid.isNotBlank()) {
                        val imageUrl = if (uuid.startsWith("http")) uuid else "https://resources.tidal.com/images/${uuid.replace("-", "/")}/1280x1280.jpg"
                        Picasso.with(this@ArtistActivity).load(imageUrl).into(ivPicture)
                    }
                    
                    // Setup Top Tracks
                    recyclerTopTracks.layoutManager = LinearLayoutManager(this@ArtistActivity)
                    val tracksAdapter = TrackAdapter { track ->
                        // Pass session credentials to PlayerActivity
                        val playerIntent = Intent(this@ArtistActivity, PlayerActivity::class.java).apply {
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
                    tracksAdapter.submitList(topTracks)
                    recyclerTopTracks.adapter = tracksAdapter
                    
                    // Setup Albums
                    recyclerAlbums.layoutManager = LinearLayoutManager(this@ArtistActivity, LinearLayoutManager.HORIZONTAL, false)
                    recyclerAlbums.adapter = AlbumAdapter(albums)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ArtistActivity, "Failed to load artist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
