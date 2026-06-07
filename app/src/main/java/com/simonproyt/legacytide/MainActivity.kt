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
import com.simonproyt.legacytide.api.models.Playlist
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var session: Session
    private lateinit var tidalService: TidalService
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PlaylistAdapter
    private lateinit var nowPlayingHelper: NowPlayingHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "Your Playlists"

        val accessToken = intent.getStringExtra("ACCESS_TOKEN")
        val userId = intent.getLongExtra("USER_ID", -1)
        val countryCode = intent.getStringExtra("COUNTRY_CODE")

        if (accessToken == null || userId == -1L) {
            finish()
            return
        }

        session = Session().apply {
            this.accessToken = accessToken
            this.userId = userId
            this.countryCode = countryCode
        }
        tidalService = TidalService(session)

        recyclerView = findViewById(R.id.recycler_playlists)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PlaylistAdapter { playlist ->
            val intent = Intent(this, TracksActivity::class.java).apply {
                putExtra("ACCESS_TOKEN", session.accessToken)
                putExtra("USER_ID", session.userId)
                putExtra("COUNTRY_CODE", session.countryCode)
                putExtra("PLAYLIST_ID", playlist.uuid)
                putExtra("PLAYLIST_TITLE", playlist.title)
            }
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        nowPlayingHelper = NowPlayingHelper(this, session)

        loadPlaylists()
    }

    override fun onResume() {
        super.onResume()
        nowPlayingHelper.onResume()
    }

    override fun onPause() {
        super.onPause()
        nowPlayingHelper.onPause()
    }

    private fun loadPlaylists() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val playlists = tidalService.getUserPlaylists()
                withContext(Dispatchers.Main) {
                    adapter.submitList(playlists)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@MainActivity, "Error loading playlists: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    inner class PlaylistAdapter(private val onClick: (Playlist) -> Unit) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {
        private var items: List<Playlist> = emptyList()

        fun submitList(newItems: List<Playlist>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleView: TextView = itemView.findViewById(R.id.tv_playlist_title)
            private val descView: TextView = itemView.findViewById(R.id.tv_playlist_desc)
            private val artView: ImageView = itemView.findViewById(R.id.img_playlist_art)

            init {
                itemView.setOnClickListener {
                    onClick(items[adapterPosition])
                }
            }

            fun bind(playlist: Playlist) {
                titleView.text = playlist.title
                descView.text = "${playlist.numberOfTracks} tracks"
                
                val uuid = playlist.squareImage ?: playlist.image
                if (uuid != null && uuid.isNotBlank()) {
                    val imageUrl = "https://resources.tidal.com/images/${uuid.replace("-", "/")}/320x320.jpg"
                    Picasso.with(itemView.context).load(imageUrl).into(artView)
                } else {
                    artView.setImageDrawable(null)
                }
            }
        }
    }
}
