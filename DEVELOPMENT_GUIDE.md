# StreamBD IPTV - Development Guide

This document explains the architecture, technology stack, and implementation details of the **StreamBD IPTV** application. It serves as a guide for developers or AI assistants to understand the codebase and continue development.

## 🎯 Project Overview
StreamBD IPTV is a unified streaming application designed for both **Android Mobile** and **Android TV / TV Box**. It features two main components:
1. **Live TV**: Parses an external M3U JSON playlist and streams HLS/TS links.
2. **Video on Demand (VOD)**: Uses an **Addon-based architecture** where external JavaScript files extract stream links dynamically.

## 🛠 Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Pure Native, no WebView or React Native)
- **Video Player**: `androidx.media3:media3-exoplayer` (Jetpack Media3)
- **Javascript Engine**: `app.cash.quickjs:quickjs-android`
- **Network**: `OkHttp3`
- **Serialization**: `kotlinx.serialization`
- **Navigation**: `androidx.navigation:navigation-compose`
- **Image Loading**: Coil (`io.coil-kt:coil-compose`)
- **Build System**: Gradle 8.4 (downgraded from 9.x for AGP stability)

---

## 🏗 Architecture & Code Structure

### 1. User Interface (UI) & TV Compatibility
The UI is fully built with **Jetpack Compose**. To support both touch (Mobile) and DPad/Remote (Android TV), we utilized:
- `Modifier.onFocusChanged`: To detect when a TV remote focuses on an element.
- `Modifier.border`: Focus indicators are drawn as a `TealAccent` border dynamically based on focus state.
- `TvLazyVerticalGrid` (or standard `LazyVerticalGrid` with focus wrappers) for grid layouts.

**Important UI Screens (`ui/screens`):**
- `MainScreen.kt`: The entry point Scaffold with a Bottom Navigation Bar.
- `LiveTvScreen.kt` & `LiveCategoryChannelsScreen.kt`: Displays IPTV categories and their respective channels.
- `VodScreen.kt`: A rich TMDB Dashboard showing Trending Movies, Trending TV Shows, and a Search bar.
- `TmdbDetailsScreen.kt`: Displays media metadata (Poster, Synopsis) and fetches streams dynamically when the user clicks "Watch Now".

### 2. Video Player Implementations
Because Live TV and VOD have completely different user experience requirements on TV, two separate player screens were created:

#### `LivePlayerScreen.kt`
- Uses `PlayerView` but hides default controllers (`useController = false`).
- A custom semi-transparent overlay slides in from the top with the Channel Logo and Name.
- The overlay auto-hides after 5 seconds and can be toggled by pressing the DPad Center (OK) button on a remote.
- Includes advanced features:
  - **Native System PiP (Picture-in-Picture)** via Android's `enterPictureInPictureMode`.
  - **Aspect Ratio Toggling** (Stretch, Fit, Zoom) using ExoPlayer's `AspectRatioFrameLayout`.
  - **Quality/Track Selection Dialog** using `DefaultTrackSelector`.
  - **Mute/Unmute** volume control.

#### `VodPlayerScreen.kt`
- Includes a custom Compose-based Seekbar (`Slider`).
- Provides large Rewind (-10s), Play/Pause, and Fast-Forward (+10s) buttons.
- These buttons are fully DPad-navigable using the focus system.

### 3. VOD Addon System (The Core Engine)
Instead of hardcoding stream scrapers natively, the app runs external JavaScript files using **QuickJS** (`utils/AddonManager.kt`).

**How it works:**
1. `AddonManager` fetches `StreamBD_IPTV_Addon.json` which lists available addon `.js` files.
2. When the user tries to watch a movie (e.g., Deadpool TMDB: `293660`), `AddonManager.resolveFromAddons` injects the Javascript code into the `QuickJS` engine.
3. The engine requires network requests (Fetch API) and cryptography (CryptoJS). 
   - A custom `AndroidBridge` interface polyfills the `fetch()` API synchronously using OkHttp.
   - `crypto-js.min.js` (stored in `assets/`) is injected into the QuickJS environment before execution.
4. The addon script executes and returns a JSON array of `StreamSource` objects which the native Kotlin app then plays via ExoPlayer.

---

## 🚀 How to build / run
1. Open the project in **Android Studio**.
2. Sync Gradle (Make sure you are using JDK 17).
3. Run `assembleDebug` or click the "Play" button to install the APK on an emulator or physical device.
4. For TV testing, create an Android TV emulator in the AVD Manager.

## 📝 Next Steps for Future Development
- **Favorites System**: Add a local Room Database to save favorite Live Channels or TMDB media.
- **Subtitles**: Utilize `MediaItem.SubtitleConfiguration` to pass parsed VOD subtitles to ExoPlayer.
