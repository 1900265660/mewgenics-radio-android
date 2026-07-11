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
    private var timeoutRunnable: Runnable? = null
    private val prepareTimeoutMs = 15_000L  // 15 秒超时

    private val player = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        cancelTimeout()
                        mainHandler.post(onEnded)
                    }
                    Player.STATE_READY -> {
                        // 准备完成，取消超时检测
                        cancelTimeout()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                cancelTimeout()
                mainHandler.post { onError(error) }
            }
        })
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun scheduleTimeout() {
        cancelTimeout()
        timeoutRunnable = Runnable {
            onError(Exception("Playback preparation timeout (${prepareTimeoutMs}ms)"))
        }
        mainHandler.postDelayed(timeoutRunnable!!, prepareTimeoutMs)
    }

    val isPlaying: Boolean
        get() = player.isPlaying

    fun play(resolved: ResolvedPlayback) {
        val item = MediaItem.fromUri(resolved.uri)
        player.setMediaItem(item)
        player.prepare()
        scheduleTimeout()  // 开始超时检测
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
        cancelTimeout()
        player.release()
    }
}
