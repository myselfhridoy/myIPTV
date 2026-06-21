package com.streambd.iptv.utils

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

object RemoteConfigManager {
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    // Default values matching the current hardcoded strings
    private const val DEFAULT_TMDB_API_KEY = "460327acf6e0235a391222cb530de9c8"
    private const val DEFAULT_IPTV_PLAYLIST_URL = "VJTWEO8X/OvQrNObPPdfY0dPDtTdp02ZA5vuwudM08AtAJ7sLVYJpO2b5rDcbDsW31PcjyDTNXmvN89i5FMs11dWOWXh+U2J19vcgczuI20="
    private const val DEFAULT_ADDON_REGISTRY_URL = "VJTWEO8X/OvQrNObPPdfY0dPDtTdp02ZA5vuwudM08BQEQzu+2ffIAfnsrnQ6sviFcFvAuuNnPP93U7pk0FCUMelLRvlVHblOmdTVETlJpc="
    private const val DEFAULT_LIVE_EVENTS_API_URL = "VJTWEO8X/OvQrNObPPdfY0dPDtTdp02ZA5vuwudM08Cx9M76O0ljeP8W5U8OUHq+4l2nsmQOvUR0kccfoQivFiwecDnbPD2PNcNTQ3uOexQ="

    // Remote Config Keys
    private const val KEY_TMDB_API_KEY = "tmdb_api_key"
    private const val KEY_IPTV_PLAYLIST_URL = "iptv_playlist_url"
    private const val KEY_ADDON_REGISTRY_URL = "addon_registry_url"
    private const val KEY_LIVE_EVENTS_API_URL = "live_events_api_url"

    fun init() {
        val fetchInterval = if (com.streambd.iptv.BuildConfig.DEBUG) 0L else 3600L
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(fetchInterval) // 0 in debug, 1 hour in production
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Set default values so the app always works even before fetching
        val defaults = mapOf(
            KEY_TMDB_API_KEY to DEFAULT_TMDB_API_KEY,
            KEY_IPTV_PLAYLIST_URL to DEFAULT_IPTV_PLAYLIST_URL,
            KEY_ADDON_REGISTRY_URL to DEFAULT_ADDON_REGISTRY_URL,
            KEY_LIVE_EVENTS_API_URL to DEFAULT_LIVE_EVENTS_API_URL
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
        val encryptedStr = value.ifEmpty { DEFAULT_IPTV_PLAYLIST_URL }
        return CryptoUtils.decryptJson(encryptedStr)
    }
    
    fun getAddonRegistryUrl(): String {
        val value = remoteConfig.getString(KEY_ADDON_REGISTRY_URL).replace("\\s".toRegex(), "")
        val encryptedStr = value.ifEmpty { DEFAULT_ADDON_REGISTRY_URL }
        return CryptoUtils.decryptJson(encryptedStr)
    }
    
    fun getLiveEventsApiUrl(): String {
        val value = remoteConfig.getString(KEY_LIVE_EVENTS_API_URL).replace("\\s".toRegex(), "")
        val encryptedStr = value.ifEmpty { DEFAULT_LIVE_EVENTS_API_URL }
        return CryptoUtils.decryptJson(encryptedStr)
    }
}
