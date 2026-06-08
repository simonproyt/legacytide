package com.simonproyt.legacytide

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.simonproyt.legacytide.api.models.Album
import com.squareup.picasso.Picasso
import java.io.File

class AlbumAdapter(private val onClick: ((Album) -> Unit)? = null) : RecyclerView.Adapter<AlbumAdapter.ViewHolder>() {

    private var albums: List<Album> = emptyList()
    var isSelectionMode = false
    val selectedAlbums = mutableSetOf<Album>()
    var onSelectionChanged: ((Int) -> Unit)? = null
    var onLongClick: ((Album) -> Unit)? = null

    fun submitList(newAlbums: List<Album>) {
        albums = newAlbums
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ImageView = view.findViewById(R.id.iv_album_cover)
        val tvTitle: TextView = view.findViewById(R.id.tv_album_title)
        val cbSelect: CheckBox = view.findViewById(R.id.cb_album_select)
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
            val albumId = album.id
            val localFile = File(holder.itemView.context.getExternalFilesDir(null), "legacytide_downloads/${albumId}_320.jpg")
            
            if (localFile.exists()) {
                Picasso.with(holder.itemView.context).load(localFile).into(holder.ivCover)
            } else {
                val imageUrl = if (uuid.startsWith("http")) uuid else "https://resources.tidal.com/images/${uuid.replace("-", "/")}/320x320.jpg"
                Picasso.with(holder.itemView.context).load(imageUrl).into(holder.ivCover)
            }
        } else {
            holder.ivCover.setImageDrawable(null)
        }

        if (isSelectionMode) {
            holder.cbSelect.visibility = View.VISIBLE
            holder.cbSelect.isChecked = selectedAlbums.contains(album)
        } else {
            holder.cbSelect.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                if (selectedAlbums.contains(album)) {
                    selectedAlbums.remove(album)
                } else {
                    selectedAlbums.add(album)
                }
                notifyItemChanged(position)
                onSelectionChanged?.invoke(selectedAlbums.size)
            } else if (onClick != null) {
                onClick.invoke(album)
            } else {
                val context = holder.itemView.context
                val intent = Intent(context, AlbumActivity::class.java).apply {
                    putExtra("ALBUM_ID", album.id)
                    putExtra("ALBUM_TITLE", album.title)
                }
                context.startActivity(intent)
            }
        }
        
        holder.itemView.setOnLongClickListener {
            if (onLongClick != null && !isSelectionMode) {
                onLongClick?.invoke(album)
                true
            } else {
                false
            }
        }
    }

    override fun getItemCount() = albums.size
}
