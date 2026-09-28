package com.streambd.iptv.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

object WebViewSniffer {

    private val knownStreamDomains = listOf(
        "dlhd", "daddylive", "streamserver", "hdstreams",
        "embedstream", "livehdstream", "streambtw", "hdvid",
        "embed", "stream", "live", "player", "cdn", "sportshd",
        "crictime", "crichd", "livetv", "tvstream", "sportlemon",
        "footybite", "buffstream", "crackstreams", "reddit-stream",
        "sportsupa", "sport"
    )

    private val adKeywords = listOf(
        "adsystem", "popads", "popcash", "exoclick", "propellerads",
        "adsterra", "doubleclick", "syndication", "adservice", "adserver",
        "tracker", "analytics", "banner", "popunder", "exdynsrv",
        "adskeeper", "mgid", "/ads/", "adx", "pyppo", "prebid",
        "googletag", "outbrain", "taboola", "adclick", "clickadu",
        "adcash", "hilltopads", "trafficjunky",
        "caligaoctrois", "/lp/i/"
    )

    // NOTE: ".cyou" is checked separately (host suffix) so it doesn't match
    // inside query strings or path segments.
    private val adultKeywords = listOf("bongacams", "chaturbate", "jerkmate")
    private val adultHostSuffixes = listOf(".cyou")

    private val adVideoKeywords = listOf(
        "preroll", "midroll", "postroll", "advertisement",
        "vast", "vmap", "adbreak", "ad_segment"
    )

    // Page-URL hints that must NEVER be classified as a video URL.
    private val pageUrlHints = listOf(
        "tokenurl=",
        "streamplay",
        "streambd"
    )
    private val pageUrlExts = listOf(".php", ".html", ".htm", ".asp", ".aspx", ".jsp")

    private val videoExtRegex = Regex("""\.(m3u8|mpd|mp4|webm|flv|mkv)(?=$|[/;])""", RegexOption.IGNORE_CASE)
    private val bareVideoNames = setOf("manifest", "chunklist")

    /**
     * Returns true only for URLs that are genuinely playable media.
     *
     * Fixes the case where `.../playlist?tokenUrl=streambd` (a page) was treated as
     * video, then ExoPlayer complained:
     *   "Input does not start with the #EXTM3U header".
     */
    private fun isVideoUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()

        // 1) Reject page URLs that carry token/page hints.
        if (pageUrlHints.any { lower.contains(it) }) return false
        if (pageUrlExts.any { lower.substringBefore("?").endsWith(it) }) return false

        val path = try {
            Uri.parse(url).path.orEmpty()
        } catch (e: Exception) {
            url.substringBefore("?")
        }
        if (path.isEmpty()) return false

        // 2) Must end with a real media extension…
        if (videoExtRegex.containsMatchIn(path)) return true

