# StreamBD IPTV - Development Guide

This document explains the architecture, technology stack, and implementation details of the **StreamBD IPTV** application. It serves as a guide for developers or AI assistants to understand the codebase and continue development.

## 🎯 Project Overview
StreamBD IPTV is a unified streaming application designed for both **Android Mobile** and **Android TV / TV Box**. It features two main components:
1. **Live TV**: Parses complex M3U/PLS/JSON playlists. Features a robust parsing engine (`M3UParser`, `TokenParser`, `JSDecoder`) supporting Prysm formats, `#EXTVLCOPT` headers, `#KODIPROP` DRM (Widevine, ClearKey), and dynamic stream resolution (InfinityFree, CricHD bypass).
2. **Video on Demand (VOD) & Series**: Integrates with TMDB API for rich metadata (posters, genres, cast, backdrops, seasons/episodes) and uses an **Addon-based architecture** where external JavaScript files extract stream links dynamically.

## 🛠 Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Pure Native, no WebView or React Native)
- **Video Player**: `androidx.media3:media3-exoplayer` (Jetpack Media3)
- **Javascript Engine**: `app.cash.quickjs:quickjs-android`
- **Network**: `OkHttp3`, `Retrofit2`
- **Serialization**: `kotlinx.serialization` (JSON)
- **Navigation**: `androidx.navigation:navigation-compose`
- **Image Loading**: Coil (`io.coil-kt:coil-compose`) with crossfade and error placeholders
- **Analytics/Crash Reporting**: Firebase Analytics & Crashlytics
- **Build System**: Gradle 8.4

---

## 🏗 Architecture & Code Structure

### 1. User Interface (UI) & TV Compatibility
The UI is fully built with **Jetpack Compose**. To support both touch (Mobile) and DPad/Remote (Android TV), we utilized:
- `Modifier.onFocusChanged`: To detect when a TV remote focuses on an element.
- `Modifier.tvFocusBorder()`: A custom modifier (in `ui/components/TvComponents.kt`) that draws a `TealAccent` border dynamically based on focus state.
- **TV Detection Engine**: Generic TV Boxes (like TX3 Mini) often do not declare `UI_MODE_TYPE_TELEVISION`. We use a custom detection system in `MainScreen` that checks `PackageManager.FEATURE_TOUCHSCREEN` and `PackageManager.FEATURE_LEANBACK` to reliably render the TV interface.
- **Overlays & Modals**: For Android TV, standard `ModalBottomSheet` traps D-Pad focus. We use standard `androidx.compose.ui.window.Dialog` instead to ensure native remote control focus behavior.
- **Premium Aesthetics**: The Mobile `NavigationBar` features rounded top corners and a subtle shadow, while the TV `NavigationRail` uses a floating-dock style with semi-transparent glass effects and glowing active states.

**Important UI Screens:**
- `ui/screens/main/MainScreen.kt`: The entry point Scaffold with the adaptive premium Navigation Bar/Rail and Mobile Side Navigation Drawer.
- `ui/screens/live/`: Contains `LiveTvScreen.kt`, `LiveCategoryChannelsScreen.kt`, and `LivePlayerScreen.kt`.
- `ui/screens/vod/`: Contains `VodScreen.kt` (Dashboard), `TmdbDetailsScreen.kt` (Rich UI for Movies/Series), and `VodPlayerScreen.kt`.
- `ui/components/`: Shared global components like `TvIconButton.kt`, `TvComponents.kt`, and `TrackSelectionDialog.kt`.

### 2. Video Player Implementations
Because Live TV and VOD have completely different user experience requirements on TV, two separate player screens were created:

#### `LivePlayerScreen.kt`
- Uses `PlayerView` but hides default controllers (`useController = false`).
- A custom semi-transparent overlay slides in from the top with the Channel Logo and Name.
- The overlay auto-hides after 5 seconds and can be toggled by pressing the DPad Center (OK) button on a remote.
- Includes advanced features:
  - **Native System PiP (Picture-in-Picture)** via Android's `enterPictureInPictureMode`.
  - **Aspect Ratio Toggling** (Stretch, Fit, Zoom) using ExoPlayer's `AspectRatioFrameLayout`. Defaults to `FIT` to prevent video cropping.
  - **Quality/Track Selection Dialog** using `DefaultTrackSelector`.
  - **Mute/Unmute** volume control.
  - **Dynamic Stream Resolution & Bypasses**: Uses `TokenParser` to resolve tokens (`if`, `crichd`, `fetchiframe`, `/1`) and decode JS before playback.
  - **DRM Support**: Automatically configures ExoPlayer with `ClearKey`, `Widevine`, or `PlayReady` using native DRM builders based on playlist properties.

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

### 4. Auto-Update via GitHub Releases
To bypass the need for Google Play Store updates, the app includes a self-updating mechanism:
- Uses the `UpdateManager` (inside `utils/UpdateManager.kt`) to query the GitHub API (`https://api.github.com/repos/myselfhridoy/StreamBD-IPTV-Release/releases/latest`).
- Compares the latest tag version with `BuildConfig.VERSION_NAME`.
- If a new version exists, an `UpdateDialog` is shown, prompting the user to download the APK.
- The update check runs automatically on app launch and can also be triggered manually via the **Check Update** button in the Mobile Side Drawer.

### 5. Writing JS Playlists (Prompt/Instructions)
When creating or updating JS Playlists for the app, you can use the following standard instruction block as a prompt for AI or for yourself:

