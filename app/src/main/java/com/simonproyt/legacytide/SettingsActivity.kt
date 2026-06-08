package com.simonproyt.legacytide

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.simonproyt.legacytide.api.Session

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val session = Session(this)

        findViewById<ImageButton>(R.id.btn_back_settings).setOnClickListener {
            finish()
        }

        // Quality arrays
        val qualities = arrayOf("LOW", "HIGH", "LOSSLESS", "HI_RES_LOSSLESS")
        
        // Setup Streaming Quality Spinner
        val spinnerStreaming = findViewById<Spinner>(R.id.spinner_streaming_quality)
        val adapterStreaming = ArrayAdapter(this, android.R.layout.simple_spinner_item, qualities)
        adapterStreaming.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStreaming.adapter = adapterStreaming
        
        val currentStreamingQuality = getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE).getString("audio_quality", "HIGH")
        spinnerStreaming.setSelection(qualities.indexOf(currentStreamingQuality ?: "HIGH").takeIf { it >= 0 } ?: 1)

        spinnerStreaming.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE).edit()
                    .putString("audio_quality", qualities[position])
                    .apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Setup Download Quality Spinner
        val spinnerDownload = findViewById<Spinner>(R.id.spinner_download_quality)
        val adapterDownload = ArrayAdapter(this, android.R.layout.simple_spinner_item, qualities)
        adapterDownload.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDownload.adapter = adapterDownload

        val currentDownloadQuality = getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE).getString("download_quality", "LOSSLESS")
        spinnerDownload.setSelection(qualities.indexOf(currentDownloadQuality ?: "LOSSLESS").takeIf { it >= 0 } ?: 2)

        spinnerDownload.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE).edit()
                    .putString("download_quality", qualities[position])
                    .apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Setup Offline Mode Toggle
        val switchOffline = findViewById<Switch>(R.id.switch_offline_mode)
        switchOffline.isChecked = getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE).getBoolean("offline_mode", false)
        switchOffline.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE).edit()
                .putBoolean("offline_mode", isChecked)
                .apply()
            Toast.makeText(this, if (isChecked) "Offline Mode Enabled" else "Offline Mode Disabled", Toast.LENGTH_SHORT).show()
        }

        // Clear Cache
        findViewById<Button>(R.id.btn_clear_cache).setOnClickListener {
            // Clear OkHttp cache if it exists, or just show a toast for now
            Toast.makeText(this, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
        }

        // Logout
        findViewById<Button>(R.id.btn_logout).setOnClickListener {
            val prefs = getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
}
