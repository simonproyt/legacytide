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

        session = Session().apply {
            this.accessToken = accessToken
            this.userId = userId
            this.countryCode = countryCode
        }
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
        btnNavPlaylists = findViewById(R.id.btn_nav_playlists)
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

        btnNavPlaylists.setOnClickListener {
            tvHeader.text = "My Playlists"
            loadPlaylists()
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
    }

    override fun onPause() {
        super.onPause()
        nowPlayingHelper.onPause()
    }

    private fun loadPlaylists() {
        progressPlaylists.visibility = View.VISIBLE
        recyclerPlaylists.visibility = View.GONE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val playlists = tidalService.getUserPlaylists()
                withContext(Dispatchers.Main) {
                    playlistAdapter.submitList(playlists)
                    progressPlaylists.visibility = View.GONE
                    recyclerPlaylists.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressPlaylists.visibility = View.GONE
                    android.widget.Toast.makeText(this@MainActivity, "Error loading playlists: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
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
                    android.widget.Toast.makeText(this@MainActivity, "Error loading mixes: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
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
                if (playlist.isHeader) {
                    titleView.text = playlist.title
                    titleView.textSize = 20f
                    titleView.setTextColor(android.graphics.Color.parseColor("#55AADD"))
                    descView.visibility = View.GONE
                    artView.visibility = View.GONE
                    itemView.isClickable = false
                    return
                }
                
                titleView.text = playlist.title
                titleView.textSize = 16f
                titleView.setTextColor(android.graphics.Color.WHITE)
                descView.visibility = View.VISIBLE
                artView.visibility = View.VISIBLE
                itemView.isClickable = true
                
                if (playlist.numberOfTracks > 0) {
                    descView.text = "${playlist.numberOfTracks} tracks"
                } else if (!playlist.description.isNullOrEmpty()) {
                    descView.text = playlist.description
                } else {
                    descView.text = ""
                }
                
                val uuid = playlist.squareImage ?: playlist.image
                if (uuid != null && uuid.isNotBlank()) {
                    val imageUrl = if (uuid.startsWith("http")) uuid else "https://resources.tidal.com/images/${uuid.replace("-", "/")}/320x320.jpg"
                    Picasso.with(itemView.context).load(imageUrl).into(artView)
                } else {
                    artView.setImageDrawable(null)
                }
            }
        }
    }
}
