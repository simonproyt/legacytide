package com.simonproyt.legacytide.api

import android.content.Context
import com.simonproyt.legacytide.api.models.LinkLogin
import com.simonproyt.legacytide.api.models.OAuthTokenResponse
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.conscrypt.Conscrypt
import java.io.IOException
import java.security.Security

class Session(val context: Context? = null, val config: Config = Config()) {
    var client: OkHttpClient

    init {
        // Install Conscrypt to provide modern TLS (1.2/1.3) and ciphers (GCM) on API 18
        if (Security.getProvider("Conscrypt") == null) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }
        
        client = OkHttpClient.Builder()
            .authenticator(object : Authenticator {
                override fun authenticate(route: Route?, response: Response): Request? {
                    if (response.request().header("Authorization") != null && response.priorResponse() != null) {
                        return null // Already retried
                    }
                    
                    synchronized(this@Session) {
                        // Check if token was refreshed by another thread while waiting
                        val currentHeaderToken = response.request().header("Authorization")?.removePrefix("Bearer ")
                        if (currentHeaderToken != null && currentHeaderToken != accessToken) {
                            // Token already refreshed, just retry with the new one
                            return response.request().newBuilder()
                                .header("Authorization", "Bearer $accessToken")
                                .build()
                        }
                        
                        val newToken = refreshToken() ?: return null
                        
                        return response.request().newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                    }
                }
            })
            .build()
    }

    private val gson = Gson()

    var accessToken: String? = null
    var refreshToken: String? = null
    var sessionId: String? = null
    var userId: Long? = null
    var countryCode: String? = null

    fun getLinkLogin(): LinkLogin {
        val formBody = FormBody.Builder()
            .add("client_id", config.clientId)
            .add("scope", "r_usr w_usr w_sub")
            .build()

        val request = Request.Builder()
            .url("https://auth.tidal.com/v1/oauth2/device_authorization")
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val responseBody = response.body()?.string() ?: throw IOException("Empty body")
            return gson.fromJson(responseBody, LinkLogin::class.java)
        }
    }

    fun checkLinkLogin(linkLogin: LinkLogin): OAuthTokenResponse? {
        val formBody = FormBody.Builder()
            .add("client_id", config.clientId)
            .add("client_secret", config.clientSecret)
            .add("device_code", linkLogin.deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("scope", "r_usr w_usr w_sub")
            .build()

        val request = Request.Builder()
            .url(config.apiOauth2Token)
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body()?.string() ?: return null
            val tokenResponse = gson.fromJson(responseBody, OAuthTokenResponse::class.java)

            if (response.isSuccessful) {
                this.accessToken = tokenResponse.accessToken
                this.refreshToken = tokenResponse.refreshToken
                
                // Fetch user session data
                fetchSessionData()
                return tokenResponse
            } else {
                if (tokenResponse.error == "authorization_pending") {
                    return null // User hasn't authorized yet
                }
                if (tokenResponse.error == "expired_token") {
                    throw IOException("Token expired")
                }
                throw IOException("Auth failed: ${tokenResponse.errorDescription}")
            }
        }
    }

    private fun fetchSessionData() {
        val request = Request.Builder()
            .url("${config.apiV1Location}sessions")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            // Parse basic session response manually or with gson
            val body = response.body()?.string() ?: return
            // Example response: {"sessionId":"...", "userId":1234, "countryCode":"US"}
            // Skipping full model mapping to keep it simple, doing a quick parse:
            // (Using simple string manipulation or Map via Gson since it's a simple flat JSON)
            val map = gson.fromJson(body, Map::class.java)
            sessionId = map["sessionId"] as? String
            countryCode = map["countryCode"] as? String
            userId = (map["userId"] as? Double)?.toLong()
        }
    }

    fun refreshToken(): String? {
        val currentRefreshToken = this.refreshToken ?: context?.getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE)?.getString("REFRESH_TOKEN", null)
        
        if (currentRefreshToken == null) return null
        
        val formBody = FormBody.Builder()
            .add("client_id", config.clientId)
            .add("client_secret", config.clientSecret)
            .add("refresh_token", currentRefreshToken)
            .add("grant_type", "refresh_token")
            .add("scope", "r_usr w_usr w_sub")
            .build()

        val request = Request.Builder()
            .url(config.apiOauth2Token)
            .post(formBody)
            .build()
            
        // Use a new client without the authenticator to avoid infinite loops during refresh
        val tempClient = OkHttpClient.Builder().build()

        try {
            tempClient.newCall(request).execute().use { response ->
                val responseBody = response.body()?.string() ?: return null
                if (!response.isSuccessful) {
                    return null
                }
                
                val tokenResponse = gson.fromJson(responseBody, OAuthTokenResponse::class.java)
                this.accessToken = tokenResponse.accessToken
                if (tokenResponse.refreshToken != null) {
                    this.refreshToken = tokenResponse.refreshToken
                }
                
                context?.let { ctx ->
                    val prefs = ctx.getSharedPreferences("LegacyTidePrefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("ACCESS_TOKEN", this.accessToken)
                        .putString("REFRESH_TOKEN", this.refreshToken)
                        .apply()
                }
                
                return this.accessToken
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
