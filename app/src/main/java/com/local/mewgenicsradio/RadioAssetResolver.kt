package com.local.mewgenicsradio

class RadioAssetResolver(
    private val cacheManager: RadioCacheManager,
) {
    fun resolve(segment: PlaybackSegment): ResolvedPlayback? {
        val remote = segment.track.remote
        if (remote != null) {
            val cached = cacheManager.cachedFileFor(remote)
            if (cacheManager.isCached(remote)) {
                return ResolvedPlayback(
                    segment = segment,
                    uri = cached.toURI().toString(),
                    source = PlaybackSource.Cached,
                )
            }

            if (remote.url.isNotBlank()) {
                return ResolvedPlayback(
                    segment = segment,
                    uri = remote.url,
                    source = PlaybackSource.Remote,
                )
            }
        }

        val assetPath = segment.track.assetPath
        if (!assetPath.isNullOrBlank()) {
            return ResolvedPlayback(
                segment = segment,
                uri = "asset:///$assetPath",
                source = PlaybackSource.Bundled,
            )
        }

        return null
    }
}
