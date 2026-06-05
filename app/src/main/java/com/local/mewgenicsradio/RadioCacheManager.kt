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

    fun cachedFileFor(track: RemoteRadioTrack): File =
        File(cacheRoot, track.relativePath.replace("/", File.separator))

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

    fun isCached(track: RemoteRadioTrack): Boolean {
        val file = cachedFileFor(track)
        return file.isFile &&
            file.length() == track.bytes &&
            sha256(file).equals(track.sha256, ignoreCase = true)
    }

    fun cacheTrack(track: RemoteRadioTrack): File {
        val target = cachedFileFor(track)
        if (isCached(track)) return target

        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.download")
        if (temp.exists()) temp.delete()

        val connection = URL(track.url).openConnection() as HttpURLConnection
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

        if (temp.length() != track.bytes) {
            temp.delete()
            throw IllegalStateException("Downloaded size mismatch for ${track.relativePath}")
        }

        val downloadedHash = sha256(temp)
        if (!downloadedHash.equals(track.sha256, ignoreCase = true)) {
            temp.delete()
            throw IllegalStateException("Downloaded hash mismatch for ${track.relativePath}")
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
