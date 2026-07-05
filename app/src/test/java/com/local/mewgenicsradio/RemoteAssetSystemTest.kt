package com.local.mewgenicsradio

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemoteAssetSystemTest {
    @Test
    fun manifestParserReadsTracksAndQuality() {
        val manifest = RemoteRadioManifestParser.parse(
            """
            {
              "version": 1,
              "baseUrl": "https://cdn.example/radio/v1/",
              "codec": "opus",
              "bitrateKbps": 128,
              "radioConfigPath": "radio.gon",
              "visualizer": {
                "id": "music_visualizer",
                "relativePath": "visualizer/music_visualizer.json",
                "url": "https://cdn.example/radio/v1/visualizer/music_visualizer.json",
                "bytes": 4,
                "sha256": "hash"
              },
              "trackCount": 1,
              "tracks": [
                {
                  "id": "catsanova",
                  "category": "songs",
                  "relativePath": "audio/music/radio/songs/catsanova.opus",
                  "url": "https://cdn.example/radio/v1/audio/music/radio/songs/catsanova.opus",
                  "bytes": 4,
                  "sha256": "hash",
                  "durationMs": 1000,
                  "codec": "opus",
                  "bitrateKbps": 128
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("OPUS 128kbps", manifest.qualityLabel)
        assertEquals(1, manifest.tracks.size)
        assertEquals("catsanova", manifest.tracks.first().id)
        assertEquals("music_visualizer", manifest.visualizer?.id)
    }

    @Test
    fun manifestNormalizationRewritesStaleBaseUrlAndTrackUrls() {
        val manifest = RemoteRadioManifestParser.parse(
            """
            {
              "version": 1,
              "baseUrl": "http://10.99.239.143:8088/",
              "codec": "opus",
              "bitrateKbps": 128,
              "radioConfigPath": "radio.gon",
              "visualizer": {
                "id": "music_visualizer",
                "relativePath": "visualizer/music_visualizer.json",
                "url": "http://10.99.239.143:8088/visualizer/music_visualizer.json",
                "bytes": 4,
                "sha256": "hash"
              },
              "trackCount": 1,
              "tracks": [
                {
                  "id": "catsanova",
                  "category": "songs",
                  "relativePath": "audio/music/radio/songs/catsanova.opus",
                  "url": "http://10.99.239.143:8088/audio/music/radio/songs/catsanova.opus",
                  "bytes": 4,
                  "sha256": "hash",
                  "durationMs": 1000,
                  "codec": "opus",
                  "bitrateKbps": 128
                }
              ]
            }
            """.trimIndent(),
        )

        val normalized = manifest.normalizedAgainst("http://127.0.0.1:8088/manifest.json")

        assertEquals("http://127.0.0.1:8088/", normalized.baseUrl)
        assertEquals("http://127.0.0.1:8088/audio/music/radio/songs/catsanova.opus", normalized.tracks.first().url)
        assertEquals("http://127.0.0.1:8088/visualizer/music_visualizer.json", normalized.visualizer?.url)
    }

    @Test
    fun cacheManagerDetectsHitMissMismatchAndClear() {
        val root = createTempDir(prefix = "radio-cache-test")
        try {
            val content = "meow".toByteArray()
            val hash = sha256(content)
            val track = RemoteRadioTrack(
                id = "catsanova",
                category = "songs",
                relativePath = "audio/music/radio/songs/catsanova.opus",
                url = "https://cdn.example/catsanova.opus",
                bytes = content.size.toLong(),
                sha256 = hash,
                durationMs = 1000,
                codec = "opus",
                bitrateKbps = 128,
            )
            val cache = RadioCacheManager(root)

            assertFalse(cache.isCached(track))

            val file = cache.cachedFileFor(track)
            file.parentFile?.mkdirs()
            file.writeBytes(content)

            assertTrue(cache.isCached(track))
            assertEquals(content.size.toLong(), cache.cacheSizeBytes())

            file.writeText("wrong")
            assertFalse(cache.isCached(track))

            cache.clear()
            assertEquals(0L, cache.cacheSizeBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolverPrefersValidCacheThenRemoteThenBundled() {
        val root = createTempDir(prefix = "radio-resolver-test")
        try {
            val content = "meow".toByteArray()
            val remote = RemoteRadioTrack(
                id = "catsanova",
                category = "songs",
                relativePath = "audio/music/radio/songs/catsanova.opus",
                url = "https://cdn.example/catsanova.opus",
                bytes = content.size.toLong(),
                sha256 = sha256(content),
                durationMs = 1000,
                codec = "opus",
                bitrateKbps = 128,
            )
            val cache = RadioCacheManager(root)
            val resolver = RadioAssetResolver(cache)
            val remoteSegment = PlaybackSegment("song", RadioTrack("catsanova", "songs", remote = remote))

            val remoteResolved = resolver.resolve(remoteSegment)
            assertNotNull(remoteResolved)
            assertEquals(PlaybackSource.Remote, remoteResolved.source)

            val cached = cache.cachedFileFor(remote)
            cached.parentFile?.mkdirs()
            cached.writeBytes(content)

            val cachedResolved = resolver.resolve(remoteSegment)
            assertNotNull(cachedResolved)
            assertEquals(PlaybackSource.Cached, cachedResolved.source)

            val bundledSegment = PlaybackSegment(
                "song",
                RadioTrack("catsanova", "songs", assetPath = "radio/audio/music/radio/songs/catsanova.ogg"),
            )
            val bundledResolved = resolver.resolve(bundledSegment)
            assertNotNull(bundledResolved)
            assertEquals(PlaybackSource.Bundled, bundledResolved.source)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun visualizerParserClampsExtremeValues() {
        val style = RadioVisualizerStyleParser.parse(
            """
            {
              "motionScale": 9.0,
              "contrast": 0.2,
              "cloudCount": 20,
              "atomCount": 1,
              "catprintColumns": 99,
              "catPilePeaks": 2
            }
            """.trimIndent(),
        )

        assertEquals(2.0f, style.normalizedMotionScale)
        assertEquals(0.7f, style.normalizedContrast)
        assertEquals(8, style.normalizedCloudCount)
        assertEquals(4, style.normalizedAtomCount)
        assertEquals(10, style.normalizedCatprintColumns)
        assertEquals(3, style.normalizedCatPilePeaks)
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
