package com.local.mewgenicsradio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class RadioPlayer(
    context: Context,
    private val onEnded: () -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val player = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    mainHandler.post(onEnded)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                mainHandler.post { onError(error) }
            }
        })
    }

    val isPlaying: Boolean
        get() = player.isPlaying

    fun play(resolved: ResolvedPlayback) {
        val item = MediaItem.fromUri(resolved.uri)
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun release() {
        player.release()
    }
}
