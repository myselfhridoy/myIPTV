package com.streambd.iptv.ui.screens.stream

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import org.json.JSONObject
import org.json.JSONArray
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.KeyboardActions
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.data.models.DrmConfig
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.components.tvFocusBorder
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.TealAccent
import com.streambd.iptv.ui.viewmodels.MainViewModel

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
    val tokenId: Int? = null
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
                    tokenUrl = if (obj.has("tokenUrl")) obj.optString("tokenUrl") else null,
                    tokenMatch = if (obj.has("tokenMatch")) obj.optString("tokenMatch") else null,
                    tokenReplace = if (obj.has("tokenReplace")) obj.optString("tokenReplace") else null,
                    tokenId = if (obj.has("tokenId")) obj.optInt("tokenId") else null
                )
            } catch (e: Exception) { null }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun StreamScreen(mainViewModel: MainViewModel, tmdbViewModel: com.streambd.iptv.ui.viewmodels.TmdbViewModel, navController: NavController, onMenuClick: (() -> Unit)? = null) {
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
    
    LaunchedEffect(mediaStreamUrl, cookieValue, refererValue, originValue, drmLicenseUrl, userAgent, customUserAgent, drmScheme) {
        sharedPreferences.edit().apply {
            putString("draft_url", mediaStreamUrl)
            putString("draft_cookie", cookieValue)
            putString("draft_referer", refererValue)
            putString("draft_origin", originValue)
            putString("draft_drmLicense", drmLicenseUrl)
            putString("draft_userAgent", userAgent)
            putString("draft_customUa", customUserAgent)
            putString("draft_drmScheme", drmScheme)
            apply()
        }
    }
    
    var showUserAgentDropdown by remember { mutableStateOf(false) }
    var showDrmSchemeDropdown by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    val userAgents = listOf("Default", "Chrome(Android)", "Chrome(PC)", "IE(PC)", "Firefox(PC)", "iPhone", "Nokia", "Custom")
    val drmSchemes = listOf("None", "clearkey", "widevine", "playready")
    
    val historyUrls = remember { 
        mutableStateListOf<StreamHistoryItem>().apply {
            val savedHistoryStr = sharedPreferences.getString("history_items_array", "[]") ?: "[]"
            try {
                val array = JSONArray(savedHistoryStr)
                for (i in 0 until array.length()) {
                    StreamHistoryItem.fromJson(array.getString(i))?.let { add(it) }
                }
            } catch (e: Exception) {}
        }
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
                        items(userAgents) { selectionOption ->
                            androidx.tv.material3.ListItem(
                                selected = false,
                                onClick = {
                                    if (selectionOption == "Custom") {
                                        showCustomUaDialog = true
                                    } else {
                                        userAgent = selectionOption
                                    }
                                    showUserAgentDropdown = false
                                },
                                headlineContent = { Text(selectionOption, color = Color.White) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .tvTouchClick {
                                        if (selectionOption == "Custom") {
                                            showCustomUaDialog = true
                                        } else {
                                            userAgent = selectionOption
                                        }
                                        showUserAgentDropdown = false
                                    },
                                colors = androidx.tv.material3.ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = TealAccent.copy(alpha = 0.1f)
                                ),
                                shape = androidx.tv.material3.ListItemDefaults.shape(RoundedCornerShape(8.dp))
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
                        items(drmSchemes) { selectionOption ->
                            androidx.tv.material3.ListItem(
                                selected = false,
                                onClick = {
                                    drmScheme = selectionOption
                                    showDrmSchemeDropdown = false
                                },
                                headlineContent = { Text(selectionOption, color = Color.White) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .tvTouchClick {
                                        drmScheme = selectionOption
                                        showDrmSchemeDropdown = false
                                    },
                                colors = androidx.tv.material3.ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = TealAccent.copy(alpha = 0.1f)
                                ),
                                shape = androidx.tv.material3.ListItemDefaults.shape(RoundedCornerShape(8.dp))
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
                            items(historyUrls) { item ->
                                ListItem(
                                    selected = false,
                                    onClick = {
                                        mediaStreamUrl = item.url
                                        cookieValue = item.cookie
                                        refererValue = item.referer
                                        originValue = item.origin
                                        drmLicenseUrl = item.drmLicense
                                        userAgent = item.userAgent
                                        drmScheme = item.drmScheme
                                        showHistoryDialog = false
                                        
                                        // Auto-play immediately
                                        val selectedUa = if (item.userAgent == "Custom") customUserAgent else if (item.userAgent != "Default") item.userAgent else null
                                        val drmConfig = if (item.drmScheme != "None" && item.drmScheme.isNotBlank()) {
                                            DrmConfig(type = item.drmScheme, licenseServer = item.drmLicense.takeIf { it.isNotBlank() })
                                        } else null
                                        
                                        val streamSource = com.streambd.iptv.data.models.StreamSource(
                                            url = item.url,
                                            provider = "Stream",
                                            headers = mapOf(
                                                "User-Agent" to (selectedUa ?: ""),
                                                "Cookie" to item.cookie,
                                                "Referer" to item.referer,
                                                "Origin" to item.origin
                                            ).filterValues { it.isNotBlank() },
                                            drm = drmConfig,
                                            tokenUrl = item.tokenUrl,
                                            tokenMatch = item.tokenMatch,
                                            tokenReplace = item.tokenReplace,
                                            tokenId = item.tokenId
                                        )
                                        
                                        mainViewModel.selectStream(streamSource)
                                        tmdbViewModel.clearSelectedMedia()
                                        navController.navigate(com.streambd.iptv.ui.navigation.Routes.VOD_PLAYER)
                                    },
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
                                                val array = JSONArray()
                                                historyUrls.forEach { array.put(it.toJson().toString()) }
                                                sharedPreferences.edit().putString("history_items_array", array.toString()).apply()
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
                                        .tvTouchClick {
                                            mediaStreamUrl = item.url
                                            cookieValue = item.cookie
                                            refererValue = item.referer
                                            originValue = item.origin
                                            drmLicenseUrl = item.drmLicense
                                            userAgent = item.userAgent
                                            drmScheme = item.drmScheme
                                            showHistoryDialog = false
                                            
                                            // Auto-play immediately
                                            val selectedUa = if (item.userAgent == "Custom") customUserAgent else if (item.userAgent != "Default") item.userAgent else null
                                            val drmConfig = if (item.drmScheme != "None" && item.drmScheme.isNotBlank()) {
                                                DrmConfig(type = item.drmScheme, licenseServer = item.drmLicense.takeIf { it.isNotBlank() })
                                            } else null
                                            
                                            val streamSource = com.streambd.iptv.data.models.StreamSource(
                                                url = item.url,
                                                provider = "Stream",
                                                headers = mapOf(
                                                    "User-Agent" to (selectedUa ?: ""),
                                                    "Cookie" to item.cookie,
                                                    "Referer" to item.referer,
                                                    "Origin" to item.origin
                                                ).filterValues { it.isNotBlank() },
                                                drm = drmConfig,
                                                tokenUrl = item.tokenUrl,
                                                tokenMatch = item.tokenMatch,
                                                tokenReplace = item.tokenReplace,
                                                tokenId = item.tokenId
                                            )
                                            
                                            mainViewModel.selectStream(streamSource)
                                            tmdbViewModel.clearSelectedMedia()
                                            navController.navigate(com.streambd.iptv.ui.navigation.Routes.VOD_PLAYER)
                                        }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onMenuClick != null) {
                    androidx.compose.material3.IconButton(onClick = onMenuClick) {
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
                androidx.compose.material3.IconButton(onClick = { showHistoryDialog = true }) {
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StreamTextField("Media Stream URL", mediaStreamUrl) { mediaStreamUrl = it }
                StreamTextField("Cookie Value", cookieValue) { cookieValue = it }
                StreamTextField("Referer Value", refererValue) { refererValue = it }
                StreamTextField("Origin Value", originValue) { originValue = it }
                StreamTextField("DRM License URL", drmLicenseUrl) { drmLicenseUrl = it }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // UserAgent Dropdown
                    DropdownField(
                        label = "UserAgent",
                        value = userAgent,
                        expanded = showUserAgentDropdown,
                        onExpandedChange = { showUserAgentDropdown = it },
                        modifier = Modifier.weight(1f)
                    )

                    // DrmScheme Dropdown
                    DropdownField(
                        label = "DrmScheme",
                        value = drmScheme,
                        expanded = showDrmSchemeDropdown,
                        onExpandedChange = { showDrmSchemeDropdown = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // TV-Friendly Play Button
                val handlePlay = {
                    if (mediaStreamUrl.isBlank()) {
                        Toast.makeText(context, "Please Check the Play URL", Toast.LENGTH_SHORT).show()
                    } else {
                        var finalUrl = mediaStreamUrl
                        var finalCookie = cookieValue
                        var finalReferer = refererValue
                        var finalOrigin = originValue
                        var finalUa = userAgent
                        var finalCustomUa = customUserAgent
                        var finalDrmLicense = drmLicenseUrl
                        
                        var tokenUrl: String? = null
                        var tokenMatch: String? = null
                        var tokenReplace: String? = null
                        var tokenId: Int? = null

                        if (mediaStreamUrl.contains("|")) {
                            val parts = mediaStreamUrl.split("|")
                            finalUrl = parts[0]
                            for (i in 1 until parts.size) {
                                val part = parts[i]
                                val key = part.substringBefore("=").trim().lowercase()
                                val value = part.substringAfter("=").trim()
                                when (key) {
                                    "cookie" -> finalCookie = value
                                    "referer", "http-referer", "referrer" -> finalReferer = value
                                    "origin", "http-origin" -> finalOrigin = value
                                    "user-agent", "http-user-agent" -> {
                                        val matchingUa = userAgents.find { it.equals(value, ignoreCase = true) }
                                        if (matchingUa != null) {
                                            finalUa = matchingUa
                                        } else {
                                            finalUa = "Custom"
                                            finalCustomUa = value
                                        }
                                    }
                                    "tokenurl" -> tokenUrl = value
                                    "tokenmatch" -> tokenMatch = value
                                    "tokenreplace" -> tokenReplace = value
                                    "tokenid" -> tokenId = value.toIntOrNull()
                                }
                            }
                            
                            mediaStreamUrl = finalUrl
                            cookieValue = finalCookie
                            refererValue = finalReferer
                            originValue = finalOrigin
                            userAgent = finalUa
                            if (finalUa == "Custom") customUserAgent = finalCustomUa
                            drmLicenseUrl = finalDrmLicense
                        }

                        val newItem = StreamHistoryItem(
                            finalUrl, finalCookie, finalReferer, finalOrigin, finalDrmLicense, finalUa, drmScheme,
                            tokenUrl, tokenMatch, tokenReplace, tokenId
                        )
                        historyUrls.removeAll { it.url == finalUrl }
                        historyUrls.add(0, newItem)
                        
                        // Save to SharedPreferences
                        val array = JSONArray()
                        historyUrls.forEach { array.put(it.toJson().toString()) }
                        sharedPreferences.edit().putString("history_items_array", array.toString()).apply()
                        
                        val selectedUa = if (finalUa == "Custom") finalCustomUa else if (finalUa != "Default") finalUa else null
                        val drmConfig = if (drmScheme != "None" && drmScheme.isNotBlank()) {
                            DrmConfig(type = drmScheme, licenseServer = finalDrmLicense.takeIf { it.isNotBlank() })
                        } else null
                        
                        val streamSource = com.streambd.iptv.data.models.StreamSource(
                            url = finalUrl,
                            provider = "Stream",
                            headers = mapOf(
                                "User-Agent" to (selectedUa ?: ""),
                                "Cookie" to finalCookie,
                                "Referer" to finalReferer,
                                "Origin" to finalOrigin
                            ).filterValues { it.isNotBlank() },
                            drm = drmConfig,
                            tokenUrl = tokenUrl,
                            tokenMatch = tokenMatch,
                            tokenReplace = tokenReplace,
                            tokenId = tokenId
                        )
                        
                        mainViewModel.selectStream(streamSource)
                        tmdbViewModel.clearSelectedMedia()
                        navController.navigate(com.streambd.iptv.ui.navigation.Routes.VOD_PLAYER)
                    }
                }

                androidx.tv.material3.Button(
                    onClick = handlePlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .tvTouchClick(onClick = handlePlay),
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = TealAccent,
                        focusedContainerColor = Color(0xFF008D94)
                    ),
                    shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PLAY STREAM", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

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
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label, color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (!state.isFocused) {
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
                        Text(value, color = Color.White)
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
