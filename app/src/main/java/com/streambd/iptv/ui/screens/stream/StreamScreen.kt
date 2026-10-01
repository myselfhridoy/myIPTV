package com.streambd.iptv.ui.screens.stream

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.DrmConfig
import com.streambd.iptv.data.models.StreamSource
import com.streambd.iptv.ui.components.PremiumButton
import com.streambd.iptv.ui.components.PremiumDialogScaffold
import com.streambd.iptv.ui.components.PremiumOptionRow
import com.streambd.iptv.ui.components.TvFocusableItem
import com.streambd.iptv.ui.components.TvIconButton
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.GrayText
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.utils.M3UParser
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------
// Constants / helpers
// ---------------------------------------------------------------------

private val USER_AGENTS = listOf(
    "Default", "Chrome(Android)", "Chrome(PC)", "IE(PC)", "Firefox(PC)",
    "iPhone", "Nokia", "Custom"
)
private val DRM_SCHEMES = listOf("None", "clearkey", "widevine", "playready")

private val UA_STRINGS = mapOf(
    "Chrome(Android)" to "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
    "Chrome(PC)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "IE(PC)" to "Mozilla/5.0 (Windows NT 10.0; WOW64; Trident/7.0; rv:11.0) like Gecko",
    "Firefox(PC)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
    "iPhone" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
    "Nokia" to "Nokia6300/2.0 (05.20) Profile/MIDP-2.0 Configuration/CLDC-1.1"
)

private val CLEARKEY_PAIR = Regex("^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}$")
private val STD_HEADERS = setOf("user-agent", "referer", "origin", "cookie")
private const val MAX_HISTORY = 50

private fun resolveUserAgent(ua: String, custom: String): String? = when {
    ua == "Custom" -> custom.trim().takeIf { it.isNotEmpty() }
    ua.isBlank() || ua == "Default" -> null
    else -> UA_STRINGS[ua] ?: ua
}

private fun normalizeDrmScheme(raw: String): String {
    val t = raw.trim().lowercase()
    return when {
        t.contains("widevine") -> "widevine"
        t.contains("playready") -> "playready"
        t.contains("clearkey") -> "clearkey"
        t == "none" || t.isEmpty() -> "None"
        else -> t
    }
}

private fun buildDrmConfig(scheme: String, license: String): DrmConfig? {
    if (scheme.isBlank() || scheme.equals("None", ignoreCase = true)) return null
    val lic = license.trim().takeIf { it.isNotEmpty() }
    val type = scheme.lowercase()
    return if (type == "clearkey" && lic != null && CLEARKEY_PAIR.matches(lic)) {
        DrmConfig(type = "clearkey", rawKeyPair = lic)
    } else {
        DrmConfig(type = type, licenseServer = lic)
    }
}

private fun JSONObject.optStrOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = optString(key)
    return if (v.isEmpty() || v == "null") null else v
}

private fun mapToJson(map: Map<String, String>): JSONObject {
    val o = JSONObject()
    map.forEach { (k, v) -> o.put(k, v) }
    return o
}

private fun jsonToMap(obj: JSONObject?): Map<String, String> {
    if (obj == null) return emptyMap()
    val out = mutableMapOf<String, String>()
    obj.keys().forEach { k -> obj.optStrOrNull(k)?.let { out[k] = it } }
    return out
}

/** Read the current plain-text from the system clipboard, or null if empty. */
private fun readClipboardText(context: Context): String? {
    return try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(context).toString()
            text.takeIf { it.isNotBlank() }
        } else null
    } catch (_: Exception) {
        null
    }
}

