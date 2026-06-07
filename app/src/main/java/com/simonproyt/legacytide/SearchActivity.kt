package com.simonproyt.legacytide

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simonproyt.legacytide.api.Config
import com.simonproyt.legacytide.api.Session
import com.simonproyt.legacytide.api.TidalService
import com.simonproyt.legacytide.api.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var progressSearch: ProgressBar
    private lateinit var recyclerSearchResults: RecyclerView
    private lateinit var trackAdapter: TrackAdapter
    
    private lateinit var btnNavHome: TextView
    private lateinit var btnNavPlaylists: TextView
    private lateinit var btnNavSearch: TextView
    
    private lateinit var tidalService: TidalService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        val accessToken = intent.getStringExtra("ACCESS_TOKEN")
        val userId = intent.getLongExtra("USER_ID", -1L)
        val countryCode = intent.getStringExtra("COUNTRY_CODE")
        
        if (accessToken == null || userId == -1L) {
            finish()
            return
        }

        val config = Config()
        val session = Session(config).apply {
            this.accessToken = accessToken
            this.userId = userId
            this.countryCode = countryCode
        }
        tidalService = TidalService(session)

        etSearch = findViewById(R.id.et_search)
        btnSearch = findViewById(R.id.btn_search)
        progressSearch = findViewById(R.id.progress_search)
        recyclerSearchResults = findViewById(R.id.recycler_search_results)
        
        btnNavHome = findViewById(R.id.btn_nav_home)
        btnNavPlaylists = findViewById(R.id.btn_nav_playlists)
        btnNavSearch = findViewById(R.id.btn_nav_search)

        btnNavHome.setOnClickListener { finish() } // Goes back to MainActivity
        btnNavPlaylists.setOnClickListener {
            val intent = Intent(this, CollectionActivity::class.java)
            startActivity(intent)
            finish()
        }

        trackAdapter = TrackAdapter { track ->
            // Queue up the single track
            PlaybackQueue.tracks = ArrayList(trackAdapter.getTracks())
            PlaybackQueue.currentIndex = PlaybackQueue.tracks.indexOfFirst { it.id == track.id }

            val playIntent = Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PLAY
                putExtra(PlaybackService.EXTRA_ACCESS_TOKEN, session.accessToken)
                putExtra(PlaybackService.EXTRA_USER_ID, session.userId)
                putExtra(PlaybackService.EXTRA_COUNTRY_CODE, session.countryCode)
            }
            startService(playIntent)
        }

        recyclerSearchResults.layoutManager = LinearLayoutManager(this)
        recyclerSearchResults.adapter = trackAdapter

        btnSearch.setOnClickListener {
            performSearch()
        }
        
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isEmpty()) return

        // Hide keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)

        progressSearch.visibility = View.VISIBLE
        recyclerSearchResults.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tracks = tidalService.searchTracks(query)
                withContext(Dispatchers.Main) {
                    trackAdapter.submitList(tracks)
                    progressSearch.visibility = View.GONE
                    recyclerSearchResults.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progressSearch.visibility = View.GONE
                }
            }
        }
    }
}
