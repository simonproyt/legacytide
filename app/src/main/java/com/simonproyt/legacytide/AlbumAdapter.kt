package com.simonproyt.legacytide

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.simonproyt.legacytide.api.models.Album
import com.squareup.picasso.Picasso

class AlbumAdapter(private val albums: List<Album>) : RecyclerView.Adapter<AlbumAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ImageView = view.findViewById(R.id.iv_album_cover)
        val tvTitle: TextView = view.findViewById(R.id.tv_album_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_album, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album = albums[position]
        holder.tvTitle.text = album.title ?: "Unknown Album"
        
        val uuid = album.cover
        if (uuid != null && uuid.isNotBlank()) {
            val imageUrl = if (uuid.startsWith("http")) uuid else "https://resources.tidal.com/images/${uuid.replace("-", "/")}/320x320.jpg"
            Picasso.with(holder.itemView.context).load(imageUrl).into(holder.ivCover)
        } else {
            holder.ivCover.setImageDrawable(null)
        }

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, AlbumActivity::class.java).apply {
                putExtra("ALBUM_ID", album.id)
                putExtra("ALBUM_TITLE", album.title)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = albums.size
}
