# StreamBD IPTV Playlist Documentation

This document outlines the exact formats and properties supported by **StreamBD IPTV** for parsing **M3U** and **JSON** playlists.

The application uses a custom parser (`M3UParser`) that has advanced support for passing custom headers, DRM keys, token resolution parameters, and even multiple sources for a single channel.

---

## 1. M3U Playlist Format

The M3U parser supports standard `#EXTINF` metadata along with `#KODIPROP` and `#EXTVLCOPT` for advanced configurations.

### Basic Channel
```m3u
#EXTM3U
#EXTINF:-1 tvg-logo="https://example.com/logo.png" group-title="Sports",My Sports Channel
https://example.com/stream.m3u8
```

### Passing Headers (User-Agent, Referer, Origin)
You can pass custom HTTP headers directly using the VLC standard or the pipe (`|`) syntax appended to the stream URL.

**Method 1: Pipe Syntax (Recommended)**
```m3u
#EXTINF:-1 group-title="Sports",Channel 1
https://example.com/stream.m3u8|User-Agent=Mozilla/5.0|Referer=https://example.com/
```

**Method 2: VLC Options**
```m3u
#EXTINF:-1 group-title="Sports",Channel 1
#EXTVLCOPT:http-user-agent=Mozilla/5.0
#EXTVLCOPT:http-referrer=https://example.com/
https://example.com/stream.m3u8
```

### DRM Configurations (ClearKey, Widevine, PlayReady)

**ClearKey (KID:KEY)**
```m3u
#EXTINF:-1 group-title="Sports",TSN 1
#KODIPROP:inputstream.adaptive.license_type=clearkey
#KODIPROP:inputstream.adaptive.license_key=e51aa21f2a0fef9aabc120dfb655b52f:a12a987fe725a40b6be95cd84b15f689
https://example.com/cenc.mpd
```

**Widevine (License Server)**
```m3u
#EXTINF:-1 group-title="Movies",HBO
#KODIPROP:inputstream.adaptive.license_type=widevine
#KODIPROP:inputstream.adaptive.license_key=https://license.server.com/get_key
https://example.com/stream.mpd
```

**Alternative Method: Pipe Syntax for DRM**
```m3u
#EXTINF:-1 group-title="Sports",Channel 1
https://example.com/stream.mpd|drm-type=clearkey|drm-key=e51aa:a12a
```

### Advanced Token Resolution (WebView Sniffer)
If a stream requires the app to execute Javascript or bypass protections (like Cloudflare) using an invisible WebView, you can use token parameters:

```m3u
#EXTINF:-1 group-title="Sports",Live Event
http://example.com/play.php?id=101|tokenUrl=fetchiframe|tokenMatch=embed.html|tokenReplace=index.m3u8
```
- `tokenUrl`: URL keyword to trigger the sniffer. (e.g., `if` or `fetchiframe`)
- `tokenMatch`: A substring in the sniffed URL that confirms it's the correct media link.
- `tokenReplace`: Replaces the matched substring to format the final `.m3u8` link.

---

## 2. JSON Playlist Format

The app can also import channels from a JSON array. This is especially useful for organizing multiple sources under a single channel.

### JSON Structure (Single Source & Multi-Source)

```json
[
    {
        "name": "Germany vs Curacao",
        "image": "https://cdn.example.com/logos/worldcup.png",
        "group-title": "FIFA World Cup",
        "url": "https://example.com/default.m3u8",
        "sources": {
            "ITV 1": "https://cdn8.example.com/hls/do1.m3u8|Referer=https://swiftlogicpath.com|User-Agent=Mozilla/5.0",
            "ITV 1 S2": "https://bundlecz.click/e/w0jho3|Referer=https://bundlecz.click",
            "SBS": "https://cdn8.example.com/hls/do9.m3u8|Referer=https://swiftlogicpath.com"
        }
    }
]
```
> **Note:** If `sources` is provided, the app will generate a "Multi-Source" channel where the user can pick a stream from the player settings. The `url` property acts as the fallback or primary URL.

### Passing Headers in JSON
Headers can be passed globally via JSON keys or attached directly to individual stream URLs using the pipe (`|`) syntax.

**Global Headers for a Channel:**
```json
{
    "name": "Sports Channel",
    "url": "https://example.com/stream.m3u8",
    "userAgent": "Mozilla/5.0",
    "referer": "https://example.com",
    "cookie": "session_id=123"
}
```

### DRM in JSON Playlists
Because the JSON parser relies on the `sources` mapping to switch streams dynamically, **DRM must be appended directly to the stream URL using the pipe (`|`) syntax.** 

This ensures that when a user switches from "Source 1" to "Source 2", the player correctly receives the distinct DRM key for the selected source.

**Example with DRM (ClearKey & Widevine):**
```json
[
    {
        "name": "FIFA Matches DRM",
        "image": "https://cdn.example.com/logos/worldcup.png",
        "group-title": "FIFA World Cup",
        "sources": {
            "TSN 1 (ClearKey)": "https://example.com/cenc.mpd|drm-type=clearkey|drm-key=e51aa21f2a0fef9aabc120dfb655b52f:a12a987fe725a40b6be95cd84b15f689",
            "Telemundo (ClearKey)": "https://live-oneapp.akamaized.net/master.mpd|drm-type=clearkey|drm-key=ce7ab3022e753307997f58afe001bac4:72d631a66e635c60829a0fe7705516c1",
            "HBO (Widevine)": "https://example.com/widevine.mpd|drm-type=widevine|drm-url=https://license.server.com/get_key"
        }
    }
]
```

### Supported JSON Keys
- **Title Properties:** `name`, `title`
- **Logo Properties:** `tvg-logo`, `logo`, `image`, `tvgLogo`
- **Group Properties:** `group-title`, `group`, `category`, `groupTitle`
- **URL/Source Properties:** `url`, `link`, `sources` (Map/Dictionary of strings)
- **Header Properties:** `userAgent`, `user-agent`, `user_agent`, `referer`, `referrer`, `origin`, `cookie`
- **Header Dictionary:** `headers` (e.g., `"headers": {"User-Agent": "Mozilla/5.0"}`)
- **Token Properties:** `tokenUrl`, `tokenMatch`, `tokenReplace`, `tokenId`
