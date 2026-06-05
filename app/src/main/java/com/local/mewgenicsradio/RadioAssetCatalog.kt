package com.local.mewgenicsradio

class RadioAssetCatalog(
    tracks: List<RadioTrack>,
) {
    private val preferredTracks = tracks.distinctBy { "${it.category}/${it.id}" }
    private val byCategory = preferredTracks.groupBy { it.category }
    private val bySongId = byCategory["songs"].orEmpty().associateBy { it.id }

    val allTracks: List<RadioTrack>
        get() = preferredTracks

    fun songsFor(config: RadioConfig): List<RadioTrack> {
        val configured = config.allSongs.mapNotNull { bySongId[it] }
        return configured.ifEmpty { byCategory["songs"].orEmpty() }
    }

    fun tracksForCategory(category: String): List<RadioTrack> =
        byCategory[category].orEmpty()

    fun specialTracks(category: String, songId: String?): List<RadioTrack> {
        if (songId.isNullOrBlank()) return tracksForCategory(category)

        val prefix = "${songId}_"
        val radioPrefix = "radio_${songId}_"
        val exactRadio = "radio_$songId"

        return tracksForCategory(category).filter { track ->
            track.id == songId ||
                track.id == exactRadio ||
                track.id.startsWith(prefix) ||
                track.id.startsWith(radioPrefix)
        }.ifEmpty {
            tracksForCategory(category)
        }
    }

    fun hasAnyTracks(): Boolean = byCategory.values.any { it.isNotEmpty() }
}
