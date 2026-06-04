package com.streambd.iptv.utils

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

object RemoteConfigManager {
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    // Default values matching the current hardcoded strings
    private const val DEFAULT_TMDB_API_KEY = "460327acf6e0235a391222cb530de9c8"
    private const val DEFAULT_IPTV_PLAYLIST_URL = "https://streambd-iptv.netlify.app/playlists/IPTV.json"
    private const val DEFAULT_ADDON_REGISTRY_URL = "https://streambd-iptv.netlify.app/addons/StreamBD_IPTV_Addon.json"

    // Remote Config Keys
    private const val KEY_TMDB_API_KEY = "tmdb_api_key"
    private const val KEY_IPTV_PLAYLIST_URL = "iptv_playlist_url"
    private const val KEY_ADDON_REGISTRY_URL = "addon_registry_url"

    fun init() {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600) // Fetch once an hour in production
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Set default values so the app always works even before fetching
        val defaults = mapOf(
            KEY_TMDB_API_KEY to DEFAULT_TMDB_API_KEY,
            KEY_IPTV_PLAYLIST_URL to DEFAULT_IPTV_PLAYLIST_URL,
            KEY_ADDON_REGISTRY_URL to DEFAULT_ADDON_REGISTRY_URL
        )
        remoteConfig.setDefaultsAsync(defaults)

        // Fetch and activate the latest values from Firebase
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    android.util.Log.d("RemoteConfig", "Config params updated: $updated")
                } else {
                    android.util.Log.e("RemoteConfig", "Fetch failed")
                }
            }
    }

    fun getTmdbApiKey(): String {
        val value = remoteConfig.getString(KEY_TMDB_API_KEY).replace("\\s".toRegex(), "")
        return value.ifEmpty { DEFAULT_TMDB_API_KEY }
    }
    
    fun getIptvPlaylistUrl(): String {
        val value = remoteConfig.getString(KEY_IPTV_PLAYLIST_URL).replace("\\s".toRegex(), "")
        return value.ifEmpty { DEFAULT_IPTV_PLAYLIST_URL }
    }
    
    fun getAddonRegistryUrl(): String {
        val value = remoteConfig.getString(KEY_ADDON_REGISTRY_URL).replace("\\s".toRegex(), "")
        return value.ifEmpty { DEFAULT_ADDON_REGISTRY_URL }
    }
}
