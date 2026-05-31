package com.myselfhridoy.streambdplayer.ui.screens.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.myselfhridoy.streambdplayer.data.models.StreamSource
import com.myselfhridoy.streambdplayer.ui.navigation.Routes
import com.myselfhridoy.streambdplayer.ui.theme.DarkNavyBackground
import com.myselfhridoy.streambdplayer.ui.theme.DarkNavySurface
import com.myselfhridoy.streambdplayer.ui.theme.TealAccent
import com.myselfhridoy.streambdplayer.ui.theme.WhiteText
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel
import com.myselfhridoy.streambdplayer.ui.viewmodels.TmdbViewModel

@Composable
fun TmdbDetailsScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val media by tmdbViewModel.selectedMedia.collectAsState()
    val isResolving by mainViewModel.isResolvingStreams.collectAsState()
    val streams by mainViewModel.availableStreams.collectAsState()

    if (media == null) return

    val m = media!!

    // Reset streams when entering a new media
    LaunchedEffect(m.id) {
        // Optionally clear previous streams here
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
            .padding(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Poster
            AsyncImage(
                model = m.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(180.dp)
                    .height(270.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            
            Spacer(modifier = Modifier.width(24.dp))
            
            // Details
            Column {
                Text(
                    text = m.displayTitle,
                    color = WhiteText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "${if (m.isMovie) "Movie" else "TV Show"} • ${m.displayDate}",
                    color = TealAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = m.overview ?: "No overview available.",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    maxLines = 5
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                var btnFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        val type = if (m.isMovie) "movie" else "tv"
                        mainViewModel.fetchVodStreams(context, type, m.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                    modifier = Modifier
                        .onFocusChanged { btnFocused = it.isFocused }
                        .border(
                            width = if (btnFocused) 3.dp else 0.dp,
                            color = if (btnFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Text("Watch Now", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Streams Section
        if (isResolving) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TealAccent)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Searching Addons for streams...", color = WhiteText)
                }
            }
        } else if (streams.isNotEmpty()) {
            Text(
                text = "Available Streams",
                color = WhiteText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(streams) { stream ->
                    StreamCard(stream = stream) {
                        mainViewModel.selectStream(stream)
                        navController.navigate(Routes.VOD_PLAYER)
                    }
                }
            }
        }
    }
}

@Composable
fun StreamCard(stream: StreamSource, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) TealAccent else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stream.provider,
                    color = WhiteText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stream.quality,
                    color = TealAccent,
                    fontSize = 14.sp
                )
            }
            Text(
                text = "▶ Play",
                color = WhiteText,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
