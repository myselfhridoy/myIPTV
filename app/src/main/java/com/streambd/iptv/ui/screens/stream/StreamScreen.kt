package com.streambd.iptv.ui.screens.stream

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import com.streambd.iptv.data.models.DrmConfig
import com.streambd.iptv.data.models.StreamSource
import com.streambd.iptv.ui.components.tvFocusBorder
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.TealAccent
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.utils.M3UParser
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------
// Constants / helpers
// ---------------------------------------------------------------------

private val USER_AGENTS = listOf("Default", "Chrome(Android)", "Chrome(PC)", "IE(PC)", "Firefox(PC)", "iPhone", "Nokia", "Custom")
private val DRM_SCHEMES = listOf("None", "clearkey", "widevine", "playready")

// ড্রপডাউনের নাম আসল User-Agent স্ট্রিং নয়। আগে "Chrome(Android)" লেখাটাই হেডার হিসেবে যেত,
// এখন আসল UA স্ট্রিং যায়। (নিজের UA দিলে সেটাই যায়।)
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
    // kid:key জোড়া license server নয়, এটা rawKeyPair (M3UParser এর মতোই)
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

/** পাইপ (|) থেকে পাওয়া কাস্টম হেডার/টোকেন, যেগুলোর আলাদা UI ফিল্ড নেই। */
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
    // নতুন: আগে এগুলো হিস্ট্রিতে সেভ হতো না, তাই রিপ্লে করলে হারিয়ে যেত
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun StreamScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: com.streambd.iptv.ui.viewmodels.TmdbViewModel,
    navController: NavController,
    onMenuClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("stream_history_prefs", android.content.Context.MODE_PRIVATE)

    var mediaStreamUrl by remember { mutableStateOf(sharedPreferences.getString("draft_url", "") ?: "") }
    var cookieValue by remember { mutableStateOf(sharedPreferences.getString("draft_cookie", "") ?: "") }
    var refererValue by remember { mutableStateOf(sharedPreferences.getString("draft_referer", "") ?: "") }
    var originValue by remember { mutableStateOf(sharedPreferences.getString("draft_origin", "") ?: "") }
    var drmLicenseUrl by remember { mutableStateOf(sharedPreferences.getString("draft_drmLicense", "") ?: "") }

    var userAgent by remember { mutableStateOf(sharedPreferences.getString("draft_userAgent", "Default") ?: "Default") }
    var customUserAgent by remember { mutableStateOf(sharedPreferences.getString("draft_customUa", "") ?: "") }
    var showCustomUaDialog by remember { mutableStateOf(false) }

    var drmScheme by remember { mutableStateOf(sharedPreferences.getString("draft_drmScheme", "clearkey") ?: "clearkey") }

    var pipeExtras by remember { mutableStateOf(PipeExtras.fromJsonString(sharedPreferences.getString("draft_extras", null))) }

    LaunchedEffect(mediaStreamUrl, cookieValue, refererValue, originValue, drmLicenseUrl, userAgent, customUserAgent, drmScheme, pipeExtras) {
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

    // Button এর onClick আর tvTouchClick দুটো একসাথে ফায়ার করলে দুইবার navigate হতো, তাই ডিবাউন্স
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
        navController.navigate(Routes.VOD_PLAYER)
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
            // M3UParser এর পার্সার ব্যবহার: ':' বা '=' সেপারেটর, '&' জোড়া হেডার, drm*/token* সব হ্যান্ডেল হয়
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
            // পাইপ ছাড়া: একই URL হলে আগের পাইপ-এক্সট্রা (হিডেন হেডার/টোকেন) রেখে দেওয়া হয়
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
        AlertDialog(
            onDismissRequest = { showEmptyUrlErrorDialog = false },
            title = { Text("Error", color = Color.White) },
            text = { Text("Please enter a valid media stream URL.", color = Color.White) },
            confirmButton = {
                TextButton(onClick = { showEmptyUrlErrorDialog = false }) {
                    Text("OK", color = TealAccent)
                }
            },
            containerColor = Color(0xFF1F2430)
        )
    }

    if (showCustomUaDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUaDialog = false },
            title = { Text("Custom UserAgent", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = customUserAgent,
                    onValueChange = { customUserAgent = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    userAgent = "Custom"
                    showCustomUaDialog = false
                }) {
                    Text("OK", color = TealAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUaDialog = false }) {
                    Text("CANCEL", color = TealAccent)
                }
            },
            containerColor = Color(0xFF1F2430)
        )
    }

    if (showUserAgentDropdown) {
        Dialog(onDismissRequest = { showUserAgentDropdown = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF151A25),
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select UserAgent", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(USER_AGENTS) { selectionOption ->
                            val select = {
                                if (selectionOption == "Custom") {
                                    showCustomUaDialog = true
                                } else {
                                    userAgent = selectionOption
                                }
                                showUserAgentDropdown = false
                            }
                            ListItem(
                                selected = false,
                                onClick = select,
                                headlineContent = { androidx.tv.material3.Text(selectionOption, color = Color.White) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .tvTouchClick { select() },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = TealAccent.copy(alpha = 0.1f)
                                ),
                                shape = ListItemDefaults.shape(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDrmSchemeDropdown) {
        Dialog(onDismissRequest = { showDrmSchemeDropdown = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF151A25),
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select DRM Scheme", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(DRM_SCHEMES) { selectionOption ->
                            val select = {
                                drmScheme = selectionOption
                                showDrmSchemeDropdown = false
                            }
                            ListItem(
                                selected = false,
                                onClick = select,
                                headlineContent = { androidx.tv.material3.Text(selectionOption, color = Color.White) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .tvTouchClick { select() },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = TealAccent.copy(alpha = 0.1f)
                                ),
                                shape = ListItemDefaults.shape(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }
        }
    }

    if (showHistoryDialog) {
        Dialog(onDismissRequest = { showHistoryDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF151A25),
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("History", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (historyUrls.isEmpty()) {
                        Text("No history found.", color = Color.Gray)
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(historyUrls.toList()) { item ->
                                ListItem(
                                    selected = false,
                                    onClick = { playFromHistory(item) },
                                    headlineContent = {
                                        androidx.tv.material3.Text(
                                            text = item.url,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                historyUrls.remove(item)
                                                persistHistory()
                                            }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                        }
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent,
                                        focusedContainerColor = TealAccent.copy(alpha = 0.1f)
                                    ),
                                    shape = ListItemDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .tvTouchClick { playFromHistory(item) }
                                )
                                HorizontalDivider(color = Color.DarkGray)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { showHistoryDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("CLOSE", color = TealAccent)
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
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onMenuClick != null) {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White, modifier = Modifier.tvFocusBorder(CircleShape))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    text = "Network Stream",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showHistoryDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = Color.White,
                        modifier = Modifier
                            .tvFocusBorder(CircleShape)
                            .background(com.streambd.iptv.ui.theme.SurfaceDark, CircleShape)
                            .padding(8.dp)
                    )
                }
            }

            // Main Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp) // TV overscan padding
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamTextField("Media Stream URL", mediaStreamUrl) { mediaStreamUrl = it }

                // পাইপ থেকে আসা লুকানো হেডার/টোকেন থাকলে দেখানো হয়, যাতে বোঝা যায় সেগুলো প্রয়োগ হবে
                if (pipeExtras.url.isNotEmpty() && pipeExtras.url == mediaStreamUrl.trim()) {
                    val notes = buildList {
                        if (pipeExtras.headers.isNotEmpty()) add("Headers: " + pipeExtras.headers.keys.joinToString(", "))
                        if (pipeExtras.tokenUrl != null || pipeExtras.tokenId != null) add("Token")
                    }
                    if (notes.isNotEmpty()) {
                        Text(
                            text = "Extra: " + notes.joinToString(" • "),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                StreamTextField("Cookie Value", cookieValue) { cookieValue = it }
                StreamTextField("Referer Value", refererValue) { refererValue = it }
                StreamTextField("Origin Value", originValue) { originValue = it }
                StreamTextField("DRM License URL", drmLicenseUrl) { drmLicenseUrl = it }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    androidx.tv.material3.Button(
                        onClick = handlePlay,
                        modifier = Modifier
                            .padding(bottom = 16.dp, top = 8.dp)
                            .tvTouchClick(onClick = handlePlay),
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = TealAccent,
                            focusedContainerColor = Color(0xFF008D94)
                        ),
                        shape = androidx.tv.material3.ButtonDefaults.shape(CircleShape),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
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
        val isTelevision = uiMode == Configuration.UI_MODE_TYPE_TELEVISION
        val pm = context.packageManager
        val isLeanback = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        isTelevision || isLeanback || isLandscape
    }

    if (isTv) {
        var isEditing by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }

        if (isEditing) {
            // শুরুতে onFocusChanged এ isFocused=false আসে; সেটায় সাথে সাথে এডিট বন্ধ হওয়া আটকাতে hadFocus গার্ড
            var hadFocus by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label, color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = TealAccent,
                    unfocusedBorderColor = Color.DarkGray,
                    cursorColor = TealAccent
                ),
                shape = RoundedCornerShape(8.dp)
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        } else {
            var isFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .tvTouchClick { isEditing = true }
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) TealAccent else Color.DarkGray,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                if (value.isEmpty()) {
                    Text(label, color = Color.Gray)
                } else {
                    Column {
                        Text(label, color = Color.Gray, fontSize = 12.sp)
                        Text(value, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = Color.DarkGray,
                cursorColor = TealAccent
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = Color.Gray) },
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.Gray
                )
            },
            singleLine = true,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = Color.DarkGray,
                cursorColor = TealAccent
            ),
            shape = RoundedCornerShape(8.dp)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent)
                .clickable { onExpandedChange(!expanded) }
        )
    }
}
