package com.local.mewgenicsradio

import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RemoteRadioManifest(
    val version: Int,
    val baseUrl: String,
    val codec: String,
    val bitrateKbps: Int,
    val radioConfigPath: String = "radio.gon",
    val visualizer: RemoteVisualizerAsset? = null,
    val trackCount: Int = 0,
    val tracks: List<RemoteRadioTrack>,
) {
    val qualityLabel: String
        get() = "${codec.uppercase()} ${bitrateKbps}kbps"

    fun normalizedAgainst(manifestUrl: String): RemoteRadioManifest {
        val manifestBaseUri = URI(manifestUrl)
        val normalizedBaseUrl = manifestBaseUri.resolve(".").toString()
        return copy(
            baseUrl = normalizedBaseUrl,
            visualizer = visualizer?.copy(
                url = manifestBaseUri.resolve(visualizer.relativePath).toString(),
            ),
            tracks = tracks.map { track ->
                track.copy(url = manifestBaseUri.resolve(track.relativePath).toString())
            },
        )
    }
}

@Serializable
data class RemoteRadioTrack(
    val id: String,
    val category: String,
    override val relativePath: String,
    override val url: String,
    override val bytes: Long,
    override val sha256: String,
    val durationMs: Long,
    val codec: String,
    val bitrateKbps: Int,
) : CacheableRemoteAsset

@Serializable
data class RemoteVisualizerAsset(
    val id: String = "music_visualizer",
    override val relativePath: String,
    override val url: String,
    override val bytes: Long,
    override val sha256: String,
) : CacheableRemoteAsset

object RemoteRadioManifestParser {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun parse(raw: String): RemoteRadioManifest = json.decodeFromString(raw)
}
