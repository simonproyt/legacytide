package com.simonproyt.legacytide

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.simonproyt.legacytide.api.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private var session: Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE)
        val savedToken = prefs.getString("ACCESS_TOKEN", null)
        val savedUserId = prefs.getLong("USER_ID", -1L)
        val savedCountry = prefs.getString("COUNTRY_CODE", null)
        
        if (savedToken != null && savedUserId != -1L) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("ACCESS_TOKEN", savedToken)
                putExtra("USER_ID", savedUserId)
                putExtra("COUNTRY_CODE", savedCountry)
            }
            startActivity(intent)
            finish()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val statusText = TextView(this).apply {
            text = "Initializing Login..."
            textSize = 18f
        }

        val openBrowserButton = Button(this).apply {
            text = "Open Browser to Login"
            isEnabled = false
        }

        layout.addView(statusText)
        layout.addView(openBrowserButton)
        setContentView(layout)

        try {
            session = Session()
        } catch (e: Throwable) {
            statusText.text = "Initialization Error:\n${e.javaClass.name}: ${e.message}"
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val linkLogin = session!!.getLinkLogin()
                
                withContext(Dispatchers.Main) {
                    statusText.text = "Please go to:\n${linkLogin.verificationUri}\n\nAnd enter code:\n${linkLogin.userCode}"
                    openBrowserButton.text = "Waiting for login..."
                    openBrowserButton.isEnabled = false
                }

                // Poll for authorization completion
                var expiry = linkLogin.expiresIn
                while (expiry > 0) {
                    val authResponse = session!!.checkLinkLogin(linkLogin)
                    if (authResponse != null) {
                        // Login successful
                        val prefs = getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("ACCESS_TOKEN", session!!.accessToken)
                            .putLong("USER_ID", session!!.userId ?: -1L)
                            .putString("COUNTRY_CODE", session!!.countryCode)
                            .apply()

                        withContext(Dispatchers.Main) {
                            statusText.text = "Login successful!"
                            val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                                putExtra("ACCESS_TOKEN", session!!.accessToken)
                                putExtra("USER_ID", session!!.userId)
                                putExtra("COUNTRY_CODE", session!!.countryCode)
                            }
                            startActivity(intent)
                            finish()
                        }
                        return@launch
                    }
                    delay(linkLogin.interval * 1000L)
                    expiry -= linkLogin.interval
                }

                withContext(Dispatchers.Main) {
                    statusText.text = "Login timed out. Please restart the app."
                    openBrowserButton.isEnabled = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Login error: ${e.message}"
                }
            }
        }
    }
}
