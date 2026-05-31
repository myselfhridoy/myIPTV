package com.myselfhridoy.streambdplayer.ui.screens

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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.myselfhridoy.streambdplayer.data.models.StreamSource
import com.myselfhridoy.streambdplayer.ui.navigation.Routes
import com.myselfhridoy.streambdplayer.ui.theme.DarkNavyBackground
import com.myselfhridoy.streambdplayer.ui.theme.DarkNavySurface
import com.myselfhridoy.streambdplayer.ui.theme.TealAccent
import com.myselfhridoy.streambdplayer.ui.theme.WhiteText
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel

@Composable
fun VodBrowseScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    val isResolving by viewModel.isResolvingStreams.collectAsState()
    val streams by viewModel.availableStreams.collectAsState()
    val addon by viewModel.selectedAddon.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
            .padding(16.dp)
    ) {
        Text(
            text = "Browse: ${addon?.name ?: "Unknown"}",
            color = WhiteText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        var btnFocused by remember { mutableStateOf(false) }
        Button(
            onClick = {
                // Fetch streams for a test movie (Deadpool: TMDB ID 293660)
                viewModel.fetchVodStreams(context, "movie", 293660)
            },
            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { btnFocused = it.isFocused }
                .border(
                    width = if (btnFocused) 3.dp else 0.dp,
                    color = if (btnFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(50)
                )
        ) {
            Text("Fetch Test Movie (Deadpool)", color = Color.White)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isResolving) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TealAccent)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Resolving streams from Javascript Addon...", color = WhiteText)
                }
            }
        } else if (streams.isNotEmpty()) {
            Text(
                text = "Available Streams",
                color = WhiteText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(streams) { stream ->
                    StreamCard(stream = stream) {
                        viewModel.selectStream(stream)
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
