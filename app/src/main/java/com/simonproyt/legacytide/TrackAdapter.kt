package com.simonproyt.legacytide

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.simonproyt.legacytide.api.models.Track
import com.squareup.picasso.Picasso
import java.io.File

class TrackAdapter(private val onClick: (Track) -> Unit) : RecyclerView.Adapter<TrackAdapter.ViewHolder>() {
    private var items: List<Track> = emptyList()
    var isSelectionMode = false
    val selectedTracks = mutableSetOf<Track>()
    var onSelectionChanged: ((Int) -> Unit)? = null
    var onLongClick: ((Track) -> Unit)? = null

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
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.tv_track_title)
        private val artistView: TextView = itemView.findViewById(R.id.tv_track_artist)
        private val coverView: ImageView = itemView.findViewById(R.id.img_track_art)
        private val checkBox: CheckBox = itemView.findViewById(R.id.cb_track_select)
        private val btnDownload: android.widget.ImageButton = itemView.findViewById(R.id.btn_track_download)

        init {
            itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(items[adapterPosition])
                } else {
                    onClick(items[adapterPosition])
                }
            }
            itemView.setOnLongClickListener {
                if (onLongClick != null && !isSelectionMode) {
                    onLongClick?.invoke(items[adapterPosition])
                    true
                } else {
                    false
                }
            }
        }

        private fun toggleSelection(track: Track) {
            if (selectedTracks.contains(track)) {
                selectedTracks.remove(track)
            } else {
                selectedTracks.add(track)
            }
            notifyItemChanged(adapterPosition)
            onSelectionChanged?.invoke(selectedTracks.size)
        }

        fun bind(track: Track) {
            if (isSelectionMode) {
                checkBox.visibility = View.VISIBLE
                checkBox.isChecked = selectedTracks.contains(track)
            } else {
                checkBox.visibility = View.GONE
            }

            titleView.text = track.title
            
            val currentTrack = PlaybackQueue.getCurrentTrack()
            if (currentTrack != null && currentTrack.id == track.id) {
                titleView.setTextColor(android.graphics.Color.parseColor("#55AADD")) // Tidal Blue
            } else {
                titleView.setTextColor(android.graphics.Color.WHITE)
            }
            
            val artistName = track.artist?.name ?: track.artists?.firstOrNull()?.name ?: "Unknown Artist"
            artistView.text = artistName
            
            artistView.setOnClickListener {
                val artistId = track.artist?.id ?: track.artists?.firstOrNull()?.id
                if (artistId != null) {
                    val context = itemView.context
                    val intent = android.content.Intent(context, ArtistActivity::class.java).apply {
                        putExtra("ARTIST_ID", artistId)
                        putExtra("ARTIST_NAME", artistName)
                    }
                    context.startActivity(intent)
                }
            }

            val uuid = track.album?.cover
            if (uuid != null && uuid.isNotBlank()) {
                val albumId = track.album?.id
                val localFile = File(itemView.context.getExternalFilesDir(null), "legacytide_downloads/${albumId}_160.jpg")
                
                if (localFile.exists()) {
                    Picasso.with(itemView.context)
                        .load(localFile)
                        .placeholder(android.R.color.darker_gray)
                        .into(coverView)
                } else {
                    val imageUrl = if (uuid.startsWith("http")) uuid else "https://resources.tidal.com/images/${uuid.replace("-", "/")}/160x160.jpg"
                    Picasso.with(itemView.context)
                        .load(imageUrl)
                        .placeholder(android.R.color.darker_gray)
                        .into(coverView)
                }
            } else {
                coverView.setImageResource(android.R.color.darker_gray)
            }

            btnDownload.setOnClickListener {
                val context = itemView.context
                android.widget.Toast.makeText(context, "Preparing track download...", android.widget.Toast.LENGTH_SHORT).show()
                val prefs = context.getSharedPreferences("LegacyTidePrefs", android.content.Context.MODE_PRIVATE)
                val session = com.simonproyt.legacytide.api.Session(context).apply {
                    accessToken = prefs.getString("ACCESS_TOKEN", null)
                    userId = prefs.getLong("USER_ID", -1)
                    countryCode = prefs.getString("COUNTRY_CODE", "US") ?: "US"
                }
                DownloadHelper(context, session).downloadTrack(track)
            }
        }
    }
}
