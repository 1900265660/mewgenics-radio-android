package com.local.mewgenicsradio

import android.content.Context
import java.io.IOException
import java.net.URL

class RadioRepository(
    private val context: Context,
) {
    fun loadConfig(manifest: RemoteRadioManifest?): RadioConfig {
        val raw = runCatching {
            context.assets.open("radio/radio.gon").bufferedReader().use { it.readText() }
        }.getOrElse {
            val remoteConfigUrl = manifest?.radioConfigUrl()
                ?: throw IllegalStateException("radio.gon is missing and no remote manifest is configured.")
            URL(remoteConfigUrl).openStream().bufferedReader().use { it.readText() }
        }
        return RadioConfigParser().parse(raw)
    }

    fun loadManifest(): RemoteRadioManifest? {
        val bundled = runCatching {
            context.assets.open("radio/manifest.json").bufferedReader().use { it.readText() }
        }.getOrNull()

        if (!bundled.isNullOrBlank()) {
            return RemoteRadioManifestParser.parse(bundled)
        }

        return null
    }

    fun loadRemoteManifest(manifestUrl: String): RemoteRadioManifest {
        val raw = URL(manifestUrl).openStream().bufferedReader().use { it.readText() }
        return RemoteRadioManifestParser.parse(raw)
    }

    fun loadCatalog(manifest: RemoteRadioManifest?): RadioAssetCatalog {
        val remoteTracks = manifest.orEmptyTracks()
        val bundledTracks = listAssets("radio/audio/music/radio")
            .filter { it.endsWith(".ogg", ignoreCase = true) }
            .mapNotNull { path ->
                val parts = path.split("/")
                val category = parts.getOrNull(4) ?: return@mapNotNull null
                val filename = parts.last()
                val id = filename.substringBeforeLast(".")

                RadioTrack(
                    id = id,
                    category = category,
                    assetPath = path,
                )
            }

        // Put remote tracks first so the release path wins when both sources exist.
        return RadioAssetCatalog(remoteTracks + bundledTracks)
    }

    private fun listAssets(path: String): List<String> {
        val names = try {
            context.assets.list(path).orEmpty()
        } catch (_: IOException) {
            emptyArray()
        }

        if (names.isEmpty()) return listOf(path)

        return names.flatMap { name ->
            listAssets("$path/$name")
        }
    }

    private fun RemoteRadioManifest?.orEmptyTracks(): List<RadioTrack> =
        this?.tracks.orEmpty().map { track ->
            RadioTrack(
                id = track.id,
                category = track.category,
                assetPath = null,
                remote = track,
            )
        }

    private fun RemoteRadioManifest.radioConfigUrl(): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return "$base$radioConfigPath"
    }
}
