package com.local.mewgenicsradio

import kotlin.random.Random

class RadioScheduler(
    private val config: RadioConfig,
    private val catalog: RadioAssetCatalog,
    private val random: Random = Random.Default,
) {
    private var currentState = "begin"
    private var currentSongId: String? = null

    fun next(mode: PlaybackMode): PlaybackSegment? {
        repeat(12) {
            val stateName = if (mode == PlaybackMode.SongsOnly) {
                "songs_only"
            } else {
                chooseNextState(currentState)
            }

            currentState = stateName
            val definition = config.stateMachine[stateName]
            val track = pickTrack(definition, stateName, mode)
            if (track != null) {
                if (track.category == "songs") {
                    currentSongId = track.id
                }
                return PlaybackSegment(stateName = stateName, track = track)
            }
        }

        return catalog.songsFor(config).randomOrNull(random)?.let { track ->
            currentSongId = track.id
            PlaybackSegment(stateName = "song", track = track)
        }
    }

    private fun chooseNextState(from: String): String {
        val current = config.stateMachine[from] ?: config.stateMachine["begin"]
        val nextStates = current?.nextStates.orEmpty().ifEmpty { listOf("song") }
        return nextStates.random(random)
    }

    private fun pickTrack(
        definition: RadioStateDefinition?,
        stateName: String,
        mode: PlaybackMode,
    ): RadioTrack? {
        if (mode == PlaybackMode.SongsOnly || stateName == "songs_only") {
            return catalog.songsFor(config).randomOrNull(random)
        }

        val playlist = definition?.playlist
        if (playlist != null) {
            if (playlist == "songs") {
                return catalog.songsFor(config).randomOrNull(random)
            }
            return catalog.tracksForCategory(playlist).randomOrNull(random)
        }

        val specialPlaylist = definition?.specialPlaylist
        if (specialPlaylist != null) {
            return catalog.specialTracks(specialPlaylist, currentSongId).randomOrNull(random)
        }

        return null
    }
}