> You are an expert JavaScript developer writing a JSON-to-M3U parser script for an Android IPTV app (StreamBD). The app executes your JavaScript using a WebView, captures the generated M3U string, and passes it to ExoPlayer.
> 
> Your task is to write a script that fetches a JSON API and converts it into M3U format, adhering STRICTLY to the following custom rules required by the app's internal `M3UParser.kt`:
> 
> #### 1. Basic Structure
> - Use `fetch()` to get the JSON data.
> - Start the M3U string with `#EXTM3U\n`.
> - For each channel, use `#EXTINF:-1 tvg-logo="LOGO_URL" group-title="CATEGORY",CHANNEL_NAME\n`.
> 
> #### 2. DRM Handling (Important)
> If a channel has DRM (like ClearKey), use Kodi properties.
> - For ClearKey (kid:key pairs):
>   ```m3u
>   #KODIPROP:inputstream.adaptive.license_type=clearkey
>   #KODIPROP:inputstream.adaptive.license_key=YOUR_KID:YOUR_KEY
>   ```
> - For Widevine (License URL):
>   ```m3u
>   #KODIPROP:inputstream.adaptive.license_type=widevine
>   #KODIPROP:inputstream.adaptive.license_key=LICENSE_SERVER_URL
>   ```
> 
> #### 3. HTTP Headers (CRITICAL)
> You must attach HTTP headers to the stream URL using a Pipe `|` delimiter.
> - Extract `token`, `userAgent`, `referer`, `origin`, and any custom headers from the JSON.
> - If the token is meant to be a Cookie, prefix it with `Cookie=`.
> - If the stream is strictly protected by Cloudflare bot detection, you can optionally append standard browser headers to mimic a real browser:
>   - `Accept=text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7`
>   - `Accept-Language=en-US,en;q=0.9`
>   - `Sec-Fetch-Dest=document`
>   - `Sec-Fetch-Mode=navigate`
>   - `Sec-Fetch-Site=none`
> 
> **Pipe Delimiter Rules:**
> - DO NOT use `&` to join headers! You MUST join them with `|`.
> - DO NOT use `encodeURIComponent()` on header values! The app's parser does not decode them, so it expects exact raw strings.
> - Format: `STREAM_URL|Header1=Value1|Header2=Value2|Header3=Value3`
> 
> #### 4. Sending the Payload
> Send the final M3U string to the Android app using the `streamBD` bridge:
> ```javascript
> if (typeof streamBD !== 'undefined') {
>     streamBD.send(m3u);
> } else {
>     console.log(m3u); // Fallback for local testing
> }
> ```
> 
> #### Example Template
> ```javascript
> fetch('API_URL_HERE')
>   .then(res => res.json())
>   .then(data => {
>       let m3u = "#EXTM3U\n";
>       for (const ch of data) {
>           m3u += `#EXTINF:-1 tvg-logo="${ch.logo}" group-title="${ch.category}",${ch.name}\n`;
>           
>           if (ch.drm) {
>               // Add DRM KODIPROP logic here
>           }
>           
>           let headers = [];
>           if (ch.userAgent) headers.push(`User-Agent=${ch.userAgent}`);
>           if (ch.referer) headers.push(`Referer=${ch.referer}`);
>           // Only add extra Sec-Fetch headers if Cloudflare bypass is needed
>           
>           let finalUrl = ch.url;
>           if (headers.length > 0) {
>               finalUrl += "|" + headers.join("|");
>           }
>           m3u += `${finalUrl}\n`;
>       }
>       if (typeof streamBD !== 'undefined') streamBD.send(m3u);
>   });
> ```
>
---

## 🛠 Troubleshooting

### YouTube VOD & Live Stream Playback Failures
YouTube frequently updates its internal API and delivery mechanisms to prevent third-party clients from extracting media streams. If YouTube Live or VOD playback stops working and throws a `403 Forbidden` or `Playback Error`, follow these steps:

1. **Update NewPipeExtractor (Primary Solution):**
   The app uses `com.github.TeamNewPipe:NewPipeExtractor` to generate PO-Tokens and bypass YouTube's signatures.
   - Check the [NewPipeExtractor GitHub Releases](https://github.com/TeamNewPipe/NewPipeExtractor/releases) for a newer version.
   - If a new version exists (e.g., `v0.27.0`), update the dependency version in `app/build.gradle.kts`.
   - **Important Note:** Do *not* try to manually scrape `hlsManifestUrl` using regex in `YouTubeExtractor.kt`. While it may return a manifest that parses successfully, the internal video chunks will be blocked with `403 Forbidden` because they lack proper deciphering signatures. Rely entirely on `NewPipeExtractor`.

2. **Fallback to Public API Instances:**
   If `NewPipeExtractor` breaks and a fix is delayed, you can temporarily route requests to public open-source instances:
   - **Piped API**: `https://pipedapi.kavin.rocks/streams/VIDEO_ID`
   - **Invidious API**: Extract streams via public Invidious instances.

3. **Fallback to WebViewSniffer:**
   If strict bot-protection (like Cloudflare) is added by YouTube, utilize the existing `WebViewSniffer` implementation (currently used for `streambd` links) to silently load the YouTube page in the background and hijack the media stream directly from the browser context.

---

## 🚀 How to build / run
1. Open the project in **Android Studio**.
2. Sync Gradle (Make sure you are using JDK 17).
3. Ensure you have `google-services.json` in the `app/` directory (Required for Firebase Analytics/Crashlytics).
4. Run `.\gradlew assembleDebug` or click the "Play" button to install the APK on an emulator or physical device.
5. For TV testing, create an Android TV emulator in the AVD Manager.

## 📝 Next Steps for Future Development
- **Favorites System**: Add a local Room Database to save favorite Live Channels or TMDB media.
- **Subtitles**: Utilize `MediaItem.SubtitleConfiguration` to pass parsed VOD subtitles to ExoPlayer.
