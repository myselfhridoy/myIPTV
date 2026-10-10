# Playlist Format Reference

This document describes every playlist format and header syntax the
`M3UParser` accepts. It is written so you can hand it to a playlist
provider and say "produce output in any of these formats".

---

## Table of Contents

1. [Basic M3U / M3U8](#1-basic-m3u--m3u8)
2. [Extended M3U attributes](#2-extended-m3u-attributes)
3. [Pipe headers](#3-pipe-headers)
4. [Ampersand headers](#4-ampersand-headers)
5. [Mixed pipe + ampersand](#5-mixed-pipe--ampersand)
6. [URL query strings vs headers](#6-url-query-strings-vs-headers)
7. [Standard HTTP headers](#7-standard-http-headers)
8. [Custom headers (pass-through)](#8-custom-headers-pass-through)
9. [DRM — ClearKey](#9-drm--clearkey)
10. [DRM — Widevine / PlayReady / FairPlay](#10-drm--widevine--playready--fairplay)
11. [Kodi #KODIPROP](#11-kodi-kodiprop)
12. [Kodi #EXTVLCOPT](#12-kodi-extvlcopt)
13. [Kodi #EXTHTTP JSON](#13-kodi-exthttp-json)
14. [Token-based URLs](#14-token-based-urls)
15. [PLS format](#15-pls-format)
16. [XSPF format](#16-xspf-format)
17. [JSON format](#17-json-format)
18. [Multi-source failover](#18-multi-source-failover)
19. [Complete real-world example](#19-complete-real-world-example)
20. [Compatibility matrix](#20-compatibility-matrix)

---

## 1. Basic M3U / M3U8

The simplest possible playlist. One URL per line, with an `#EXTM3U`
header and an optional `#EXTINF:` line before each entry.

```m3u
#EXTM3U

#EXTINF:-1,Star Sports HD
https://cdn.example.com/live/star-sports.m3u8

#EXTINF:-1,Sony Ten 1
https://cdn.example.com/live/sony-ten-1.m3u8

#EXTINF:-1,ESPN HD
https://cdn.example.com/live/espn.m3u8
```

**Parsed:**
- `name` — text after the comma in `#EXTINF:` (e.g. `Star Sports HD`)
- `url` — the URL on the next line
- `group` — empty (no `group-title` attribute)
- `logo` — empty

**UTF-8 BOM:** A leading `\uFEFF` byte-order mark is automatically
stripped.

---

## 2. Extended M3U attributes

Attribute syntax inside `#EXTINF:` line. Quotes are optional.

```m3u
#EXTM3U

#EXTINF:-1 tvg-id="star-sports.in" tvg-logo="https://logos.example.com/star.png" group-title="Sports",Star Sports HD
https://cdn.example.com/live/star-sports.m3u8

#EXTINF:-1 tvg-logo='https://logos.example.com/sony.png' group-title='Sports',Sony Ten 1
https://cdn.example.com/live/sony-ten-1.m3u8

#EXTINF:-1 tvg-logo=https://logos.example.com/espn.png group-title=Sports,ESPN
https://cdn.example.com/live/espn.m3u8
```

### Recognised attributes

| Attribute | Effect |
|-----------|--------|
| `tvg-logo="..."` | Channel logo URL (also accepted: `logo`, `icon`, `poster`) |
| `group-title="..."` | Channel group / category |
| `http-user-agent="..."` | User-Agent header for this stream |
| `http-referer="..."` | Referer header |
| `http-origin="..."` | Origin header |
| `http-cookie="..."` | Cookie header |
| `token-id=42` | Token identifier for the resolver |

### Alias note

Logo and group fields accept three quote styles:
- `attr="value"`
- `attr='value'`
- `attr=value` (unquoted, up to the next whitespace or comma)

---

## 3. Pipe headers

The classic Kodi / TiviMate / OTT Navigator syntax. Each header is
prefixed by `|` and uses `Key=Value`.

```m3u
#EXTM3U

#EXTINF:-1,Star Sports HD
https://cdn.example.com/live/star.m3u8|User-Agent=Mozilla/5.0|Referer=https://example.com/|Origin=https://example.com

#EXTINF:-1,Sony Ten 1
https://cdn.example.com/live/sony.m3u8|User-Agent=Mozilla/5.0|Cookie=session=abc123
```

### Split rules

- The first `|` that is followed by a header-shaped token begins the
  header section. Everything before it is the URL.
- Additional `|` characters are always treated as separators.
- Values may contain spaces, slashes, colons (`:`) — only `|` and
  unescaped `&` are separators.

### What counts as a "header-shaped token"

A pipe is treated as the header-section start only when the text after
it matches one of:

- `Key=value`
- `Key:value`
- `<32hex>:<32hex>` (raw ClearKey pair)
- `clearkey`, `widevine`, `playready`, `fairplay` (bare DRM markers)

So a URL like `https://host/path?x=a|b` is treated as a URL, not as a
header delimiter — because `b` isn't header-shaped.

---

## 4. Ampersand headers

Identical to pipe syntax, but uses `&` as the separator. This is the
format used by most providers that copy browser DevTools output.

```m3u
#EXTM3U

#EXTINF:-1,Colors HD
https://cdn.example.com/live/colors.mpd&User-Agent=Mozilla/5.0&Referer=https://provider.com/&Origin=https://provider.com
```

### Split rules

`&` is a separator **only when the following token is header-shaped**.
This means URLs with query strings (`?a=1&b=2`) stay intact when they
appear before the header section.

```
https://cdn.example.com/x.mpd?a=1&b=2&User-Agent=Mozilla/5.0
                                     ^
                                     this & starts headers because
                                     "User-Agent" is header-shaped
```

---

## 5. Mixed pipe + ampersand

Pipe and ampersand separators work together in any combination:

```m3u
#EXTM3U

#EXTINF:-1,Real-world sample
https://mini.example.com/jtv/index.mpd?__hdnea__=st=1~hmac=abc|Accept-Encoding=gzip, deflate&sec-fetch-site=cross-site&sec-fetch-mode=cors&Origin=https://provider.com&drmScheme=clearkey&drmLicense=5360e12d4bb2519a9148d1089a55908d:7e8f500cef51595f3d43acafab1a8fb4
```

**Parsed as:**

| Key | Value |
|-----|-------|
| `Accept-Encoding` | `gzip, deflate` |
| `sec-fetch-site` | `cross-site` |
| `sec-fetch-mode` | `cors` |
| `Origin` | `https://provider.com` |
| `drm-type` | `clearkey` (from `drmScheme`) |
| `drm-key` | `5360e12d4bb2519a9148d1089a55908d:7e8f500cef51595f3d43acafab1a8fb4` (from `drmLicense`) |

---

## 6. URL query strings vs headers

URL query parameters are **not** touched. Only the section that begins
with a header-shaped `|` or `&` is treated as headers.

### Safe — `?` query preserved

```m3u
#EXTINF:-1,Test
https://cdn.com/x.mpd?token=abc&id=42&type=live|User-Agent=Mozilla/5.0
```

The `?token=abc&id=42&type=live` part stays part of the URL. Only
`User-Agent` becomes a header.

### Why the `&` after `type=live` is not a separator

The parser checks the token following `&`. `User-Agent` is header-
shaped; `id`, `type` are not in the canonical list AND come right after
`?` (URL context), so they stay in the URL.

> **Rule of thumb:** the parser is conservative — it only splits when
> there's strong evidence the token is a header, not a query parameter.

---

## 7. Standard HTTP headers

These are the headers the parser stores in dedicated fields (they end
up on `Channel.userAgent`, `Channel.cookie`, `Channel.httpReferer`,
`Channel.origin`, or in `httpHeaders` for the rest).

| Header | Accepted aliases |
|--------|------------------|
| `User-Agent` | `user-agent`, `useragent`, `http-user-agent`, `ua` |
| `Referer` | `referer`, `referrer`, `http-referer`, `http-referrer`, `ref` |
| `Origin` | `origin`, `http-origin` |
| `Cookie` | `cookie`, `http-cookie` |
| `Authorization` | `authorization`, `auth`, `http-authorization` |

### Example

```m3u
#EXTINF:-1,Auth Stream
https://cdn.com/stream.m3u8|User-Agent=Mozilla/5.0|Referer=https://provider.com/|Origin=https://provider.com|Cookie=session=xyz|Authorization=Bearer abc123
```

---

## 8. Custom headers (pass-through)

Any header the parser doesn't recognise is stored verbatim with its
original casing and forwarded to OkHttp. This is how you support
provider-specific headers like `sec-fetch-*`, `X-Api-Key`, etc.

```m3u
#EXTINF:-1,Custom headers
https://cdn.com/x.m3u8|X-Api-Key=abc123|X-Device-Id=phone-42|sec-fetch-site=cross-site|sec-fetch-mode=cors
```

**All headers forwarded as-is:**

```
X-Api-Key       : abc123
X-Device-Id     : phone-42
sec-fetch-site  : cross-site
sec-fetch-mode  : cors
```

### Handy tip — separation rule

If your header value contains a `&` (e.g. a base64 string with padding
or a query param), use pipe separators around it:

```m3u
#EXTINF:-1,Test
https://cdn.com/x|X-Data=abc&def=ghi|User-Agent=Mozilla/5.0
```

The `&def=ghi` is safe because `def` isn't header-shaped — the parser
keeps it as part of `X-Data`'s value.

---

## 9. DRM — ClearKey

ClearKey is the open, license-free DRM scheme. Four equivalent
notations are supported.

### 9.1 Shorthand (Kodi / TiviMate style)

```m3u
#EXTINF:-1,ClearKey Shorthand
https://cdn.com/x.mpd|5360e12d4bb2519a9148d1089a55908d:7e8f500cef51595f3d43acafab1a8fb4|clearkey
```

### 9.2 Reversed shorthand

```m3u
#EXTINF:-1,ClearKey Reverse
https://cdn.com/x.mpd|clearkey|5360e12d4bb2519a9148d1089a55908d:7e8f500cef51595f3d43acafab1a8fb4
```

### 9.3 Explicit drmScheme / drmLicense

```m3u
#EXTINF:-1,Explicit
https://cdn.com/x.mpd|drmScheme=clearkey|drmLicense=5360e12d4bb2519a9148d1089a55908d:7e8f500cef51595f3d43acafab1a8fb4
```

### 9.4 Underscore / hyphen aliases

```m3u
#EXTINF:-1,Aliases 1
https://cdn.com/x.mpd|drm_type=clearkey|drm_key=5360e12d4bb2519a9148d1089a55908d:7e8f500cef51595f3d43acafab1a8fb4

#EXTINF:-1,Aliases 2
https://cdn.com/x.mpd|drm-type=clearkey|drm-key=5360e12d4bb2519a9148d1089a55908d:7e8f500cef51595f3d43acafab1a8fb4
```

### Accepted key aliases

| Canonical | Aliases |
|-----------|---------|
| `drm-type` | `drmScheme`, `drm-scheme`, `drm_scheme`, `drmtype`, `drm_type`, `license_type`, `license-type`, `licensetype` |
| `drm-key` | `drmLicense`, `drm-license`, `drm_license`, `drmkey`, `drm_key`, `license_key`, `license-key`, `licensekey` |
| `drm-url` | `drm_url`, `drmurl`, `drm-license-server` |

### Key format

The value must be `<32-hex-kid>:<32-hex-key>`:

```
5360e12d4bb2519a9148d1089a55908d : 7e8f500cef51595f3d43acafab1a8fb4
└── KID (16 bytes hex) ────────┘   └── key (16 bytes hex) ─────────┘
```

The parser automatically converts this to a W3C ClearKey JWK set and
hands it to ExoPlayer's `LocalMediaDrmCallback`.

---

## 10. DRM — Widevine / PlayReady / FairPlay

For license-server-based DRM, specify the license URL instead of a raw
key pair.

```m3u
#EXTINF:-1,Widevine
https://cdn.com/x.mpd|drmScheme=widevine|drmLicense=https://license.example.com/wv

#EXTINF:-1,PlayReady
https://cdn.com/x.mpd|drmScheme=playready|drmLicense=https://license.example.com/pr

#EXTINF:-1,FairPlay
https://cdn.com/x.mpd|drmScheme=fairplay|drmLicense=https://license.example.com/fp
```

Any request headers already set on the URL are forwarded to the license
server automatically (except `User-Agent`).

---

## 11. Kodi #KODIPROP

Kodi-style DRM declarations on their own lines. All three Kodi formats
are supported.

```m3u
#EXTM3U

#KODIPROP:inputstream.adaptive.license_type=clearkey
#KODIPROP:inputstream.adaptive.license_key=5360e12d4bb2519a9148d1089a55908d:7e8f500cef51595f3d43acafab1a8fb4
#EXTINF:-1,Kodi ClearKey
https://cdn.com/x.mpd

#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
#KODIPROP:inputstream.adaptive.license_key=https://license.example.com/wv
#EXTINF:-1,Kodi Widevine
https://cdn.com/y.mpd
```

### Supported license_type values

| Input | Parsed as |
|-------|-----------|
| `clearkey`, `org.w3.clearkey` | `clearkey` |
| `widevine`, `com.widevine.alpha` | `widevine` |
| `playready`, `com.microsoft.playready` | `playready` |
| `fairplay`, `com.apple.fps` | `fairplay` |

### Multi-key syntax (Kodi `#` separator)

```m3u
#KODIPROP:inputstream.adaptive.license_key=5360e12d4bb2519a9148d1089a55908d:7e8f500cef51595f3d43acafab1a8fb4#kid2:key2
```

The parser takes the first pair. Remaining keys are ignored (only one
KID is typically used per stream).

---

## 12. Kodi #EXTVLCOPT

Kodi / VLC per-stream options. Only the header-related directives are
recognised; everything else is treated as a custom header.

```m3u
#EXTM3U

#EXTVLCOPT:http-user-agent=Mozilla/5.0
#EXTVLCOPT:http-referrer=https://provider.com/
#EXTVLCOPT:http-origin=https://provider.com
#EXTVLCOPT:http-cookie=session=xyz
#EXTINF:-1,VLC options
https://cdn.com/x.m3u8
```

### Recognised directives

| Directive | Effect |
|-----------|--------|
| `http-user-agent` | Sets `Channel.userAgent` |
| `http-referrer` / `http-referer` | Sets `Channel.httpReferer` |
| `http-origin` | Sets `Channel.origin` |
| `cookie` / `http-cookie` | Sets `Channel.cookie` |

Anything else (e.g. `#EXTVLCOPT:network-caching=1000`) is added as a
custom header with the directive name as the header key.

---

## 13. Kodi #EXTHTTP JSON

The Kodi JSON form. Two layouts are accepted — with or without the
`headers` wrapper.

### Layout A — flat

```m3u
#EXTM3U

#EXTHTTP:{"User-Agent":"Mozilla/5.0","Referer":"https://provider.com/","X-Api-Key":"abc123"}
#EXTINF:-1,Flat JSON
https://cdn.com/x.m3u8
```

### Layout B — wrapped

```m3u
#EXTHTTP:{"headers":{"User-Agent":"Mozilla/5.0","Referer":"https://provider.com/","X-Api-Key":"abc123"}}
#EXTINF:-1,Wrapped JSON
https://cdn.com/y.m3u8
```

### Special handling

| JSON key | Destination |
|----------|-------------|
| `User-Agent` | `Channel.userAgent` |
| `Referer` / `Referrer` | `Channel.httpReferer` |
| `Origin` | `Channel.origin` |
| `Cookie` | `Channel.cookie` |
| `Authorization` | `httpHeaders["Authorization"]` |
| Everything else | `httpHeaders[...]` with original casing |

---

## 14. Token-based URLs

For providers that require a page-scrape or WebView resolver, add
`tokenUrl` and related parameters to the URL.

```m3u
#EXTM3U

#EXTINF:-1,Token stream
https://provider.com/page?id=42|tokenUrl=streambd|tokenId=42|tokenMatch=old|tokenReplace=new
```

### Token parameters

| Key | Aliases | Purpose |
|-----|---------|---------|
| `tokenUrl` | `token-url`, `token_url` | Resolver type: `streambd`, `streamplay`, `crichd`, `dlhd`, `youtube`, `fetchiframe`, `fetch`, `/3`, numeric index, etc. |
| `tokenId` | `token-id`, `token_id` | Optional identifier passed to the resolver |
| `tokenMatch` | `token-match`, `token_match` | Regex string to replace in the resolved URL |
| `tokenReplace` | `token-replace`, `token_replace` | Replacement text |

### Example with all four

```m3u
#EXTINF:-1,Full token
https://api.provider.com/get?id=99|tokenUrl=streambd|tokenId=99|tokenMatch=http://old|tokenReplace=https://new
```

---

## 15. PLS format

Winamp-compatible playlist.

```ini
[playlist]
NumberOfEntries=2

File1=https://cdn.com/a.m3u8
Title1=Star Sports HD
Length1=-1

File2=https://cdn.com/b.m3u8
Title2=Sony Ten 1
Group2=Sports
Length2=-1
```

### Supported keys

- `File<n>` — URL (required)
- `Title<n>` — channel name (optional)
- `Group<n>` — group / category (optional)

If `Group<n>` is missing, the parser attempts to split `Title` on a
dash: `"Sports - Star Sports"` → group = `Sports`, title = `Star Sports`.

---

## 16. XSPF format

XML Shareable Playlist Format.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<playlist version="1" xmlns="http://xspf.org/ns/0/">
  <trackList>

    <track>
      <title>Star Sports HD</title>
      <location>https://cdn.com/a.m3u8|User-Agent=Mozilla/5.0</location>
      <image>https://logos.com/star.png</image>
      <creator>Sports</creator>
      <annotation>Live sports channel</annotation>
    </track>

    <track>
      <title>Sony Ten 1</title>
      <location>https://cdn.com/b.m3u8</location>
      <image>https://logos.com/sony.png</image>
      <creator>Sports</creator>
    </track>

  </trackList>
</playlist>
```

### Multiple `<location>` = failover

XSPF allows multiple `<location>` per track. The first one is used as
the primary URL; the rest become `sources` for failover.

```xml
<track>
  <title>Failover Example</title>
  <location>https://cdn1.com/a.m3u8</location>
  <location>https://cdn2.com/a.m3u8</location>
  <location>https://cdn3.com/a.m3u8</location>
</track>
```

---

## 17. JSON format

A JSON array of channel objects.

```json
[
  {
    "name": "Star Sports HD",
    "url": "https://cdn.com/a.m3u8",
    "logo": "https://logos.com/star.png",
    "group": "Sports",
    "userAgent": "Mozilla/5.0",
    "referer": "https://provider.com/",
    "cookie": "session=xyz",
    "isLive": true
  },
  {
    "name": "Failover Example",
    "sources": {
      "primary": "https://cdn1.com/x.m3u8",
      "backup":  "https://cdn2.com/x.m3u8"
    },
    "group": "News"
  }
]
```

### Recognised fields

| Field | Aliases |
|-------|---------|
| `name` | `title` |
| `url` | `link`, `stream_url`, `streamUrl` |
| `logo` | `tvg-logo`, `logo_url`, `poster`, `image`, `icon`, `thumb` |
| `group` | `group-title`, `category`, `groupTitle` |
| `userAgent` | `user-agent`, `user_agent` |
| `referer` | `referrer` |
| `cookie` | — |
| `origin` | — |
| `isLive` | — (defaults to URL heuristic) |
| `sources` | (object with multiple URLs) |

Wrapped formats (`{"channels": [...]}`, `{"streams": [...]}`,
`{"items": [...]}`, `{"data": [...]}`) are also accepted.

---

## 18. Multi-source failover

Two ways to declare backup URLs for a channel.

### JSON — `sources` object

```json
{
  "name": "Channel with backups",
  "url": "https://cdn1.com/x.m3u8",
  "sources": {
    "backup1": "https://cdn2.com/x.m3u8",
    "backup2": "https://cdn3.com/x.m3u8"
  }
}
```

### XSPF — multiple `<location>`

```xml
<track>
  <title>Channel with backups</title>
  <location>https://cdn1.com/x.m3u8</location>
  <location>https://cdn2.com/x.m3u8</location>
</track>
```

When the primary URL fails (403 / 404 / parse error), the player
automatically retries the next source in the list.

---

## 19. Complete real-world example

An M3U that exercises every supported syntax:

```m3u
#EXTM3U

# ───────────────────────────────────────────────────────────────
# Plain channel with no headers
# ───────────────────────────────────────────────────────────────
#EXTINF:-1 group-title="Sports",Plain Channel
https://cdn.com/plain.m3u8

# ───────────────────────────────────────────────────────────────
# Extended attributes + pipe headers
# ───────────────────────────────────────────────────────────────
#EXTINF:-1 tvg-logo="https://logos.com/star.png" group-title="Sports",Star Sports HD
https://cdn.com/star.m3u8|User-Agent=Mozilla/5.0|Referer=https://provider.com/|X-Api-Key=abc123

# ───────────────────────────────────────────────────────────────
# ClearKey DRM (shorthand)
# ───────────────────────────────────────────────────────────────
#EXTINF:-1 group-title="Movies",ClearKey Movie
https://cdn.com/movie.mpd|5360e12d4bb2519a9148d1089a55908d:7e8f500cef51595f3d43acafab1a8fb4|clearkey

# ───────────────────────────────────────────────────────────────
# ClearKey DRM (drmScheme / drmLicense)
# ───────────────────────────────────────────────────────────────
#EXTINF:-1 group-title="Movies",ClearKey Explicit
https://cdn.com/movie2.mpd|drmScheme=clearkey|drmLicense=5360e12d4bb2519a9148d1089a55908d:7e8f500cef51595f3d43acafab1a8fb4

# ───────────────────────────────────────────────────────────────
# Widevine license server
# ───────────────────────────────────────────────────────────────
#EXTINF:-1 group-title="Movies",Widevine Stream
https://cdn.com/wv.mpd|drmScheme=widevine|drmLicense=https://license.com/wv

# ───────────────────────────────────────────────────────────────
# Kodi #KODIPROP
# ───────────────────────────────────────────────────────────────
#KODIPROP:inputstream.adaptive.license_type=clearkey
#KODIPROP:inputstream.adaptive.license_key=5360e12d4bb2519a9148d1089a55908d:7e8f500cef51595f3d43acafab1a8fb4
#EXTINF:-1 group-title="Movies",Kodi Style
https://cdn.com/kodi.mpd

# ───────────────────────────────────────────────────────────────
# Kodi #EXTHTTP JSON
# ───────────────────────────────────────────────────────────────
#EXTHTTP:{"User-Agent":"Mozilla/5.0","Referer":"https://provider.com/","X-Api-Key":"abc123"}
#EXTINF:-1 group-title="Sports",JSON Headers
https://cdn.com/json-headers.m3u8

# ───────────────────────────────────────────────────────────────
# Token-based channel
# ───────────────────────────────────────────────────────────────
#EXTINF:-1 group-title="News",Token Channel
https://provider.com/page?id=42|tokenUrl=streambd|tokenId=42

# ───────────────────────────────────────────────────────────────
# Mixed pipe + ampersand
# ───────────────────────────────────────────────────────────────
#EXTINF:-1 group-title="Live",Mixed Separators
https://mini.example.com/jtv/index.mpd?__hdnea__=st=1~hmac=abc|Accept-Encoding=gzip, deflate&sec-fetch-site=cross-site&Origin=https://provider.com&drmScheme=clearkey&drmLicense=5360e12d4bb2519a9148d1089a55908d:7e8f500cef51595f3d43acafab1a8fb4
```

---

## 20. Compatibility matrix

How each format maps to what popular apps accept.

| Format | Kodi | TiviMate | OTT Navigator | Televizo | StreamBD (this app) |
|--------|------|----------|---------------|----------|---------------------|
| Basic M3U | ✅ | ✅ | ✅ | ✅ | ✅ |
| Pipe headers | ✅ | ✅ | ✅ | ✅ | ✅ |
| Ampersand headers | ⚠️ | ✅ | ✅ | ✅ | ✅ |
| Mixed pipe + amp | ⚠️ | ✅ | ✅ | ✅ | ✅ |
| ClearKey shorthand | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| `drmScheme`/`drmLicense` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `drm-type`/`drm-key` | ⚠️ | ✅ | ✅ | ✅ | ✅ |
| `#KODIPROP` | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| `#EXTVLCOPT` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `#EXTHTTP` JSON | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| PLS | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| XSPF | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| JSON | ⚠️ | ✅ | ✅ | ✅ | ✅ |

Legend: ✅ full support · ⚠️ partial or requires plugin · ❌ not supported

### Recommendation

For maximum cross-app portability, prefer:

1. **Basic M3U + pipe headers** — the most widely accepted form.
2. **`drmScheme` + `drmLicense`** — the canonical way to embed
   ClearKey / Widevine, accepted by nearly every player.
3. **Avoid** the shorthand `|<kid>:<key>|clearkey` unless the target
   player is confirmed to accept it — the explicit form is safer.

---

## Parsing algorithm (short version)

For those who want to understand how the parser decides what's a URL
and what's a header:

1. Scan for the first `|` that is followed by a **header-shaped token**
   (`Key=...`, `Key:...`, `<32hex>:<32hex>`, or a bare DRM keyword).
2. Everything to the left is the **URL** — including `?`, `&`, and any
   non-header-shaped `|` characters.
3. Split the remaining section on `|` and on `&` when the token after
   `&` is also header-shaped.
4. For each piece, classify:
   - `<32hex>:<32hex>` → ClearKey key pair
   - `clearkey` / `widevine` / ... → DRM type marker
   - `Key=Value` or `Key:Value` → route by canonical name
5. Unknown keys are forwarded verbatim as custom HTTP headers.

See `M3UParser.parseUrlHeaders` and `M3UParser.processHeaderPiece` for
the reference implementation.

---

*End of document.*
