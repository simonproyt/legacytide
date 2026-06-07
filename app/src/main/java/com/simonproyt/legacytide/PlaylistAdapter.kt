package com.simonproyt.legacytide

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.simonproyt.legacytide.api.models.Playlist
import com.squareup.picasso.Picasso

class PlaylistAdapter(private val onClick: (Playlist) -> Unit) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {
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
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onClick(items[adapterPosition])
                }
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