data class PipeExtras(
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val tokenUrl: String? = null,
    val tokenMatch: String? = null,
    val tokenReplace: String? = null,
    val tokenId: Int? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("url", url)
        put("headers", mapToJson(headers))
        tokenUrl?.let { put("tokenUrl", it) }
        tokenMatch?.let { put("tokenMatch", it) }
        tokenReplace?.let { put("tokenReplace", it) }
        tokenId?.let { put("tokenId", it) }
    }

    companion object {
        fun fromJsonString(s: String?): PipeExtras {
            if (s.isNullOrBlank()) return PipeExtras()
            return try {
                val o = JSONObject(s)
                PipeExtras(
                    url = o.optString("url", ""),
                    headers = jsonToMap(o.optJSONObject("headers")),
                    tokenUrl = o.optStrOrNull("tokenUrl"),
                    tokenMatch = o.optStrOrNull("tokenMatch"),
                    tokenReplace = o.optStrOrNull("tokenReplace"),
                    tokenId = if (o.has("tokenId") && !o.isNull("tokenId")) o.optInt("tokenId") else null
                )
            } catch (e: Exception) {
                PipeExtras()
            }
        }
    }
}

data class StreamHistoryItem(
    val url: String,
    val cookie: String,
    val referer: String,
    val origin: String,
    val drmLicense: String,
    val userAgent: String,
    val drmScheme: String,
    val tokenUrl: String? = null,
    val tokenMatch: String? = null,
    val tokenReplace: String? = null,
    val tokenId: Int? = null,
    val customUa: String = "",
    val extraHeaders: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("url", url)
        obj.put("cookie", cookie)
        obj.put("referer", referer)
        obj.put("origin", origin)
        obj.put("drmLicense", drmLicense)
        obj.put("userAgent", userAgent)
        obj.put("drmScheme", drmScheme)
        obj.put("customUa", customUa)
        obj.put("extraHeaders", mapToJson(extraHeaders))
        tokenUrl?.let { obj.put("tokenUrl", it) }
        tokenMatch?.let { obj.put("tokenMatch", it) }
        tokenReplace?.let { obj.put("tokenReplace", it) }
        tokenId?.let { obj.put("tokenId", it) }
        return obj
    }

    companion object {
        fun fromJson(jsonStr: String): StreamHistoryItem? {
            return try {
                val obj = JSONObject(jsonStr)
                StreamHistoryItem(
                    url = obj.optString("url", ""),
                    cookie = obj.optString("cookie", ""),
                    referer = obj.optString("referer", ""),
                    origin = obj.optString("origin", ""),
                    drmLicense = obj.optString("drmLicense", ""),
                    userAgent = obj.optString("userAgent", "Default"),
                    drmScheme = obj.optString("drmScheme", "clearkey"),
                    tokenUrl = obj.optStrOrNull("tokenUrl"),
                    tokenMatch = obj.optStrOrNull("tokenMatch"),
                    tokenReplace = obj.optStrOrNull("tokenReplace"),
                    tokenId = if (obj.has("tokenId") && !obj.isNull("tokenId")) obj.optInt("tokenId") else null,
                    customUa = obj.optString("customUa", ""),
                    extraHeaders = jsonToMap(obj.optJSONObject("extraHeaders"))
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

// ---------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: com.streambd.iptv.ui.viewmodels.TmdbViewModel,
    navController: NavController,
    onMenuClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences(
        "stream_history_prefs",
        android.content.Context.MODE_PRIVATE
    )

    var mediaStreamUrl by remember { mutableStateOf(sharedPreferences.getString("draft_url", "") ?: "") }
    var cookieValue by remember { mutableStateOf(sharedPreferences.getString("draft_cookie", "") ?: "") }
    var refererValue by remember { mutableStateOf(sharedPreferences.getString("draft_referer", "") ?: "") }
    var originValue by remember { mutableStateOf(sharedPreferences.getString("draft_origin", "") ?: "") }
    var drmLicenseUrl by remember { mutableStateOf(sharedPreferences.getString("draft_drmLicense", "") ?: "") }

    var userAgent by remember { mutableStateOf(sharedPreferences.getString("draft_userAgent", "Default") ?: "Default") }
    var customUserAgent by remember { mutableStateOf(sharedPreferences.getString("draft_customUa", "") ?: "") }
    var showCustomUaDialog by remember { mutableStateOf(false) }

    var drmScheme by remember { mutableStateOf(sharedPreferences.getString("draft_drmScheme", "clearkey") ?: "clearkey") }
    var pipeExtras by remember {
        mutableStateOf(PipeExtras.fromJsonString(sharedPreferences.getString("draft_extras", null)))
    }

    LaunchedEffect(
        mediaStreamUrl, cookieValue, refererValue, originValue, drmLicenseUrl,
        userAgent, customUserAgent, drmScheme, pipeExtras
    ) {
        sharedPreferences.edit().apply {
            putString("draft_url", mediaStreamUrl)
            putString("draft_cookie", cookieValue)
            putString("draft_referer", refererValue)
            putString("draft_origin", originValue)
            putString("draft_drmLicense", drmLicenseUrl)
            putString("draft_userAgent", userAgent)
            putString("draft_customUa", customUserAgent)
            putString("draft_drmScheme", drmScheme)
            putString("draft_extras", pipeExtras.toJson().toString())
            apply()
        }
    }

    var showUserAgentDropdown by remember { mutableStateOf(false) }
    var showDrmSchemeDropdown by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showEmptyUrlErrorDialog by remember { mutableStateOf(false) }

    val historyUrls = remember {
        mutableStateListOf<StreamHistoryItem>().apply {
            val savedHistoryStr = sharedPreferences.getString("history_items_array", "[]") ?: "[]"
            try {
                val array = JSONArray(savedHistoryStr)
                for (i in 0 until array.length()) {
                    StreamHistoryItem.fromJson(array.getString(i))?.let { add(it) }
                }
            } catch (e: Exception) {
            }
        }
    }

    val lastNavAt = remember { LongArray(1) }

    fun persistHistory() {
        val array = JSONArray()
        historyUrls.forEach { array.put(it.toJson().toString()) }
        sharedPreferences.edit().putString("history_items_array", array.toString()).apply()
    }

    fun startPlayback(item: StreamHistoryItem) {
        val now = System.currentTimeMillis()
        if (now - lastNavAt[0] < 1000L) return
        lastNavAt[0] = now

        val headers = mutableMapOf<String, String>()
        resolveUserAgent(item.userAgent, item.customUa)?.let { headers["User-Agent"] = it }
        if (item.cookie.isNotBlank()) headers["Cookie"] = item.cookie
        if (item.referer.isNotBlank()) headers["Referer"] = item.referer
        if (item.origin.isNotBlank()) headers["Origin"] = item.origin
        headers.putAll(item.extraHeaders)

        val streamSource = StreamSource(
            url = item.url,
            provider = "Stream",
            headers = headers,
            drm = buildDrmConfig(item.drmScheme, item.drmLicense),
            tokenUrl = item.tokenUrl,
            tokenMatch = item.tokenMatch,
            tokenReplace = item.tokenReplace,
            tokenId = item.tokenId
        )

        mainViewModel.selectStream(streamSource)
        tmdbViewModel.clearSelectedMedia()
        navController.navigate(Routes.VOD_PLAYER) {
            launchSingleTop = true
        }
    }

    fun playFromHistory(item: StreamHistoryItem) {
        mediaStreamUrl = item.url
        cookieValue = item.cookie
        refererValue = item.referer
        originValue = item.origin
        drmLicenseUrl = item.drmLicense
        userAgent = item.userAgent
        if (item.customUa.isNotBlank()) customUserAgent = item.customUa
        drmScheme = item.drmScheme
        pipeExtras = PipeExtras(
            url = item.url,
            headers = item.extraHeaders,
            tokenUrl = item.tokenUrl,
            tokenMatch = item.tokenMatch,
            tokenReplace = item.tokenReplace,
            tokenId = item.tokenId
        )
        showHistoryDialog = false
        startPlayback(item)
    }

    val handlePlay: () -> Unit = handlePlay@{
        val raw = mediaStreamUrl.trim()
        if (raw.isBlank()) {
            showEmptyUrlErrorDialog = true
            return@handlePlay
        }

        var finalUrl = raw
        var finalCookie = cookieValue
        var finalReferer = refererValue
        var finalOrigin = originValue
        var finalUa = userAgent
        var finalCustomUa = customUserAgent
        var finalDrmScheme = drmScheme
        var finalDrmLicense = drmLicenseUrl
        var extras: PipeExtras

        if (raw.contains("|")) {
            val parsed = M3UParser.parseUrlHeaders(raw)
            finalUrl = parsed.cleanUrl
            parsed.headers["cookie"]?.let { finalCookie = it }
            parsed.headers["referer"]?.let { finalReferer = it }
            parsed.headers["origin"]?.let { finalOrigin = it }
            parsed.headers["user-agent"]?.let { v ->
                val match = USER_AGENTS.find { it != "Custom" && it.equals(v, ignoreCase = true) }
                if (match != null) {
                    finalUa = match
                } else {
                    finalUa = "Custom"
                    finalCustomUa = v
                }
            }
            parsed.custom["drm-type"]?.let { finalDrmScheme = normalizeDrmScheme(it) }
            (parsed.custom["drm-url"] ?: parsed.custom["drm-key"])?.let { finalDrmLicense = it }

            extras = PipeExtras(
                url = finalUrl,
                headers = parsed.headers.filterKeys { it.lowercase() !in STD_HEADERS },
                tokenUrl = parsed.custom["tokenurl"],
                tokenMatch = parsed.custom["tokenmatch"],
                tokenReplace = parsed.custom["tokenreplace"],
                tokenId = parsed.custom["tokenid"]?.toIntOrNull()
            )
        } else {
            extras = if (pipeExtras.url == raw) pipeExtras else PipeExtras()
        }

        if (finalUrl.isBlank()) {
            showEmptyUrlErrorDialog = true
            return@handlePlay
        }

        mediaStreamUrl = finalUrl
        cookieValue = finalCookie
        refererValue = finalReferer
        originValue = finalOrigin
        userAgent = finalUa
        if (finalUa == "Custom") customUserAgent = finalCustomUa
        drmScheme = finalDrmScheme
        drmLicenseUrl = finalDrmLicense
        pipeExtras = extras

        val newItem = StreamHistoryItem(
            url = finalUrl,
            cookie = finalCookie,
            referer = finalReferer,
            origin = finalOrigin,
            drmLicense = finalDrmLicense,
            userAgent = finalUa,
            drmScheme = finalDrmScheme,
            tokenUrl = extras.tokenUrl,
            tokenMatch = extras.tokenMatch,
            tokenReplace = extras.tokenReplace,
            tokenId = extras.tokenId,
            customUa = if (finalUa == "Custom") finalCustomUa else "",
            extraHeaders = extras.headers
        )
        historyUrls.removeAll { it.url == finalUrl }
        historyUrls.add(0, newItem)
        while (historyUrls.size > MAX_HISTORY) historyUrls.removeAt(historyUrls.lastIndex)
        persistHistory()

        startPlayback(newItem)
    }

    // ------------------------- Dialogs -------------------------

    if (showEmptyUrlErrorDialog) {
        PremiumDialogScaffold(
            title = "Error",
            subtitle = "Please enter a valid media stream URL.",
            onDismiss = { showEmptyUrlErrorDialog = false }
        ) {}
    }

    if (showCustomUaDialog) {
        PremiumDialogScaffold(
            title = "Custom UserAgent",
            subtitle = "Type the exact User-Agent string to send",
            onDismiss = { showCustomUaDialog = false },
            actions = {
                PremiumButton("Cancel", { showCustomUaDialog = false })
                PremiumButton("OK", {
                    userAgent = "Custom"
                    showCustomUaDialog = false
                }, primary = true)
            }
        ) {
            OutlinedTextField(
                value = customUserAgent,
                onValueChange = { customUserAgent = it },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = BioAccent,
                    unfocusedBorderColor = Color.DarkGray,
                    cursorColor = BioAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                singleLine = true
            )
        }
    }

    if (showUserAgentDropdown) {
        PremiumDialogScaffold(
            title = "Select UserAgent",
            onDismiss = { showUserAgentDropdown = false },
            scrollContent = false
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = USER_AGENTS,
                    key = { index, ua -> "ua_${index}_$ua" }
                ) { _, option ->
                    PremiumOptionRow(
                        title = option,
                        selected = userAgent == option,
                        onClick = {
                            if (option == "Custom") {
                                showCustomUaDialog = true
                            } else {
                                userAgent = option
                            }
                            showUserAgentDropdown = false
                        }
                    )
                }
            }
        }
    }

    if (showDrmSchemeDropdown) {
        PremiumDialogScaffold(
            title = "Select DRM Scheme",
            onDismiss = { showDrmSchemeDropdown = false },
            scrollContent = false
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = DRM_SCHEMES,
                    key = { index, scheme -> "drm_${index}_$scheme" }
                ) { _, option ->
                    PremiumOptionRow(
                        title = option,
                        selected = drmScheme == option,
                        onClick = {
                            drmScheme = option
                            showDrmSchemeDropdown = false
                        }
                    )
                }
            }
        }
    }

    if (showHistoryDialog) {
        PremiumDialogScaffold(
            title = "History",
            subtitle = if (historyUrls.isEmpty())
                "No history found"
            else
                "${historyUrls.size} recent stream${if (historyUrls.size > 1) "s" else ""}",
            onDismiss = { showHistoryDialog = false },
            scrollContent = false
        ) {
            if (historyUrls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nothing here yet.", color = GrayText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = historyUrls.toList(),
                        key = { index, item -> "hist_${index}_${item.url}" }
                    ) { _, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                TvFocusableItem(
                                    onClick = { playFromHistory(item) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.url,
                                        color = WhiteText,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TvIconButton(
                                onClick = {
                                    historyUrls.remove(item)
                                    persistHistory()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    // ------------------------- Main UI -------------------------

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onMenuClick != null) {
                    TvIconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = WhiteText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    text = "Network Stream",
                    color = WhiteText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TvIconButton(
                    onClick = { showHistoryDialog = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = WhiteText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Main content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamTextField(
                    label = "Media Stream URL",
                    value = mediaStreamUrl,
                    onValueChange = { mediaStreamUrl = it }
                )

                if (pipeExtras.url.isNotEmpty() && pipeExtras.url == mediaStreamUrl.trim()) {
                    val notes = buildList {
                        if (pipeExtras.headers.isNotEmpty())
                            add("Headers: " + pipeExtras.headers.keys.joinToString(", "))
                        if (pipeExtras.tokenUrl != null || pipeExtras.tokenId != null) add("Token")
                    }
                    if (notes.isNotEmpty()) {
                        Text(
                            text = "Extra: " + notes.joinToString(" • "),
                            color = GrayText,
                            fontSize = 12.sp
                        )
                    }
                }

                StreamTextField(
                    label = "Cookie Value",
                    value = cookieValue,
                    onValueChange = { cookieValue = it }
                )
                StreamTextField(
                    label = "Referer Value",
                    value = refererValue,
                    onValueChange = { refererValue = it }
                )
                StreamTextField(
                    label = "Origin Value",
                    value = originValue,
                    onValueChange = { originValue = it }
                )
                StreamTextField(
                    label = "DRM License URL",
                    value = drmLicenseUrl,
                    onValueChange = { drmLicenseUrl = it }
                )

                // ── Dropdowns side-by-side (always) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DropdownField(
                        label = "UserAgent",
                        value = userAgent,
                        expanded = showUserAgentDropdown,
                        onExpandedChange = { showUserAgentDropdown = it },
                        modifier = Modifier.weight(1f)
                    )
                    DropdownField(
                        label = "DrmScheme",
                        value = drmScheme,
                        expanded = showDrmSchemeDropdown,
                        onExpandedChange = { showDrmSchemeDropdown = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    StreamPlayButton(onClick = handlePlay)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Custom Play button
// ---------------------------------------------------------------------
@Composable
private fun StreamPlayButton(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF008D94) else BioAccent,
        animationSpec = tween(180),
        label = "playBg"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(180),
        label = "playScale"
    )

    Box(
        modifier = Modifier
            .padding(bottom = 16.dp, top = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(CircleShape)
            .background(bg)
            .tvTouchClick(onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

// ---------------------------------------------------------------------
// Fields
// ---------------------------------------------------------------------

@Composable
fun StreamTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isTv = remember(configuration) {
    val uiMode = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val isTelevision = uiMode == Configuration.UI_MODE_TYPE_TELEVISION   // ← Configuration. prefix
    val pm = context.packageManager
    val isLeanback = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    isTelevision || isLeanback
}

    if (isTv) {
        // TV mode — non-inline edit; TV keyboards are awkward, so we render a
        // display box that opens a real OutlinedTextField on click.
        var isEditing by remember { mutableStateOf(false) }
        val editFocusRequester = remember { FocusRequester() }

        if (isEditing) {
            var hadFocus by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label, color = GrayText) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(editFocusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hadFocus = true
                        } else if (hadFocus) {
                            hadFocus = false
                            isEditing = false
                        }
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { isEditing = false }),
                trailingIcon = {
                    FieldTrailingIcons(
                        value = value,
                        onValueChange = onValueChange,
                        context = context
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = BioAccent,
                    unfocusedBorderColor = Color.DarkGray,
                    cursorColor = BioAccent
                ),
                shape = RoundedCornerShape(8.dp)
            )
            LaunchedEffect(Unit) {
                try { editFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        } else {
            var isFocused by remember { mutableStateOf(false) }
            val borderColor by animateColorAsState(
                targetValue = if (isFocused) BioAccent else Color.DarkGray,
                animationSpec = tween(180),
                label = "fieldBorder"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.hasFocus }
                    .tvTouchClick { isEditing = true }
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(label, color = GrayText)
                    } else {
                        Column {
                            Text(label, color = GrayText, fontSize = 12.sp)
                            Text(
                                value,
                                color = WhiteText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                FieldTrailingIcons(
                    value = value,
                    onValueChange = onValueChange,
                    context = context
                )
            }
        }
    } else {
        // Mobile — standard OutlinedTextField with paste + clear trailing icons.
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = GrayText) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            trailingIcon = {
                FieldTrailingIcons(
                    value = value,
                    onValueChange = onValueChange,
                    context = context
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = BioAccent,
                unfocusedBorderColor = Color.DarkGray,
                cursorColor = BioAccent
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

/**
 * Trailing icons for a text field:
 *   • Paste button — always visible; reads clipboard text into the field.
 *   • Clear button — visible only when the field has a value; empties it.
 */
@Composable
private fun FieldTrailingIcons(
    value: String,
    onValueChange: (String) -> Unit,
    context: Context
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Paste
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .tvTouchClick {
                    val clip = readClipboardText(context)
                    if (!clip.isNullOrBlank()) onValueChange(clip)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = "Paste",
                tint = BioAccent,
                modifier = Modifier.size(20.dp)
            )
        }

        // Clear — only when there is content
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .tvTouchClick { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = GrayText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
// Dropdown — compact; two side-by-side fit on all phone widths
// ---------------------------------------------------------------------
@Composable
fun DropdownField(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused || expanded) BioAccent else Color.DarkGray,
        animationSpec = tween(180),
        label = "ddBorder"
    )

    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.hasFocus }
            .tvTouchClick { onExpandedChange(!expanded) }
            .border(
                width = if (isFocused || expanded) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = label,
                    color = GrayText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    color = WhiteText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.ArrowDropUp
                else Icons.Default.ArrowDropDown,
                tint = GrayText,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
