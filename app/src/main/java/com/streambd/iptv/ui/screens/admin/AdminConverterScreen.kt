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
    
    var outputM3u by remember { mutableStateOf("") }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

            Button(
                onClick = {
                    if (jsonUrl.isNotEmpty()) {
                        isLoading = true
                        scope.launch {
                            val m3u = fetchAndConvert(
                                jsonUrl, arrayPath, streamField, titleField, logoField, categoryField, prefix, suffix
                            )
                            outputM3u = m3u
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
    suffix: String
): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) return@withContext "Error: ${response.code}"
        
        val body = response.body?.string() ?: return@withContext "Empty response"
        
        var jsonArray: JSONArray? = null
        if (body.trim().startsWith("[")) {
            jsonArray = JSONArray(body)
        } else if (body.trim().startsWith("{")) {
            val obj = JSONObject(body)
            if (arrayPath.isNotEmpty() && obj.has(arrayPath)) {
                jsonArray = obj.getJSONArray(arrayPath)
            }
        }
        
        if (jsonArray == null) return@withContext "Could not find JSON Array. Make sure Array Path is correct."
        
        val sb = java.lang.StringBuilder()
        sb.append("#EXTM3U\n")
        
        for (i in 0 until jsonArray.length()) {
            try {
                val item = jsonArray.getJSONObject(i)
                val title = item.optString(titleField, "Unknown")
                val logo = item.optString(logoField, "")
                val category = item.optString(categoryField, "Uncategorized")
                
                var stream = item.optString(streamField, "")
                if (stream.isEmpty()) continue
                
                // Url encode stream name if prefix suggests it is a query parameter
                if (streamField == "stream_name" && prefix.contains("channel=")) {
                     stream = java.net.URLEncoder.encode(stream, "UTF-8")
                }
                
                val finalUrl = "$prefix$stream$suffix"
                
                sb.append("#EXTINF:-1 tvg-logo=\"$logo\" group-title=\"$category\",$title\n")
                sb.append("$finalUrl\n")
            } catch (e: Exception) { }
        }
        
        return@withContext sb.toString()
    } catch (e: Exception) {
        return@withContext "Error: ${e.message}"
    }
}
