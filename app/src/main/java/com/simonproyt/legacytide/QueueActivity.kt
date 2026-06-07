package com.simonproyt.legacytide

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class QueueActivity : AppCompatActivity() {

    private lateinit var adapter: TrackAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue)

        findViewById<ImageButton>(R.id.btn_back_queue).setOnClickListener {
            finish()
        }

        recyclerView = findViewById(R.id.recycler_queue)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = TrackAdapter { track ->
            // Clicking a track in queue makes it the current track and starts playback
            PlaybackQueue.currentIndex = PlaybackQueue.tracks.indexOfFirst { it.id == track.id }
            
            val sharedPreferences = getSharedPreferences("LegacyTidePrefs", MODE_PRIVATE)
            val accessToken = sharedPreferences.getString("ACCESS_TOKEN", null)
            val userId = sharedPreferences.getLong("USER_ID", -1)
            val countryCode = sharedPreferences.getString("COUNTRY_CODE", "US")

            if (accessToken != null && userId != -1L) {
                val playIntent = android.content.Intent(this, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_PLAY
                    putExtra(PlaybackService.EXTRA_ACCESS_TOKEN, accessToken)
                    putExtra(PlaybackService.EXTRA_USER_ID, userId)
                    putExtra(PlaybackService.EXTRA_COUNTRY_CODE, countryCode)
                }
                startService(playIntent)
            }
            finish() // Close queue after selecting
        }
        
        adapter.submitList(PlaybackQueue.tracks.toList())
        recyclerView.adapter = adapter

        if (PlaybackQueue.currentIndex != -1) {
            recyclerView.scrollToPosition(PlaybackQueue.currentIndex)
        }

        // Setup Drag & Drop and Swipe to Delete
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                
                // Update PlaybackQueue and currentIndex logic
                if (fromPos < toPos) {
                    for (i in fromPos until toPos) {
                        Collections.swap(PlaybackQueue.tracks, i, i + 1)
                    }
                } else {
                    for (i in fromPos downTo toPos + 1) {
                        Collections.swap(PlaybackQueue.tracks, i, i - 1)
                    }
                }
                
                // Adjust currentIndex
                if (PlaybackQueue.currentIndex == fromPos) {
                    PlaybackQueue.currentIndex = toPos
                } else if (PlaybackQueue.currentIndex in (fromPos + 1)..toPos) {
                    PlaybackQueue.currentIndex--
                } else if (PlaybackQueue.currentIndex in toPos until fromPos) {
                    PlaybackQueue.currentIndex++
                }

                adapter.submitList(PlaybackQueue.tracks.toList())
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                PlaybackQueue.tracks.removeAt(position)
                
                // Adjust currentIndex
                if (position < PlaybackQueue.currentIndex) {
                    PlaybackQueue.currentIndex--
                } else if (position == PlaybackQueue.currentIndex) {
                    // Current track was deleted, wait for service to advance or stop
                    // For simplicity, just decrement so next() works right, or let it be
                }

                adapter.submitList(PlaybackQueue.tracks.toList())
            }
        })

        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}
