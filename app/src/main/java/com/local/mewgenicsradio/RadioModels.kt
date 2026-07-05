package com.local.mewgenicsradio

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
sealed interface CacheableRemoteAsset {
    val relativePath: String
    val url: String
    val bytes: Long
    val sha256: String
}

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
    val visualizerStyle: RadioVisualizerStyle = RadioVisualizerStyle(),
)

@Serializable
data class RadioVisualizerStyle(
    val motionScale: Float = 1.0f,
    val contrast: Float = 1.0f,
    val cloudCount: Int = 4,
    val atomCount: Int = 10,
    val catprintColumns: Int = 6,
    val catPilePeaks: Int = 5,
    val palette: RadioVisualizerPalette = RadioVisualizerPalette(),
) {
    val normalizedMotionScale: Float
        get() = motionScale.coerceIn(0.5f, 2.0f)

    val normalizedContrast: Float
        get() = contrast.coerceIn(0.7f, 1.5f)

    val normalizedCloudCount: Int
        get() = cloudCount.coerceIn(2, 8)

    val normalizedAtomCount: Int
        get() = atomCount.coerceIn(4, 18)

    val normalizedCatprintColumns: Int
        get() = catprintColumns.coerceIn(3, 10)

    val normalizedCatPilePeaks: Int
        get() = catPilePeaks.coerceIn(3, 8)
}

@Serializable
data class RadioVisualizerPalette(
    val skyTop: String = "#2A1637",
    val skyBottom: String = "#130E1C",
    val cloud: String = "#735E97",
    val catprint: String = "#D9976C",
    val atom: String = "#F3D46B",
    val pile: String = "#3D2A2B",
    val pileAccent: String = "#70524B",
    val overlay: String = "#F4EAD0",
)

fun Float.visualizerAlpha(): Float = coerceIn(0f, 1f)

fun Int.visualizerLerp(other: Int, fraction: Float): Int =
    (this + ((other - this) * fraction)).roundToInt()
