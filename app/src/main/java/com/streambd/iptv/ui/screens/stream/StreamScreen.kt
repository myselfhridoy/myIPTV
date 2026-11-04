package com.streambd.iptv.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.StreamSource
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.ui.components.AppButton
import com.streambd.iptv.ui.components.AppIconButton
import com.streambd.iptv.ui.components.AppTextField
import com.streambd.iptv.ui.components.AppTopBar
import com.streambd.iptv.ui.components.CollapsibleSection
import com.streambd.iptv.ui.components.PremiumButton
import com.streambd.iptv.ui.components.PremiumDialogScaffold
import com.streambd.iptv.ui.components.PremiumOptionRow
import com.streambd.iptv.ui.components.SelectField
import com.streambd.iptv.ui.components.TvFocusableItem
import com.streambd.iptv.ui.components.initialFocus
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import com.streambd.iptv.utils.M3UParser
import org.json.JSONArray

/**
 * "Network Stream": play any URL with custom headers / User-Agent / DRM.
 *
 * ALL state, draft saving, history, URL-pipe parsing and the hand-off to
 * the VOD player are copied unchanged from the old screen (see the block
 * between "STATE & LOGIC" markers). Only the UI is new:
 *   • URL field first; Cookie / Referer / Origin / User-Agent / DRM sit in a
 *     collapsible "Advanced settings" section (open automatically when the saved
 *     draft already uses any of them)
 *   • full-width Play button instead of a round one at the right edge
 *   • themed fields, selectors, dialogs; TV: URL field auto-focused
 */
@Composable
fun StreamScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController,
    onMenuClick: (() -> Unit)? = null
) {
    // ======================= STATE & LOGIC (unchanged) =======================
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

    // ============================= END LOGIC =============================

    val scheme = MaterialTheme.colorScheme
    val d = AppTheme.dimens

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
            AppTextField(
                value = customUserAgent,
                onValueChange = { customUserAgent = it },
                label = "User-Agent",
                imeAction = ImeAction.Done,
                onDone = {
                    userAgent = "Custom"
                    showCustomUaDialog = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .initialFocus(tvOnly = false)
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
                        modifier = if (userAgent == option) Modifier.initialFocus() else Modifier,
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
                        modifier = if (drmScheme == option) Modifier.initialFocus() else Modifier,
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
                    Text(
                        text = "Nothing here yet.",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                            TvFocusableItem(
                                onClick = { playFromHistory(item) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = item.url,
                                    color = scheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            AppIconButton(
                                icon = Icons.Default.Delete,
                                contentDescription = "Delete from history",
                                onClick = {
                                    historyUrls.remove(item)
                                    persistHistory()
                                },
                                tint = scheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // ------------------------- Main UI -------------------------

    // Open "Headers & DRM" right away when the saved draft already uses it.
    var advancedExpanded by remember {
        mutableStateOf(
            cookieValue.isNotBlank() || refererValue.isNotBlank() || originValue.isNotBlank() ||
                drmLicenseUrl.isNotBlank() || userAgent != "Default" ||
                pipeExtras.headers.isNotEmpty()
        )
    }
    val advancedSummary = listOfNotNull(
        "Cookie".takeIf { cookieValue.isNotBlank() },
        "Referer".takeIf { refererValue.isNotBlank() },
        "Origin".takeIf { originValue.isNotBlank() },
        "UA: $userAgent".takeIf { userAgent != "Default" },
        "DRM: $drmScheme".takeIf { !drmScheme.equals("None", ignoreCase = true) }
    ).joinToString(" • ").ifEmpty { "Headers, User-Agent & DRM" }

    // TV: Back opens the drawer, so no hamburger button on TV.
    val isTv = LocalDeviceProfile.current.isTv
    val menuLeading: (@Composable () -> Unit)? = if (onMenuClick != null && !isTv) {
        { AppIconButton(Icons.Default.Menu, "Menu", onMenuClick) }
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        AppTopBar(
            title = "Network Stream",
            leading = menuLeading,
            actions = {
                AppIconButton(
                    icon = Icons.Default.History,
                    contentDescription = "History",
                    onClick = { showHistoryDialog = true }
                )
            }
        )
        HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = d.screenPaddingH + 4.dp)
                    .padding(top = d.spaceLg, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StreamTextField(
                    label = "Media Stream URL",
                    value = mediaStreamUrl,
                    onValueChange = { mediaStreamUrl = it },
                    // TV: land on the URL field.
                    modifier = Modifier.initialFocus()
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
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                CollapsibleSection(
                    title = "Advanced settings",
                    summary = advancedSummary,
                    expanded = advancedExpanded,
                    onToggle = { advancedExpanded = !advancedExpanded }
                ) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SelectField(
                            label = "UserAgent",
                            value = userAgent,
                            onClick = { showUserAgentDropdown = true },
                            modifier = Modifier.weight(1f)
                        )
                        SelectField(
                            label = "DrmScheme",
                            value = drmScheme,
                            onClick = { showDrmSchemeDropdown = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    StreamTextField(
                        label = "DRM License URL",
                        value = drmLicenseUrl,
                        onValueChange = { drmLicenseUrl = it }
                    )
                }

                Spacer(Modifier.height(4.dp))

                AppButton(
                    text = "Play stream",
                    onClick = handlePlay,
                    icon = Icons.Default.PlayArrow,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
