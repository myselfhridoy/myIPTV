package com.streambd.iptv.ui.screens.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import com.streambd.iptv.utils.M3UParser

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F131A))
            )
        },
        containerColor = Color(0xFF141920)
    ) { padding ->
        var selectedTabIndex by remember { mutableStateOf(0) }
        
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF0F131A),
                contentColor = Color(0xFF00E676)
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("JSON Converter", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("JS Tester", fontWeight = FontWeight.Bold) }
                )
            }
            
            if (selectedTabIndex == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = jsonUrl,
                onValueChange = { jsonUrl = it },
                label = { Text("JSON API URL", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E676),
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = arrayPath,
                    onValueChange = { arrayPath = it },
                    label = { Text("Array Path (e.g. 'channels')", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E676),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }

            Text("Field Mappings", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = streamField,
                    onValueChange = { streamField = it },
                    label = { Text("Stream Field", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E676),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                OutlinedTextField(
                    value = titleField,
                    onValueChange = { titleField = it },
                    label = { Text("Title Field", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E676),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = logoField,
                    onValueChange = { logoField = it },
                    label = { Text("Logo Field", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E676),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                OutlinedTextField(
                    value = categoryField,
                    onValueChange = { categoryField = it },
                    label = { Text("Category Field", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E676),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }

            Text("URL Modifiers", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

            OutlinedTextField(
                value = prefix,
                onValueChange = { prefix = it },
                label = { Text("Stream URL Prefix", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E676),
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            OutlinedTextField(
                value = suffix,
                onValueChange = { suffix = it },
                label = { Text("Stream URL Suffix", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E676),
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Text("API Request Headers (Optional)", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

            OutlinedTextField(
                value = requestHeaders,
                onValueChange = { requestHeaders = it },
                label = { Text("One per line, e.g. User-Agent: Mozilla/5.0", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E676),
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Button(
                onClick = {
                    if (jsonUrl.isNotEmpty()) {
                        isLoading = true
                        scope.launch {
                            val result = fetchAndConvert(
                                jsonUrl, arrayPath, streamField, titleField, logoField, categoryField, prefix, suffix, requestHeaders
                            )
                            outputM3u = result.first
                            proxyUrl = result.second
                            isLoading = false
                        }
                    } else {
                        Toast.makeText(context, "Please enter a valid JSON URL.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Fetch & Convert to M3U", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            if (proxyUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Magic Proxy Link (For IPTV.json):", color = Color.White, fontWeight = FontWeight.Bold)
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Proxy URL", proxyUrl)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Color.White)
                    }
                }
                
                OutlinedTextField(
                    value = proxyUrl,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E676),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color(0xFF00E676),
                        unfocusedTextColor = Color(0xFF00E676)
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )
            }

            if (outputM3u.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("M3U Output:", color = Color.White, fontWeight = FontWeight.Bold)
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("M3U Playlist", outputM3u)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Color.White)
                    }
                }
                
                OutlinedTextField(
                    value = outputM3u,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.DarkGray,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color(0xFFAAAAAA),
                        unfocusedTextColor = Color(0xFFAAAAAA)
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
            }
        } else if (selectedTabIndex == 1) {
                var jsCode by remember { mutableStateOf("") }
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
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                    
                    Button(
                        onClick = {
                            if (jsCode.isNotBlank()) {
                                isExecuting = true
                                scope.launch {
                                    val result = com.streambd.iptv.utils.QuickJsPlaylistParser.executeJsPlaylist(context, jsCode)
                                    jsOutput = when (result) {
                                        is com.streambd.iptv.utils.JsPlaylistResult.Success -> result.m3u
                                        is com.streambd.iptv.utils.JsPlaylistResult.Error -> "Error: ${result.message}"
                                        is com.streambd.iptv.utils.JsPlaylistResult.Empty -> "No output generated."
                                    }
                                    isExecuting = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Run JS", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    if (jsOutput.isNotEmpty()) {
                        Text("Output:", color = Color.White, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = jsOutput,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.DarkGray,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color(0xFFAAAAAA),
                                unfocusedTextColor = Color(0xFFAAAAAA)
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        )
                    }
                }
            }
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
        
        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        
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
        
        if (rootElement == null) return@withContext Pair("Could not parse JSON. Make sure Array Path is correct or leave it empty.", "")
        
        val sb = java.lang.StringBuilder()
        sb.append("#EXTM3U\n")
        
        extractChannelsRecursively(rootElement, "Uncategorized", streamField, titleField, logoField, categoryField, prefix, suffix, sb)

        
        // Generate proxy URL
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
        
        val proxyLink = uriBuilder.build().toString()
        
        return@withContext Pair(sb.toString(), proxyLink)
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
    sb: java.lang.StringBuilder
) {
    if (element is JSONArray) {
        for (i in 0 until element.length()) {
            extractChannelsRecursively(element.opt(i), currentCategory, streamField, titleField, logoField, categoryField, prefix, suffix, sb)
        }
    } else if (element is JSONObject) {
        val hasDirectStream = element.has(streamField) || element.has("url") || element.has("stream_url") || element.has("link")
        val hasSourcesObj = element.has("sources") && element.opt("sources") is JSONObject
        
        if (hasDirectStream || hasSourcesObj) {
            val title = element.optString(titleField, element.optString("name", element.optString("title", element.optString("tv_name", "Unknown")))).takeIf { it.isNotBlank() } ?: "Unknown"
            val logo = M3UParser.extractBestLogo(element, logoField)
            val category = element.optString(categoryField, element.optString("group-title", element.optString("category", element.optString("group", currentCategory)))).takeIf { it.isNotBlank() } ?: currentCategory
            
            var stream = element.optString(streamField, element.optString("url", element.optString("stream_url", element.optString("link", "")))).trim()
            if (stream.isEmpty() && hasSourcesObj) {
                try {
                    val sObj = element.getJSONObject("sources")
                    val firstKey = sObj.keys().asSequence().firstOrNull()
                    if (firstKey != null) stream = sObj.optString(firstKey, "").trim()
                } catch (e: Exception) {}
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
            val nextCategory = element.optString(categoryField, "").takeIf { it.isNotBlank() } ?: currentCategory
            val keys = element.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                extractChannelsRecursively(element.opt(key), nextCategory, streamField, titleField, logoField, categoryField, prefix, suffix, sb)
            }
        }
    }
}

