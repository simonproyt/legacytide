package com.simonproyt.legacytide

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo

object NetworkUtils {
    fun isOnline(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo
            activeNetwork?.isConnected == true
        } catch (e: Exception) {
            // If permission is somehow denied, assume false to trigger offline mode, or true?
            // Actually, if we assume false, the user will be permanently stuck in offline mode if permission is missing.
            // Let's assume true so we don't break the app if permission fails.
            true
        }
    }
}
