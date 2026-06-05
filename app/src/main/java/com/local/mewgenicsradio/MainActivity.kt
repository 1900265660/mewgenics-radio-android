package com.local.mewgenicsradio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
                onCacheSongs = viewModel::cacheAllSongs,
                onCacheRadio = viewModel::cacheAllRadio,
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
    onCacheSongs: () -> Unit,
    onCacheRadio: () -> Unit,
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF16140F),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = onClearCache,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Clear Cache")
                        }
                        Button(
                            onClick = onCacheSongs,
                            modifier = Modifier.weight(1f),
                            enabled = state.isReady,
                        ) {
                            Text("Cache Songs")
                        }
                    }

                    Button(
                        onClick = onCacheRadio,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.isReady,
                    ) {
                        Text("Cache Full Radio")
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioPanel(state: PlayerUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF242014))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (state.isPlaying) Color(0xFFF05E3E) else Color(0xFF5C5545)),
            )
            Text(
                text = state.message,
                color = Color(0xFFF4EAD0),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = state.currentSegment?.track?.id ?: "No segment loaded",
            color = Color(0xFFFFFFFF),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = state.currentSegment?.let {
                "${it.stateName} / ${it.track.category}"
            } ?: state.mode.name,
            color = Color(0xFFC5BFA8),
            style = MaterialTheme.typography.bodyLarge,
        )

        Text(
            text = "Source: ${state.currentSource.name}  Quality: ${state.qualityLabel}",
            color = Color(0xFFC5BFA8),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = "Cache: ${formatBytes(state.cacheBytes)}",
            color = Color(0xFFC5BFA8),
            style = MaterialTheme.typography.bodyMedium,
        )
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
