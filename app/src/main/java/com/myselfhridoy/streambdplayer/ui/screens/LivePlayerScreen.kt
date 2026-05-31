package com.myselfhridoy.streambdplayer.ui.screens

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.myselfhridoy.streambdplayer.ui.screens.components.TrackSelectionDialog
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(
    viewModel: MainViewModel, 
    navController: NavController,
    exoPlayer: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    onEnterPiP: () -> Unit
) {
    val context = LocalContext.current
    val selectedChannel by viewModel.selectedChannel.collectAsState()

    if (selectedChannel == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("No channel selected", color = Color.White)
        }
        return
    }

    val channel = selectedChannel!!
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var isLoading by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showTrackSelector by remember { mutableStateOf(false) }

    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    // Lock Screen Orientation to Landscape
    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }

    // Handle ExoPlayer Media Setup
    LaunchedEffect(channel.url) {
        if (exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() != channel.url) {
            val mediaItem = MediaItem.fromUri(channel.url)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // ExoPlayer Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = playbackState == Player.STATE_BUFFERING
            }
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Progress Tracker
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(1000)
        }
    }

    // Auto-hide overlay
    LaunchedEffect(showOverlay, isPlaying) {
        if (showOverlay && isPlaying && !isLocked) {
            delay(5000)
            showOverlay = false
        }
    }

    // Back Handler
    BackHandler {
        if (isLocked) {
            showOverlay = true
        } else {
            navController.popBackStack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { 
                showOverlay = !showOverlay 
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )

        // Overlay UI
        if (showOverlay) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
                if (isLocked) {
                    // Locked State UI
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color.White)
                    }
                } else {
                    // Unlocked State UI
                    
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(text = channel.name, color = Color.White, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Top Right Icons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { isLocked = true }) {
                                Icon(Icons.Default.Lock, "Lock", tint = Color.White)
                            }
                            IconButton(onClick = { onEnterPiP() }) {
                                Icon(Icons.Default.PictureInPicture, "PiP", tint = Color.White)
                            }
                            IconButton(onClick = { showTrackSelector = true }) {
                                Icon(Icons.Default.Settings, "Tracks", tint = Color.White) // Mockup has multiple icons, using Settings for track
                            }
                        }
                    }

                    // Center Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { exoPlayer.seekTo(exoPlayer.currentPosition - 10000) },
                            modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        
                        IconButton(
                            onClick = {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            modifier = Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 10000) },
                            modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    // Bottom Bar
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatTime(currentPosition), color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { value ->
                                exoPlayer.seekTo((value * duration).toLong())
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.Red,
                                inactiveTrackColor = Color.Gray
                            )
                        )
                        Text(formatTime(duration), color = Color.White, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(Icons.Default.VolumeUp, contentDescription = "Volume", tint = Color.White)
                    }

                    // Share Icon (Right Middle)
                    IconButton(
                        onClick = { /* TODO */ },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 24.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.Default.Share, "Share", tint = Color.White)
                    }
                }
            }
        }

        if (isLoading && !isLocked) {
            CircularProgressIndicator(
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    if (showTrackSelector) {
        TrackSelectionDialog(
            trackSelector = trackSelector,
            currentTracks = exoPlayer.currentTracks,
            onDismiss = { showTrackSelector = false }
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
