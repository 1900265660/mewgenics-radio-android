package com.local.mewgenicsradio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RemoteRadioManifest(
    val version: Int,
    val baseUrl: String,
    val codec: String,
    val bitrateKbps: Int,
    val radioConfigPath: String = "radio.gon",
    val trackCount: Int = 0,
    val tracks: List<RemoteRadioTrack>,
) {
    val qualityLabel: String
        get() = "${codec.uppercase()} ${bitrateKbps}kbps"
}

@Serializable
data class RemoteRadioTrack(
    val id: String,
    val category: String,
    val relativePath: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
    val durationMs: Long,
    val codec: String,
    val bitrateKbps: Int,
)

object RemoteRadioManifestParser {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun parse(raw: String): RemoteRadioManifest = json.decodeFromString(raw)
}
