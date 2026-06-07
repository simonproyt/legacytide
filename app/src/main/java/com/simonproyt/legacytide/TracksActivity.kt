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
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TrackAdapter
    private lateinit var nowPlayingHelper: NowPlayingHelper
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

        adapter = TrackAdapter { track ->
            PlaybackQueue.tracks = adapter.getTracks()
            PlaybackQueue.currentIndex = PlaybackQueue.tracks.indexOfFirst { it.id == track.id }
            
            val intent = Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PLAY
                putExtra(PlaybackService.EXTRA_ACCESS_TOKEN, session.accessToken)
                putExtra(PlaybackService.EXTRA_USER_ID, session.userId)
                putExtra(PlaybackService.EXTRA_COUNTRY_CODE, session.countryCode)
            }
            startService(intent)
        }
        recyclerView = findViewById(R.id.recycler_tracks)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
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
                    adapter.submitList(tracks)
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

    inner class TrackAdapter(private val onClick: (Track) -> Unit) : RecyclerView.Adapter<TrackAdapter.ViewHolder>() {
        private var items: List<Track> = emptyList()

        fun submitList(newItems: List<Track>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun getTracks(): List<Track> = items

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleView: TextView = itemView.findViewById(R.id.tv_track_title)
            private val artistView: TextView = itemView.findViewById(R.id.tv_track_artist)
            private val artView: ImageView = itemView.findViewById(R.id.img_track_art)

            fun bind(track: Track) {
                titleView.text = track.title
                artistView.text = track.artist?.name ?: "Unknown Artist"
                
                val album = track.album
                if (album != null && album.cover != null && album.cover.isNotBlank()) {
                    val imageUrl = "https://resources.tidal.com/images/${album.cover.replace("-", "/")}/160x160.jpg"
                    Picasso.with(itemView.context).load(imageUrl).into(artView)
                } else {
                    artView.setImageDrawable(null)
                }
            }
        }
    }
}
