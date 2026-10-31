package com.streambd.iptv.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.NetworkWifi
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The four top-level content tabs — ONE definition for phone bottom bar,
 * tablet rail and TV drawer (they used to be duplicated in MainScreen and
 * TvMainScreen).
 *
 * [ordinal] == the old tab index (0..3), so the saved `selectedTab`
 * value keeps working.  [key] is the analytics feature name and MUST NOT
 * change (it feeds existing reports).
 */
enum class MainTab(
    val key: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Events("events", "Events", Icons.Filled.Event, Icons.Outlined.Event),
    LiveTv("live_tv", "Live TV", Icons.Filled.LiveTv, Icons.Outlined.LiveTv),
    Media("media", "Media", Icons.Filled.MovieFilter, Icons.Outlined.MovieFilter),
    Stream("stream", "Stream", Icons.Filled.NetworkWifi, Icons.Outlined.NetworkWifi);

    companion object {
        fun fromIndex(index: Int): MainTab = entries.getOrElse(index) { Events }
    }
}

/** Grouping used by the TV drawer's section labels. */
enum class MenuSection { Library, Settings, About, Session }

/**
 * Non-tab entries (open a screen, run an action, show a dialog).
 * Dynamic text (e.g. "TV Mode: ON") is built by the shell at runtime;
 * [label] is the default.
 */
enum class AppMenuItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val section: MenuSection,
    val danger: Boolean = false
) {
    Playlists("playlists", "Playlists", Icons.Outlined.Folder, MenuSection.Library),
    CheckUpdate("check_update", "Check Update", Icons.Filled.Refresh, MenuSection.Settings),
    More("more", "More", Icons.Outlined.MoreVert, MenuSection.Settings),
    TvMode("tv_mode", "TV Mode", Icons.Outlined.LiveTv, MenuSection.Settings),
    Notice("notice", "Notice", Icons.Outlined.Notifications, MenuSection.About),
    JoinUs("join_us", "Join Us", Icons.Outlined.Group, MenuSection.About),
    Copyright("copyright", "Copyright", Icons.Outlined.Copyright, MenuSection.About),
    Share("share", "Share Our App", Icons.Outlined.Share, MenuSection.About),
    Exit("exit", "Exit App", Icons.AutoMirrored.Outlined.ExitToApp, MenuSection.Session, danger = true);

    companion object {
        /** Phone side drawer — same items & order as today. */
        val PhoneDrawer = listOf(TvMode, Playlists, Notice, JoinUs, Copyright, Share, CheckUpdate, Exit)

        /** "More Options" dialog — same items & order as today. */
        val MoreDialog = listOf(TvMode, Notice, JoinUs, Copyright, Share)

        /** TV drawer (below the four tabs) — same items & order as today. */
        val TvDrawer = listOf(Playlists, CheckUpdate, More, Exit)
    }
}
