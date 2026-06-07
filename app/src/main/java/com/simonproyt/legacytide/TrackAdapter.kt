package com.simonproyt.legacytide

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.simonproyt.legacytide.api.models.Track
import com.squareup.picasso.Picasso

class TrackAdapter(private val onClick: (Track) -> Unit) : RecyclerView.Adapter<TrackAdapter.ViewHolder>() {
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
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.tv_track_title)
        private val artistView: TextView = itemView.findViewById(R.id.tv_track_artist)
        private val coverView: ImageView = itemView.findViewById(R.id.img_track_art)

        init {
            itemView.setOnClickListener {
                onClick(items[adapterPosition])
            }
        }

        fun bind(track: Track) {
            titleView.text = track.title
            
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
                val imageUrl = "https://resources.tidal.com/images/${uuid.replace("-", "/")}/160x160.jpg"
                Picasso.with(itemView.context).load(imageUrl).into(coverView)
            } else {
                coverView.setImageDrawable(null)
            }
        }
    }
}
