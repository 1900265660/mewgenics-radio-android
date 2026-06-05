package com.local.mewgenicsradio

enum class PlaybackMode {
    FullRadio,
    SongsOnly,
}

data class RadioStateDefinition(
    val name: String,
    val playlist: String? = null,
    val specialPlaylist: String? = null,
    val nextStates: List<String> = emptyList(),
)

data class RadioConfig(
    val allSongs: List<String>,
    val lockedSongs: List<String>,
    val tutorialSongs: List<String>,
    val stateMachine: Map<String, RadioStateDefinition>,
)

data class RadioTrack(
    val id: String,
    val category: String,
    val assetPath: String? = null,
    val remote: RemoteRadioTrack? = null,
)

data class PlaybackSegment(
    val stateName: String,
    val track: RadioTrack,
)

enum class PlaybackSource {
    Bundled,
    Cached,
    Remote,
    Missing,
}

data class ResolvedPlayback(
    val segment: PlaybackSegment,
    val uri: String,
    val source: PlaybackSource,
)

data class PlayerUiState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val mode: PlaybackMode = PlaybackMode.FullRadio,
    val currentSegment: PlaybackSegment? = null,
    val currentSource: PlaybackSource = PlaybackSource.Missing,
    val cacheBytes: Long = 0,
    val qualityLabel: String = "Bundled",
    val message: String = "Extract radio assets before launching the app.",
)
