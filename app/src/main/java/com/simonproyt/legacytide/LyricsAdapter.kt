package com.simonproyt.legacytide

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.simonproyt.legacytide.api.models.TimedLyric

class LyricsAdapter : RecyclerView.Adapter<LyricsAdapter.ViewHolder>() {

    private var lyrics: List<TimedLyric> = emptyList()
    private var currentIndex: Int = -1

    fun submitList(newLyrics: List<TimedLyric>) {
        lyrics = newLyrics
        currentIndex = -1
        notifyDataSetChanged()
    }

    fun setCurrentIndex(index: Int) {
        if (currentIndex != index && index >= 0 && index < lyrics.size) {
            val oldIndex = currentIndex
            currentIndex = index
            if (oldIndex != -1) notifyItemChanged(oldIndex)
            notifyItemChanged(currentIndex)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lyric, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val lyric = lyrics[position]
        holder.textView.text = lyric.text
        holder.textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        
        if (position == currentIndex) {
            holder.textView.setTextColor(Color.WHITE)
            holder.textView.setTypeface(null, Typeface.BOLD)
            holder.textView.textSize = 20f
        } else {
            holder.textView.setTextColor(Color.parseColor("#888888"))
            holder.textView.setTypeface(null, Typeface.NORMAL)
            holder.textView.textSize = 16f
        }
    }

    override fun getItemCount(): Int = lyrics.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.tv_lyric_line)
    }
}
