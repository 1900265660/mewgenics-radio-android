package com.local.mewgenicsradio

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class RadioCacheManager(
    private val cacheRoot: File,
) {
    init {
        cacheRoot.mkdirs()
    }

    fun cachedFileFor(asset: CacheableRemoteAsset): File =
        File(cacheRoot, asset.relativePath.replace("/", File.separator))

    fun cacheSizeBytes(): Long =
        cacheRoot.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

    fun clear() {
        if (cacheRoot.exists()) {
            cacheRoot.deleteRecursively()
        }
        cacheRoot.mkdirs()
    }

    fun isCached(asset: CacheableRemoteAsset): Boolean {
        val file = cachedFileFor(asset)
        return file.isFile &&
            file.length() == asset.bytes &&
            sha256(file).equals(asset.sha256, ignoreCase = true)
    }

    fun cacheTrack(track: RemoteRadioTrack): File = cacheAsset(track)

    fun cacheAsset(asset: CacheableRemoteAsset): File {
        val target = cachedFileFor(asset)
        if (isCached(asset)) return target

        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.download")
        if (temp.exists()) temp.delete()

        val connection = URL(asset.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true

        try {
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }

        if (temp.length() != asset.bytes) {
            temp.delete()
            throw IllegalStateException("Downloaded size mismatch for ${asset.relativePath}")
        }

        val downloadedHash = sha256(temp)
        if (!downloadedHash.equals(asset.sha256, ignoreCase = true)) {
            temp.delete()
            throw IllegalStateException("Downloaded hash mismatch for ${asset.relativePath}")
        }

        if (target.exists()) target.delete()
        temp.renameTo(target)
        return target
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
