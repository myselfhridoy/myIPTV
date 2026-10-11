package com.streambd.iptv.ui.screens.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.streambd.iptv.utils.M3UParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private val ADMIN_ACCENT = Color(0xFF00E676)
private val ADMIN_BG = Color(0xFF141920)
private val ADMIN_SURFACE = Color(0xFF0F131A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConverterScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var jsonUrl by remember { mutableStateOf("") }
    var arrayPath by remember { mutableStateOf("channels") }

    var streamField by remember { mutableStateOf("stream_name") }
    var titleField by remember { mutableStateOf("title") }
    var logoField by remember { mutableStateOf("logo") }
    var categoryField by remember { mutableStateOf("category") }

    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("|tokenUrl=/2") }
    var requestHeaders by remember { mutableStateOf("") }

    var outputM3u by remember { mutableStateOf("") }
    var proxyUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin JSON -> M3U Converter", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ADMIN_SURFACE)
            )
        },
        containerColor = ADMIN_BG
    ) { padding ->
        var selectedTabIndex by remember { mutableStateOf(0) }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = ADMIN_SURFACE,
                contentColor = ADMIN_ACCENT
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("JSON Converter", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("VOD JS Tester", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Live JS Tester", fontWeight = FontWeight.Bold) }
                )
            }

            when (selectedTabIndex) {
                0 -> JsonConverterTab(
                    jsonUrl = jsonUrl,
                    onJsonUrlChange = { jsonUrl = it },
                    arrayPath = arrayPath,
                    onArrayPathChange = { arrayPath = it },
                    streamField = streamField,
                    onStreamFieldChange = { streamField = it },
                    titleField = titleField,
                    onTitleFieldChange = { titleField = it },
                    logoField = logoField,
                    onLogoFieldChange = { logoField = it },
                    categoryField = categoryField,
                    onCategoryFieldChange = { categoryField = it },
                    prefix = prefix,
                    onPrefixChange = { prefix = it },
                    suffix = suffix,
                    onSuffixChange = { suffix = it },
                    requestHeaders = requestHeaders,
                    onRequestHeadersChange = { requestHeaders = it },
                    outputM3u = outputM3u,
                    proxyUrl = proxyUrl,
                    isLoading = isLoading,
                    onConvert = {
                        if (jsonUrl.isNotEmpty()) {
                            isLoading = true
                            scope.launch {
                                try {
                                    val result = fetchAndConvert(
                                        jsonUrl, arrayPath, streamField, titleField,
                                        logoField, categoryField, prefix, suffix, requestHeaders
                                    )
                                    outputM3u = result.first
                                    proxyUrl = result.second
                                } catch (e: Throwable) {
                                    outputM3u = "Error: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Please enter a valid JSON URL.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    context = context
                )

                1 -> VodJsTesterTab(context = context, scope = scope)
                2 -> LiveJsTesterTab(context = context, scope = scope)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Tab 0 — JSON → M3U Converter
// ─────────────────────────────────────────────────────────────
@Composable
private fun JsonConverterTab(
    jsonUrl: String,
    onJsonUrlChange: (String) -> Unit,
    arrayPath: String,
    onArrayPathChange: (String) -> Unit,
    streamField: String,
    onStreamFieldChange: (String) -> Unit,
    titleField: String,
    onTitleFieldChange: (String) -> Unit,
    logoField: String,
    onLogoFieldChange: (String) -> Unit,
    categoryField: String,
    onCategoryFieldChange: (String) -> Unit,
    prefix: String,
    onPrefixChange: (String) -> Unit,
    suffix: String,
    onSuffixChange: (String) -> Unit,
    requestHeaders: String,
    onRequestHeadersChange: (String) -> Unit,
    outputM3u: String,
    proxyUrl: String,
    isLoading: Boolean,
    onConvert: () -> Unit,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AdminTextField(
            value = jsonUrl,
            onValueChange = onJsonUrlChange,
            label = "JSON API URL"
        )

        AdminTextField(
            value = arrayPath,
            onValueChange = onArrayPathChange,
            label = "Array Path (e.g. 'channels')"
        )

        Text("Field Mappings", color = Color.White, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminTextField(
                value = streamField,
                onValueChange = onStreamFieldChange,
                label = "Stream Field",
                modifier = Modifier.weight(1f)
            )
            AdminTextField(
                value = titleField,
                onValueChange = onTitleFieldChange,
                label = "Title Field",
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminTextField(
                value = logoField,
                onValueChange = onLogoFieldChange,
                label = "Logo Field",
                modifier = Modifier.weight(1f)
            )
            AdminTextField(
                value = categoryField,
                onValueChange = onCategoryFieldChange,
                label = "Category Field",
                modifier = Modifier.weight(1f)
            )
        }

        Text("URL Modifiers", color = Color.White, fontWeight = FontWeight.Bold)

        AdminTextField(
            value = prefix,
            onValueChange = onPrefixChange,
            label = "Stream URL Prefix"
        )
        AdminTextField(
            value = suffix,
            onValueChange = onSuffixChange,
            label = "Stream URL Suffix"
        )

        Text("API Request Headers (Optional)", color = Color.White, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = requestHeaders,
            onValueChange = onRequestHeadersChange,
            label = { Text("One per line, e.g. User-Agent: Mozilla/5.0", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ADMIN_ACCENT,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Button(
            onClick = onConvert,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADMIN_ACCENT),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Fetch & Convert to M3U", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        if (proxyUrl.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            CopyRow(
                title = "Magic Proxy Link (For IPTV.json):",
                text = proxyUrl,
                textColor = ADMIN_ACCENT,
                context = context
            )
        }

        if (outputM3u.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            LargeOutputDisplay(
                title = "M3U Output:",
                fullText = outputM3u,
                textColor = Color(0xFFAAAAAA),
                context = context,
                height = 300.dp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Tab 1 — VOD JS Tester
// ─────────────────────────────────────────────────────────────
@Composable
private fun VodJsTesterTab(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var jsCode by remember { mutableStateOf("") }
    var testTmdbId by remember { mutableStateOf("293660") }
    var testType by remember { mutableStateOf("movie") }
    var testSeason by remember { mutableStateOf("") }
    var testEpisode by remember { mutableStateOf("") }
    var jsOutput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("QuickJS Addon Tester", color = Color.White, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = jsCode,
            onValueChange = { jsCode = it },
            label = { Text("Paste JS Code Here", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ADMIN_ACCENT,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminTextField(
                value = testType,
                onValueChange = { testType = it },
                label = "Type",
                modifier = Modifier.weight(1f)
            )
            AdminTextField(
                value = testTmdbId,
                onValueChange = { testTmdbId = it },
                label = "TMDB ID",
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminTextField(
                value = testSeason,
                onValueChange = { testSeason = it },
                label = "Season (TV)",
                modifier = Modifier.weight(1f)
            )
            AdminTextField(
                value = testEpisode,
                onValueChange = { testEpisode = it },
                label = "Episode (TV)",
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = {
                if (jsCode.isNotBlank()) {
                    isExecuting = true
                    scope.launch {
                        try {
                            val (resultList, executionLogs) = try {
                                com.streambd.iptv.utils.QuickJsAddonManager.testAddon(
                                    context,
                                    jsCode,
                                    testType,
                                    testTmdbId.toIntOrNull() ?: 12345,
                                    testSeason.toIntOrNull(),
                                    testEpisode.toIntOrNull()
                                )
                            } catch (e: Throwable) {
                                Pair(emptyList(), "Exception: ${e.message}")
                            }

                            jsOutput = if (resultList.isNotEmpty()) {
                                "LOGS:\n$executionLogs\n\nSTREAMS:\n" +
                                    resultList.joinToString("\n\n") {
                                        "Provider: ${it.provider}\nURL: ${it.url}\nQuality: ${it.quality}"
                                    }
                            } else {
                                "LOGS:\n$executionLogs\n\nNo output generated or error occurred."
                            }
                        } catch (t: Throwable) {
                            jsOutput = "Error: ${t.message}"
                        } finally {
                            isExecuting = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADMIN_ACCENT),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isExecuting) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Run JS", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        if (jsOutput.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            LargeOutputDisplay(
                title = "Output:",
                fullText = jsOutput,
                context = context,
                height = 250.dp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Tab 2 — Live JS Tester
// ─────────────────────────────────────────────────────────────
@Composable
private fun LiveJsTesterTab(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var liveJsCode by remember { mutableStateOf("") }
    var liveOutput by remember { mutableStateOf("") }
    var isLiveExecuting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("QuickJS Playlist Parser Tester", color = Color.White, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = liveJsCode,
            onValueChange = { liveJsCode = it },
            label = { Text("Paste JS Parser Code Here", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ADMIN_ACCENT,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        )

        Button(
            onClick = {
                if (liveJsCode.isNotBlank()) {
                    isLiveExecuting = true
                    scope.launch {
                        try {
                            val result = try {
                                val parsed = com.streambd.iptv.utils.QuickJsPlaylistParser
                                    .executeJsPlaylist(context, liveJsCode)
                                when (parsed) {
                                    is com.streambd.iptv.utils.JsPlaylistResult.Success -> parsed.m3u
                                    is com.streambd.iptv.utils.JsPlaylistResult.Error -> "Error: ${parsed.message}"
                                    is com.streambd.iptv.utils.JsPlaylistResult.Empty -> "Result was empty"
                                }
                            } catch (e: Throwable) {
                                "Exception: ${e.message}"
                            }

                            liveOutput = result
                        } catch (t: Throwable) {
                            liveOutput = "Error: ${t.message}"
                        } finally {
                            isLiveExecuting = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADMIN_ACCENT),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLiveExecuting) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Run Live JS", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        if (liveOutput.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            LargeOutputDisplay(
                title = "Output:",
                fullText = liveOutput,
                context = context,
                height = 250.dp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────
@Composable
private fun AdminTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.Gray) },
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ADMIN_ACCENT,
            unfocusedBorderColor = Color.DarkGray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
}

@Composable
private fun LargeOutputDisplay(
    title: String,
    fullText: String,
    context: Context,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 260.dp,
    textColor: Color = Color(0xFFAAAAAA)
) {
    if (fullText.isEmpty()) return

    val charCount = fullText.length
    val lineCount = remember(fullText) { fullText.count { it == '\n' } + 1 }
    val sizeKb = remember(fullText) { fullText.toByteArray(Charsets.UTF_8).size / 1024.0 }

    val maxPreviewChars = 4000
    val isTruncated = charCount > maxPreviewChars
    val previewText = remember(fullText) {
        if (isTruncated) {
            fullText.take(maxPreviewChars) + "\n\n⚠️ --- [ PREVIEW TRUNCATED: Showing first 4,000 of $charCount chars ($lineCount lines). Click 'Copy Full' to copy 100% of output ] ---"
        } else {
            fullText
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "%.1f KB • %d lines • %d chars".format(sizeKb, lineCount, charCount),
                    color = if (isTruncated) ADMIN_ACCENT else Color.Gray,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Copied Output", fullText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(
                        context,
                        "Copied full output (${String.format("%.1f", sizeKb)} KB) to clipboard!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ADMIN_ACCENT),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy Full",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isTruncated) "Copy Full" else "Copy",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .background(Color(0xFF0F131A), RoundedCornerShape(8.dp))
                .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SelectionContainer {
                Text(
                    text = previewText,
                    color = textColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun CopyRow(
    title: String,
    text: String,
    textColor: Color,
    context: Context,
    height: androidx.compose.ui.unit.Dp = 120.dp
) {
    val charCount = text.length
    val maxPreviewChars = 4000
    val isTruncated = charCount > maxPreviewChars
    val previewText = remember(text) {
        if (isTruncated) {
            text.take(maxPreviewChars) + "\n\n⚠️ --- [ PREVIEW TRUNCATED: $charCount chars. Click copy icon to copy full text ] ---"
        } else {
            text
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Color.White)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(Color(0xFF0F131A), RoundedCornerShape(8.dp))
            .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SelectionContainer {
            Text(
                text = previewText,
                color = textColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )
        }
    }
}

private suspend fun fetchAndConvert(
    url: String,
    arrayPath: String,
    streamField: String,
    titleField: String,
    logoField: String,
    categoryField: String,
    prefix: String,
    suffix: String,
    headers: String
): Pair<String, String> = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val requestBuilder = Request.Builder().url(url)

        if (headers.isNotEmpty()) {
            headers.split("\n").forEach { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    requestBuilder.addHeader(parts[0].trim(), parts[1].trim())
                }
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) return@withContext Pair("Error: ${response.code}", "")

        val body = response.body?.string() ?: return@withContext Pair("Empty response", "")

        var rootElement: Any? = null
        if (body.trim().startsWith("[")) {
            rootElement = JSONArray(body)
        } else if (body.trim().startsWith("{")) {
            val obj = JSONObject(body)
            if (arrayPath.isNotEmpty() && obj.has(arrayPath)) {
                rootElement = obj.opt(arrayPath)
            } else {
                rootElement = obj
            }
        }

        if (rootElement == null) {
            return@withContext Pair(
                "Could not parse JSON. Make sure Array Path is correct or leave it empty.",
                ""
            )
        }

        val sb = StringBuilder()
        sb.append("#EXTM3U\n")

        extractChannelsRecursively(
            rootElement, "", streamField, titleField, logoField, categoryField,
            prefix, suffix, sb
        )

        val uriBuilder = android.net.Uri.Builder()
            .scheme("myiptv")
            .authority("proxy")
            .appendQueryParameter("url", url)
            .appendQueryParameter("array_path", arrayPath)
            .appendQueryParameter("stream_field", streamField)
            .appendQueryParameter("title_field", titleField)
            .appendQueryParameter("logo_field", logoField)
            .appendQueryParameter("category_field", categoryField)
            .appendQueryParameter("prefix", prefix)
            .appendQueryParameter("suffix", suffix)

        if (headers.isNotEmpty()) {
            uriBuilder.appendQueryParameter("headers", headers)
        }

        return@withContext Pair(sb.toString(), uriBuilder.build().toString())
    } catch (e: Exception) {
        return@withContext Pair("Error: ${e.message}", "")
    }
}

private fun extractChannelsRecursively(
    element: Any?,
    currentCategory: String,
    streamField: String,
    titleField: String,
    logoField: String,
    categoryField: String,
    prefix: String,
    suffix: String,
    sb: StringBuilder
) {
    if (element is JSONArray) {
        for (i in 0 until element.length()) {
            extractChannelsRecursively(
                element.opt(i), currentCategory, streamField, titleField,
                logoField, categoryField, prefix, suffix, sb
            )
        }
    } else if (element is JSONObject) {
        val hasDirectStream = element.has(streamField) ||
            element.has("url") || element.has("stream_url") || element.has("link")
        val hasSourcesObj = element.has("sources") && element.opt("sources") is JSONObject

        if (hasDirectStream || hasSourcesObj) {
            val title = element.optString(
                titleField,
                element.optString(
                    "name",
                    element.optString("title", element.optString("tv_name", "Unknown"))
                )
            ).takeIf { it.isNotBlank() } ?: "Unknown"

            val logo = M3UParser.extractBestLogo(element, logoField)
            val category = element.optString(
                categoryField,
                element.optString(
                    "group-title",
                    element.optString("category", element.optString("group", currentCategory))
                )
            ).takeIf { it.isNotBlank() } ?: currentCategory

            var stream = element.optString(
                streamField,
                element.optString(
                    "url",
                    element.optString("stream_url", element.optString("link", ""))
                )
            ).trim()

            if (stream.isEmpty() && hasSourcesObj) {
                try {
                    val sObj = element.getJSONObject("sources")
                    val firstKey = sObj.keys().asSequence().firstOrNull()
                    if (firstKey != null) stream = sObj.optString(firstKey, "").trim()
                } catch (_: Exception) {
                }
            }

            if (stream.isNotBlank()) {
                if (streamField == "stream_name" && prefix.contains("channel=")) {
                    stream = java.net.URLEncoder.encode(stream, "UTF-8")
                }
                val finalUrl = "$prefix$stream$suffix"
                sb.append("#EXTINF:-1 tvg-logo=\"$logo\" group-title=\"$category\",$title\n")
                sb.append("$finalUrl\n")
            }
        } else {
            val nextCategory = element.optString(categoryField, "")
                .takeIf { it.isNotBlank() } ?: currentCategory
            val keys = element.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                extractChannelsRecursively(
                    element.opt(key), nextCategory, streamField, titleField,
                    logoField, categoryField, prefix, suffix, sb
                )
            }
        }
    }
}
