package com.local.mewgenicsradio

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RadioConfigParserTest {
    private val sample = """
        all [
            catsanova
            battle_of_the_fleabags
            //locked_song
        ]

        locked [
            locked_song //world
        ]

        tutorial_radio [
            catsanova
        ]

        radio_state_machine {
            begin {
                next [call_letter intro song song]
            }

            song {
                playlist songs
                next [intro outro song]
            }

            intro {
                special_playlist intros
                next [song]
            }

            songs_only {
                playlist songs
                next [songs_only]
            }
        }
    """.trimIndent()

    @Test
    fun parserReadsPlaylistsAndStateMachine() {
        val config = RadioConfigParser().parse(sample)

        assertEquals(listOf("catsanova", "battle_of_the_fleabags"), config.allSongs)
        assertEquals(listOf("locked_song"), config.lockedSongs)
        assertEquals(listOf("catsanova"), config.tutorialSongs)

        assertNotNull(config.stateMachine["begin"])
        assertEquals("songs", config.stateMachine["song"]?.playlist)
        assertEquals("intros", config.stateMachine["intro"]?.specialPlaylist)
        assertEquals(listOf("call_letter", "intro", "song", "song"), config.stateMachine["begin"]?.nextStates)
    }

    @Test
    fun schedulerUsesSongsOnlyMode() {
        val config = RadioConfigParser().parse(sample)
        val catalog = RadioAssetCatalog(
            listOf(
                RadioTrack("catsanova", "songs", "radio/audio/music/radio/songs/catsanova.ogg"),
                RadioTrack("battle_of_the_fleabags", "songs", "radio/audio/music/radio/songs/battle_of_the_fleabags.ogg"),
                RadioTrack("catsanova_1", "intros", "radio/audio/music/radio/intros/catsanova_1.ogg"),
            ),
        )
        val scheduler = RadioScheduler(config, catalog, Random(7))

        repeat(100) {
            val segment = scheduler.next(PlaybackMode.SongsOnly)
            assertNotNull(segment)
            assertEquals("songs_only", segment.stateName)
            assertEquals("songs", segment.track.category)
        }
    }

    @Test
    fun schedulerProducesExistingTracks() {
        val config = RadioConfigParser().parse(sample)
        val validPaths = setOf(
            "radio/audio/music/radio/songs/catsanova.ogg",
            "radio/audio/music/radio/songs/battle_of_the_fleabags.ogg",
            "radio/audio/music/radio/intros/catsanova_1.ogg",
            "radio/audio/music/radio/intros/battle_of_the_fleabags_1.ogg",
        )
        val catalog = RadioAssetCatalog(
            validPaths.map { path ->
                val parts = path.split("/")
                RadioTrack(
                    id = parts.last().substringBeforeLast("."),
                    category = parts[4],
                    assetPath = path,
                )
            },
        )
        val scheduler = RadioScheduler(config, catalog, Random(1))

        repeat(100) {
            val segment = scheduler.next(PlaybackMode.FullRadio)
            assertNotNull(segment)
            assertTrue(segment.track.assetPath in validPaths)
        }
    }
}
