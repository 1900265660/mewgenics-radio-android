package com.local.mewgenicsradio

import kotlin.random.Random

class RadioScheduler(
    private val config: RadioConfig,
    private val catalog: RadioAssetCatalog,
    private val random: Random = Random.Default,
) {
    private var currentState = "begin"
    private var lastSongId: String? = null
    private var queuedSong: RadioTrack? = null

    fun next(mode: PlaybackMode): PlaybackSegment? {
        repeat(12) {
            val stateName = if (mode == PlaybackMode.SongsOnly) {
                "songs_only"
            } else {
                chooseNextState(currentState)
            }

            currentState = stateName
            val definition = config.stateMachine[stateName]
            val segment = pickSegment(definition, stateName, mode)
            if (segment != null) {
                if (segment.track.category == "songs") {
                    lastSongId = segment.track.id
                }
                return segment
            }
        }

        return catalog.songsFor(config).randomOrNull(random)?.let { track ->
            lastSongId = track.id
            PlaybackSegment(stateName = "song", track = track)
        }
    }

    private fun chooseNextState(from: String): String {
        val current = config.stateMachine[from] ?: config.stateMachine["begin"]
        val nextStates = current?.nextStates.orEmpty().ifEmpty { listOf("song") }
        return nextStates.random(random)
    }

    private fun pickSegment(
        definition: RadioStateDefinition?,
        stateName: String,
        mode: PlaybackMode,
    ): PlaybackSegment? {
        if (mode == PlaybackMode.SongsOnly || stateName == "songs_only") {
            return catalog.songsFor(config)
                .randomOrNull(random)
                ?.let { PlaybackSegment(stateName = stateName, track = it, associatedSongId = it.id) }
        }

        val playlist = definition?.playlist
        if (playlist != null) {
            if (playlist == "songs") {
                val track = queuedSong ?: catalog.songsFor(config).randomOrNull(random)
                queuedSong = null
                return track?.let {
                    PlaybackSegment(stateName = stateName, track = it, associatedSongId = it.id)
                }
            }
            return catalog.tracksForCategory(playlist)
                .randomOrNull(random)
                ?.let { PlaybackSegment(stateName = stateName, track = it) }
        }

        val specialPlaylist = definition?.specialPlaylist
        if (specialPlaylist != null) {
            val songId = if (definition.announcesUpcomingSong(stateName)) {
                val song = queuedSong ?: catalog.songsFor(config).randomOrNull(random)
                queuedSong = song
                song?.id
            } else {
                lastSongId
            }
            return catalog.specialTracks(specialPlaylist, songId)
                .randomOrNull(random)
                ?.let { PlaybackSegment(stateName = stateName, track = it, associatedSongId = songId) }
        }

        return null
    }

    private fun RadioStateDefinition.announcesUpcomingSong(stateName: String): Boolean =
        nextStates.contains("song") || stateName.contains("intro", ignoreCase = true)
}
