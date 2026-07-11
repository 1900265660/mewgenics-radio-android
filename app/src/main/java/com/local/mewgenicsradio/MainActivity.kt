package com.local.mewgenicsradio

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

private const val ProjectGithubUrl = "https://github.com/1900265660/mewgenics-radio-android"

class MainActivity : ComponentActivity() {
    private val viewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsState()
            RadioApp(
                state = state,
                onPlayPause = viewModel::togglePlayback,
                onNext = viewModel::playNext,
                onModeChange = viewModel::setMode,
                onClearCache = viewModel::clearCache,
                onCacheCurrent = viewModel::cacheCurrentTrack,
            )
        }
    }
}

@Composable
fun RadioApp(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onModeChange: (PlaybackMode) -> Unit,
    onClearCache: () -> Unit,
    onCacheCurrent: () -> Unit,
) {
    MaterialTheme {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                BackendDrawer(
                    state = state,
                    onClearCache = onClearCache,
                    onCacheCurrent = onCacheCurrent,
                )
            },
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF16140F),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF16140F)),
                ) {
                    VideoPlayer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0x1A110D10))
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column {
                                Text(
                                    text = "WMEW",
                                    color = Color(0xFFF7D36A),
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Black,
                                )
                                Text(
                                    text = "99.9 Lives",
                                    color = Color(0xFFC5BFA8),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }

                            TextButton(
                                onClick = { scope.launch { drawerState.open() } },
                            ) {
                                Text("Backend")
                            }
                        }

                        RadioPanel(state = state)

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Button(
                                    onClick = onPlayPause,
                                    modifier = Modifier.weight(1f),
                                    enabled = state.isReady,
                                ) {
                                    Text(if (state.isPlaying) "Pause" else "Play")
                                }
                                Button(
                                    onClick = onNext,
                                    modifier = Modifier.weight(1f),
                                    enabled = state.isReady,
                                ) {
                                    Text("Next")
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Button(
                                    onClick = { onModeChange(PlaybackMode.FullRadio) },
                                    modifier = Modifier.weight(1f),
                                    enabled = state.mode != PlaybackMode.FullRadio,
                                ) {
                                    Text("Full Radio")
                                }
                                Button(
                                    onClick = { onModeChange(PlaybackMode.SongsOnly) },
                                    modifier = Modifier.weight(1f),
                                    enabled = state.mode != PlaybackMode.SongsOnly,
                                ) {
                                    Text("Songs Only")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val videoUri = remember(context) {
        Uri.parse("android.resource://${context.packageName}/${R.raw.mewgenics_loop}")
    }
    val player = remember(context, videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                this.player = player
            }
        },
        update = { playerView ->
            playerView.player = player
        },
    )
}

@Composable
private fun RadioPanel(state: PlayerUiState) {
    val visibleSong = state.currentSegment?.associatedSongId
        ?: state.currentSegment
            ?.takeIf { it.track.category == "songs" }
            ?.track
            ?.id
    val label = when {
        visibleSong != null && state.currentSegment?.track?.category != "songs" -> "Up Next"
        visibleSong != null -> "Now Playing"
        else -> "No Song Loaded"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xB3242014))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (state.isPlaying) Color(0xFFF05E3E) else Color(0xFF5C5545)),
            )
            Text(
                text = label,
                color = Color(0xFFF4EAD0),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = visibleSong ?: "Open Backend for status",
            color = Color(0xFFFFFFFF),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = if (state.mode == PlaybackMode.SongsOnly) "Songs Only" else "Full Radio",
            color = Color(0xFFC5BFA8),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun BackendDrawer(
    state: PlayerUiState,
    onClearCache: () -> Unit,
    onCacheCurrent: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val report = remember(state) { buildErrorReport(state) }

    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.86f),
        drawerContainerColor = Color(0xFF211D16),
        drawerContentColor = Color(0xFFF4EAD0),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Backend",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(text = state.message, style = MaterialTheme.typography.bodyMedium)

            HorizontalDivider(color = Color(0xFF514735))

            DiagnosticLine("Segment", state.currentSegment?.stateName ?: "none")
            DiagnosticLine("Track", state.currentSegment?.track?.id ?: "none")
            DiagnosticLine("Song", state.currentSegment?.associatedSongId ?: "none")
            DiagnosticLine("Category", state.currentSegment?.track?.category ?: "none")
            DiagnosticLine("Source", state.currentSource.name)
            DiagnosticLine("Quality", state.qualityLabel)
            DiagnosticLine("Cache", formatBytes(state.cacheBytes))
            DiagnosticLine("Visualizer", if (state.isPlaying) "Animated" else "Idle")

            HorizontalDivider(color = Color(0xFF514735))

            Button(
                onClick = onCacheCurrent,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.currentSegment?.track?.remote != null,
            ) {
                Text("Cache Current Track")
            }
            OutlinedButton(
                onClick = onClearCache,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear Cache")
            }

            HorizontalDivider(color = Color(0xFF514735))

            Text(
                text = "Error Report",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.lastError ?: "No errors recorded.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(report)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Copy Error Report")
            }
            OutlinedButton(
                onClick = { uriHandler.openUri(ProjectGithubUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open GitHub Updates")
            }
            Text(
                text = ProjectGithubUrl,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFC5BFA8),
            )
        }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = Color(0xFFC5BFA8),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            color = Color(0xFFF4EAD0),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun buildErrorReport(state: PlayerUiState): String = buildString {
    appendLine("Mewgenics Radio Android Error Report")
    appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("GitHub: $ProjectGithubUrl")
    appendLine("Ready: ${state.isReady}")
    appendLine("Playing: ${state.isPlaying}")
    appendLine("Mode: ${state.mode}")
    appendLine("Status: ${state.message}")
    appendLine("Segment: ${state.currentSegment?.stateName ?: "none"}")
    appendLine("Track: ${state.currentSegment?.track?.id ?: "none"}")
    appendLine("Song: ${state.currentSegment?.associatedSongId ?: "none"}")
    appendLine("Category: ${state.currentSegment?.track?.category ?: "none"}")
    appendLine("Source: ${state.currentSource}")
    appendLine("Quality: ${state.qualityLabel}")
    appendLine("Cache: ${formatBytes(state.cacheBytes)}")
    appendLine("Last Error: ${state.lastError ?: "none"}")
    appendLine("Recent Errors:")
    if (state.errorLog.isEmpty()) {
        appendLine("- none")
    } else {
        state.errorLog.forEach { appendLine("- $it") }
    }
}

private fun formatBytes(bytes: Long): String {
    val mib = bytes / 1024.0 / 1024.0
    return if (mib >= 1.0) {
        "%.1f MiB".format(mib)
    } else {
        "$bytes B"
    }
}
