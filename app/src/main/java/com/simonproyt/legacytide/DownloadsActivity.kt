package com.simonproyt.legacytide

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import androidx.recyclerview.widget.ItemTouchHelper
import com.simonproyt.legacytide.api.Session

class DownloadsActivity : AppCompatActivity() {

    private lateinit var nowPlayingHelper: NowPlayingHelper
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var layoutSelectionActions: View
    private lateinit var tvSelectionCount: TextView
    private lateinit var btnCancelSelection: View
    private lateinit var btnDeleteSelected: View
    private lateinit var tabLayout: TabLayout
    private var currentTab = 0 // 0 = Tracks, 1 = Albums

    private val downloadReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            loadDownloads(Session(this@DownloadsActivity))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloads)

        val sharedPreferences = getSharedPreferences("LegacyTidePrefs", MODE_PRIVATE)
        val accessToken = sharedPreferences.getString("ACCESS_TOKEN", null)
        val userId = sharedPreferences.getLong("USER_ID", -1)
        val countryCode = sharedPreferences.getString("COUNTRY_CODE", "US")

        val session = Session(this).apply {
            this.accessToken = accessToken
            this.userId = userId
            this.countryCode = countryCode
        }

        findViewById<ImageButton>(R.id.btn_back_downloads).setOnClickListener {
            val isOffline = sharedPreferences.getBoolean("offline_mode", false)
            if (isOffline && isTaskRoot) {
                moveTaskToBack(true)
            } else {
                finish()
            }
        }

        findViewById<ImageButton>(R.id.btn_settings_downloads).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        recyclerView = findViewById(R.id.recycler_downloads)
        emptyText = findViewById(R.id.tv_empty_downloads)
        tabLayout = findViewById(R.id.tab_layout_downloads)

        recyclerView.layoutManager = LinearLayoutManager(this)
        
        trackAdapter = TrackAdapter { track ->
            val helper = DownloadHelper(this, session)
            val tracks = helper.getDownloadedTracks()
            val position = tracks.indexOfFirst { it.id == track.id }
            PlaybackQueue.tracks = ArrayList(tracks)
            PlaybackQueue.currentIndex = if (position >= 0) position else 0

            val playIntent = Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PLAY
                putExtra(PlaybackService.EXTRA_ACCESS_TOKEN, session.accessToken)
                putExtra(PlaybackService.EXTRA_USER_ID, session.userId)
                putExtra(PlaybackService.EXTRA_COUNTRY_CODE, session.countryCode)
            }
            startService(playIntent)
            
            val playerIntent = Intent(this, PlayerActivity::class.java)
            startActivity(playerIntent)
        }
        
        albumAdapter = AlbumAdapter()
        
        recyclerView.adapter = trackAdapter
        
        layoutSelectionActions = findViewById(R.id.layout_selection_actions)
        tvSelectionCount = findViewById(R.id.tv_selection_count)
        btnCancelSelection = findViewById(R.id.btn_cancel_selection)
        btnDeleteSelected = findViewById(R.id.btn_delete_selected)
        
        trackAdapter.onLongClick = { track ->
            trackAdapter.isSelectionMode = true
            trackAdapter.selectedTracks.add(track)
            trackAdapter.notifyDataSetChanged()
            layoutSelectionActions.visibility = View.VISIBLE
            tvSelectionCount.text = "1 Selected"
        }
        
        trackAdapter.onSelectionChanged = { count ->
            tvSelectionCount.text = "$count Selected"
            if (count == 0) {
                exitSelectionMode()
            }
        }
        
        albumAdapter.onLongClick = { album ->
            albumAdapter.isSelectionMode = true
            albumAdapter.selectedAlbums.add(album)
            albumAdapter.notifyDataSetChanged()
            layoutSelectionActions.visibility = View.VISIBLE
            tvSelectionCount.text = "1 Selected"
        }
        
        albumAdapter.onSelectionChanged = { count ->
            tvSelectionCount.text = "$count Selected"
            if (count == 0) {
                exitSelectionMode()
            }
        }
        
        btnCancelSelection.setOnClickListener {
            exitSelectionMode()
        }
        
        btnDeleteSelected.setOnClickListener {
            val helper = DownloadHelper(this, session)
            if (currentTab == 0) {
                val count = trackAdapter.selectedTracks.size
                trackAdapter.selectedTracks.forEach { track ->
                    helper.deleteTrack(track.id)
                }
                android.widget.Toast.makeText(this, "Deleted $count tracks", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val count = albumAdapter.selectedAlbums.size
                albumAdapter.selectedAlbums.forEach { album ->
                    helper.deleteAlbum(album.id)
                }
                android.widget.Toast.makeText(this, "Deleted $count albums", android.widget.Toast.LENGTH_SHORT).show()
            }
            exitSelectionMode()
            loadDownloads(session)
        }
        
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                exitSelectionMode()
                currentTab = tab?.position ?: 0
                if (currentTab == 0) {
                    recyclerView.layoutManager = LinearLayoutManager(this@DownloadsActivity)
                    recyclerView.adapter = trackAdapter
                } else {
                    recyclerView.layoutManager = GridLayoutManager(this@DownloadsActivity, 3)
                    recyclerView.adapter = albumAdapter
                }
                loadDownloads(session)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        nowPlayingHelper = NowPlayingHelper(this, session)

        loadDownloads(session)
    }

    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter("com.simonproyt.legacytide.DOWNLOAD_COMPLETE")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(downloadReceiver)
    }

    override fun onResume() {
        super.onResume()
        nowPlayingHelper.onResume()
        
        val isOfflinePref = getSharedPreferences("LegacyTidePrefs", MODE_PRIVATE)
            .getBoolean("offline_mode", false)
        if (!isOfflinePref && isTaskRoot) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        nowPlayingHelper.onPause()
    }

    private fun exitSelectionMode() {
        if (currentTab == 0) {
            trackAdapter.isSelectionMode = false
            trackAdapter.selectedTracks.clear()
            trackAdapter.notifyDataSetChanged()
        } else {
            albumAdapter.isSelectionMode = false
            albumAdapter.selectedAlbums.clear()
            albumAdapter.notifyDataSetChanged()
        }
        layoutSelectionActions.visibility = View.GONE
    }

    private fun loadDownloads(session: Session) {
        val helper = DownloadHelper(this, session)
        if (currentTab == 0) {
            val downloadedTracks = helper.getDownloadedTracks()
            
            if (downloadedTracks.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                trackAdapter.submitList(downloadedTracks)
            }
        } else {
            val downloadedAlbums = helper.getDownloadedAlbums()
            
            if (downloadedAlbums.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                emptyText.text = "No downloaded albums yet."
                recyclerView.visibility = View.GONE
            } else {
                emptyText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                albumAdapter.submitList(downloadedAlbums)
            }
        }
    }
}