        // 3) …or be an extensionless bare manifest name.
        val last = path.trimEnd('/').substringAfterLast('/').lowercase()
        return !last.contains('.') && last in bareVideoNames
    }

    private fun isAdVideoUrl(url: String) = adVideoKeywords.any { url.lowercase().contains(it) }

    private fun isAdUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (adKeywords.any { lower.contains(it) }) return true
        if (adultKeywords.any { lower.contains(it) }) return true
        val host = try { Uri.parse(url).host?.lowercase().orEmpty() } catch (e: Exception) { "" }
        if (host.isNotEmpty() && adultHostSuffixes.any { host.endsWith(it) }) return true
        return false
    }

    private fun isSameDomain(h1: String, h2: String): Boolean {
        if (h1 == h2) return true
        val r1 = h1.split(".").takeLast(2).joinToString(".")
        val r2 = h2.split(".").takeLast(2).joinToString(".")
        return r1 == r2
    }

    private fun getRootDomain(host: String?): String {
        if (host.isNullOrBlank()) return ""
        val parts = host.split(".")
        return if (parts.size >= 3 && parts[parts.size - 2] in listOf("com", "co", "net", "org", "gov", "edu")) {
            parts.takeLast(3).joinToString(".")
        } else if (parts.size >= 2) {
            parts.takeLast(2).joinToString(".")
        } else host
    }

    private fun extractOrigin(url: String): String {
        return try {
            val u = Uri.parse(url)
            "${u.scheme}://${u.host}"
        } catch (e: Exception) {
            ""
        }
    }

    private val garbageCookiePrefixes = listOf(
        "_ga", "_gid", "_fbp", "_gcl", "wzrk", "mp_", "ajs_", "ad_", "_hj", "__utm"
    )

    private fun filterEssentialCookies(rawCookie: String?): String {
        if (rawCookie.isNullOrBlank()) return ""
        val cookieMap = linkedMapOf<String, String>()
        rawCookie.split(";").forEach { part ->
            val eq = part.indexOf('=')
            if (eq > 0) {
                val k = part.substring(0, eq).trim()
                val v = part.substring(eq + 1).trim()
                if (k.isNotEmpty()) cookieMap[k] = v
            }
        }
        if (cookieMap.containsKey("Edge-Cache-Cookie")) {
            return "Edge-Cache-Cookie=${cookieMap["Edge-Cache-Cookie"]}"
        }
        val cleanMap = cookieMap.filterKeys { key ->
            garbageCookiePrefixes.none { g -> key.startsWith(g, ignoreCase = true) } &&
                !key.contains("mixpanel", ignoreCase = true)
        }
        return cleanMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun mergeCookies(vararg cookieStrings: String?): String {
        val mergedRaw = cookieStrings.filterNotNull().filter { it.isNotBlank() }.joinToString("; ")
        return filterEssentialCookies(mergedRaw)
    }

    private fun parseSimpleJson(json: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val org = android.util.JsonReader(java.io.StringReader(json))
            org.beginObject()
            while (org.hasNext()) { map[org.nextName()] = org.nextString() }
            org.endObject()
        } catch (e: Exception) {}
        return map
    }

    /**
     * 403 for blocked requests — 200-with-empty-body causes some players to retry the URL.
     */
    private fun blockedResponse(): WebResourceResponse {
        val response = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
        response.setStatusCodeAndReasonPhrase(403, "Forbidden")
        response.responseHeaders = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Cache-Control" to "no-store, no-cache"
        )
        return response
    }

    private class SnifferJSInterface(
        private val secretToken: String,
        private val currentInputUrl: String,
        private val bypassUA: String,
        private val onVideoDetected: (ResolvedToken) -> Unit,
        private val onKeyFound: (String) -> Unit,
        private val currentPageUrlGetter: () -> String
    ) {
        @JavascriptInterface
        fun onClearKey(token: String, keyJson: String) {
            if (token != secretToken) return
            onKeyFound(keyJson)
        }

        @JavascriptInterface
        fun onVideoFoundWithHeaders(token: String, videoUrl: String, headersJson: String) {
            if (token != secretToken) return
            if (videoUrl.isBlank()) return
            if (!isVideoUrl(videoUrl) || isAdVideoUrl(videoUrl) || isAdUrl(videoUrl)) return

            try {
                val headers = parseSimpleJson(headersJson)
                val referer = headers["referer"] ?: headers["Referer"] ?: currentInputUrl
                val origin = headers["origin"] ?: headers["Origin"] ?: extractOrigin(currentInputUrl)
                val ua = headers["user-agent"] ?: headers["User-Agent"] ?: bypassUA
                val jsCookie = headers["cookie"] ?: headers["Cookie"] ?: ""

                val cm = CookieManager.getInstance()
                cm.flush()

                val rootDomain = getRootDomain(Uri.parse(referer).host)
                val finalCookie = mergeCookies(
                    jsCookie,
                    cm.getCookie(videoUrl),
                    cm.getCookie(referer),
                    cm.getCookie("https://$rootDomain"),
                    cm.getCookie(currentPageUrlGetter()),
                    cm.getCookie(currentInputUrl)
                )

                val extraHeaders = headers.filterKeys { key ->
                    key.lowercase() !in listOf(
                        "referer", "origin", "cookie", "user-agent", "host", "content-length"
                    )
                }.toMutableMap()

                extraHeaders["Referer"] = referer
                extraHeaders["Origin"] = origin
                extraHeaders["User-Agent"] = ua
                if (finalCookie.isNotEmpty()) extraHeaders["Cookie"] = finalCookie

                onVideoDetected(ResolvedToken(url = videoUrl, headers = extraHeaders))
            } catch (e: Exception) {
                android.util.Log.e("WebViewSniffer", "Header parse error: ${e.message}")
            }
        }

        @JavascriptInterface
        fun log(msg: String) {
            if (com.streambd.iptv.BuildConfig.DEBUG) {
                android.util.Log.d("WebViewSniffer_JS", msg)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun sniff(
        context: Context,
        url: String,
        headers: Map<String, String>? = null,
        userAgent: String? = null
    ): ResolvedToken? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(20000L) {
            suspendCancellableCoroutine { continuation ->
                var isResumed = false
                var foundMpdUrl: String? = null
                var foundMpdHeaders: Map<String, String>? = null
                var foundKeyJson: String? = null
                var mpdTimeoutRunnable: Runnable? = null
                val handler = Handler(Looper.getMainLooper())
                var currentPageUrl = url

                val bypassUA = userAgent
                    ?: "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

                CookieManager.getInstance().apply { setAcceptCookie(true) }

                val webView = WebView(context).apply {
                    alpha = 0f
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                var windowManager: android.view.WindowManager? = null
                var layoutParams: android.view.WindowManager.LayoutParams? = null
                var addedToWindow = false

                try {
                    if (context is android.app.Activity && !context.isFinishing && !context.isDestroyed) {
                        val rootView = context.findViewById<android.view.ViewGroup>(android.R.id.content)
                        if (rootView != null) {
                            rootView.addView(webView, android.view.ViewGroup.LayoutParams(20, 20))
                            addedToWindow = true
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WebViewSniffer", "Error attaching WebView to Activity", e)
                }

                if (!addedToWindow) {
                    try {
                        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                        layoutParams = android.view.WindowManager.LayoutParams(
                            20, 20,
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            else
                                android.view.WindowManager.LayoutParams.TYPE_PHONE,
                            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                            android.graphics.PixelFormat.TRANSPARENT
                        )
                        if (context is android.app.Activity) {
                            layoutParams.type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
                        }
                        windowManager.addView(webView, layoutParams)
                    } catch (e: Exception) {
                        android.util.Log.e("WebViewSniffer", "Error attaching WebView via WindowManager", e)
                        continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }
                }

                var isCleanedUp = false
                fun cleanup() {
                    if (isCleanedUp) return
                    isCleanedUp = true
                    // Safe to clear ALL callbacks — this handler is local to the coroutine.
                    handler.removeCallbacksAndMessages(null)
                    try {
                        webView.stopLoading()
                        try { webView.removeJavascriptInterface("AndroidBridge") } catch (e: Exception) {}
                        try {
                            if (webView.parent != null) {
                                (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                            } else {
                                windowManager?.removeView(webView)
                            }
                        } catch (e: Exception) {}
                        webView.destroy()
                    } catch (e: Exception) {}
                }

                continuation.invokeOnCancellation { handler.post { cleanup() } }

                val secretToken = java.util.UUID.randomUUID().toString()

                val jsInterface = SnifferJSInterface(
                    secretToken = secretToken,
                    currentInputUrl = url,
                    bypassUA = bypassUA,
                    onVideoDetected = { resolved ->
                        handler.post {
                            if (isResumed) return@post
                            val lowerUrl = resolved.url.lowercase()

                            if (lowerUrl.contains(".mpd")) {
                                if (foundKeyJson != null) {
                                    isResumed = true
                                    cleanup()
                                    val drmConfig = com.streambd.iptv.data.models.DrmConfig(
                                        type = "clearkey",
                                        licenseServer = foundKeyJson!!
                                    )
                                    continuation.resume(
                                        ResolvedToken(url = resolved.url, headers = resolved.headers, drm = drmConfig)
                                    )
                                } else {
                                    foundMpdUrl = resolved.url
                                    foundMpdHeaders = resolved.headers
                                    if (mpdTimeoutRunnable == null) {
                                        mpdTimeoutRunnable = Runnable {
                                            if (!isResumed) {
                                                isResumed = true
                                                cleanup()
                                                continuation.resume(
                                                    ResolvedToken(url = resolved.url, headers = resolved.headers)
                                                )
                                            }
                                        }
                                        handler.postDelayed(mpdTimeoutRunnable!!, 3000)
                                    }
                                }
                            } else {
                                isResumed = true
                                cleanup()
                                continuation.resume(resolved)
                            }
                        }
                    },
                    onKeyFound = { keyJson ->
                        handler.post {
                            foundKeyJson = keyJson
                            if (!isResumed && foundMpdUrl != null) {
                                isResumed = true
                                mpdTimeoutRunnable?.let { handler.removeCallbacks(it) }
                                cleanup()
                                val drmConfig = com.streambd.iptv.data.models.DrmConfig(
                                    type = "clearkey",
                                    licenseServer = keyJson
                                )
                                continuation.resume(
                                    ResolvedToken(url = foundMpdUrl!!, headers = foundMpdHeaders, drm = drmConfig)
                                )
                            }
                        }
                    },
                    currentPageUrlGetter = { currentPageUrl }
                )

                webView.addJavascriptInterface(jsInterface, "AndroidBridge")

                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = bypassUA
                    setSupportMultipleWindows(true)
                    javaScriptCanOpenWindowsAutomatically = true
                }

                fun injectEarlyHooks(view: WebView) {
                    val js = """
                        (function() {
                        if (window.__snifferInstalled) return;
                        window.__snifferInstalled = true;
                        var _b = window.AndroidBridge;
                        var _token = '$secretToken';
                        var _pageUrl = window.location.href;

                        function buildHeadersJson(extraObj) {
                            var h = {};
                            h['Referer']    = _pageUrl;
                            h['Origin']     = window.location.origin || '';
                            h['User-Agent'] = navigator.userAgent || '';
                            try { h['Cookie'] = document.cookie || ''; } catch(e) {}
                            if (extraObj) {
                                for (var k in extraObj) {
                                    if (extraObj.hasOwnProperty(k)) h[k] = extraObj[k];
                                }
                            }
                            return JSON.stringify(h);
                        }

                        // IMPORTANT: same rules as the Kotlin side. Page URLs like
                        // `.../playlist?tokenUrl=streambd` MUST NOT be reported.
                        function isVideoUrl(url) {
                            if (!url || typeof url !== 'string') return false;
                            var l = url.toLowerCase();
                            if (l.indexOf('tokenurl=') !== -1) return false;
                            if (l.indexOf('streamplay') !== -1) return false;
                            if (l.indexOf('streambd') !== -1) return false;
                            var bare = l.split('?')[0].split('#')[0];
                            if (bare.endsWith('.php') || bare.endsWith('.html') ||
                                bare.endsWith('.htm') || bare.endsWith('.asp')) return false;
                            return bare.endsWith('.m3u8') ||
                                   bare.endsWith('.mpd')  ||
                                   bare.endsWith('.mp4')  ||
                                   bare.endsWith('.webm') ||
                                   bare.endsWith('.mkv')  ||
                                   bare.endsWith('.flv');
                        }
                        function report(url, extraHeaders) {
                            try {
                                var full = (url && url.startsWith('http')) ? url : new URL(url, _pageUrl).href;
                                if (isVideoUrl(full) && _b) {
                                    _b.onVideoFoundWithHeaders(_token, full, buildHeadersJson(extraHeaders));
                                }
                            } catch(e) {}
                        }

                        // ─── Auto-click player buttons ────────────────────────────────────────
                        setInterval(function() {
                            var ads = document.querySelectorAll('#first-click-ads-layer, .delayed-popup-ads-layer, [id*="ads"], [class*="ads"]');
                            ads.forEach(function(ad) { ad.remove(); });

                            document.querySelectorAll('video').forEach(function(v) {
                                if (v.paused) {
                                    v.muted = true;
                                    var p = v.play();
                                    if (p && p.catch) p.catch(function(){});
                                }
                            });

                            var btnSelectors = [
                                '.vjs-big-play-button', '.jw-display-icon-display', '.plyr__control--overlaid',
                                '.play-btn', '#play-btn', '[aria-label="Play"]', '.play-wrapper',
                                'button.play', '.amp-flush-ui-play-video', '.fp-play', '.vjs-play-control'
                            ];
                            btnSelectors.forEach(function(sel) {
                                document.querySelectorAll(sel).forEach(function(btn) {
                                    try { btn.click(); } catch(e){}
                                });
                            });

                            var elements = document.querySelectorAll('button, div, span, a');
                            for (var i = 0; i < elements.length; i++) {
                                var el = elements[i];
                                var txt = (el.innerText || '').toLowerCase().trim();
                                if (txt === 'continue' || txt === 'start') {
                                    try { el.click(); } catch(e){}
                                }
                            }

                            var videos = document.getElementsByTagName('video');
                            if (videos.length > 0) { try { videos[0].play(); } catch(e) {} }

                            try {
                                var clickEvent = document.createEvent('MouseEvents');
                                clickEvent.initMouseEvent('click', true, true, window, 0, 0, 0,
                                    window.innerWidth/2, window.innerHeight/2,
                                    false, false, false, false, 0, null);
                                var elAt = document.elementFromPoint(window.innerWidth/2, window.innerHeight/2);
                                if (elAt) elAt.dispatchEvent(clickEvent);
                            } catch(e) {}
                        }, 1000);

                        // ─── iframe reload trick ──────────────────────────────────────────────
                        document.querySelectorAll('iframe').forEach(function(f) {
                            var origSrc = f.getAttribute('src') || f.getAttribute('data-src');
                            if (!origSrc) return;
                            report(origSrc);
                            f.setAttribute('src', '');
                            setTimeout(function() { f.setAttribute('src', origSrc); }, 100);
                        });

                        // ─── MutationObserver ────────────────────────────────────────────────
                        var _observer = new MutationObserver(function(mutations) {
                            mutations.forEach(function(m) {
                                m.addedNodes.forEach(function(node) {
                                    if (node.tagName === 'IFRAME') {
                                        var src = node.getAttribute('src') || node.getAttribute('data-src');
                                        if (src) report(src);
                                    }
                                    if (node.tagName === 'SCRIPT' && node.textContent) {
                                        scanTextForStream(node.textContent, {});
                                    }
                                });
                            });
                        });
                        _observer.observe(document.body || document.documentElement, { childList: true, subtree: true });

                        // ─── MSE hook (log only) ─────────────────────────────────────────────
                        if (window.MediaSource) {
                            var _origMSOpen = MediaSource.prototype.addSourceBuffer;
                            MediaSource.prototype.addSourceBuffer = function(mime) {
                                _b && _b.log('MSE detected: ' + mime);
                                return _origMSOpen.apply(this, arguments);
                            };
                        }

                        // ─── Text scanner ────────────────────────────────────────────────────
                        function scanTextForStream(text, extraHeaders) {
                            if (!text || typeof text !== 'string') return;
                            var patterns = [
                                /https?:\/\/[^\s"'\\<>]+\.m3u8[^\s"'\\<>]*/gi,
                                /https?:\/\/[^\s"'\\<>]+\.mpd[^\s"'\\<>]*/gi,
                                /https?:\/\/[^\s"'\\<>]+\.mp4[^\s"'\\<>]*/gi
                            ];
                            patterns.forEach(function(pat) {
                                var m;
                                while ((m = pat.exec(text)) !== null) {
                                    if (_b) _b.onVideoFoundWithHeaders(_token, m[0], buildHeadersJson(extraHeaders));
                                }
                            });
                        }

                        // ─── XHR hook ────────────────────────────────────────────────────────
                        var _origOpen  = XMLHttpRequest.prototype.open;
                        var _origSend  = XMLHttpRequest.prototype.send;
                        var _origSetRH = XMLHttpRequest.prototype.setRequestHeader;

                        XMLHttpRequest.prototype.open = function(method, url) {
                            this.__url     = url;
                            this.__headers = {};
                            report(url || '');
                            return _origOpen.apply(this, arguments);
                        };

                        XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
                            if (this.__headers) this.__headers[name] = value;
                            return _origSetRH.apply(this, arguments);
                        };

                        XMLHttpRequest.prototype.send = function(body) {
                            var self = this;
                            if (self.__url && isVideoUrl(self.__url)) {
                                report(self.__url, self.__headers);
                            }
                            this.addEventListener('load', function() {
                                try { scanTextForStream(this.responseText, self.__headers); } catch(e){}
                            });
                            return _origSend.apply(this, arguments);
                        };

                        // ─── Fetch hook ──────────────────────────────────────────────────────
                        var _origFetch = window.fetch;
                        window.fetch = function(input, init) {
                            var url = typeof input === 'string' ? input
                                  : (input && input.url ? input.url : '');
                            var extraH = {};
                            try {
                                if (init && init.headers) {
                                    if (init.headers instanceof Headers) {
                                        init.headers.forEach(function(v, k) { extraH[k] = v; });
                                    } else {
                                        for (var k in init.headers) extraH[k] = init.headers[k];
                                    }
                                }
                                if (input instanceof Request && input.headers) {
                                    input.headers.forEach(function(v, k) { extraH[k] = v; });
                                }
                            } catch(e) {}
                            report(url, extraH);

                            var p = _origFetch.apply(this, arguments);
                            p.then(function(res) {
                                try {
                                    res.clone().text().then(function(t) {
                                        scanTextForStream(t, extraH);
                                    }).catch(function(){});
                                } catch(e){}
                            });
                            return p;
                        };

                        // ─── Page source scan ────────────────────────────────────────────────
                        setTimeout(function() {
                            try { scanTextForStream(document.documentElement.innerHTML, {}); } catch(e){}
                        }, 2000);

                        // ─── EME (ClearKey) hook ─────────────────────────────────────────────
                        if (!window.hookedEME && window.MediaKeySession) {
                            window.hookedEME = true;
                            const originalUpdate = MediaKeySession.prototype.update;
                            MediaKeySession.prototype.update = function(response) {
                                try {
                                    const text = new TextDecoder().decode(response);
                                    if (text.includes('"kty":"oct"')) {
                                        _b.onClearKey(_token, text);
                                    }
                                } catch(e) {}
                                return originalUpdate.apply(this, arguments);
                            };
                        }

                        _b && _b.log('[Sniffer] Early hooks installed on: ' + _pageUrl);
                        })();
                    """.trimIndent()
                    view.evaluateJavascript(js, null)
                }

                fun injectDeepSniffer(view: WebView) {
                    val js = """
                        (function() {
                        if (window.__deepSnifferInstalled) return;
                        window.__deepSnifferInstalled = true;
                        var _b = window.AndroidBridge;
                        var _token = '$secretToken';
                        var _pageUrl = window.location.href;

                        function buildHeadersJson(extra) {
                            var h = {};
                            h['Referer']    = _pageUrl;
                            h['Origin']     = window.location.origin || '';
                            h['User-Agent'] = navigator.userAgent || '';
                            try { h['Cookie'] = document.cookie || ''; } catch(e) {}
                            if (extra) {
                                for (var k in extra) {
                                    if (extra.hasOwnProperty(k)) h[k] = extra[k];
                                }
                            }
                            return JSON.stringify(h);
                        }

                        function isVideoUrl(url) {
                            if (!url || typeof url !== 'string') return false;
                            var l = url.toLowerCase();
                            if (l.indexOf('tokenurl=') !== -1) return false;
                            if (l.indexOf('streamplay') !== -1) return false;
                            if (l.indexOf('streambd') !== -1) return false;
                            var bare = l.split('?')[0].split('#')[0];
                            if (bare.endsWith('.php') || bare.endsWith('.html') ||
                                bare.endsWith('.htm') || bare.endsWith('.asp')) return false;
                            return bare.endsWith('.m3u8') ||
                                   bare.endsWith('.mpd')  ||
                                   bare.endsWith('.mp4')  ||
                                   bare.endsWith('.webm') ||
                                   bare.endsWith('.mkv')  ||
                                   bare.endsWith('.flv');
                        }

                        function report(url, extra) {
                            try {
                                var full = (url && url.startsWith('http')) ? url : new URL(url, _pageUrl).href;
                                if (isVideoUrl(full) && _b) {
                                    _b.onVideoFoundWithHeaders(_token, full, buildHeadersJson(extra));
                                }
                            } catch(e) {}
                        }

                        // ─── HLS.js hook ────────────────────────────────────────────────────
                        if (window.Hls && !window.Hls.__hooked) {
                            window.Hls.__hooked = true;
                            var _origLS = window.Hls.prototype.loadSource;
                            window.Hls.prototype.loadSource = function(src) {
                                var extra = {};
                                try {
                                    if (this.config && this.config.xhrSetup) extra['__hlsXhrSetup'] = 'true';
                                } catch(e) {}
                                report(src || '', extra);
                                return _origLS.apply(this, arguments);
                            };
                            _b && _b.log('[Sniffer] HLS.js hooked');
                        }

                        // ─── Shaka Player hook ──────────────────────────────────────────────
                        if (window.shaka && window.shaka.Player && !window.shaka.__hooked) {
                            window.shaka.__hooked = true;
                            var _origLoad = window.shaka.Player.prototype.load;
                            window.shaka.Player.prototype.load = function(manifestUri) {
                                report(manifestUri || '');
                                _b && _b.log('[Sniffer] Shaka load: ' + manifestUri);
                                return _origLoad.apply(this, arguments);
                            };
                        }

                        // ─── Video.js hook ──────────────────────────────────────────────────
                        if (window.videojs && !window.videojs.__hooked) {
                            window.videojs.__hooked = true;
                            var _origVjsSrc = window.videojs.prototype && window.videojs.prototype.src;
                            if (_origVjsSrc) {
                                window.videojs.prototype.src = function(src) {
                                    if (src) {
                                        var url = typeof src === 'string' ? src : (src.src || '');
                                        if (url) report(url);
                                    }
                                    return _origVjsSrc.apply(this, arguments);
                                };
                            }
                        }

                        // ─── JW Player hook ─────────────────────────────────────────────────
                        if (window.jwplayer && !window.jwplayer.__hooked) {
                            window.jwplayer.__hooked = true;
                            var _origJwSetup = window.jwplayer.prototype && window.jwplayer.prototype.setup;
                            if (_origJwSetup) {
                                window.jwplayer.prototype.setup = function(config) {
                                    try {
                                        if (config && config.file) report(config.file);
                                        if (config && config.sources) {
                                            config.sources.forEach(function(s) { if (s.file) report(s.file); });
                                        }
                                    } catch(e) {}
                                    return _origJwSetup.apply(this, arguments);
                                };
                            }
                        }

                        // ─── HTMLMediaElement.src hook ──────────────────────────────────────
                        if (!window.__mediaSrcHooked) {
                            window.__mediaSrcHooked = true;
                            var _nativeSrcDesc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
                            if (_nativeSrcDesc && _nativeSrcDesc.set) {
                                Object.defineProperty(HTMLMediaElement.prototype, 'src', {
                                    set: function(val) {
                                        if (val && isVideoUrl(val)) {
                                            _b && _b.onVideoFoundWithHeaders(_token, val, buildHeadersJson({}));
                                        }
                                        _nativeSrcDesc.set.call(this, val);
                                    },
                                    get: _nativeSrcDesc.get,
                                    configurable: true
                                });
                            }
                        }

                        // ─── All script tags scan ───────────────────────────────────────────
                        document.querySelectorAll('script').forEach(function(s) {
                            var text = s.textContent;
                            if (!text || typeof text !== 'string') return;
                            var patterns = [
                                /https?:\/\/[^\s"'\\<>]+\.m3u8[^\s"'\\<>]*/gi,
                                /https?:\/\/[^\s"'\\<>]+\.mpd[^\s"'\\<>]*/gi,
                                /https?:\/\/[^\s"'\\<>]+\.mp4[^\s"'\\<>]*/gi
                            ];
                            patterns.forEach(function(pat) {
                                var m;
                                while ((m = pat.exec(text)) !== null) {
                                    if (_b) _b.onVideoFoundWithHeaders(_token, m[0], buildHeadersJson({}));
                                }
                            });
                        });

                        // ─── DOM-এ video element poll (40s) ────────────────────────────────
                        var pollCount = 0;
                        var pollInterval = setInterval(function() {
                            document.querySelectorAll('video').forEach(function(v) {
                                if (v.src && v.src.startsWith('http'))             report(v.src);
                                if (v.currentSrc && v.currentSrc.startsWith('http')) report(v.currentSrc);
                            });
                            document.querySelectorAll('source').forEach(function(s) {
                                if (s.src && s.src.startsWith('http')) report(s.src);
                            });
                            if (++pollCount > 80) clearInterval(pollInterval);
                        }, 500);

                        _b && _b.log('[Sniffer] Deep sniffer injected on: ' + _pageUrl);
                        })();
                    """.trimIndent()
                    view.evaluateJavascript(js, null)
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: android.webkit.SslErrorHandler?,
                        error: android.net.http.SslError?
                    ) {
                        val requestUrl = error?.url ?: ""
                        if (!requestUrl.contains("github.io", ignoreCase = true) &&
                            !requestUrl.contains("tmdb.org", ignoreCase = true)
                        ) {
                            handler?.proceed()
                        } else {
                            handler?.cancel()
                        }
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (!url.isNullOrBlank()) currentPageUrl = url
                        view?.let { injectEarlyHooks(it) }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (!url.isNullOrBlank()) currentPageUrl = url
                        CookieManager.getInstance().flush()

                        view?.let { wv ->
                            injectDeepSniffer(wv)
                            handler.postDelayed({ injectDeepSniffer(wv) }, 1500)
                            handler.postDelayed({ injectDeepSniffer(wv) }, 3500)
                            handler.postDelayed({ injectDeepSniffer(wv) }, 6000)
                            handler.postDelayed({ injectDeepSniffer(wv) }, 9000)
                            handler.postDelayed({ injectDeepSniffer(wv) }, 12000)
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val reqUrl = request?.url?.toString() ?: return true
                        if (!reqUrl.startsWith("http://") && !reqUrl.startsWith("https://")) return true

                        if (isVideoUrl(reqUrl) && !isAdUrl(reqUrl)) return false

                        if (request.isForMainFrame) {
                            val curHost = Uri.parse(currentPageUrl).host ?: ""
                            val newHost = Uri.parse(reqUrl).host ?: ""

                            if (!isSameDomain(curHost, newHost)) {
                                if (isAdUrl(reqUrl)) return true
                                val isKnownStream = knownStreamDomains.any {
                                    newHost.contains(it, ignoreCase = true)
                                }
                                if (isKnownStream) return false
                                return true
                            }
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString()
                            ?: return super.shouldInterceptRequest(view, request)
                        val reqHeaders = request.requestHeaders ?: emptyMap()

                        val acceptHeader = reqHeaders.entries
                            .firstOrNull { it.key.equals("Accept", ignoreCase = true) }
                            ?.value?.lowercase() ?: ""

                        if (acceptHeader.contains("text/html")) {
                            return super.shouldInterceptRequest(view, request)
                        }

                        val lowerUrl = reqUrl.lowercase()

                        if (isVideoUrl(reqUrl)) {
                            if ((url.contains("crichd") || url.contains("daddylive")) &&
                                (lowerUrl.contains(".mp4") || lowerUrl.contains(".webm"))
                            ) {
                                // passthrough
                            } else {
                                if (isAdVideoUrl(reqUrl)) return blockedResponse()

                                val cm = CookieManager.getInstance()
                                cm.flush()

                                val referer = reqHeaders["Referer"] ?: reqHeaders["referer"]
                                    ?: currentPageUrl.ifEmpty { url }
                                val origin = reqHeaders["Origin"] ?: reqHeaders["origin"]
                                    ?: extractOrigin(referer)
                                val ua = reqHeaders["User-Agent"] ?: bypassUA
                                val rootDomain = getRootDomain(Uri.parse(referer).host)

                                val finalCookie = mergeCookies(
                                    reqHeaders["Cookie"], reqHeaders["cookie"],
                                    cm.getCookie(reqUrl), cm.getCookie(referer),
                                    cm.getCookie("https://$rootDomain"),
                                    cm.getCookie(currentPageUrl), cm.getCookie(url)
                                )

                                val outHeaders = reqHeaders.filterKeys { k ->
                                    k.lowercase() !in listOf("referer", "origin", "cookie", "user-agent", "host")
                                }.toMutableMap()

                                outHeaders["Referer"] = referer
                                outHeaders["Origin"] = origin
                                outHeaders["User-Agent"] = ua
                                if (finalCookie.isNotEmpty()) outHeaders["Cookie"] = finalCookie

                                handler.post {
                                    if (isResumed) return@post

                                    if (lowerUrl.contains(".mpd")) {
                                        if (foundKeyJson != null) {
                                            isResumed = true
                                            cleanup()
                                            val drmConfig = com.streambd.iptv.data.models.DrmConfig(
                                                type = "clearkey",
                                                licenseServer = foundKeyJson!!
                                            )
                                            continuation.resume(
                                                ResolvedToken(url = reqUrl, headers = outHeaders, drm = drmConfig)
                                            )
                                        } else {
                                            foundMpdUrl = reqUrl
                                            foundMpdHeaders = outHeaders
                                            if (mpdTimeoutRunnable == null) {
                                                mpdTimeoutRunnable = Runnable {
                                                    if (!isResumed) {
                                                        isResumed = true
                                                        cleanup()
                                                        continuation.resume(
                                                            ResolvedToken(url = reqUrl, headers = outHeaders)
                                                        )
                                                    }
                                                }
                                                handler.postDelayed(mpdTimeoutRunnable!!, 3000)
                                            }
                                        }
                                    } else {
                                        isResumed = true
                                        cleanup()
                                        continuation.resume(ResolvedToken(url = reqUrl, headers = outHeaders))
                                    }
                                }
                                return null
                            }
                        }

                        if (isAdUrl(reqUrl)) {
                            return blockedResponse()
                        }

                        return super.shouldInterceptRequest(view, request)
                    }
                }

                val additionalHeaders = headers?.toMutableMap() ?: mutableMapOf()
                if (userAgent != null) additionalHeaders["User-Agent"] = userAgent

                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                        .log("WebViewSniffer: Loading URL: $url")
                } catch (e: Exception) {}

                webView.loadUrl(url, additionalHeaders)
            }
        } ?: run {
            try {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                    .recordException(Exception("WebViewSniffer Timeout after 20s for URL: $url"))
            } catch (e: Exception) {}
            null
        }
    }
}
